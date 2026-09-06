package com.f4xizzz.greatcosmetics.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.architectury.platform.Platform;
import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class EffectConfig {
    public static Map<String, EffectData> effectsMap = new HashMap<>();
    private static final Path CONFIG_FILE = Platform.getConfigFolder().resolve("GreatCosmetics/effects.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static void loadEffects() {
        File file = CONFIG_FILE.toFile();

        // Cria a pasta sascosmetics se ela não existir
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        // Se o effects.json não existir, gera os exemplos e salva
        if (!file.exists()) {
            generateDefaults();
            saveEffects();
            System.out.println("[GreatCosmetics] effects.json file generated with default presets.");
        } else {
            // Se existir, lê o arquivo
            boolean shouldResave = false;
            try (Reader reader = new FileReader(file)) {
                java.lang.reflect.Type type = new TypeToken<Map<String, EffectData>>(){}.getType();
                effectsMap = GSON.fromJson(reader, type);
                if (effectsMap == null) {
                    effectsMap = new HashMap<>();
                }
                shouldResave = true;
                com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("EffectConfig: effects.json loaded — " + effectsMap.size() + " effect(s).");
            } catch (Exception e) {
                System.out.println("[GreatCosmetics] Error loading o effects.json!");
                com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("EffectConfig: FAILED to load effects.json — " + e);
                e.printStackTrace();
            }
            // Regrava só DEPOIS do try-with-resources fechar o reader — abrir o FileWriter
            // (dentro de saveEffects()) enquanto o reader ainda está aberto pro MESMO arquivo é
            // um conflito de lock clássico no Windows, que corrompia/esvaziava o effects.json.
            if (shouldResave) saveEffects();
        }
    }

    public static void saveEffects() {
        try (Writer writer = new FileWriter(CONFIG_FILE.toFile())) {
            GSON.toJson(effectsMap, writer);
            com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("EffectConfig: effects.json saved with " + effectsMap.size() + " effect(s).");
        } catch (Exception e) {
            com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("EffectConfig: FAILED to save effects.json — " + e);
            e.printStackTrace();
        }
    }

    private static void generateDefaults() {
        effectsMap.clear();

        EffectData e1 = new EffectData();
        e1.particleId = "minecraft:soul_fire_flame";
        e1.count = 5; e1.speed = 0.05; e1.spreadX = 0.5; e1.spreadY = 0.5; e1.spreadZ = 0.5; e1.offsetY = 1.0; e1.tickInterval = 10;
        effectsMap.put("effect123", e1);

        EffectData e2 = new EffectData();
        e2.particleId = "minecraft:end_rod";
        e2.count = 15; e2.speed = 0.1; e2.spreadX = 1.2; e2.spreadY = 0.2; e2.spreadZ = 1.2; e2.offsetY = 0.1; e2.tickInterval = 5;
        effectsMap.put("legendaryAura", e2);

        EffectData e3 = new EffectData();
        e3.particleId = "minecraft:campfire_cosy_smoke";
        e3.count = 1; e3.speed = 0.01; e3.spreadX = 0.2; e3.spreadY = 0.1; e3.spreadZ = 0.2; e3.offsetY = 2.1; e3.tickInterval = 20;
        effectsMap.put("headSmoke", e3);
    }
}