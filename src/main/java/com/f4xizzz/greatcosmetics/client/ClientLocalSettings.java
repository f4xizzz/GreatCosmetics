package com.f4xizzz.greatcosmetics.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;

/**
 * Preferências LOCAIS do cliente que não vão pro servidor (não são por-player, não sincronizam):
 * só coisas de performance/conveniência da máquina, tipo "esconder cosmético dos outros jogadores".
 * Guardado em {@code config/GreatCosmetics/client_local.json}.
 */
public final class ClientLocalSettings {

    private ClientLocalSettings() {}

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("GreatCosmetics/client_local.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static class Data {
        boolean hideOtherPlayersCosmetics = false;
    }

    public static void load() {
        try {
            if (FILE.toFile().exists()) {
                try (FileReader r = new FileReader(FILE.toFile())) {
                    Data d = GSON.fromJson(r, Data.class);
                    if (d != null) ClientCosmeticCache.hideOtherPlayersCosmetics = d.hideOtherPlayersCosmetics;
                }
            }
        } catch (Exception ignored) {}
    }

    public static void save() {
        try {
            FILE.toFile().getParentFile().mkdirs();
            Data d = new Data();
            d.hideOtherPlayersCosmetics = ClientCosmeticCache.hideOtherPlayersCosmetics;
            try (FileWriter w = new FileWriter(FILE.toFile())) {
                GSON.toJson(d, w);
            }
        } catch (Exception ignored) {}
    }
}
