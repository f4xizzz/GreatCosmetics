package com.f4xizzz.greatcosmetics.client;

import com.f4xizzz.greatcosmetics.network.OpenBackpackKeybindPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvents;
import org.lwjgl.glfw.GLFW;

public class KeybindManager {

    public static KeyBinding uiKeybind;
    public static KeyBinding warpsKeybind;
    public static KeyBinding pcKeybind;
    public static KeyBinding backpackKeybind; // Adicionado

    // Variável para controlar se o UI está ligado ou desligado
    private static boolean isUiOn = false;

    public static boolean isUiOn() {
        return isUiOn;
    }

    /** Manda /ui on ou /ui off pro servidor e atualiza o estado rastreado — usado tanto pela
     * tecla U quanto pelo auto-hide de UI em batalhas (ver BattleUiHider). */
    public static void setUiState(boolean on) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) return;
        isUiOn = on;
        client.getNetworkHandler().sendCommand(on ? "ui on" : "ui off");
    }

    public static void registerKeybinds() {
        // 1. Tecla U -> Alternar /ui on e /ui off
        uiKeybind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sascosmetics.ui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_U,
                "category.sascosmetics.keys"
        ));

        // 2. Tecla Y -> /warps
        warpsKeybind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sascosmetics.warps",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Y,
                "category.sascosmetics.keys"
        ));

        // 3. Tecla P -> /pc
        pcKeybind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sascosmetics.pc",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                "category.sascosmetics.keys"
        ));

        // 4. Tecla B -> Abrir Mochila
        backpackKeybind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.greatcosmetics.open_backpack",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "category.greatcosmetics.keys"
        ));

        // Evento que checa as teclas a cada tick do client
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.getNetworkHandler() == null) return;

            // Lógica da Tecla U (Toggle UI)
            while (uiKeybind.wasPressed()) {
                setUiState(!isUiOn);
                client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
            }

            // Lógica da Tecla Y (Warps)
            while (warpsKeybind.wasPressed()) {
                client.getNetworkHandler().sendCommand("warps");
                client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0F, 1.5F);
            }

            // Lógica da Tecla P (PC)
            while (pcKeybind.wasPressed()) {
                client.getNetworkHandler().sendCommand("pc");
            }

            // Lógica da Tecla B (Mochila)
            while (backpackKeybind.wasPressed()) {
                ClientPlayNetworking.send(new OpenBackpackKeybindPayload());
            }
        });
    }
}