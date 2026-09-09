package com.f4xizzz.greatcosmetics.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Cosméticos "equipados" em entidades que não são jogadores reais (NPCs do EasyNPC, Armor
 * Stands, etc), chaveados por UUID da entidade. O sistema de renderização de cosméticos do mod
 * (ArmorFeatureRendererMixin) é 100% orientado pelo ClientCosmeticCache — nunca lê o ItemStack
 * real do slot de equipamento — então aqui a gente só precisa manter esse registro e sincronizar
 * pros clientes com o mesmo payload já usado pra jogadores (SyncPlayerCosmeticsPayload).
 */
public class NpcCosmeticsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final File DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), "GreatCosmetics");
    private static final File FILE = new File(DIR, "npc_cosmetics.json");

    public static final Map<UUID, Set<String>> EQUIPPED = new ConcurrentHashMap<>();

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "GreatCosmetics-NpcCosmeticsSave");
                t.setDaemon(true);
                return t;
            });
    private static ScheduledFuture<?> pendingSave;

    public static synchronized void load() {
        EQUIPPED.clear();
        if (!DIR.exists()) DIR.mkdirs();
        if (!FILE.exists()) return;

        try (JsonReader reader = new JsonReader(new FileReader(FILE))) {
            reader.setLenient(true);
            Map<String, List<String>> raw = GSON.fromJson(reader, new TypeToken<Map<String, List<String>>>() {}.getType());
            if (raw != null) {
                for (Map.Entry<String, List<String>> entry : raw.entrySet()) {
                    try {
                        EQUIPPED.put(UUID.fromString(entry.getKey()), new HashSet<>(entry.getValue()));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            System.out.println("[GreatCosmetics] npc_cosmetics.json loaded: " + EQUIPPED.size() + " entity(ies) with a saved cosmetic.");
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Error loading npc_cosmetics.json!");
            com.f4xizzz.greatcosmetics.GreatCosmetics.debugLog("NpcCosmeticsConfig: FAILED to load npc_cosmetics.json — " + e);
            e.printStackTrace();
        }
    }

    private static synchronized void writeToDisk() {
        if (!DIR.exists()) DIR.mkdirs();

        Map<String, List<String>> raw = new HashMap<>();
        for (Map.Entry<UUID, Set<String>> entry : EQUIPPED.entrySet()) {
            raw.put(entry.getKey().toString(), new java.util.ArrayList<>(entry.getValue()));
        }

        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(raw, writer);
        } catch (IOException e) {
            System.err.println("[GreatCosmetics] Error saving npc_cosmetics.json!");
            e.printStackTrace();
        }
    }

    public static synchronized void flush() {
        if (pendingSave != null) {
            pendingSave.cancel(false);
            pendingSave = null;
        }
        writeToDisk();
    }

    private static synchronized void scheduleSave() {
        if (pendingSave != null) {
            pendingSave.cancel(false);
        }
        pendingSave = SCHEDULER.schedule(NpcCosmeticsConfig::writeToDisk, 500, TimeUnit.MILLISECONDS);
    }

    public static Set<String> get(UUID targetUuid) {
        return EQUIPPED.getOrDefault(targetUuid, new HashSet<>());
    }

    public static void equip(UUID targetUuid, String cosmeticId) {
        EQUIPPED.computeIfAbsent(targetUuid, k -> ConcurrentHashMap.newKeySet()).add(cosmeticId);
        scheduleSave();
    }

    /** Remove um cosmético específico. Retorna true se algo foi de fato removido. */
    public static boolean unequip(UUID targetUuid, String cosmeticId) {
        Set<String> set = EQUIPPED.get(targetUuid);
        if (set == null) return false;
        boolean removed = set.remove(cosmeticId);
        if (set.isEmpty()) EQUIPPED.remove(targetUuid);
        if (removed) scheduleSave();
        return removed;
    }

    /** Remove TODOS os cosméticos de uma entidade (usado pra apagar um /gc display). Retorna true se havia algo. */
    public static boolean removeAll(UUID targetUuid) {
        boolean had = EQUIPPED.remove(targetUuid) != null;
        if (had) scheduleSave();
        return had;
    }

    /** Remove todos os cosméticos equipados que ocupam o slot virtual informado. Retorna quantos foram removidos. */
    public static int unequipSlot(UUID targetUuid, CosmeticData.VirtualSlot slot) {
        Set<String> set = EQUIPPED.get(targetUuid);
        if (set == null || set.isEmpty()) return 0;

        int removedCount = 0;
        for (java.util.Iterator<String> it = set.iterator(); it.hasNext(); ) {
            String id = it.next();
            CosmeticData data = com.f4xizzz.greatcosmetics.GreatCosmetics.getCosmeticById(id);
            String variantId = com.f4xizzz.greatcosmetics.util.EquippedCosmeticId.variant(id);
            CosmeticData.VirtualSlot effSlot = data == null ? null : data.slot;
            if (data != null && !variantId.isEmpty()) {
                var v = data.findVariant(variantId).orElse(null);
                if (v != null && v.slot != null) effSlot = v.slot;
            }
            if (data != null && effSlot == slot) {
                it.remove();
                removedCount++;
            }
        }
        if (set.isEmpty()) EQUIPPED.remove(targetUuid);
        if (removedCount > 0) scheduleSave();
        return removedCount;
    }

    /** Envia o estado atual de cosméticos de uma entidade (NPC/Armor Stand) pra todos os jogadores online. */
    public static void broadcast(MinecraftServer server, UUID targetUuid) {
        List<String> equipped = new java.util.ArrayList<>(get(targetUuid));

        com.f4xizzz.greatcosmetics.network.SyncPlayerCosmeticsPayload payload =
                new com.f4xizzz.greatcosmetics.network.SyncPlayerCosmeticsPayload(
                        targetUuid, equipped,
                        false, false, false, false,
                        java.util.List.of()
                );

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    /** Envia todas as entidades com cosmético salvo pra um jogador específico (usado no join). */
    public static void syncAllTo(ServerPlayerEntity player) {
        for (Map.Entry<UUID, Set<String>> entry : EQUIPPED.entrySet()) {
            com.f4xizzz.greatcosmetics.network.SyncPlayerCosmeticsPayload payload =
                    new com.f4xizzz.greatcosmetics.network.SyncPlayerCosmeticsPayload(
                            entry.getKey(), new java.util.ArrayList<>(entry.getValue()),
                            false, false, false, false,
                            java.util.List.of()
                    );
            ServerPlayNetworking.send(player, payload);
        }
    }
}
