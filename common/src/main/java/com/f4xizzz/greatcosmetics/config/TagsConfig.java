package com.f4xizzz.greatcosmetics.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.f4xizzz.greatcosmetics.database.DatabaseManager;
import com.f4xizzz.greatcosmetics.util.LuckPermsTagManager;
import dev.architectury.platform.Platform;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Catálogo de tags (o "TagsConfig" da feature de Tags Page). Cada entrada é ou uma tag criada
 * na GUI (editável/deletável livremente) ou uma tag importada de um grupo do LuckPerms
 * ({@code isGroupTag=true}, nunca deletável — ver {@link com.f4xizzz.greatcosmetics.network.DeleteTagPayload}).
 */
public class TagsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final File DIR = new File(Platform.getConfigFolder().toFile(), "GreatCosmetics");
    private static final File FILE = new File(DIR, "tags.json");

    public static Map<String, TagData> tagsMap = new HashMap<>();

    public static void load() {
        if (!DIR.exists()) DIR.mkdirs();

        if (FILE.exists()) {
            try (JsonReader reader = new JsonReader(new FileReader(FILE))) {
                reader.setLenient(true);
                Type type = new TypeToken<Map<String, TagData>>() {}.getType();
                Map<String, TagData> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    tagsMap = loaded;
                }
            } catch (Exception e) {
                System.err.println("[GreatCosmetics] Error loading tags.json!");
                com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("TagsConfig: FAILED to load tags.json — " + e);
                e.printStackTrace();
            }
        }

        for (Map.Entry<String, TagData> entry : tagsMap.entrySet()) {
            entry.getValue().id = entry.getKey();
        }

        syncGroupsFromLuckPerms();
        save();
        com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("TagsConfig: load completed — " + tagsMap.size() + " tag(s) in the catalog.");
    }

    /**
     * Importa qualquer grupo do LuckPerms (que não esteja na blacklist — ver
     * MainConfig.tagGroupBlacklist) que ainda não tenha uma tag correspondente no catálogo, já
     * preenchendo displayName/weight/TAG (prefix atual do grupo) — descrição e permissions
     * ficam vazias pro dev preencher na GUI. Tags de grupo já existentes NÃO são sobrescritas
     * (o dev pode ter customizado os campos manualmente depois da importação). Grupos que JÁ
     * tinham sido importados e entraram na blacklist depois são removidos do catálogo.
     */
    private static void syncGroupsFromLuckPerms() {
        Set<String> blacklist = new HashSet<>();
        for (String name : MainConfig.config.tagGroupBlacklist) {
            if (name != null) blacklist.add(name.toLowerCase());
        }

        for (LuckPermsTagManager.GroupSnapshot group : LuckPermsTagManager.listGroups()) {
            if (blacklist.contains(group.name().toLowerCase())) continue;

            TagData existing = tagsMap.get(group.name());

            if (existing == null) {
                TagData data = new TagData();
                data.id = group.name();
                data.displayName = group.displayName();
                data.tag = group.prefix();
                data.isGroupTag = true;
                data.weight = group.weight();
                tagsMap.put(group.name(), data);

                System.out.println("[GreatCosmetics] Tag group importada do LuckPerms: " + group.name());
            } else if (existing.isGroupTag && (existing.tag == null || existing.tag.isBlank())) {
                // Migração: entradas importadas ANTES do TAG vir preenchido automaticamente
                // (versão anterior) recebem o prefix agora — só quando ainda está vazio, então
                // nunca sobrescreve um TAG que o dev já customizou manualmente.
                existing.tag = group.prefix();
            }
        }

        // Remove tags de grupo que já estavam no catálogo mas entraram na blacklist depois.
        List<String> toRemove = new ArrayList<>();
        for (TagData data : tagsMap.values()) {
            if (data.isGroupTag && blacklist.contains(data.id.toLowerCase())) {
                toRemove.add(data.id);
            }
        }
        for (String id : toRemove) {
            tagsMap.remove(id);
            DatabaseManager.removeAllOwnershipOfTag(id);
            System.out.println("[GreatCosmetics] Tag group '" + id + "' removed from catalog (group is in the blacklist).");
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(tagsMap, writer);
        } catch (IOException e) {
            System.err.println("[GreatCosmetics] Error saving tags.json!");
            e.printStackTrace();
        }
    }

    public static TagData getById(String id) {
        return id == null ? null : tagsMap.get(id);
    }
}
