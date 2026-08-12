package com.f4xizzz.greatcosmetics.util;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;

public class TextUtils {

    // legacySection() puro faz o Adventure ARREDONDAR qualquer cor RGB/hex pra mais perto de uma
    // das 16 cores legadas (§0-§f) — não existe "§#RRGGBB". Precisa do formato hex do Bungee
    // ("§x§R§R§G§G§B§B", 1 char por dígito), que o TextRenderer do próprio Minecraft SABE ler de
    // uma String crua desde a 1.16. Sem isso, digitar hex na GUI sempre virava a cor básica mais
    // próxima e ignorava o valor exato digitado.
    private static final LegacyComponentSerializer LEGACY_HEX = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    /**
     * Transforma a String em um objeto Text nativo do Minecraft.
     * Ideal para chat, action bar, nomes de mochilas, etc.
     */
    public static Text parseToText(String input, RegistryWrapper.WrapperLookup registries) {
        if (input == null || input.isEmpty()) return Text.empty();
        String processed = preProcessLegacy(input);
        var component = MiniMessage.miniMessage().deserialize(processed);
        String json = GsonComponentSerializer.gson().serialize(component);
        return Text.Serialization.fromJson(json, registries);
    }

    /**
     * Transforma o MiniMessage em uma String legada usando o símbolo §.
     * Ideal para os métodos da GUI (DrawContext) e TextRenderer que exigem String pura.
     */
    public static String parseToString(String input) {
        if (input == null || input.isEmpty()) return "";
        String processed = preProcessLegacy(input);
        var component = MiniMessage.miniMessage().deserialize(processed);
        return LEGACY_HEX.serialize(component);
    }

    /**
     * Mantém compatibilidade com configurações antigas permitindo o uso de & e &#Hex.
     */
    private static String preProcessLegacy(String input) {
        if (input == null) return "";
        return input.replace("&0", "<black>").replace("&1", "<dark_blue>").replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>").replace("&4", "<dark_red>").replace("&5", "<dark_purple>")
                .replace("&6", "<gold>").replace("&7", "<gray>").replace("&8", "<dark_gray>")
                .replace("&9", "<blue>").replace("&a", "<green>").replace("&b", "<aqua>")
                .replace("&c", "<red>").replace("&d", "<light_purple>").replace("&e", "<yellow>")
                .replace("&f", "<white>").replace("&l", "<bold>").replace("&m", "<strikethrough>")
                .replace("&n", "<underlined>").replace("&o", "<italic>").replace("&r", "<reset>")
                .replaceAll("&#([0-9a-fA-F]{6})", "<#$1>");
    }
}