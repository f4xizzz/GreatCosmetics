package com.f4xizzz.greatcosmetics.client;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientCosmeticCache {

    // Record sincronizado com as novas adições do DatabaseManager. hiddenCosmeticIds substituiu
    // os 7 booleans hide_acc_* — esconder agora é por cosmético específico, não por slot inteiro.
    public record PlayerSettings(
            boolean hideHelmet, boolean hideChestplate, boolean hideLeggings, boolean hideBoots,
            Set<String> hiddenCosmeticIds
    ) {}

    private static final Map<UUID, Set<String>> equippedCosmetics = new ConcurrentHashMap<>();
    private static final Map<UUID, PlayerSettings> playerSettings = new ConcurrentHashMap<>();

    public static boolean isDevModeActive = false; // Controle do botão Dev Mode

    /** Setting LOCAL do cliente (não vai pro servidor, não é por-player): quando true, os cosméticos
     *  de OUTROS jogadores não renderizam — pra aliviar FPS em PC fraco. Persistido em
     *  config/GreatCosmetics/client_local.json (ver ClientLocalSettings). */
    public static boolean hideOtherPlayersCosmetics = false;

    public static void setEquipped(UUID playerUuid, List<String> cosmeticIds) {
        equippedCosmetics.put(playerUuid, new HashSet<>(cosmeticIds));
    }

    public static Set<String> getEquipped(UUID playerUuid) {
        return equippedCosmetics.getOrDefault(playerUuid, new HashSet<>());
    }

    public static void setSettings(
            UUID playerUuid,
            boolean h, boolean c, boolean l, boolean b,
            Set<String> hiddenCosmeticIds
    ) {
        playerSettings.put(playerUuid, new PlayerSettings(h, c, l, b, hiddenCosmeticIds));
    }

    public static PlayerSettings getSettings(UUID playerUuid) {
        return playerSettings.getOrDefault(playerUuid, new PlayerSettings(
                false, false, false, false, Set.of()
        ));
    }

    public static void clear() {
        equippedCosmetics.clear();
        playerSettings.clear();
    }
}