package com.f4xizzz.greatcosmetics.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.architectury.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
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
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Cache client-side de resource packs mandados por QUALQUER servidor (o resource-pack/
 *  resource-pack-sha1 do server.properties, não o pack forçado do próprio GreatCosmetics — ver
 *  GreatCosmetics#refreshTextureHashBlocking pra esse outro caso). Guarda uma cópia local em
 *  config/GreatCosmetics/servertextures/, indexada pela URL — ver ServerResourcePackLoaderMixin
 *  pra onde isso é consultado.
 *
 *  Motivo de existir: muitos servidores não preenchem resource-pack-sha1 direito (fica em branco
 *  ou errado), então o cache nativo do Minecraft (indexado pelo hash que O SERVIDOR declara) nunca
 *  bate, e ele rebaixa o pack toda vez que o player entra. Calculando nossa própria chave (a partir
 *  da URL, não do hash declarado) a gente resolve isso client-side, sem depender do servidor
 *  configurar nada direito. */
public class ServerTextureCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("GreatCosmetics-TextureCache");
    private static final File DIR = new File(Platform.getConfigFolder().toFile(), "GreatCosmetics/servertextures");
    private static final File INDEX_FILE = new File(DIR, "index.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type INDEX_TYPE = new TypeToken<Map<String, Entry>>() {}.getType();

    private static Map<String, Entry> index;
    private static final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    private static class Entry {
        String url;
        String hash;
        String fileName;
    }

    private static synchronized void loadIndexIfNeeded() {
        if (index != null) return;
        index = new HashMap<>();
        if (INDEX_FILE.exists()) {
            try (FileReader reader = new FileReader(INDEX_FILE)) {
                Map<String, Entry> loaded = GSON.fromJson(reader, INDEX_TYPE);
                if (loaded != null) index = loaded;
            } catch (Exception e) {
                LOGGER.warn("Failed to read {} — the server texture cache will be rebuilt from scratch.", INDEX_FILE, e);
            }
        }
    }

    private static synchronized void saveIndex() {
        DIR.mkdirs();
        try (FileWriter writer = new FileWriter(INDEX_FILE)) {
            GSON.toJson(index, writer);
        } catch (Exception e) {
            LOGGER.warn("Failed to save {} — the cached texture will not survive a restart.", INDEX_FILE, e);
        }
    }

    private static String keyFor(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Devolve o arquivo em cache pra essa URL, ou null se não tiver (primeira vez) ou se o hash
     *  que o servidor declarou agora for DIFERENTE do que ele declarou da última vez que cacheamos
     *  (sinal real de que o admin trocou o conteúdo do pack). Hash em branco dos dois lados não
     *  conta como mudança — não tem como saber, então mantém o cache (limitação inerente de
     *  servidor que não configura resource-pack-sha1; nesse caso só uma URL nova força refresh). */
    public static synchronized Path getCachedFile(String url, String declaredHash) {
        loadIndexIfNeeded();
        Entry entry = index.get(keyFor(url));
        if (entry == null) {
            LOGGER.info("No cache for {} yet — first time seeing this URL.", url);
            return null;
        }

        String oldHash = entry.hash == null ? "" : entry.hash;
        String newHash = declaredHash == null ? "" : declaredHash;
        if (!oldHash.isEmpty() && !newHash.isEmpty() && !oldHash.equalsIgnoreCase(newHash)) {
            LOGGER.info("Hash declared by {} changed since last time ({} -> {}) — treating as an updated pack, re-downloading.", url, oldHash, newHash);
            return null;
        }

        File file = new File(DIR, entry.fileName);
        if (!file.exists()) {
            LOGGER.warn("Cache for {} was indexed but file {} vanished from disk — re-downloading.", url, file);
            return null;
        }

        LOGGER.info("Cache HIT for {} — reusing {} without downloading from the network.", url, file);
        return file.toPath();
    }

    /** Apaga a entrada (e o arquivo) em cache pra essa URL, se existir — usado uma vez por
     *  ServerResourcePackLoaderMixin ao migrar a textura FORÇADA do GreatCosmetics pro cache novo
     *  criptografado (ver ClientForcedTextureCache): antes dessa feature existir, a textura
     *  forçada já passava por ESSE cache genérico (em texto puro), então um player que já tinha
     *  entrado antes ficava com uma cópia em claro solta aqui mesmo depois de migrar — sem esse
     *  cleanup, "não dá pra copiar a textura da pasta" ficava furado pra quem já tinha esse
     *  arquivo de antes. Sem efeito nenhum em resource packs GENÉRICOS de outros servidores/mods
     *  (esse cache continua cobrindo isso normalmente) — só remove a entrada específica da URL
     *  passada. */
    public static synchronized void evict(String url) {
        loadIndexIfNeeded();
        Entry entry = index.remove(keyFor(url));
        if (entry != null && entry.fileName != null) {
            //noinspection ResultOfMethodCallIgnored
            new File(DIR, entry.fileName).delete();
            saveIndex();
        }
    }

    /** Baixa a URL numa thread separada e guarda no cache pra próxima vez — chamado quando não
     *  achamos cache válido (primeira vez nessa URL, ou o hash declarado mudou). Roda em paralelo
     *  com o download normal do vanilla (que continua acontecendo dessa vez); não interfere nele,
     *  só prepara o terreno pro PRÓXIMO join. */
    public static void cacheInBackground(String url, String declaredHash) {
        String key = keyFor(url);
        if (!inFlight.add(key)) {
            LOGGER.debug("A download is already in progress for {}, ignoring duplicate request.", url);
            return;
        }

        new Thread(() -> {
            try {
                // HTTP/1.1 forçado de propósito: o HttpClient do Java prefere HTTP/2 por padrão, e
                // vários hosts de resource pack (proxies/CDNs mal configurados) respondem de forma
                // inconsistente ou derrubam a conexão nessa negociação — o que aqui aparecia como um
                // download que nunca completava/nunca populava o cache, sem NENHUM erro visível (o
                // catch genérico engolia tudo). User-Agent explícito pelo mesmo motivo: alguns hosts
                // bloqueiam requisições sem UA reconhecível.
                HttpClient client = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(15))
                        .build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(60))
                        .header("User-Agent", "GreatCosmetics-ResourcePackCache/1.0 (Minecraft)")
                        .GET()
                        .build();

                LOGGER.info("Downloading server resource pack for local cache: {}", url);
                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200) {
                    LOGGER.warn("Download of {} returned HTTP {} — will not cache, it will keep downloading every time until this is fixed on the host.", url, response.statusCode());
                    return;
                }
                if (response.body() == null || response.body().length == 0) {
                    LOGGER.warn("Download of {} returned an empty body — will not cache.", url);
                    return;
                }

                DIR.mkdirs();
                String fileName = key + ".zip";
                File target = new File(DIR, fileName);
                File tmp = new File(DIR, fileName + ".tmp");
                Files.write(tmp.toPath(), response.body());
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);

                synchronized (ServerTextureCache.class) {
                    loadIndexIfNeeded();
                    Entry entry = new Entry();
                    entry.url = url;
                    entry.hash = declaredHash == null ? "" : declaredHash;
                    entry.fileName = fileName;
                    index.put(key, entry);
                    saveIndex();
                }
                LOGGER.info("Resource pack cached successfully ({} bytes) — future joins on this server will reuse this file without downloading again.", response.body().length);
            } catch (Exception e) {
                LOGGER.warn("Failed to download/cache resource pack from {} — it will keep downloading every time until this is fixed.", url, e);
            } finally {
                inFlight.remove(key);
            }
        }, "GreatCosmetics-ServerTextureCache").start();
    }
}
