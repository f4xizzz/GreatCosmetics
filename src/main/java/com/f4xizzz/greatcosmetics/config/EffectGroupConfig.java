package com.f4xizzz.greatcosmetics.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Espelha {@link EffectConfig} pros grupos de efeito. Mesmo mapa no client e no server — o
 *  client recebe via {@code SyncEffectGroupsPayload} (ver GreatCosmeticsClient), o server carrega
 *  do {@code config/GreatCosmetics/effect_groups.json}. */
public class EffectGroupConfig {
    public static Map<String, EffectGroupData> groupsMap = new HashMap<>();
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("GreatCosmetics/effect_groups.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static void load() {
        File file = CONFIG_FILE.toFile();
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();

        if (!file.exists()) {
            groupsMap = new HashMap<>();
            save();
            System.out.println("[GreatCosmetics] effect_groups.json generated (empty).");
            return;
        }

        boolean shouldResave = false;
        try (Reader reader = new FileReader(file)) {
            java.lang.reflect.Type type = new TypeToken<Map<String, EffectGroupData>>(){}.getType();
            groupsMap = GSON.fromJson(reader, type);
            if (groupsMap == null) groupsMap = new HashMap<>();
            shouldResave = true;
            com.f4xizzz.greatcosmetics.GreatCosmetics.debugLog("EffectGroupConfig: effect_groups.json loaded — " + groupsMap.size() + " group(s).");
        } catch (Exception e) {
            System.out.println("[GreatCosmetics] Error loading effect_groups.json!");
            com.f4xizzz.greatcosmetics.GreatCosmetics.debugLog("EffectGroupConfig: FAILED to load effect_groups.json — " + e);
            e.printStackTrace();
        }
        // Regrava só DEPOIS do reader fechar (lock no Windows — mesmo motivo de EffectConfig).
        if (shouldResave) save();
    }

    public static void save() {
        try (Writer writer = new FileWriter(CONFIG_FILE.toFile())) {
            GSON.toJson(groupsMap, writer);
            com.f4xizzz.greatcosmetics.GreatCosmetics.debugLog("EffectGroupConfig: effect_groups.json saved with " + groupsMap.size() + " group(s).");
        } catch (Exception e) {
            com.f4xizzz.greatcosmetics.GreatCosmetics.debugLog("EffectGroupConfig: FAILED to save effect_groups.json — " + e);
            e.printStackTrace();
        }
    }

    /** {@code id} pode ser um id de EFEITO (→ 1 efeito) ou de GRUPO (→ todos os membros). Ignora
     *  ids de membro que não resolvem pra nenhum efeito. Nunca retorna {@code null} nem entradas
     *  {@code null}. */
    public static List<EffectData> resolve(String id) {
        if (id == null) return List.of();
        EffectGroupData group = groupsMap.get(id);
        if (group == null) {
            EffectData single = EffectConfig.effectsMap.get(id);
            return single != null ? List.of(single) : List.of();
        }
        List<EffectData> out = new ArrayList<>();
        if (group.ownedEffects != null) {
            for (EffectData e : group.ownedEffects) if (e != null) out.add(e);
        }
        if (group.effectIds != null) {
            for (String memberId : group.effectIds) {
                EffectData e = EffectConfig.effectsMap.get(memberId);
                if (e != null) out.add(e);
            }
        }
        return out;
    }
}
