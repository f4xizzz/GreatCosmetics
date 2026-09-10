package com.f4xizzz.greatcosmetics.client.gui.pages;

import com.f4xizzz.greatcosmetics.client.ClientCosmeticCache;
import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import com.f4xizzz.greatcosmetics.network.ToggleVisibilityPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.UUID;

public class CustomizePage extends WardrobePage {

    // Guarda a posição do painel para usar no clique do mouse
    private int lastX = 0;
    private int lastY = 0;

    public CustomizePage(Wardrobe3DScreen parent) {
        super(parent);
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta, int x, int y, int width, int height) {
        this.lastX = x;
        this.lastY = y;

        c.drawTextWithShadow(getTextRenderer(), com.f4xizzz.greatcosmetics.config.LangConfig.legacy("wardrobe.customize.header"), x + 16, y + 40, 0xFFFFAA);

        if (MinecraftClient.getInstance().player == null) return;
        UUID uuid = MinecraftClient.getInstance().player.getUuid();
        ClientCosmeticCache.PlayerSettings settings = ClientCosmeticCache.getSettings(uuid);

        // Desenhando os 4 botões de armadura
        drawToggleButton(c, com.f4xizzz.greatcosmetics.config.LangConfig.legacy("wardrobe.customize.helmet"), settings.hideHelmet(), x + 16, y + 60, mouseX, mouseY);
        drawToggleButton(c, com.f4xizzz.greatcosmetics.config.LangConfig.legacy("wardrobe.customize.chestplate"), settings.hideChestplate(), x + 16, y + 85, mouseX, mouseY);
        drawToggleButton(c, com.f4xizzz.greatcosmetics.config.LangConfig.legacy("wardrobe.customize.leggings"), settings.hideLeggings(), x + 16, y + 110, mouseX, mouseY);
        drawToggleButton(c, com.f4xizzz.greatcosmetics.config.LangConfig.legacy("wardrobe.customize.boots"), settings.hideBoots(), x + 16, y + 135, mouseX, mouseY);

        // 5º botão: setting LOCAL do cliente (não vai pro servidor) — esconde o cosmético dos
        // OUTROS jogadores pra aliviar FPS. "Hidden" aqui = escondido / ligado.
        drawToggleButton(c, com.f4xizzz.greatcosmetics.config.LangConfig.legacy("wardrobe.customize.hide_others"),
                ClientCosmeticCache.hideOtherPlayersCosmetics, x + 16, y + 170, mouseX, mouseY);
    }

    private void drawToggleButton(DrawContext c, String label, boolean isHidden, int bx, int by, int mx, int my) {
        boolean hovered = over(mx, my, bx, by, 148, 20);

        c.fill(bx, by, bx + 148, by + 20, hovered ? 0x66FFFFFF : 0x44000000);

        int accentColor = isHidden ? 0xFFAA0000 : 0xFF00AA00;
        c.fill(bx, by, bx + 2, by + 20, accentColor);

        String status = com.f4xizzz.greatcosmetics.config.LangConfig.legacy(isHidden ? "wardrobe.customize.hidden" : "wardrobe.customize.visible");
        c.drawTextWithShadow(getTextRenderer(), label, bx + 8, by + 6, 0xFFFFFF);
        c.drawTextWithShadow(getTextRenderer(), status, bx + 148 - getTextRenderer().getWidth(status) - 6, by + 6, accentColor);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || MinecraftClient.getInstance().player == null) return false;

        UUID uuid = MinecraftClient.getInstance().player.getUuid();
        ClientCosmeticCache.PlayerSettings settings = ClientCosmeticCache.getSettings(uuid);

        if (over(mx, my, lastX + 16, lastY + 60, 148, 20)) {
            toggleSetting("hide_helmet", settings.hideHelmet(), settings);
            return true;
        }
        if (over(mx, my, lastX + 16, lastY + 85, 148, 20)) {
            toggleSetting("hide_chestplate", settings.hideChestplate(), settings);
            return true;
        }
        if (over(mx, my, lastX + 16, lastY + 110, 148, 20)) {
            toggleSetting("hide_leggings", settings.hideLeggings(), settings);
            return true;
        }
        if (over(mx, my, lastX + 16, lastY + 135, 148, 20)) {
            toggleSetting("hide_boots", settings.hideBoots(), settings);
            return true;
        }
        if (over(mx, my, lastX + 16, lastY + 170, 148, 20)) {
            ClientCosmeticCache.hideOtherPlayersCosmetics = !ClientCosmeticCache.hideOtherPlayersCosmetics;
            com.f4xizzz.greatcosmetics.client.ClientLocalSettings.save();
            playClick();
            return true;
        }

        return false;
    }

    private void toggleSetting(String settingName, boolean currentState, ClientCosmeticCache.PlayerSettings old) {
        boolean newState = !currentState;
        ClientPlayNetworking.send(new ToggleVisibilityPayload(settingName, newState));

        UUID uuid = MinecraftClient.getInstance().player.getUuid();
        ClientCosmeticCache.setSettings(
                uuid,
                settingName.equals("hide_helmet") ? newState : old.hideHelmet(),
                settingName.equals("hide_chestplate") ? newState : old.hideChestplate(),
                settingName.equals("hide_leggings") ? newState : old.hideLeggings(),
                settingName.equals("hide_boots") ? newState : old.hideBoots(),
                old.hiddenCosmeticIds()
        );
        playClick();
    }

    private void playClick() {
        if (MinecraftClient.getInstance() != null) {
            try {
                MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            } catch (Exception ignored) {}
        }
    }

    private boolean over(double mx, double my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }
}