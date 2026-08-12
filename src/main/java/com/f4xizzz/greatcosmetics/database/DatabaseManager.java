package com.f4xizzz.greatcosmetics.database;

import com.f4xizzz.greatcosmetics.GreatCosmetics;
import com.f4xizzz.greatcosmetics.config.MainConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatabaseManager {

    /** hiddenCosmeticIds: substituiu os 7 booleans hide_acc_* (por SLOT inteiro) — agora esconder
     *  é por COSMÉTICO ESPECÍFICO (ver player_hidden_cosmetics), então dá pra esconder só um item
     *  de um slot que tem vários equipados ao mesmo tempo, sem esconder os outros junto. */
    public record PlayerSettings(
            boolean hideHelmet, boolean hideChestplate, boolean hideLeggings, boolean hideBoots,
            java.util.Set<String> hiddenCosmeticIds
    ) {}

    private static Connection connection;

    // Cache em memória de "cosméticos equipados por jogador" — getPlayerEquippedCosmetics() é
    // chamado UMA VEZ POR JOGADOR A CADA TICK no loop principal (GreatCosmetics.java) e também
    // toda vez que LivingEntity#getArmor() roda no servidor (LivingEntityMixin), que é MUITO
    // frequente (cálculo de dano, sincronização de atributos, etc). Sem cache, cada uma dessas
    // chamadas era uma query SQL SÍNCRONA na thread principal do servidor — com muitos jogadores
    // online isso vira centenas/milhares de round-trips de banco por segundo travando o tick do
    // servidor inteiro. Invalidado (removido do mapa) toda vez que o banco muda de verdade
    // (equip/unequip) — a próxima leitura repopula sozinha via computeIfAbsent.
    private static final java.util.Map<UUID, List<String>> equippedCache = new java.util.concurrent.ConcurrentHashMap<>();

    // Mesmo motivo/padrão do equippedCache acima — getHiddenCosmeticIds() passou a ser chamado
    // por jogador a cada tick (GreatCosmetics.java, pra não tocar som/partícula de um cosmético
    // escondido), então também precisa de cache em memória pra não virar uma query SQL síncrona
    // por jogador por tick. Invalidado em setCosmeticHidden() e no disconnect (clearPlayerCache).
    private static final java.util.Map<UUID, java.util.Set<String>> hiddenCosmeticsCache = new java.util.concurrent.ConcurrentHashMap<>();

    public static void initialize() {
        boolean useMySQL = MainConfig.config != null && MainConfig.config.useMySQL;
        boolean mysqlSuccess = false;

        if (useMySQL) {
            try {
                String host = MainConfig.config.mysqlHost;
                int port = MainConfig.config.mysqlPort;
                String database = MainConfig.config.mysqlDatabase;
                String user = MainConfig.config.mysqlUser;
                String password = MainConfig.config.mysqlPassword;

                String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?autoReconnect=true";
                connection = DriverManager.getConnection(url, user, password);
                GreatCosmetics.LOGGER.info("[SASCosmetics] Conectado ao banco de dados MySQL com sucesso!");
                mysqlSuccess = true;
            } catch (Exception e) {
                GreatCosmetics.LOGGER.error("[SASCosmetics] Erro ao conectar ao MySQL: " + e.getMessage());
                GreatCosmetics.LOGGER.warn("[SASCosmetics] Realizando Fallback automático para SQLite local!");
            }
        }

        if (!useMySQL || !mysqlSuccess) {
            try {
                File configDir = new File(FabricLoader.getInstance().getConfigDir().toFile(), "greatcosmetics");
                if (!configDir.exists()) configDir.mkdirs();

                File dbFile = new File(configDir, "database.db");
                String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                connection = DriverManager.getConnection(url);
                GreatCosmetics.LOGGER.info("[SASCosmetics] Conectado ao banco de dados SQLite (Local) com sucesso!");
            } catch (Exception e) {
                GreatCosmetics.LOGGER.error("[SASCosmetics] Erro CRÍTICO ao conectar ao SQLite de Fallback: " + e.getMessage());
            }
        }

        createTables();
    }

    private static void createTables() {
        if (connection == null) return;
        try {
            // Tabelas de Cosméticos (Acessórios)
            String sqlCosmetics = "CREATE TABLE IF NOT EXISTS player_unlocked_cosmetics (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "cosmetic_id VARCHAR(64) NOT NULL, " +
                    "PRIMARY KEY (uuid, cosmetic_id))";
            try (PreparedStatement stmt = connection.prepareStatement(sqlCosmetics)) { stmt.execute(); }

            String sqlEquipped = "CREATE TABLE IF NOT EXISTS player_equipped_cosmetics (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "cosmetic_id VARCHAR(64) NOT NULL, " +
                    "virtual_slot VARCHAR(32) NOT NULL, " +
                    "cosmetic_type VARCHAR(64) NOT NULL, " +
                    "PRIMARY KEY (uuid, cosmetic_id))";
            try (PreparedStatement stmt = connection.prepareStatement(sqlEquipped)) { stmt.execute(); }

            String sqlSettings = "CREATE TABLE IF NOT EXISTS player_cosmetic_settings (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "hide_helmet BOOLEAN DEFAULT FALSE, " +
                    "hide_chestplate BOOLEAN DEFAULT FALSE, " +
                    "hide_leggings BOOLEAN DEFAULT FALSE, " +
                    "hide_boots BOOLEAN DEFAULT FALSE)";
            try (PreparedStatement stmt = connection.prepareStatement(sqlSettings)) { stmt.execute(); }

            String sqlHiddenCosmetics = "CREATE TABLE IF NOT EXISTS player_hidden_cosmetics (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "cosmetic_id VARCHAR(64) NOT NULL, " +
                    "PRIMARY KEY (uuid, cosmetic_id))";
            try (PreparedStatement stmt = connection.prepareStatement(sqlHiddenCosmetics)) { stmt.execute(); }

            String sqlBackpacks = "CREATE TABLE IF NOT EXISTS player_backpacks (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "cosmetic_id VARCHAR(64) NOT NULL, " +
                    "inventory_data TEXT, " +
                    "PRIMARY KEY (uuid, cosmetic_id))";
            try (PreparedStatement stmt = connection.prepareStatement(sqlBackpacks)) { stmt.execute(); }

            // ==========================================================
            // NOVAS TABELAS: SKINS DE POKÉMON
            // ==========================================================
            String sqlUnlockedSkins = "CREATE TABLE IF NOT EXISTS player_unlocked_skins (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "skin_id VARCHAR(64) NOT NULL, " +
                    "PRIMARY KEY (uuid, skin_id))";
            try (PreparedStatement stmt = connection.prepareStatement(sqlUnlockedSkins)) { stmt.execute(); }

            String sqlSkinCooldowns = "CREATE TABLE IF NOT EXISTS player_skin_cooldowns (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "skin_id VARCHAR(64) NOT NULL, " +
                    "last_applied BIGINT NOT NULL, " +
                    "PRIMARY KEY (uuid, skin_id))";
            try (PreparedStatement stmt = connection.prepareStatement(sqlSkinCooldowns)) { stmt.execute(); }

            // ==========================================================
            // NOVA TABELA: TAGS (posse + qual está equipada)
            // ==========================================================
            String sqlTags = "CREATE TABLE IF NOT EXISTS player_tags (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "tag_id VARCHAR(64) NOT NULL, " +
                    "equipped BOOLEAN NOT NULL DEFAULT FALSE, " +
                    "PRIMARY KEY (uuid, tag_id))";
            try (PreparedStatement stmt = connection.prepareStatement(sqlTags)) { stmt.execute(); }

        } catch (Exception e) {
            GreatCosmetics.LOGGER.error("[SASCosmetics] Erro ao criar/atualizar tabelas no BD: " + e.getMessage());
        }
    }

    // ==========================================================
    // SISTEMA DE SKINS DE POKÉMON
    // ==========================================================

    public static boolean unlockPokemonSkin(UUID playerUuid, String skinId) {
        if (hasPokemonSkin(playerUuid, skinId)) return false;
        String sql = "INSERT INTO player_unlocked_skins (uuid, skin_id) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, skinId.toLowerCase());
            stmt.executeUpdate();
            return true;
        } catch (Exception e) { return false; }
    }

    public static boolean removePokemonSkin(UUID playerUuid, String skinId) {
        if (!hasPokemonSkin(playerUuid, skinId)) return false;
        String sql = "DELETE FROM player_unlocked_skins WHERE uuid = ? AND skin_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, skinId.toLowerCase());
            stmt.executeUpdate();
            return true;
        } catch (Exception e) { return false; }
    }

    public static boolean hasPokemonSkin(UUID playerUuid, String skinId) {
        String sql = "SELECT 1 FROM player_unlocked_skins WHERE uuid = ? AND skin_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, skinId.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) { return false; }
    }

    public static List<String> getPlayerUnlockedSkins(UUID playerUuid) {
        List<String> unlockedList = new ArrayList<>();
        if (connection == null) return unlockedList;
        String sql = "SELECT skin_id FROM player_unlocked_skins WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    unlockedList.add(rs.getString("skin_id"));
                }
            }
        } catch (Exception ignored) {}
        return unlockedList;
    }

    // Cooldowns
    public static void setSkinCooldown(UUID playerUuid, String skinId, long timestamp) {
        if (connection == null) return;
        try {
            String sql = "INSERT INTO player_skin_cooldowns (uuid, skin_id, last_applied) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE last_applied = VALUES(last_applied)";

            if (connection.getMetaData().getURL().startsWith("jdbc:sqlite:")) {
                sql = "INSERT OR REPLACE INTO player_skin_cooldowns (uuid, skin_id, last_applied) VALUES (?, ?, ?)";
            }

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, skinId.toLowerCase());
                stmt.setLong(3, timestamp);
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            GreatCosmetics.LOGGER.error("[Cosmetics] Erro ao salvar cooldown de skin: " + e.getMessage());
        }
    }

    public static long getSkinLastApplied(UUID playerUuid, String skinId) {
        if (connection == null) return 0L;
        String sql = "SELECT last_applied FROM player_skin_cooldowns WHERE uuid = ? AND skin_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, skinId.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getLong("last_applied");
            }
        } catch (Exception ignored) {}
        return 0L;
    }


    // ==========================================================
    // SISTEMA DE COSMÉTICOS (ACESSÓRIOS)
    // ==========================================================

    public static void broadcastPlayerCosmetics(net.minecraft.server.network.ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        java.util.List<String> equipped = getPlayerEquippedCosmetics(uuid);
        PlayerSettings settings = getPlayerSettings(uuid);

        com.f4xizzz.greatcosmetics.network.SyncPlayerCosmeticsPayload payload =
                new com.f4xizzz.greatcosmetics.network.SyncPlayerCosmeticsPayload(
                        uuid, equipped,
                        settings.hideHelmet(), settings.hideChestplate(), settings.hideLeggings(), settings.hideBoots(),
                        new java.util.ArrayList<>(settings.hiddenCosmeticIds())
                );

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
        for (net.minecraft.server.network.ServerPlayerEntity p : player.getServer().getPlayerManager().getPlayerList()) {
            if (p != player) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, payload);
            }
        }
    }

    public static boolean unlockCosmetic(UUID playerUuid, String cosmeticId) {
        if (hasCosmetic(playerUuid, cosmeticId)) return false;
        String sql = "INSERT INTO player_unlocked_cosmetics (uuid, cosmetic_id) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, cosmeticId.toLowerCase());
            stmt.executeUpdate();
            return true;
        } catch (Exception e) { return false; }
    }

    public static PlayerSettings getPlayerSettings(UUID playerUuid) {
        if (connection == null) return new PlayerSettings(false, false, false, false, java.util.Set.of());
        boolean hH = false, hC = false, hL = false, hB = false;
        String sql = "SELECT hide_helmet, hide_chestplate, hide_leggings, hide_boots FROM player_cosmetic_settings WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    hH = rs.getBoolean("hide_helmet");
                    hC = rs.getBoolean("hide_chestplate");
                    hL = rs.getBoolean("hide_leggings");
                    hB = rs.getBoolean("hide_boots");
                }
            }
        } catch (Exception e) {}
        return new PlayerSettings(hH, hC, hL, hB, getHiddenCosmeticIds(playerUuid));
    }

    /** Cosméticos individuais escondidos (não o slot inteiro) — ver player_hidden_cosmetics. */
    public static java.util.Set<String> getHiddenCosmeticIds(UUID playerUuid) {
        if (connection == null) return java.util.Set.of();
        return hiddenCosmeticsCache.computeIfAbsent(playerUuid, DatabaseManager::queryHiddenCosmeticIds);
    }

    private static java.util.Set<String> queryHiddenCosmeticIds(UUID playerUuid) {
        java.util.Set<String> result = new java.util.HashSet<>();
        String sql = "SELECT cosmetic_id FROM player_hidden_cosmetics WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) result.add(rs.getString("cosmetic_id"));
            }
        } catch (Exception e) {}
        return result;
    }

    public static void setCosmeticHidden(UUID playerUuid, String cosmeticId, boolean hidden) {
        if (connection == null) return;
        hiddenCosmeticsCache.remove(playerUuid);
        if (hidden) {
            // Mesma técnica de unlockCosmetic()/hasCosmetic(): tenta o INSERT puro (sem "OR
            // IGNORE"/"IGNORE", sintaxe que difere entre SQLite e MySQL) e deixa a PRIMARY KEY
            // (uuid, cosmetic_id) barrar duplicata — só ignora a exceção se já estava escondido.
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO player_hidden_cosmetics (uuid, cosmetic_id) VALUES (?, ?)")) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, cosmeticId);
                stmt.executeUpdate();
            } catch (Exception ignored) {}
        } else {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM player_hidden_cosmetics WHERE uuid = ? AND cosmetic_id = ?")) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, cosmeticId);
                stmt.executeUpdate();
            } catch (Exception e) {
                System.err.println("[GreatCosmetics] Erro ao mostrar cosmético '" + cosmeticId + "': " + e.getMessage());
            }
        }
    }

    public static void updatePlayerSetting(UUID playerUuid, String column, boolean value) {
        if (connection == null) return;
        if (!column.matches("hide_(helmet|chestplate|leggings|boots)")) return;
        try {
            boolean exists = false;
            try (PreparedStatement check = connection.prepareStatement("SELECT 1 FROM player_cosmetic_settings WHERE uuid = ?")) {
                check.setString(1, playerUuid.toString());
                try (ResultSet rs = check.executeQuery()) { exists = rs.next(); }
            }
            if (exists) {
                String sql = "UPDATE player_cosmetic_settings SET " + column + " = ? WHERE uuid = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setBoolean(1, value);
                    stmt.setString(2, playerUuid.toString());
                    stmt.executeUpdate();
                }
            } else {
                String sql = "INSERT INTO player_cosmetic_settings (uuid, " + column + ") VALUES (?, ?)";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setBoolean(2, value);
                    stmt.executeUpdate();
                }
            }
        } catch (Exception e) {}
    }

    public static boolean removeCosmetic(UUID playerUuid, String cosmeticId) {
        if (!hasCosmetic(playerUuid, cosmeticId)) return false;
        String sql = "DELETE FROM player_unlocked_cosmetics WHERE uuid = ? AND cosmetic_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, cosmeticId.toLowerCase());
            stmt.executeUpdate();
            unequipCosmetic(playerUuid, cosmeticId);
            return true;
        } catch (Exception e) { return false; }
    }

    public static boolean hasCosmetic(UUID playerUuid, String cosmeticId) {
        String sql = "SELECT 1 FROM player_unlocked_cosmetics WHERE uuid = ? AND cosmetic_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, cosmeticId.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) { return rs.next(); }
        } catch (Exception e) { return false; }
    }

    public static List<String> getPlayerUnlockedCosmetics(UUID playerUuid) {
        List<String> unlockedList = new ArrayList<>();
        if (connection == null) return unlockedList;
        String sql = "SELECT cosmetic_id FROM player_unlocked_cosmetics WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) { unlockedList.add(rs.getString("cosmetic_id")); }
            }
        } catch (Exception ignored) {}
        return unlockedList;
    }

    public static boolean equipCosmetic(UUID playerUuid, String cosmeticId, String virtualSlot, String type) {
        unequipCosmetic(playerUuid, cosmeticId);
        String sql = "INSERT INTO player_equipped_cosmetics (uuid, cosmetic_id, virtual_slot, cosmetic_type) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, cosmeticId.toLowerCase());
            stmt.setString(3, virtualSlot.toUpperCase());
            stmt.setString(4, type.toLowerCase());
            stmt.executeUpdate();
            equippedCache.remove(playerUuid);
            return true;
        } catch (Exception e) { return false; }
    }

    public static boolean unequipCosmetic(UUID playerUuid, String cosmeticId) {
        String sql = "DELETE FROM player_equipped_cosmetics WHERE uuid = ? AND cosmetic_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, cosmeticId.toLowerCase());
            stmt.executeUpdate();
            equippedCache.remove(playerUuid);
            return true;
        } catch (Exception e) { return false; }
    }

    public static List<String> getPlayerEquippedCosmetics(UUID playerUuid) {
        if (connection == null) return new ArrayList<>();
        return equippedCache.computeIfAbsent(playerUuid, DatabaseManager::queryPlayerEquippedCosmetics);
    }

    private static List<String> queryPlayerEquippedCosmetics(UUID playerUuid) {
        List<String> equippedList = new ArrayList<>();
        String sql = "SELECT cosmetic_id FROM player_equipped_cosmetics WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) { equippedList.add(rs.getString("cosmetic_id")); }
            }
        } catch (Exception ignored) {}
        return equippedList;
    }

    /** Limpa o cache de equipados de um jogador — chamar no DISCONNECT pra não acumular memória
     *  indefinidamente num servidor de longa duração com muita entrada/saída de jogadores. */
    public static void clearPlayerCache(UUID playerUuid) {
        equippedCache.remove(playerUuid);
        hiddenCosmeticsCache.remove(playerUuid);
    }

    // ==========================================================
    // SISTEMA DE TAGS
    // ==========================================================

    public static boolean unlockTag(UUID playerUuid, String tagId) {
        if (hasTag(playerUuid, tagId)) return false;
        String sql = "INSERT INTO player_tags (uuid, tag_id, equipped) VALUES (?, ?, FALSE)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, tagId.toLowerCase());
            stmt.executeUpdate();
            return true;
        } catch (Exception e) { return false; }
    }

    public static boolean hasTag(UUID playerUuid, String tagId) {
        String sql = "SELECT 1 FROM player_tags WHERE uuid = ? AND tag_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, tagId.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) { return rs.next(); }
        } catch (Exception e) { return false; }
    }

    public static List<String> getPlayerOwnedTags(UUID playerUuid) {
        List<String> owned = new ArrayList<>();
        if (connection == null) return owned;
        String sql = "SELECT tag_id FROM player_tags WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) { owned.add(rs.getString("tag_id")); }
            }
        } catch (Exception ignored) {}
        return owned;
    }

    public static String getEquippedTagId(UUID playerUuid) {
        if (connection == null) return null;
        String sql = "SELECT tag_id FROM player_tags WHERE uuid = ? AND equipped = TRUE";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getString("tag_id");
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Marca {@code tagId} como equipada e desmarca qualquer outra tag equipada do jogador. */
    /**
     * Marca {@code tagId} como equipada e desmarca qualquer outra do jogador. Faz upsert (UPDATE,
     * e se não existia linha nenhuma — caso de tags de grupo, cuja posse nunca cria uma linha em
     * player_tags, e do bypass da chavinha de Dev — cai pro INSERT) pra nunca silenciosamente
     * não fazer nada por falta de uma linha prévia na tabela.
     */
    public static void equipTag(UUID playerUuid, String tagId) {
        unequipAllForPlayer(playerUuid);

        String updateSql = "UPDATE player_tags SET equipped = TRUE WHERE uuid = ? AND tag_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(updateSql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, tagId.toLowerCase());
            int updated = stmt.executeUpdate();
            if (updated > 0) return;
        } catch (Exception ignored) {}

        String insertSql = "INSERT INTO player_tags (uuid, tag_id, equipped) VALUES (?, ?, TRUE)";
        try (PreparedStatement stmt = connection.prepareStatement(insertSql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, tagId.toLowerCase());
            stmt.executeUpdate();
        } catch (Exception ignored) {}
    }

    public static void unequipAllForPlayer(UUID playerUuid) {
        String sql = "UPDATE player_tags SET equipped = FALSE WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.executeUpdate();
        } catch (Exception ignored) {}
    }

    public static boolean removeTagOwnership(UUID playerUuid, String tagId) {
        if (!hasTag(playerUuid, tagId)) return false;
        String sql = "DELETE FROM player_tags WHERE uuid = ? AND tag_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, tagId.toLowerCase());
            stmt.executeUpdate();
            return true;
        } catch (Exception e) { return false; }
    }

    /** Limpa toda referência a uma tag que foi apagada do catálogo (todos os jogadores). */
    public static void removeAllOwnershipOfTag(String tagId) {
        String sql = "DELETE FROM player_tags WHERE tag_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, tagId.toLowerCase());
            stmt.executeUpdate();
        } catch (Exception ignored) {}
    }

    public static int getEquippedCountBySlot(UUID playerUuid, String virtualSlot) {
        String sql = "SELECT COUNT(*) AS total FROM player_equipped_cosmetics WHERE uuid = ? AND virtual_slot = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, virtualSlot.toUpperCase());
            try (ResultSet rs = stmt.executeQuery()) { if (rs.next()) return rs.getInt("total"); }
        } catch (Exception ignored) {}
        return 0;
    }

    public static int getEquippedCountByType(UUID playerUuid, String type) {
        String sql = "SELECT COUNT(*) AS total FROM player_equipped_cosmetics WHERE uuid = ? AND cosmetic_type = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, type.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) { if (rs.next()) return rs.getInt("total"); }
        } catch (Exception ignored) {}
        return 0;
    }

    public static void saveBackpackData(UUID playerUuid, String cosmeticId, String inventoryData) {
        if (connection == null) return;
        try {
            String sql = "INSERT INTO player_backpacks (uuid, cosmetic_id, inventory_data) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE inventory_data = VALUES(inventory_data)";
            if (connection.getMetaData().getURL().startsWith("jdbc:sqlite:")) {
                sql = "INSERT OR REPLACE INTO player_backpacks (uuid, cosmetic_id, inventory_data) VALUES (?, ?, ?)";
            }
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, cosmeticId.toLowerCase());
                stmt.setString(3, inventoryData);
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            // Estava um catch vazio — se a página de uma mochila (ex: "id#p1") nunca salvava e
            // ninguém via NENHUM erro no console, essa era a causa: cosmetic_id é VARCHAR(64), e um
            // id de cosmético já perto do limite ESTOURA 64 caracteres com o sufixo "#p1"/"#p2" da
            // paginação, e o INSERT falhava silenciosamente aqui (constraint/truncamento). Logando
            // pra parar de esconder esse tipo de falha.
            GreatCosmetics.LOGGER.error("[SASCosmetics] Erro ao salvar mochila '" + cosmeticId + "' de " + playerUuid + " na database: " + e.getMessage());
        }
    }

    public static String getBackpackData(UUID playerUuid, String cosmeticId) {
        if (connection == null) return "";
        String sql = "SELECT inventory_data FROM player_backpacks WHERE uuid = ? AND cosmetic_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, cosmeticId.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getString("inventory_data");
            }
        } catch (Exception e) {
            GreatCosmetics.LOGGER.error("[SASCosmetics] Erro ao ler mochila '" + cosmeticId + "' de " + playerUuid + " da database: " + e.getMessage());
        }
        return "";
    }

    public static void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (Exception ignored) {}
    }
}