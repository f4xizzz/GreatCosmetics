package com.f4xizzz.greatcosmetics.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SkinConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    // Aponta exatamente para config/greatcosmetics/pokeskins.json
    private static final File CONFIG_DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), "greatcosmetics");
    private static final File CONFIG_FILE = new File(CONFIG_DIR, "pokeskins.json");

    private static final Map<String, PokemonSkin> LOADED_SKINS = new HashMap<>();

    public static void load() {
        LOADED_SKINS.clear();

        if (!CONFIG_FILE.exists()) {
            createDefaultConfig();
        }

        boolean shouldResave = false;

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            Type listType = new TypeToken<List<PokemonSkin>>() {}.getType();
            List<PokemonSkin> skins = GSON.fromJson(reader, listType);

            if (skins != null) {
                for (PokemonSkin skin : skins) {
                    LOADED_SKINS.put(skin.getId(), skin);
                }
                shouldResave = true;
            }
            System.out.println("[GreatCosmetics] Foram carregadas " + LOADED_SKINS.size() + " skins de pokemon.");
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Erro ao carregar o arquivo de skins: " + e.getMessage());
        }

        // Regrava só DEPOIS do try-with-resources fechar o FileReader — abrir um FileWriter pro
        // MESMO arquivo enquanto o reader ainda está aberto é um conflito de lock clássico no
        // Windows, que corrompia/esvaziava o pokeskins.json.
        if (shouldResave) save();
    }

    private static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(new ArrayList<>(LOADED_SKINS.values()), writer);
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Erro ao salvar o arquivo de skins: " + e.getMessage());
        }
    }

    private static void createDefaultConfig() {
        // Garante que a pasta 'greatcosmetics' exista antes de tentar criar o arquivo
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }

        List<PokemonSkin> defaults = new ArrayList<>();
        defaults.add(new PokemonSkin("pikachu_summer", "Pikachu Praiano", "pikachu", "summer", 60));
        defaults.add(new PokemonSkin("charizard_clone", "Charizard Clone", "charizard", "clone", 120));

        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(defaults, writer);
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Erro ao criar o config padrão de skins: " + e.getMessage());
        }
    }

    public static PokemonSkin getSkin(String id) {
        return LOADED_SKINS.get(id);
    }

    public static List<PokemonSkin> getAllSkins() {
        return new ArrayList<>(LOADED_SKINS.values());
    }

    /** Client-only: substitui o catálogo local pelo que o servidor mandou (ver
     *  SyncSkinCatalogPayload) — sem isso o client só via as skins que ele mesmo já tinha em
     *  disco, nunca as que o server adicionou/editou depois, nem com /gc reload. */
    public static void setSkinsFromServer(List<PokemonSkin> skins) {
        LOADED_SKINS.clear();
        for (PokemonSkin skin : skins) {
            LOADED_SKINS.put(skin.getId(), skin);
        }
    }
}