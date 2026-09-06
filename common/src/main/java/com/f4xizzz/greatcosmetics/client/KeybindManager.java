package com.f4xizzz.greatcosmetics.client;

import com.f4xizzz.greatcosmetics.network.OpenBackpackKeybindPayload;
import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

public class KeybindManager {

    public static KeyMapping uiKeybind;
    public static KeyMapping warpsKeybind;
    public static KeyMapping pcKeybind;
    public static KeyMapping backpackKeybind;

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
        uiKeybind = new KeyMapping("key.sascosmetics.ui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, "category.sascosmetics.keys");
        warpsKeybind = new KeyMapping("key.sascosmetics.warps", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, "category.sascosmetics.keys");
        pcKeybind = new KeyMapping("key.sascosmetics.pc", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, "category.sascosmetics.keys");
        backpackKeybind = new KeyMapping("key.greatcosmetics.open_backpack", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "category.greatcosmetics.keys");

        KeyMappingRegistry.register(uiKeybind);
        KeyMappingRegistry.register(warpsKeybind);
        KeyMappingRegistry.register(pcKeybind);
        KeyMappingRegistry.register(backpackKeybind);

        ClientTickEvent.CLIENT_POST.register(client -> {
            if (client.player == null || client.getConnection() == null) return;

            while (uiKeybind.consumeClick()) {
                setUiState(!isUiOn);
                client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
            }

            while (warpsKeybind.consumeClick()) {
                client.getConnection().sendUnsignedCommand("warps");
                client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.5F);
            }

            while (pcKeybind.consumeClick()) {
                client.getConnection().sendUnsignedCommand("pc");
            }

            while (backpackKeybind.consumeClick()) {
                NetworkManager.sendToServer(new OpenBackpackKeybindPayload());
            }
        });
    }
}
