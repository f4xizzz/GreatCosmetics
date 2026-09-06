package com.f4xizzz.greatcosmetics.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.architectury.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Cache client-side da textura FORÇADA do GreatCosmetics (MainConfig#forceTexture, ver
 *  GreatCosmetics#sendForcedResourcePack) — diferente do ServerTextureCache genérico (que cobre
 *  resource-pack/resource-pack-sha1 do server.properties de QUALQUER servidor/mod e continua
 *  intocado): esse aqui é chaveado pelo SERVIDOR (ver ClientServerIdentity), não pela URL do
 *  pacote — trocar de servidor = chave diferente = cache não bate, do jeito que o usuário pediu —
 *  e guarda o arquivo CRIPTOGRAFADO em repouso, não em texto puro.
 *
 *  <p><b>Limite honesto (o mesmo já documentado no sistema de licença):</b> isso NÃO é DRM de
 *  verdade — o próprio mod, rodando no PC do jogador, precisa saber decifrar pra desenhar a
 *  textura na tela, e um arquivo decifrado sempre existe em algum momento na sessão. O objetivo
 *  aqui é dificultar a cópia CASUAL (arrastar a pasta de config pra outro PC, abrir o arquivo num
 *  visualizador de imagem comum) — não impedir um atacante determinado com acesso total à própria
 *  máquina.
 *
 *  <p>A chave AES é derivada de {@link ClientInstanceId} (NUNCA do instanceGuid do
 *  ActivationManager, que é do sistema de licença do SERVIDOR — domínios de segurança
 *  diferentes) + a chave do servidor + o hash do jar atual — ver {@link #perServerKey}. */
public class ClientForcedTextureCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("GreatCosmetics-ForcedTextureCache");
    private static final File DIR = new File(Platform.getConfigFolder().toFile(), "GreatCosmetics/servertextures/forced");
    private static final File TMP_DIR = new File(DIR, "tmp");
    private static final File INDEX_FILE = new File(DIR, "index.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type INDEX_TYPE = new TypeToken<Map<String, Entry>>() {}.getType();
    private static final SecureRandom RANDOM = new SecureRandom();

    private static Map<String, Entry> index;
    private static final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public static class Entry {
        public String serverAddress;      // só pra debug humano — a chave de verdade é o hash do servidor
        public String declaredTextureSha1;
        public String encryptedFileName;
        public long lastSeenMillis;
    }

    private static synchronized void loadIndexIfNeeded() {
        if (index != null) return;
        index = new HashMap<>();
        if (INDEX_FILE.exists()) {
            try (FileReader reader = new FileReader(INDEX_FILE)) {
                Map<String, Entry> loaded = GSON.fromJson(reader, INDEX_TYPE);
                if (loaded != null) index = loaded;
            } catch (Exception e) {
                LOGGER.warn("Failed to read {} — the forced-texture cache will be rebuilt from scratch.", INDEX_FILE, e);
            }
        }
    }

    private static synchronized void saveIndex() {
        DIR.mkdirs();
        try (FileWriter writer = new FileWriter(INDEX_FILE)) {
            GSON.toJson(index, writer);
        } catch (Exception e) {
            LOGGER.warn("Failed to save {} — the cache will not survive a restart.", INDEX_FILE, e);
        }
    }

    public static synchronized Entry getEntry(String serverKey) {
        if (serverKey == null) return null;
        loadIndexIfNeeded();
        return index.get(serverKey);
    }

    public static boolean hasEncryptedFile(Entry entry) {
        return entry != null && entry.encryptedFileName != null && new File(DIR, entry.encryptedFileName).exists();
    }

    /** Decifra o arquivo em cache pra um arquivo temporário (vive só durante a sessão — ver
     *  cleanupTempDir()) pronto pra ServerResourcePackLoaderMixin passar pro
     *  addResourcePack(UUID, Path). Fail-open: qualquer problema (arquivo corrompido,
     *  client_instance.id resetado, jar atualizado) devolve null, e quem chamou deve cair pro
     *  download normal — nunca travar a conexão por causa disso. */
    public static Path decryptToTempFile(String serverKey, Entry entry) {
        if (entry == null || entry.encryptedFileName == null) return null;
        File encFile = new File(DIR, entry.encryptedFileName);
        if (!encFile.exists()) return null;

        try {
            byte[] blob = Files.readAllBytes(encFile.toPath());
            byte[] plain = aesGcmDecrypt(perServerKey(serverKey), blob);

            TMP_DIR.mkdirs();
            String shaPrefix = entry.declaredTextureSha1 != null && entry.declaredTextureSha1.length() >= 8
                    ? entry.declaredTextureSha1.substring(0, 8) : "unknown";
            File tempFile = new File(TMP_DIR, serverKey.substring(0, Math.min(16, serverKey.length())) + "-" + shaPrefix + ".zip");
            Files.write(tempFile.toPath(), plain);
            tempFile.deleteOnExit();
            return tempFile.toPath();
        } catch (Exception e) {
            LOGGER.warn("Failed to decrypt cached forced texture for this server — falling back to a fresh download.", e);
            return null;
        }
    }

    /** Baixa a URL numa thread separada, criptografa e guarda no cache pra próxima vez — mesmos
     *  idiomas de ServerTextureCache#cacheInBackground (HTTP/1.1 forçado, User-Agent explícito,
     *  escrita atômica via .tmp+rename), com criptografia a mais antes de gravar em disco. */
    public static void cacheInBackground(String serverKey, String serverAddress, String url, String declaredHash) {
        if (serverKey == null) return;
        if (!inFlight.add(serverKey)) {
            LOGGER.debug("A download is already in progress for this server's forced texture, ignoring duplicate request.");
            return;
        }

        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(15))
                        .build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(60))
                        .header("User-Agent", "GreatCosmetics-ForcedTextureCache/1.0 (Minecraft)")
                        .GET()
                        .build();

                LOGGER.info("Downloading this server's forced resource pack for the encrypted local cache.");
                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200 || response.body() == null || response.body().length == 0) {
                    LOGGER.warn("Download of the forced texture failed (HTTP {}) — will not cache.", response.statusCode());
                    return;
                }

                // Checagem opcional de integridade — o próprio download já respondeu 200, mas
                // hashear o que baixamos de verdade (em vez de só confiar no que o servidor
                // declarou) pega corrupção em trânsito quase de graça, já que os bytes já estão em
                // memória mesmo. Só um aviso — nunca bloqueia o cache, o hash "de verdade" que
                // importa pra decidir reload é o que o servidor manda em MainConfig#textureSha1.
                if (declaredHash != null && !declaredHash.isBlank()) {
                    String realSha1 = sha1Hex(response.body());
                    if (!realSha1.equalsIgnoreCase(declaredHash)) {
                        LOGGER.warn("Downloaded forced texture's real SHA-1 ({}) does not match the declared one ({}) — caching anyway, but this may indicate transit corruption.", realSha1, declaredHash);
                    }
                }

                byte[] encrypted = aesGcmEncrypt(perServerKey(serverKey), response.body());

                DIR.mkdirs();
                String fileName = serverKey + ".enc";
                File target = new File(DIR, fileName);
                File tmp = new File(DIR, fileName + ".tmp");
                Files.write(tmp.toPath(), encrypted);
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);

                synchronized (ClientForcedTextureCache.class) {
                    loadIndexIfNeeded();
                    Entry entry = index.computeIfAbsent(serverKey, k -> new Entry());
                    entry.serverAddress = serverAddress;
                    entry.declaredTextureSha1 = declaredHash == null ? "" : declaredHash;
                    entry.encryptedFileName = fileName;
                    entry.lastSeenMillis = System.currentTimeMillis();
                    saveIndex();
                }
                LOGGER.info("Forced texture cached and encrypted successfully ({} bytes) — future joins on this server will reuse it without downloading again.", response.body().length);
            } catch (Exception e) {
                LOGGER.warn("Failed to download/cache/encrypt the forced texture — it will keep downloading every time until this is fixed.", e);
            } finally {
                inFlight.remove(serverKey);
            }
        }, "GreatCosmetics-ForcedTextureCache").start();
    }

    /** Chamado uma vez no boot do client (GreatCosmeticsClient#onInitializeClient) — apaga
     *  qualquer arquivo decifrado que tenha sobrado de uma sessão anterior encerrada sem sair
     *  limpo (crash, "kill" do processo) — deleteOnExit() não roda nesses casos. */
    public static void cleanupTempDir() {
        File[] leftovers = TMP_DIR.listFiles();
        if (leftovers == null) return;
        for (File f : leftovers) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    // ── CRIPTO (mesmo idioma de ActivationManager — AES-256-GCM, IV de 12 bytes prefixado no
    // blob, duplicado aqui de propósito: convenção já estabelecida no mod é cada feature ter sua
    // própria cópia pequena em vez de um util compartilhado). ────────────────────────────────────

    /** Chave AES-256 amarrada a ESSE client + ESSE servidor + a versão atual do jar:
     *  SHA-256(client_instance.id ∥ chaveDoServidor ∥ hashDoJar ∥ "gc-texture-cache-v1"). Incluir
     *  o hash do jar significa que uma atualização do mod invalida o cache de textura (1 download
     *  novo por player por update) — aceito de propósito, mesmo idioma de segurança do
     *  ActivationManager#machineKey(). */
    private static byte[] perServerKey(String serverKey) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(ClientInstanceId.get().getBytes(StandardCharsets.UTF_8));
        md.update(serverKey.getBytes(StandardCharsets.UTF_8));
        md.update(getJarSHA256().getBytes(StandardCharsets.UTF_8));
        md.update("gc-texture-cache-v1".getBytes(StandardCharsets.UTF_8));
        return md.digest();
    }

    private static byte[] aesGcmEncrypt(byte[] key, byte[] plain) throws Exception {
        byte[] iv = randomBytes(12);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(plain);
        byte[] out = new byte[12 + ct.length];
        System.arraycopy(iv, 0, out, 0, 12);
        System.arraycopy(ct, 0, out, 12, ct.length);
        return out;
    }

    private static byte[] aesGcmDecrypt(byte[] key, byte[] blob) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, blob, 0, 12));
        return c.doFinal(blob, 12, blob.length - 12);
    }

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    private static String getJarSHA256() {
        try {
            File jar = new File(ClientForcedTextureCache.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (!jar.exists() || jar.isDirectory()) return "DEVELOPMENT_ENV";
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(jar)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = fis.read(buf)) != -1) md.update(buf, 0, r);
            }
            return hex(md.digest());
        } catch (Exception e) {
            return "UNKNOWN_JAR";
        }
    }

    private static String sha1Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return hex(md.digest(data));
        } catch (Exception e) {
            return "";
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
