package com.f4xizzz.greatcosmetics.client.gui.pages;

import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class DevPage extends WardrobePage {

    private DevSubPage activeSubPage = null;

    // Guardados no render() (que recebe x/y/width/height de verdade) pra mouseClicked (que só
    // recebe mx/my/button, sem bounds) poder testar clique contra o MESMO retângulo que foi
    // desenhado nesse frame, em vez de um valor fixo desatualizado.
    private int lastX = 30, lastY = 50, lastWidth = 180, lastHeight = 260;

    public DevPage(Wardrobe3DScreen parent) {
        super(parent);
    }

    @Override
    public void onOpen() {
        this.activeSubPage = null;
    }

    @Override
    public int[] preferredPanelSize() {
        // Sem subpágina aberta (menu inicial, só os 5 botões): painel compacto, do tamanho do
        // menu — sem sobrar espaço vazio enorme cobrindo o personagem atrás. Com uma subpágina
        // aberta, usa o tamanho padrão de sempre (null == deixa o Wardrobe3DScreen decidir).
        // +26 de altura (260x220 -> 260x246) pra caber a linha de ajuda do Discord abaixo do
        // último botão sem espremer/cortar nada.
        return this.activeSubPage == null ? new int[]{260, 246} : null;
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta, int x, int y, int width, int height) {
        this.lastX = x; this.lastY = y; this.lastWidth = width; this.lastHeight = height;

        if (this.activeSubPage != null) {
            this.activeSubPage.render(c, mouseX, mouseY, delta, x, y, width, height);
            return;
        }

        // --- MENU INICIAL ---
        c.drawTextWithShadow(getTextRenderer(), com.f4xizzz.greatcosmetics.config.LangConfig.legacy("devstudio.header"), x + 16, y + 16, 0xFFFFFF);
        c.drawTextWithShadow(getTextRenderer(), com.f4xizzz.greatcosmetics.config.LangConfig.legacy("devstudio.menu.subtitle"), x + 16, y + 32, 0xFFAAAAAA);

        int btnW = Math.min(220, width - 32);
        int btnH = 26, gap = 8, startY = y + 52;
        String[] labels = {
                com.f4xizzz.greatcosmetics.config.LangConfig.legacy("devstudio.menu.cosmetics"),
                com.f4xizzz.greatcosmetics.config.LangConfig.legacy("devstudio.menu.effects"),
                com.f4xizzz.greatcosmetics.config.LangConfig.legacy("devstudio.menu.types"),
                com.f4xizzz.greatcosmetics.config.LangConfig.legacy("devstudio.menu.server_config"),
                com.f4xizzz.greatcosmetics.config.LangConfig.legacy("devstudio.menu.slots")
        };
        for (int i = 0; i < labels.length; i++) {
            drawMenuButton(c, labels[i], x + 16, startY + i * (btnH + gap), btnW, btnH, mouseX, mouseY);
        }

        // Nota pedida pelo usuário: jogador iniciante travado no Dev Studio precisa saber que tem
        // onde pedir ajuda. Abaixo do último botão, uma linha por vez (a string do Lang já vem com
        // "\n" pra caber na largura apertada do painel — ver preferredPanelSize, +26 de altura só
        // pra isso).
        int helpY = startY + labels.length * (btnH + gap) + 6;
        for (String line : com.f4xizzz.greatcosmetics.config.LangConfig.legacy("devstudio.menu.help").split("\n")) {
            c.drawCenteredTextWithShadow(getTextRenderer(), line, x + (width / 2), helpY, 0xAAAAAA);
            helpY += 10;
        }
    }

    private void drawMenuButton(DrawContext c, String label, int bx, int by, int bw, int bh, int mx, int my) {
        boolean hovered = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
        c.fill(bx, by, bx + bw, by + bh, hovered ? 0x66FFFFFF : 0x44000000);
        c.fill(bx, by, bx + 2, by + bh, 0xFFFFAA00);
        c.drawTextWithShadow(getTextRenderer(), label, bx + 8, by + 6, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;

        if (this.activeSubPage != null) {
            return this.activeSubPage.mouseClicked(mx, my, button);
        }

        int x = lastX, y = lastY, width = lastWidth;
        int btnW = Math.min(220, width - 32);
        int btnH = 26, gap = 8, startY = y + 52;

        Runnable[] actions = {
                () -> this.activeSubPage = new DevCosmeticsSubPage(parent, () -> this.activeSubPage = null),
                () -> this.activeSubPage = new DevEffectsSubPage(parent, () -> this.activeSubPage = null),
                () -> this.activeSubPage = new DevTypesSubPage(parent, () -> this.activeSubPage = null),
                () -> this.activeSubPage = new DevServerConfigSubPage(parent, () -> this.activeSubPage = null),
                () -> this.activeSubPage = new DevSlotsSubPage(parent, () -> this.activeSubPage = null)
        };
        for (int i = 0; i < actions.length; i++) {
            if (over(mx, my, x + 16, startY + i * (btnH + gap), btnW, btnH)) {
                playClick();
                actions[i].run();
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double deltaX, double deltaY) {
        if (button == 1) { // M2 (Botão Direito): Move a câmera 3D (Pan)
            Wardrobe3DScreen.targetPan -= (float) deltaX * 0.02f;
            Wardrobe3DScreen.targetFocusY -= (float) deltaY * 0.02f;
            return true;
        }
        if (this.activeSubPage != null) return this.activeSubPage.mouseDragged(mx, my, button, deltaX, deltaY);
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (this.activeSubPage != null) return this.activeSubPage.mouseScrolled(mx, my, hAmount, vAmount);
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Sequestra a tecla ESC (256)
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (this.activeSubPage != null && this.activeSubPage.hasUnsavedChanges) {
                this.activeSubPage.tryExit(() -> {
                    MinecraftClient.getInstance().setScreen(null); // Fecha a tela só depois de confirmar
                });
                return true; // Para a execução do ESC nativo do Minecraft!
            }
        }
        if (this.activeSubPage != null) return this.activeSubPage.keyPressed(keyCode, scanCode, modifiers);
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (this.activeSubPage != null) return this.activeSubPage.charTyped(chr, modifiers);
        return false;
    }

    private boolean over(double mx, double my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    private void playClick() {
        try {
            MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        } catch (Exception ignored) {}
    }
}