package com.f4xizzz.greatcosmetics.client;

import com.f4xizzz.greatcosmetics.network.OpenBackpackKeybindPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

public class KeybindManager {

    public static KeyMapping uiKeybind;
    public static KeyMapping warpsKeybind;
    public static KeyMapping pcKeybind;
    public static KeyMapping backpackKeybind; // Adicionado

    // Variável para controlar se o UI está ligado ou desligado
    private static boolean isUiOn = false;

    public static boolean isUiOn() {
        return isUiOn;
    }

    /** Manda /ui on ou /ui off pro servidor e atualiza o estado rastreado — usado tanto pela
     * tecla U quanto pelo auto-hide de UI em batalhas (ver BattleUiHider). */
    public static void setUiState(boolean on) {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client.player == null || client.getConnection() == null) return;
        isUiOn = on;
        client.getConnection().sendUnsignedCommand(on ? "ui on" : "ui off");
    }

    public static void registerKeybinds() {
        // 1. Tecla U -> Alternar /ui on e /ui off
        uiKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.sascosmetics.ui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_U,
                "category.sascosmetics.keys"
        ));

        // 2. Tecla Y -> /warps
        warpsKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.sascosmetics.warps",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_Y,
                "category.sascosmetics.keys"
        ));

        // 3. Tecla P -> /pc
        pcKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.sascosmetics.pc",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                "category.sascosmetics.keys"
        ));

        // 4. Tecla B -> Abrir Mochila
        backpackKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.greatcosmetics.open_backpack",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "category.greatcosmetics.keys"
        ));

        // Evento que checa as teclas a cada tick do client
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.getConnection() == null) return;

            // Lógica da Tecla U (Toggle UI)
            while (uiKeybind.consumeClick()) {
                setUiState(!isUiOn);
                client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
            }

            // Lógica da Tecla Y (Warps)
            while (warpsKeybind.consumeClick()) {
                client.getConnection().sendUnsignedCommand("warps");
                client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.5F);
            }

            // Lógica da Tecla P (PC)
            while (pcKeybind.consumeClick()) {
                client.getConnection().sendUnsignedCommand("pc");
            }

            // Lógica da Tecla B (Mochila)
            while (backpackKeybind.consumeClick()) {
                ClientPlayNetworking.send(new OpenBackpackKeybindPayload());
            }
        });
    }
}