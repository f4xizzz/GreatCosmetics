package com.f4xizzz.greatcosmetics.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.architectury.platform.Platform;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Cor de destaque de cada GRUPO de skins (PokemonSkin.getGroup()) mostrado como cabeçalho na lista
 * de skins da Party page — antes sempre dourado fixo, agora configurável por grupo em
 * greatcosmetics/skin_groups.json ({"OverWatch": "#FF6600", ...}), no mesmo espírito de
 * pokeskins.json (edição manual do arquivo + /gc reload, já que ainda não existe editor de skins
 * na GUI). Grupo sem cor definida cai no dourado padrão de sempre (ver DEFAULT_COLOR).
 */
public class SkinGroupConfigManager {

    public static final String DEFAULT_COLOR = "#FFAA00";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final File CONFIG_DIR = new File(Platform.getConfigFolder().toFile(), "GreatCosmetics");
    private static final File CONFIG_FILE = new File(CONFIG_DIR, "skin_groups.json");

    private static Map<String, String> groupColors = new HashMap<>();

    public static void load() {
        groupColors.clear();

        if (!CONFIG_FILE.exists()) {
            save();
            return;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> loaded = GSON.fromJson(reader, type);
            if (loaded != null) groupColors = loaded;
            com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("SkinGroupConfigManager: skin_groups.json loaded — " + groupColors.size() + " grupo(s).");
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Error loading skin_groups.json: " + e.getMessage());
            com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("SkinGroupConfigManager: FAILED to load skin_groups.json — " + e);
        }
    }

    private static void save() {
        if (!CONFIG_DIR.exists()) CONFIG_DIR.mkdirs();
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(groupColors, writer);
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Error saving skin_groups.json: " + e.getMessage());
        }
    }

    public static Map<String, String> getAllColors() {
        return new HashMap<>(groupColors);
    }

    /** Cor em formato "#RRGGBB" pro grupo, ou DEFAULT_COLOR se não configurado. */
    public static String getColor(String groupName) {
        if (groupName == null) return DEFAULT_COLOR;
        return groupColors.getOrDefault(groupName, DEFAULT_COLOR);
    }

    public static void setColor(String groupName, String hexColor) {
        if (groupName == null || groupName.isBlank()) return;
        groupColors.put(groupName, hexColor);
        save();
    }

    /** Client-only: substitui pelo mapa que o servidor mandou (ver SyncSkinCatalogPayload) — mesmo
     *  motivo do setSkinsFromServer em SkinConfigManager, senão o client só via a cor que ele
     *  mesmo já tinha salva localmente, nunca a de verdade do servidor. */
    public static void setColorsFromServer(Map<String, String> colors) {
        groupColors = colors != null ? new HashMap<>(colors) : new HashMap<>();
    }
}
