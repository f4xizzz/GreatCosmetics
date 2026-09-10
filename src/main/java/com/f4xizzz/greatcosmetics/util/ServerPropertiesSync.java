package com.f4xizzz.greatcosmetics.util;

import com.f4xizzz.greatcosmetics.GreatCosmetics;
import com.f4xizzz.greatcosmetics.config.MainConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mantém o resource pack forçado do mod ({@link MainConfig.ConfigData#forceTexture} +
 * {@code textureUrl}/{@code textureId}/{@code textureSha1}) espelhado no {@code server.properties}
 * do servidor dedicado:
 *
 * <ul>
 *   <li><b>No boot</b> ({@link #reconcileOnLoad}): se o {@code mainconfig.conf} ainda não tem
 *       {@code textureUrl} mas o {@code server.properties} já tem {@code resource-pack} setado,
 *       importa (URL + sha1 + id) pro mainconfig e liga o {@code forceTexture}. Caso contrário,
 *       empurra o mainconfig pro server.properties.</li>
 *   <li><b>Toda vez que o mainconfig é salvo</b> (Dev Studio → Server Config, {@code /gc reload},
 *       recomputo automático do SHA1): {@link #pushToServerProperties} escreve
 *       {@code resource-pack} / {@code resource-pack-sha1} / {@code resource-pack-id} e sincroniza
 *       {@code require-resource-pack} com o {@code forceTexture}.</li>
 * </ul>
 *
 * <p>Nunca <b>apaga</b> as linhas {@code resource-pack*} quando o Force Texture é desligado
 * (decisão do usuário) — só zera o {@code require-resource-pack}. Edição linha-a-linha: preserva
 * comentários, ordem e linhas desconhecidas do arquivo. Só reescreve o arquivo quando algo de
 * fato muda. No-op em singleplayer / servidor integrado (não existe {@code server.properties}).
 *
 * <p>Lembrete: o {@code server.properties} só é LIDO pelo Minecraft no boot — mudar aqui com o
 * servidor ligado só tem efeito no próximo restart. O envio ao vivo do pack continua sendo o
 * {@link GreatCosmetics#sendForcedResourcePack} (join + {@code /gc reload}).
 */
public final class ServerPropertiesSync {

    private ServerPropertiesSync() {}

    private static final String K_PACK = "resource-pack";
    private static final String K_SHA1 = "resource-pack-sha1";
    private static final String K_ID = "resource-pack-id";
    private static final String K_REQUIRE = "require-resource-pack";

    private static Path propsFile() {
        return FabricLoader.getInstance().getGameDir().resolve("server.properties");
    }

    private static boolean applicable(MinecraftServer server) {
        try {
            return server != null && server.isDedicated() && Files.isRegularFile(propsFile());
        } catch (Throwable t) {
            return false;
        }
    }

    /** Boot: importa do server.properties se o mainconfig ainda não tem URL, senão empurra o
     *  mainconfig pro arquivo. Retorna {@code true} se o {@code MainConfig} mudou (o caller deve
     *  re-broadcast / recomputar o SHA1). */
    public static boolean reconcileOnLoad(MinecraftServer server) {
        if (!applicable(server)) return false;
        try {
            Map<String, String> sp = readProps();
            String spPack = sp.getOrDefault(K_PACK, "").trim();
            String cfgUrl = MainConfig.config.textureUrl == null ? "" : MainConfig.config.textureUrl.trim();

            if (cfgUrl.isEmpty() && !spPack.isEmpty()) {
                MainConfig.config.textureUrl = spPack;
                String spSha = sp.getOrDefault(K_SHA1, "").trim();
                if (!spSha.isEmpty()) MainConfig.config.textureSha1 = spSha;
                String spId = sp.getOrDefault(K_ID, "").trim();
                if (!spId.isEmpty()) MainConfig.config.textureId = spId;
                MainConfig.config.forceTexture = true;
                MainConfig.saveConfig();
                GreatCosmetics.debugLog("ServerPropertiesSync: imported forced resource pack from server.properties (" + spPack + ").");
                return true;
            }

            pushToServerProperties(server);
            return false;
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] ServerPropertiesSync.reconcileOnLoad failed: " + e.getMessage());
            return false;
        }
    }

    /** Escreve os campos de textura do {@code MainConfig} atual no {@code server.properties}. */
    public static void pushToServerProperties(MinecraftServer server) {
        if (!applicable(server)) return;
        try {
            String url = MainConfig.config.textureUrl == null ? "" : MainConfig.config.textureUrl.trim();
            String sha1 = MainConfig.config.textureSha1 == null ? "" : MainConfig.config.textureSha1.trim();
            boolean effective = MainConfig.config.forceTexture && !url.isEmpty();

            // desejado -> valor. "append quando falta a chave" só no caso EFETIVO (ligado); no
            // caso desligado só mexe em linha que já existe (ver applyLine).
            Map<String, String> desired = new LinkedHashMap<>();
            if (effective) {
                desired.put(K_PACK, url);
                desired.put(K_SHA1, sha1);
                desired.put(K_ID, GreatCosmetics.resolveForcedTexturePackId().toString());
                desired.put(K_REQUIRE, "true");
            } else {
                desired.put(K_REQUIRE, "false");
            }

            List<String> lines = new ArrayList<>(Files.readAllLines(propsFile(), StandardCharsets.UTF_8));
            boolean dirty = false;
            for (Map.Entry<String, String> e : desired.entrySet()) {
                dirty |= applyLine(lines, e.getKey(), e.getValue(), effective);
            }
            if (dirty) {
                Files.writeString(propsFile(), String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
                GreatCosmetics.debugLog("ServerPropertiesSync: server.properties updated (forceTexture=" + effective + ").");
            }
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] ServerPropertiesSync.pushToServerProperties failed: " + e.getMessage());
        }
    }

    /** Substitui a 1ª linha {@code key=...} não-comentada por {@code key=value}; se não existir e
     *  {@code appendWhenMissing} for true e o valor não for vazio, adiciona no fim. Retorna true
     *  se mexeu em alguma linha. */
    private static boolean applyLine(List<String> lines, String key, String value, boolean appendWhenMissing) {
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || trimmed.charAt(0) == '#' || trimmed.charAt(0) == '!') continue;
            int eq = indexOfSeparator(trimmed);
            if (eq < 0) continue;
            if (!trimmed.substring(0, eq).trim().equals(key)) continue;
            String current = trimmed.substring(eq + 1).trim();
            if (current.equals(value)) return false;
            lines.set(i, key + "=" + value);
            return true;
        }
        if (appendWhenMissing && !value.isEmpty()) {
            lines.add(key + "=" + value);
            return true;
        }
        return false;
    }

    private static int indexOfSeparator(String line) {
        int eq = line.indexOf('=');
        int colon = line.indexOf(':');
        if (eq < 0) return colon;
        if (colon < 0) return eq;
        return Math.min(eq, colon);
    }

    private static Map<String, String> readProps() throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        for (String raw : Files.readAllLines(propsFile(), StandardCharsets.UTF_8)) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || trimmed.charAt(0) == '#' || trimmed.charAt(0) == '!') continue;
            int eq = indexOfSeparator(trimmed);
            if (eq < 0) continue;
            String k = trimmed.substring(0, eq).trim();
            String v = trimmed.substring(eq + 1).trim();
            map.putIfAbsent(k, v);
        }
        return map;
    }
}
