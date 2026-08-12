package com.f4xizzz.greatcosmetics.manager;

import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;

public class ThemeManager {
    public static boolean isLightMode = false;
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("greatcosmetics_theme.txt");

    // Roda quando o jogo abre
    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String content = Files.readString(CONFIG_PATH).trim();
                isLightMode = Boolean.parseBoolean(content);
            }
        } catch (Exception e) {
            System.err.println("[CobbleSAS] Falha ao carregar tema!");
        }
    }

    // Roda quando o jogador usa o comando
    public static void save() {
        try {
            Files.writeString(CONFIG_PATH, String.valueOf(isLightMode));
        } catch (Exception e) {
            System.err.println("[CobbleSAS] Falha ao salvar tema!");
        }
    }
}