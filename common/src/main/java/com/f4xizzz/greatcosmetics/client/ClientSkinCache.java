package com.f4xizzz.greatcosmetics.client;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ClientSkinCache {
    public static final Set<String> UNLOCKED_SKINS = new HashSet<>();
    public static final Map<String, Long> SKIN_COOLDOWNS = new HashMap<>();

    public static boolean isUnlocked(String id) {
        return UNLOCKED_SKINS.contains(id.toLowerCase());
    }

    public static long getCooldownLeftMs(String id, int cooldownMinutes) {
        long lastApplied = SKIN_COOLDOWNS.getOrDefault(id.toLowerCase(), 0L);
        long now = System.currentTimeMillis();
        long passed = now - lastApplied;
        long totalCooldownMs = cooldownMinutes * 60 * 1000L;

        if (passed >= totalCooldownMs) return 0L;
        return totalCooldownMs - passed;
    }

    /** Aplica um SyncPokemonSkinsPayload recém-recebido. O REGISTRO do receiver ficou no entrypoint
     *  client (GreatCosmeticsClient) no split multiloader; aqui é só a lógica de aplicar no cache. */
    public static void apply(Collection<String> unlockedSkins, Map<String, Long> cooldowns) {
        UNLOCKED_SKINS.clear();
        UNLOCKED_SKINS.addAll(unlockedSkins);

        SKIN_COOLDOWNS.clear();
        SKIN_COOLDOWNS.putAll(cooldowns);

        // Se o cara estiver com a Wardrobe aberta, ela atualiza na hora
        if (com.f4xizzz.greatcosmetics.client.gui.pages.PartyPage.INSTANCE != null) {
            // Força a UI atualizar os botões sem fechar o menu
            com.f4xizzz.greatcosmetics.client.gui.pages.PartyPage.INSTANCE.onOpen();
        }
    }
}