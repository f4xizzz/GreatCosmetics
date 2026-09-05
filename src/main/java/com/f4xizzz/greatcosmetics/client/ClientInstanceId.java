package com.f4xizzz.greatcosmetics.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

/** Identidade aleatória do CLIENT (jogador), gerada uma vez e persistida em
 *  config/GreatCosmetics/client_instance.id — mesma técnica de
 *  ActivationManager#readOrCreateInstanceGuid(), mas um arquivo/campo TOTALMENTE separado: aquele
 *  é do sistema de licença do SERVIDOR (dono do mod), esse é só a chave usada por
 *  ClientForcedTextureCache pra derivar a chave AES de criptografia do cache de textura por
 *  servidor — nunca reaproveitar um pelo outro, são domínios de segurança diferentes. */
public class ClientInstanceId {

    private static final File FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "GreatCosmetics/client_instance.id");
    private static volatile String cached = null;

    public static synchronized String get() {
        if (cached != null) return cached;
        try {
            if (FILE.exists()) {
                String s = Files.readString(FILE.toPath()).trim();
                if (!s.isEmpty()) { cached = s; return cached; }
            }
            File parent = FILE.getParentFile();
            if (parent != null) //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            String guid = UUID.randomUUID().toString();
            Files.writeString(FILE.toPath(), guid);
            cached = guid;
        } catch (Exception e) {
            // Sem poder persistir, deriva algo estável do ambiente pra não gerar um id novo (e
            // invalidar todo o cache de textura) a cada boot.
            cached = UUID.nameUUIDFromBytes(
                    (System.getProperty("user.dir", "") + "|" + System.getProperty("user.name", ""))
                            .getBytes(StandardCharsets.UTF_8)).toString();
        }
        return cached;
    }
}
