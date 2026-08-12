package com.f4xizzz.greatcosmetics.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class LangConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final File FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "greatcosmetics/lang.json");

    public static Map<String, String> messages = new HashMap<>();

    public static void loadLang() {
        if (FILE.exists()) {
            try (FileReader reader = new FileReader(FILE)) {
                Type type = new TypeToken<Map<String, String>>(){}.getType();
                messages = GSON.fromJson(reader, type);
                if (messages == null) messages = new HashMap<>();
            } catch (Exception e) {
                System.err.println("[Cosmetics] Erro ao ler lang.json! Recriando backup...");
                messages = new HashMap<>();
            }

            // Migração: injeta só as chaves NOVAS que ainda não existem no lang.json do jogador
            // (ex: quando uma atualização do mod passa a usar uma chave que não existia antes,
            // tipo "fly_enabled"/"fly_disabled") — sem isso, LangConfig.get() caía no fallback
            // "getOrDefault(key, key)" e devolvia a própria chave crua como mensagem, já que
            // createDefaultLang() só rodava na primeira vez (arquivo inexistente), nunca de novo
            // pra completar um lang.json já existente. Mensagens que o jogador já customizou
            // NUNCA são sobrescritas aqui (containsKey checa antes de sobrescrever).
            boolean changed = false;
            for (Map.Entry<String, String> entry : buildDefaults().entrySet()) {
                if (!messages.containsKey(entry.getKey())) {
                    messages.put(entry.getKey(), entry.getValue());
                    changed = true;
                }
            }
            if (changed) saveLang();
        } else {
            messages = buildDefaults();
            saveLang();
        }
    }

    private static Map<String, String> buildDefaults() {
        Map<String, String> d = new HashMap<>();

        // --- TEXTOS DO WARDROBE (100% ALTERÁVEIS) ---
        d.put("wardrobe_title", "<dark_gray>Guarda-Roupa Cósmico");
        d.put("wardrobe_unconfigured_name", "<gray>Cosmético não configurado");
        d.put("wardrobe_unconfigured_lore", "<gray>Esse item não está configurado no mod.\n<yellow>Clique para pegar apenas o modelo!");

        d.put("wardrobe_label_id", "<dark_gray>▪ <gray>ID (CMD): <white>{id}");
        d.put("wardrobe_label_slot", "<dark_gray>▪ <gray>Slot: <green>{slot}");
        d.put("wardrobe_label_permission", "<dark_gray>▪ <red>Permissão: <white>{perm}");

        d.put("wardrobe_backpack_on", "<dark_gray>▪ <yellow>Mochila: <green>Ativa <gray>({rows} linhas)");
        d.put("wardrobe_backpack_off", "<dark_gray>▪ <yellow>Mochila: <red>Desativada");

        d.put("wardrobe_fly_on", "<dark_gray>▪ <aqua>Voo: <green>Ativo");
        d.put("wardrobe_fly_off", "<dark_gray>▪ <aqua>Voo: <red>Desativado");

        d.put("wardrobe_click_to_get", "\n<yellow>Clique para pegar 1x deste item!");

        // --- MENSAGENS DE VOO (ACTION BAR, ao ligar/desligar o fly de cosmético) ---
        d.put("fly_enabled", "<green>✈ Voo ativado!");
        d.put("fly_disabled", "<red>✈ Voo desativado!");

        return d;
    }

    private static void saveLang() {
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(messages, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Retorna a String bruta do JSON (sem prefixo de sistema) para ser processada pelo MiniMessage real
    public static String getRaw(String key) {
        return messages.getOrDefault(key, "Missing: " + key);
    }

    public static String get(String key) {
        String prefix = messages.getOrDefault("prefix", "&e[SASCosmetics] &f");
        return prefix + messages.getOrDefault(key, key);
    }
}