package com.f4xizzz.greatcosmetics.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class SoundConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final File FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "GreatCosmetics/sounds.json");

    public static SoundData data = new SoundData();

    // A classe que define a estrutura do JSON
    public static class SoundData {
        public SoundEntry wardrobeOpen = new SoundEntry("minecraft:block.chest.open", 0.5f, 1.2f);
        public SoundEntry wardrobeClose = new SoundEntry("minecraft:block.chest.close", 0.5f, 1.2f);
        public SoundEntry clickSuccess = new SoundEntry("minecraft:entity.experience_orb.pickup", 0.7f, 1.0f);
        public SoundEntry clickError = new SoundEntry("minecraft:entity.villager.no", 1.0f, 1.0f);
        public SoundEntry pageTurn = new SoundEntry("minecraft:item.book.page_turn", 0.8f, 1.0f);

        // ==========================================
        // NOVOS SONS ADICIONADOS
        // ==========================================
        public SoundEntry open_backpack = new SoundEntry("minecraft:item.bundle.drop_contents", 0.8f, 1.0f);
        public SoundEntry close_backpack = new SoundEntry("minecraft:item.bundle.insert", 0.8f, 1.0f);
        public SoundEntry change_slot = new SoundEntry("minecraft:item.armor.equip_leather", 0.7f, 1.0f);
        public SoundEntry unequip_item = new SoundEntry("minecraft:block.note_block.pling", 1.0f, 2.0f);
        public SoundEntry error_permission = new SoundEntry("minecraft:entity.villager.no", 1.0f, 1.0f);
        public SoundEntry error_action = new SoundEntry("minecraft:block.note_block.bass", 1.0f, 1.0f);
    }

    // A classe que guarda os detalhes de cada som (ID, Volume, Pitch)
    public static class SoundEntry {
        public String id;
        public float volume;
        public float pitch;

        public SoundEntry(String id, float volume, float pitch) {
            this.id = id;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    // ==========================================
    // BUSCADOR PARA O SISTEMA UNIVERSAL
    // ==========================================
    public static SoundEntry getSoundData(String key) {
        return switch (key) {
            case "wardrobeOpen" -> data.wardrobeOpen;
            case "wardrobeClose" -> data.wardrobeClose;
            case "clickSuccess" -> data.clickSuccess;
            case "clickError" -> data.clickError;
            case "pageTurn" -> data.pageTurn;
            case "open_backpack" -> data.open_backpack;
            case "close_backpack" -> data.close_backpack;
            case "change_slot" -> data.change_slot;
            case "unequip_item" -> data.unequip_item;
            case "error_permission" -> data.error_permission;
            case "error_action" -> data.error_action;
            default -> null; // Retorna nulo se o som não existir
        };
    }

    public static void loadSounds() {
        if (FILE.exists()) {
            try (FileReader reader = new FileReader(FILE)) {
                data = GSON.fromJson(reader, SoundData.class);
            } catch (Exception e) {
                System.err.println("[GreatCosmetics] Error reading sounds.json! Recreating backup...");
                com.f4xizzz.greatcosmetics.GreatCosmetics.debugLog("SoundConfig: FAILED to read sounds.json — " + e + ". Recreating with defaults.");
                saveSounds();
            }
        } else {
            saveSounds();
        }
        com.f4xizzz.greatcosmetics.GreatCosmetics.debugLog("SoundConfig: sounds.json loaded.");
    }

    public static void saveSounds() {
        // Cria a pasta sascosmetics se ela não existir
        if (!FILE.getParentFile().exists()) {
            FILE.getParentFile().mkdirs();
        }

        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}