package com.f4xizzz.greatcosmetics.util;

import com.f4xizzz.greatcosmetics.GreatCosmetics;
import com.f4xizzz.greatcosmetics.database.DatabaseManager;
import com.f4xizzz.greatcosmetics.network.OpenWardrobePayload;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WardrobeManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final File DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), "GreatCosmetics");
    private static final File SAVE_FILE = new File(DIR, "studios.json");

    // ==========================================
    // ESTRUTURAS
    // ==========================================

    public static class RuntimeLocation {
        public ServerLevel world;
        public double x, y, z;
        public float yaw, pitch;

        public RuntimeLocation(ServerLevel world, double x, double y, double z, float yaw, float pitch) {
            this.world = world;
            this.x = x; this.y = y; this.z = z;
            this.yaw = yaw; this.pitch = pitch;
        }
    }

    public static class SerializableLocation {
        public String dimension;
        public double x, y, z;
        public float yaw, pitch;

        public SerializableLocation(String dimension, double x, double y, double z, float yaw, float pitch) {
            this.dimension = dimension;
            this.x = x; this.y = y; this.z = z;
            this.yaw = yaw; this.pitch = pitch;
        }
    }

    public static final Map<String, SerializableLocation> studioLocations = new HashMap<>();
    private static final Map<UUID, RuntimeLocation> previousLocations = new HashMap<>();

    // Persistência da localização de retorno em disco (chaveada por UUID). Sem isso, um
    // jogador que desconecta (kick, crash, fechar o jogo) enquanto está dentro do wardrobe
    // fica com a posição salva no NBT dele apontando pro estúdio — e, como previousLocations
    // é só em memória, um restart do servidor perde o "endereço de volta" também. Com o
    // arquivo em disco, tanto o hook de DISCONNECT quanto o de JOIN (rede de segurança pra
    // quedas abruptas, tipo o server crashar) sempre conseguem devolver o jogador de volta.
    private static final File SESSIONS_FILE = new File(DIR, "wardrobe_sessions.json");
    private static final Map<UUID, SerializableLocation> pendingReturns = new HashMap<>();

    // ==========================================
    // SISTEMA DE ARQUIVOS (Salvar / Carregar)
    // ==========================================
    public static void loadStudios() {
        if (!SAVE_FILE.exists()) return;
        try (FileReader reader = new FileReader(SAVE_FILE)) {
            Type type = new TypeToken<Map<String, SerializableLocation>>(){}.getType();
            Map<String, SerializableLocation> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                studioLocations.clear();
                studioLocations.putAll(loaded);
                GreatCosmetics.debugLog("Loaded " + studioLocations.size() + " saved Wardrobe studios!");
            }
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Failed to load Wardrobe studios: " + e.getMessage());
        }
    }

    public static void saveStudios() {
        if (!DIR.exists()) DIR.mkdirs(); // Garante que a pasta existe!
        try (FileWriter writer = new FileWriter(SAVE_FILE)) {
            GSON.toJson(studioLocations, writer);
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Failed to save Wardrobe studios: " + e.getMessage());
        }
    }

    // ==========================================
    // PERSISTÊNCIA DA SESSÃO DE WARDROBE (proteção contra desconexão)
    // ==========================================
    public static void loadSessions() {
        pendingReturns.clear();
        if (!SESSIONS_FILE.exists()) return;
        try (FileReader reader = new FileReader(SESSIONS_FILE)) {
            Type type = new TypeToken<Map<String, SerializableLocation>>(){}.getType();
            Map<String, SerializableLocation> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                for (Map.Entry<String, SerializableLocation> entry : loaded.entrySet()) {
                    try {
                        pendingReturns.put(UUID.fromString(entry.getKey()), entry.getValue());
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Failed to load wardrobe_sessions.json: " + e.getMessage());
        }
    }

    private static void saveSessions() {
        if (!DIR.exists()) DIR.mkdirs();
        Map<String, SerializableLocation> raw = new HashMap<>();
        for (Map.Entry<UUID, SerializableLocation> entry : pendingReturns.entrySet()) {
            raw.put(entry.getKey().toString(), entry.getValue());
        }
        try (FileWriter writer = new FileWriter(SESSIONS_FILE)) {
            GSON.toJson(raw, writer);
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Failed to save wardrobe_sessions.json: " + e.getMessage());
        }
    }

    /**
     * Rede de segurança: se o jogador desconectou (kick, crash, fechar o jogo) enquanto estava
     * dentro do wardrobe/studio, devolve ele pra última posição conhecida ANTES de entrar.
     * Chamado tanto no DISCONNECT (caso normal) quanto no JOIN (caso o disconnect não tenha
     * disparado limpo, ex: o servidor inteiro caiu). Idempotente — não faz nada se não houver
     * sessão pendente pro UUID.
     */
    public static void forceReturnIfPending(ServerPlayer player) {
        SerializableLocation saved = pendingReturns.remove(player.getUUID());
        previousLocations.remove(player.getUUID());
        if (saved == null) return;

        ResourceKey<net.minecraft.world.level.Level> worldKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(saved.dimension));
        ServerLevel world = player.getServer().getLevel(worldKey);
        if (world != null) {
            player.teleportTo(world, saved.x, saved.y, saved.z, saved.yaw, saved.pitch);
            GreatCosmetics.debugLog("Player " + player.getName().getString() + " was stuck in the wardrobe/studio — automatically returned.");
        }
        saveSessions();
    }

    // ==========================================
    // COMANDO: /gc wardrobe setbackground <nome>
    // ==========================================
    public static void setStudioLocation(ServerPlayer player, String bgName) {
        String dimensionId = player.serverLevel().dimension().location().toString();

        SerializableLocation loc = new SerializableLocation(
                dimensionId, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()
        );

        studioLocations.put(bgName.toLowerCase(), loc);
        saveStudios();

        player.displayClientMessage(com.f4xizzz.greatcosmetics.config.LangConfig.chat("commands.wardrobe.background_set", player.getServer().registryAccess(), "name", bgName), false);
    }

    // ==========================================
    // COMANDO: /wardrobe [player] [background]
    // ==========================================
    public static void openWardrobe(ServerPlayer target, String bgName) {
        // Gate de licença (ver security.ActivationManager) — servidor dedicado sem licença não abre a GUI.
        if (com.f4xizzz.greatcosmetics.GreatCosmetics.licenseBlocked(target)) return;

        // 1. Salva a posição atual
        previousLocations.put(target.getUUID(), new RuntimeLocation(
                target.serverLevel(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot()
        ));

        // Persiste em disco também (proteção contra desconexão — ver forceReturnIfPending)
        pendingReturns.put(target.getUUID(), new SerializableLocation(
                target.serverLevel().dimension().location().toString(),
                target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot()
        ));
        saveSessions();

        boolean hasSavedBackground = false;

        // 2. Tenta carregar o background
        if (bgName != null && studioLocations.containsKey(bgName.toLowerCase())) {
            SerializableLocation savedLoc = studioLocations.get(bgName.toLowerCase());
            ResourceKey<net.minecraft.world.level.Level> worldKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(savedLoc.dimension));
            ServerLevel targetWorld = target.getServer().getLevel(worldKey);

            if (targetWorld != null) {
                target.teleportTo(targetWorld, savedLoc.x, savedLoc.y, savedLoc.z, savedLoc.yaw, savedLoc.pitch);
                hasSavedBackground = true;
            } else {
                target.displayClientMessage(com.f4xizzz.greatcosmetics.config.LangConfig.chat("commands.wardrobe.dimension_not_found", target.getServer().registryAccess(), "dimension", savedLoc.dimension), false);
            }
        }

        if (!hasSavedBackground) {
            target.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(), target.getYRot(), 0.0f);
        }

        // ==========================================
        // DADOS PARA A INTERFACE CLIENT-SIDE
        // ==========================================

        // Checa se o jogador é OP DE VERDADE (ops.json) — usa isRealOperator() de propósito em
        // vez de hasPermissionLevel(2) direto: esse boolean vira "hasAllUnlocked" no cliente
        // (desbloqueia TODOS os cosméticos na GUI), e com o mod Vanilla Permissions instalado,
        // hasPermissionLevel(2) podia vir true pra qualquer jogador sem OP e sem permissão
        // nenhuma — destravando a loja inteira sem querer.
        boolean isOp = com.f4xizzz.greatcosmetics.GreatCosmetics.isRealOperator(target);

        // Recalcula e resincroniza ClientPermissionCache (isOperator/hasGcDev/hasGcPermDevmode)
        // TODA VEZ que o wardrobe abre — SyncDevPermissionsPayload só era mandado no JOIN, então
        // se o jogador fosse opado/deopado ou tivesse uma permission concedida/revogada via
        // LuckPerms DEPOIS de já estar conectado (sem desconectar), o cache do cliente ficava
        // travado no valor de quando ele entrou, mostrando (ou escondendo) os botões de Dev
        // Studio errado até o próximo relogin.
        ServerPlayNetworking.send(target, new com.f4xizzz.greatcosmetics.network.SyncDevPermissionsPayload(
                isOp,
                com.f4xizzz.greatcosmetics.GreatCosmetics.checkPermission(target, "gc.dev"),
                com.f4xizzz.greatcosmetics.GreatCosmetics.checkPermission(target, com.f4xizzz.greatcosmetics.config.MainConfig.config.devModePermission)
        ));

        // Manda prefix/suffix do LuckPerms pro nametag customizado da aba Party (ver
        // SyncNameTagPayload — vanilla nunca renderiza o nametag do PRÓPRIO jogador quando a
        // câmera é a própria entidade, então o texto tem que vir pronto do servidor).
        String[] prefixSuffix = com.f4xizzz.greatcosmetics.util.LuckPermsTagManager.getPrefixSuffix(target);
        ServerPlayNetworking.send(target, new com.f4xizzz.greatcosmetics.network.SyncNameTagPayload(prefixSuffix[0], prefixSuffix[1]));

        // Busca no Banco de Dados a lista de IDs que ele tem (MySQL ou SQLite)
        List<String> unlockedCosmetics = DatabaseManager.getPlayerUnlockedCosmetics(target.getUUID());

        // Manda o Client abrir a interface repassando os dados corretos!
        ServerPlayNetworking.send(target, new OpenWardrobePayload(hasSavedBackground, isOp, unlockedCosmetics));
    }

    // ==========================================
    // EVENTO DE RETORNO (Quando fecha o menu)
    // ==========================================
    public static void closeWardrobeAndReturn(ServerPlayer player) {
        RuntimeLocation oldLoc = previousLocations.remove(player.getUUID());
        pendingReturns.remove(player.getUUID());
        saveSessions();

        if (oldLoc != null) {
            player.teleportTo(oldLoc.world, oldLoc.x, oldLoc.y, oldLoc.z, oldLoc.yaw, oldLoc.pitch);
            GreatCosmetics.debugLog("Player " + player.getName().getString() + " returned.");
        }
    }
}