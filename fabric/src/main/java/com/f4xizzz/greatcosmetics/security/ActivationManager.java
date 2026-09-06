package com.f4xizzz.greatcosmetics.security;

import com.f4xizzz.greatcosmetics.GreatCosmetics;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Sistema de licença do GreatCosmetics.
 *
 * <p>Fluxo (servidor dedicado): lê {@code config/GreatCosmetics/license.json}, revalida na API
 * enviando um nonce de uso único + o {@code instanceGuid} da máquina, verifica offline a
 * assinatura RSA do bloco {@code signedPayload} devolvido (que amarra audience, nonce, guid, hash
 * do jar e uma janela de "grace") e revalida periodicamente. Se a API cair, um cache local
 * criptografado ({@code .gcauth}) mantém o servidor pago rodando por até {@link #GRACE_MS}.
 *
 * <p>Singleplayer / ambiente de dev: sempre ativo, sem key.
 *
 * <p>NÃO pode ser alvo de Mixin — ver {@link GreatCosmeticsMixinPlugin}.
 */
public final class ActivationManager {

    private ActivationManager() {}

    private static final Gson GSON = new Gson();
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final File CONFIG_DIR =
            new File(FabricLoader.getInstance().getConfigDir().toFile(), "GreatCosmetics");
    private static final File LICENSE_FILE = new File(CONFIG_DIR, "license.json");
    private static final File INSTANCE_FILE = new File(CONFIG_DIR, "instance.id");
    private static final File CACHE_FILE = new File(CONFIG_DIR, ".gcauth");

    /** Tem que casar com o prefixo da key e com {@code audience} assinado pela API. */
    private static final String AUDIENCE = "GREATCOSMETICS";

    // ── CHAVE PÚBLICA RSA DA API ──────────────────────────────────────────────────────────────
    // Par gerado para o deploy atual (casa com RSA_PRIVATE_KEY_PEM no .env da API). base64 do
    // SubjectPublicKeyInfo (DER), sem quebras. Se rotacionar o par na API depois, troque aqui.
    private static final String PUBLIC_KEY_BASE64 = StrV.s(0);

    // https://f4xizdev-api.onrender.com/api/activate
    private static final byte[] OBFUSCATED_URL = new byte[] {
            104, 116, 116, 112, 115, 58, 47, 47, 102, 52, 120, 105, 122, 100, 101, 118,
            45, 97, 112, 105, 46, 111, 110, 114, 101, 110, 100, 101, 114, 46, 99, 111,
            109, 47, 97, 112, 105, 47, 97, 99, 116, 105, 118, 97, 116, 101
    };

    // ── MANIFESTO DE INTEGRIDADE (Fase 1c) ────────────────────────────────────────────────────
    // Chave SEPARADA da chave da API acima — só assina o manifesto de classes no build (ver
    // signJarManifest em build.gradle), nunca sai da máquina de build. Detecta jar adulterado
    // (ex: patch de bytecode forçando isModActivated() a devolver true) mesmo sem rede nenhuma.
    private static final String BUILD_SIGNING_PUBLIC_KEY_BASE64 = StrV.s(1);
    private static final String SIG_ENTRY_NAME = StrV.s(2);

    private enum State { INACTIVE, ACTIVE }

    private static volatile State state = State.INACTIVE;
    /** Revogação assinada pela API — nem o cache reativa. */
    private static volatile boolean hardLocked = false;

    private static volatile long expirationTimeMs = -1L; // -1 = vitalícia
    private static volatile long graceUntilMs = 0L;      // deadline offline (vem assinado da API)
    private static volatile long lastOkMs = 0L;          // última validação online OK
    private static volatile long lastCheckAttemptMs = 0L;
    private static volatile String instanceGuid = null;
    /** null = ainda não checado (lazy); calculado uma vez, cacheado — ver checkIntegrityOnce(). */
    private static volatile Boolean integrityOk = null;

    private static final long GRACE_MS = 10L * 24 * 60 * 60 * 1000;      // 10 dias
    private static final long CHECK_INTERVAL_MS = 4L * 60 * 60 * 1000;   // revalida a cada 4h
    private static final long RETRY_AFTER_FAIL_MS = 15L * 60 * 1000;     // backoff após falha
    private static final int HTTP_TIMEOUT_MS = 45_000;                   // Render free hiberna

    // ── API PÚBLICA (mantida compatível com os call sites) ────────────────────────────────────

    public static boolean isModActivated() {
        if (!checkIntegrityOnce() || hardLocked || state != State.ACTIVE) return false;
        long now = System.currentTimeMillis();
        if (expirationTimeMs > 0 && now > expirationTimeMs) { state = State.INACTIVE; return false; }
        if (graceUntilMs > 0 && now > graceUntilMs) { state = State.INACTIVE; return false; }
        return true;
    }

    public static long getExpirationTimeMs() {
        return expirationTimeMs;
    }

    /** Verificação no boot do servidor. */
    public static void checkLicenseOnStartup(MinecraftServer server) {
        if (!checkIntegrityOnce()) {
            // Jar adulterado (ou manifesto de integridade não bate) — trava incondicionalmente,
            // mesmo em singleplayer/LAN. Sem log descritivo de propósito (ver Fase 1a).
            state = State.INACTIVE;
            hardLocked = true;
            return;
        }

        if (!server.isDedicatedServer() || FabricLoader.getInstance().isDevelopmentEnvironment()) {
            state = State.ACTIVE;
            lastOkMs = System.currentTimeMillis();
            graceUntilMs = Long.MAX_VALUE;
            expirationTimeMs = -1L;
            return;
        }

        instanceGuid = readOrCreateInstanceGuid();

        String key = readLicenseKey();
        if (key != null) {
            if (validateKeyRemotely(key, server)) return;   // caminho online
            if (activateFromCache()) return;                // grace offline
        }

        state = State.INACTIVE;
        GreatCosmetics.LOGGER.warn("[GreatCosmetics] License inactive. Run /gc activation <key>.");
    }

    /** Ativação manual (/gc activation &lt;key&gt;). */
    public static CompletableFuture<Boolean> tryActivateAsync(String key, MinecraftServer server) {
        return CompletableFuture.supplyAsync(() -> {
            if (instanceGuid == null) instanceGuid = readOrCreateInstanceGuid();
            return validateKeyRemotely(key, server);
        });
    }

    /** Revalidação periódica (END_SERVER_TICK). */
    public static void tickPeriodicCheck(MinecraftServer server) {
        if (!server.isDedicatedServer() || FabricLoader.getInstance().isDevelopmentEnvironment()) return;
        if (hardLocked) return;

        long now = System.currentTimeMillis();
        if (state == State.ACTIVE) {
            if (expirationTimeMs > 0 && now > expirationTimeMs) { state = State.INACTIVE; return; }
            if (graceUntilMs > 0 && now > graceUntilMs) { state = State.INACTIVE; return; }
        }

        long since = now - lastCheckAttemptMs;
        long need = (state == State.ACTIVE) ? CHECK_INTERVAL_MS : RETRY_AFTER_FAIL_MS;
        if (since < need) return;
        lastCheckAttemptMs = now;

        CompletableFuture.runAsync(() -> {
            String key = readLicenseKey();
            if (key != null) {
                // sucesso → renova lastOkMs/graceUntilMs; revogação assinada → hardLocked;
                // falha de rede → não mexe no estado (o grace cobre).
                validateKeyRemotely(key, server);
            }
        });
    }

    // ── VALIDAÇÃO REMOTA ─────────────────────────────────────────────────────────────────────

    private static boolean validateKeyRemotely(String key, MinecraftServer server) {
        try {
            boolean dev = isDevKey(key);
            String nonce = base64(randomBytes(24));
            String jarHash = dev ? "DEVELOPMENT_ENV" : getJarSHA256();
            String guid = instanceGuid != null ? instanceGuid : readOrCreateInstanceGuid();

            JsonObject req = new JsonObject();
            req.addProperty("key", key);
            req.addProperty("audience", AUDIENCE);
            req.addProperty("port", server.getPort());
            req.addProperty("hash", jarHash);
            req.addProperty("nonce", nonce);
            req.addProperty("instanceGuid", guid);

            String body = httpPostJson(new String(OBFUSCATED_URL, StandardCharsets.UTF_8), req.toString());
            JsonObject resp = GSON.fromJson(body, JsonObject.class);
            if (resp == null) return false;

            String spB64 = str(resp, "signedPayload");
            String sigB64 = str(resp, "signature");
            boolean success = "SUCCESS".equalsIgnoreCase(str(resp, "status"));
            if (spB64 == null || sigB64 == null) return false;

            byte[] spBytes = Base64.getDecoder().decode(spB64);
            if (!verifyRSASignature(spBytes, Base64.getDecoder().decode(sigB64))) return false;

            JsonObject p = GSON.fromJson(new String(spBytes, StandardCharsets.UTF_8), JsonObject.class);
            if (p == null) return false;

            boolean audOk = AUDIENCE.equals(str(p, "audience"));
            boolean guidOk = guid.equals(str(p, "instanceGuid"));
            boolean nonceOk = nonce.equals(str(p, "nonce"));
            boolean hashOk = dev || jarHash.equalsIgnoreCase(str(p, "jarHash"));
            boolean revoked = p.has("revoked") && p.get("revoked").getAsBoolean();
            long exp = p.has("expiresAt") ? p.get("expiresAt").getAsLong() : -1L;
            long grace = p.has("graceUntil") ? p.get("graceUntil").getAsLong() : 0L;

            // Revogação assinada e endereçada a este servidor → trava dura (persiste no cache).
            if (revoked && audOk && guidOk) {
                hardLocked = true;
                state = State.INACTIVE;
                writeCache(spB64, sigB64, 0L);
                return false;
            }

            if (success && audOk && nonceOk && guidOk && hashOk && !revoked
                    && (exp < 0 || System.currentTimeMillis() < exp)) {
                expirationTimeMs = exp;
                lastOkMs = System.currentTimeMillis();
                graceUntilMs = grace > 0 ? grace : lastOkMs + GRACE_MS;
                state = State.ACTIVE;
                saveLicenseFile(key, spB64, sigB64);
                writeCache(spB64, sigB64, lastOkMs);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false; // rede / API fora do ar → deixa o grace cobrir
        }
    }

    /** Reativa a partir do cache criptografado local quando a API está fora do ar. */
    private static boolean activateFromCache() {
        try {
            if (!CACHE_FILE.exists()) return false;
            byte[] blob = Files.readAllBytes(CACHE_FILE.toPath());
            byte[] plain = aesGcmDecrypt(machineKey(), blob);
            JsonObject c = GSON.fromJson(new String(plain, StandardCharsets.UTF_8), JsonObject.class);
            if (c == null) return false;

            String spB64 = str(c, "sp");
            String sigB64 = str(c, "sig");
            long cachedLastOk = c.has("ok") ? c.get("ok").getAsLong() : 0L;
            if (spB64 == null || sigB64 == null) return false;

            byte[] spBytes = Base64.getDecoder().decode(spB64);
            if (!verifyRSASignature(spBytes, Base64.getDecoder().decode(sigB64))) return false;

            JsonObject p = GSON.fromJson(new String(spBytes, StandardCharsets.UTF_8), JsonObject.class);
            if (p == null) return false;
            if (!AUDIENCE.equals(str(p, "audience"))) return false;
            if (!instanceGuid.equals(str(p, "instanceGuid"))) return false;
            if (p.has("revoked") && p.get("revoked").getAsBoolean()) { hardLocked = true; return false; }

            long exp = p.has("expiresAt") ? p.get("expiresAt").getAsLong() : -1L;
            long grace = p.has("graceUntil") ? p.get("graceUntil").getAsLong() : 0L;
            long now = System.currentTimeMillis();
            if (exp > 0 && now > exp) return false;
            if (grace <= 0 || now > grace) return false; // fora da janela de grace

            expirationTimeMs = exp;
            graceUntilMs = grace;
            lastOkMs = cachedLastOk;
            state = State.ACTIVE;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ── INTEGRIDADE DO JAR (Fase 1c) ─────────────────────────────────────────────────────────
    // Detecta bytecode adulterado (ex: alguém força isModActivated() a sempre devolver true e
    // recompila) comparando o hash de cada .class do jar EM DISCO agora com o manifesto assinado
    // embutido no build (ver signJarManifest em build.gradle). Sem a chave de build (que nunca sai
    // da máquina de build), não dá pra forjar um manifesto novo que combine com um jar alterado.

    private static boolean checkIntegrityOnce() {
        Boolean cached = integrityOk;
        if (cached != null) return cached;
        boolean result;
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            result = true; // build de dev não é assinada — nada a checar
        } else {
            result = verifyOwnIntegrity();
        }
        integrityOk = result;
        return result;
    }

    private static boolean verifyOwnIntegrity() {
        try {
            byte[] sigFile = readOwnResourceBytes(SIG_ENTRY_NAME);
            if (sigFile == null) return true; // build sem manifesto assinado (ex: teste local) — não bloqueia

            int nl = -1;
            for (int i = 0; i < sigFile.length; i++) {
                if (sigFile[i] == '\n') { nl = i; break; }
            }
            if (nl < 0) return false;

            byte[] storedSigB64 = java.util.Arrays.copyOfRange(sigFile, 0, nl);
            byte[] storedManifest = java.util.Arrays.copyOfRange(sigFile, nl + 1, sigFile.length);
            byte[] storedSig;
            try {
                storedSig = Base64.getDecoder().decode(new String(storedSigB64, StandardCharsets.US_ASCII).trim());
            } catch (Exception e) {
                return false;
            }

            byte[] buildPub = Base64.getDecoder().decode(BUILD_SIGNING_PUBLIC_KEY_BASE64);
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(buildPub));
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(storedManifest);
            if (!verifier.verify(storedSig)) return false;

            byte[] recomputed = computeClassManifest();
            if (recomputed == null) return false; // não achou o jar em disco — falha segura
            return java.util.Arrays.equals(storedManifest, recomputed);
        } catch (Exception e) {
            return false;
        }
    }

    /** Mesmo algoritmo EXATO do signJarManifest() em build.gradle: "caminho\tsha256hex\n" por
     *  classe, ordenado por caminho, excluindo a própria entry de assinatura. */
    private static byte[] computeClassManifest() {
        try {
            File jar = new File(ActivationManager.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (!jar.exists() || jar.isDirectory()) return null;

            TreeMap<String, String> hashes = new TreeMap<>();
            try (ZipFile zf = new ZipFile(jar)) {
                java.util.Enumeration<? extends ZipEntry> entries = zf.entries();
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    if (e.isDirectory()) continue;
                    String name = e.getName();
                    if (!name.endsWith(".class") || name.equals(SIG_ENTRY_NAME)) continue;
                    sha256.reset();
                    try (InputStream is = zf.getInputStream(e)) {
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = is.read(buf)) != -1) sha256.update(buf, 0, r);
                    }
                    byte[] digest = sha256.digest();
                    StringBuilder hex = new StringBuilder(64);
                    for (byte b : digest) hex.append(String.format("%02x", b));
                    hashes.put(name, hex.toString());
                }
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            for (java.util.Map.Entry<String, String> e : hashes.entrySet()) {
                out.write(e.getKey().getBytes(StandardCharsets.UTF_8));
                out.write('\t');
                out.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                out.write('\n');
            }
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** Lê um resource do PRÓPRIO jar em disco (não via classloader — evita ler bytes já
     *  transformados em memória por outro mod/agent; lê o arquivo de verdade). null se não existe. */
    private static byte[] readOwnResourceBytes(String entryName) {
        try {
            File jar = new File(ActivationManager.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (!jar.exists() || jar.isDirectory()) return null;
            try (ZipFile zf = new ZipFile(jar)) {
                ZipEntry e = zf.getEntry(entryName);
                if (e == null) return null;
                try (InputStream is = zf.getInputStream(e)) {
                    return is.readAllBytes();
                }
            }
        } catch (Exception e) {
            return null;
        }
    }

    // ── ARQUIVOS ─────────────────────────────────────────────────────────────────────────────

    private static String readLicenseKey() {
        try {
            if (!LICENSE_FILE.exists()) return null;
            JsonObject j = GSON.fromJson(Files.readString(LICENSE_FILE.toPath()), JsonObject.class);
            if (j == null) return null;
            String k = str(j, "key");
            if (k == null) k = str(j, "license_key"); // compat com formato antigo
            return k;
        } catch (Exception e) {
            return null;
        }
    }

    private static void saveLicenseFile(String key, String signedPayloadB64, String signatureB64) {
        try {
            //noinspection ResultOfMethodCallIgnored
            CONFIG_DIR.mkdirs();
            JsonObject j = new JsonObject();
            j.addProperty("key", key);
            j.addProperty("signedPayload", signedPayloadB64);
            j.addProperty("signature", signatureB64);
            Files.writeString(LICENSE_FILE.toPath(), GSON.toJson(j));
        } catch (Exception ignored) {}
    }

    private static void writeCache(String spB64, String sigB64, long lastOk) {
        try {
            //noinspection ResultOfMethodCallIgnored
            CONFIG_DIR.mkdirs();
            JsonObject j = new JsonObject();
            j.addProperty("sp", spB64);
            j.addProperty("sig", sigB64);
            j.addProperty("ok", lastOk);
            byte[] enc = aesGcmEncrypt(machineKey(), GSON.toJson(j).getBytes(StandardCharsets.UTF_8));
            Files.write(CACHE_FILE.toPath(), enc);
        } catch (Exception ignored) {}
    }

    private static String readOrCreateInstanceGuid() {
        try {
            if (INSTANCE_FILE.exists()) {
                String s = Files.readString(INSTANCE_FILE.toPath()).trim();
                if (!s.isEmpty()) return s;
            }
            //noinspection ResultOfMethodCallIgnored
            CONFIG_DIR.mkdirs();
            String guid = UUID.randomUUID().toString();
            Files.writeString(INSTANCE_FILE.toPath(), guid);
            return guid;
        } catch (Exception e) {
            // Sem poder persistir, deriva algo estável do ambiente pra não gerar guid novo a cada boot.
            return UUID.nameUUIDFromBytes(
                    (System.getProperty("user.dir", "") + "|" + System.getProperty("user.name", ""))
                            .getBytes(StandardCharsets.UTF_8)).toString();
        }
    }

    // ── CRIPTO ───────────────────────────────────────────────────────────────────────────────

    private static boolean verifyRSASignature(byte[] data, byte[] signature) {
        try {
            byte[] pub = Base64.getDecoder().decode(PUBLIC_KEY_BASE64);
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(pub));
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(data);
            return sig.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    /** Chave AES-256 amarrada à máquina: SHA-256(instanceGuid ∥ jarHash ∥ "grace-v1"). */
    private static byte[] machineKey() throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update((instanceGuid == null ? "" : instanceGuid).getBytes(StandardCharsets.UTF_8));
        md.update(getJarSHA256().getBytes(StandardCharsets.UTF_8));
        md.update("grace-v1".getBytes(StandardCharsets.UTF_8));
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

    private static String base64(byte[] b) {
        return Base64.getEncoder().withoutPadding().encodeToString(b);
    }

    /** SHA-256 do jar em execução (hex). "DEVELOPMENT_ENV" quando roda de um diretório. */
    private static String getJarSHA256() {
        try {
            File jar = new File(ActivationManager.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (!jar.exists() || jar.isDirectory()) return "DEVELOPMENT_ENV";
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(jar)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = fis.read(buf)) != -1) md.update(buf, 0, r);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "UNKNOWN_JAR";
        }
    }

    // ── HTTP ─────────────────────────────────────────────────────────────────────────────────

    private static String httpPostJson(String url, String json) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "GC-Client");
        conn.setConnectTimeout(HTTP_TIMEOUT_MS);
        conn.setReadTimeout(HTTP_TIMEOUT_MS);
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (in == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int r;
            while ((r = br.read(buf)) != -1) sb.append(buf, 0, r);
            return sb.toString();
        }
    }

    // ── UTIL ─────────────────────────────────────────────────────────────────────────────────

    private static boolean isDevKey(String key) {
        return key != null
                && key.toUpperCase(Locale.ROOT).contains("-DEV-")
                && FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    private static String str(JsonObject o, String k) {
        return (o != null && o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : null;
    }
}
