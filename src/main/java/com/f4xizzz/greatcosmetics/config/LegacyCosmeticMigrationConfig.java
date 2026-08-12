package com.f4xizzz.greatcosmetics.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Mapeamento dos custom_model_data ANTIGOS da carved_pumpkin (de antes do mod usar seu próprio
 * sistema de cosméticos) para o ID do cosmético NOVO equivalente. Usado pra migrar
 * automaticamente jogadores que ainda têm o item antigo equipado/no inventário: remove o item
 * velho e dá o acessório novo no lugar. Ver LegacyCosmeticMigrator.
 */
public class LegacyCosmeticMigrationConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final File DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), "greatcosmetics");
    private static final File FILE = new File(DIR, "legacy_cosmetic_migration.json");

    public static final Map<Integer, String> oldCmdToNewCosmeticId = new HashMap<>();

    public static void load() {
        oldCmdToNewCosmeticId.clear();
        if (!DIR.exists()) DIR.mkdirs();

        if (!FILE.exists()) {
            Map<String, String> defaults = new HashMap<>();
            defaults.put("1", "exemplo_cosmetico_novo");
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(defaults, writer);
            } catch (Exception ignored) {}
            System.out.println("[GreatCosmetics] legacy_cosmetic_migration.json gerado com exemplo padrão.");
            return;
        }

        try (FileReader reader = new FileReader(FILE)) {
            Map<String, String> raw = GSON.fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
            if (raw != null) {
                for (Map.Entry<String, String> entry : raw.entrySet()) {
                    try {
                        oldCmdToNewCosmeticId.put(Integer.parseInt(entry.getKey()), entry.getValue());
                    } catch (NumberFormatException ignored) {}
                }
            }
            System.out.println("[GreatCosmetics] legacy_cosmetic_migration.json carregado: " + oldCmdToNewCosmeticId.size() + " mapeamento(s) de migração.");
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Erro ao carregar legacy_cosmetic_migration.json!");
            e.printStackTrace();
        }
    }
}
