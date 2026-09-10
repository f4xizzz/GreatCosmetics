package com.f4xizzz.greatcosmetics.client;

import com.f4xizzz.greatcosmetics.network.SyncPokemonSkinsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

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

    // O CLIENTE RECEBE A MENSAGEM DO SERVIDOR AQUI E SALVA NA MEMÓRIA!
    public static void registerNetworkReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(SyncPokemonSkinsPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                UNLOCKED_SKINS.clear();
                UNLOCKED_SKINS.addAll(payload.unlockedSkins());

                SKIN_COOLDOWNS.clear();
                SKIN_COOLDOWNS.putAll(payload.cooldowns());

                // Se o cara estiver com a Wardrobe aberta, ela atualiza na hora.
                // SOFT-DEP COBBLEMON: PartyPage toca com.cobblemon.* — o && curto-circuita antes
                // de tocar na classe quando não há Cobblemon. Ver ModCompat.
                if (com.f4xizzz.greatcosmetics.util.ModCompat.cobblemon()
                        && com.f4xizzz.greatcosmetics.client.gui.pages.PartyPage.INSTANCE != null) {
                    // Força a UI atualizar os botões sem fechar o menu
                    com.f4xizzz.greatcosmetics.client.gui.pages.PartyPage.INSTANCE.onOpen();
                }
            });
        });
    }
}