package com.f4xizzz.greatcosmetics.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Set;

/** Cosméticos favoritados pelo JOGADOR nesse client — 100% client-side (não sincroniza com o
 *  servidor nem é visível pra outros jogadores), persistido em disco pra sobreviver a reinícios do
 *  jogo. Favoritos aparecem primeiro na grade da Acessórios (ver AcessoriesPage#getFilteredItems/
 *  computeFilteredItems). */
public class ClientFavoriteCosmetics {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static File FILE;

    public static final Set<String> favoriteIds = new HashSet<>();

    // Incrementado em toggle() — usado por AcessoriesPage pra saber que precisa recalcular a lista
    // filtrada/ordenada (ver o cache de getFilteredItems), já que .size() sozinho não pega o caso
    // de desfavoritar um E favoritar outro no mesmo frame (mesmo tamanho, ordem diferente).
    public static int version = 0;

    public static void load() {
        File configDir = new File(FabricLoader.getInstance().getConfigDir().toFile(), "greatcosmetics");
        if (!configDir.exists()) configDir.mkdirs();
        FILE = new File(configDir, "favorite_cosmetics.json");

        favoriteIds.clear();
        if (!FILE.exists()) return;

        try (FileReader reader = new FileReader(FILE)) {
            Set<String> loaded = GSON.fromJson(reader, new TypeToken<Set<String>>() {}.getType());
            if (loaded != null) favoriteIds.addAll(loaded);
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Erro ao carregar favorite_cosmetics.json: " + e.getMessage());
        }
    }

    public static void save() {
        if (FILE == null) return;
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(favoriteIds, writer);
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Erro ao salvar favorite_cosmetics.json: " + e.getMessage());
        }
    }

    public static boolean isFavorite(String cosmeticId) {
        return cosmeticId != null && favoriteIds.contains(cosmeticId);
    }

    public static void toggle(String cosmeticId) {
        if (cosmeticId == null) return;
        if (!favoriteIds.remove(cosmeticId)) favoriteIds.add(cosmeticId);
        version++;
        save();
    }
}
