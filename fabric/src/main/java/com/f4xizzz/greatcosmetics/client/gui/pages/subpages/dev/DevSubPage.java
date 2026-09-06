package com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev;

import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public abstract class DevSubPage {
    protected final Wardrobe3DScreen parent;
    protected final Runnable onBack;

    // --- VARIÁVEIS UNIVERSAIS DE SALVAMENTO ---
    public boolean hasUnsavedChanges = false;
    protected boolean showExitPopup = false;
    protected Runnable pendingExitAction = null;

    public DevSubPage(Wardrobe3DScreen parent, Runnable onBack) {
        this.parent = parent;
        this.onBack = onBack;
    }

    public abstract void render(GuiGraphics c, int mouseX, int mouseY, float delta, int x, int y, int width, int height);
    public boolean mouseClicked(double mx, double my, int button) { return false; }
    public boolean mouseDragged(double mx, double my, int button, double deltaX, double deltaY) { return false; }
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double verticalAmount) { return false; }
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }
    public boolean charTyped(char chr, int modifiers) { return false; }

    // Métodos que cada página vai preencher dizendo "como salvar" ou "como descartar"
    public abstract void saveChanges();
    public abstract void discardChanges();

    // Método que intercepta a saída (Voltar ou ESC)
    public void tryExit(Runnable exitAction) {
        if (hasUnsavedChanges) {
            this.showExitPopup = true;
            this.pendingExitAction = exitAction;
        } else {
            exitAction.run();
        }
    }

    public void renderExitPopup(GuiGraphics c, int mx, int my) {
        if (!showExitPopup) return;

        Minecraft client = Minecraft.getInstance();
        float targetScale = client.getWindow().getHeight() / 450f;
        int vWidth = (int) (client.getWindow().getWidth() / targetScale);
        int vHeight = 450;

        c.pose().pushPose();
        c.pose().translate(0, 0, 400); // Traz popup pra frente de TUDO (até dos outros popups)

        c.fill(0, 0, vWidth, vHeight, 0xCC000000); // Fundo escuro cobrindo a tela toda

        int px = (vWidth / 2) - 150;
        int py = (vHeight / 2) - 50;

        c.fill(px, py, px + 300, py + 100, 0xFF222222);
        c.renderOutline(px, py, 300, 100, 0xFFFFAA00);

        c.drawCenteredString(parent.getTextRenderer(), com.f4xizzz.greatcosmetics.config.LangConfig.legacy("devstudio.unsaved.title"), vWidth / 2, py + 15, 0xFFFFFF);
        c.drawCenteredString(parent.getTextRenderer(), com.f4xizzz.greatcosmetics.config.LangConfig.legacy("devstudio.unsaved.question"), vWidth / 2, py + 35, 0xAAAAAA);

        drawButton(c, com.f4xizzz.greatcosmetics.config.LangConfig.legacy("devstudio.unsaved.save"), px + 10, py + 65, 85, 20, mx, my, 0xFF22AA22, 0xFF55FF55);
        drawButton(c, com.f4xizzz.greatcosmetics.config.LangConfig.legacy("devstudio.unsaved.dont_save"), px + 105, py + 65, 85, 20, mx, my, 0xFFCC3333, 0xFFFF5555);
        drawButton(c, com.f4xizzz.greatcosmetics.config.LangConfig.legacy("devstudio.unsaved.continue"), px + 200, py + 65, 90, 20, mx, my, 0xFF666666, 0xFFAAAAAA);

        c.pose().popPose();
    }

    private void drawButton(GuiGraphics c, String label, int bx, int by, int bw, int bh, int mx, int my, int color, int hoverColor) {
        boolean hovered = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
        c.fill(bx, by, bx + bw, by + bh, hovered ? hoverColor : color);
        c.drawCenteredString(parent.getTextRenderer(), label, bx + (bw/2), by + 6, 0xFFFFFF);
    }

    public boolean handlePopupClick(double mx, double my) {
        if (!showExitPopup) return false;

        Minecraft client = Minecraft.getInstance();
        float targetScale = client.getWindow().getHeight() / 450f;
        int vWidth = (int) (client.getWindow().getWidth() / targetScale);
        int vHeight = 450;

        int px = (vWidth / 2) - 150;
        int py = (vHeight / 2) - 50;

        // Salvar
        if (mx >= px + 10 && mx <= px + 95 && my >= py + 65 && my <= py + 85) {
            playClick(); saveChanges(); hasUnsavedChanges = false; showExitPopup = false;
            if (pendingExitAction != null) pendingExitAction.run();
            return true;
        }
        // Não Salvar
        if (mx >= px + 105 && mx <= px + 190 && my >= py + 65 && my <= py + 85) {
            playClick(); discardChanges(); hasUnsavedChanges = false; showExitPopup = false;
            if (pendingExitAction != null) pendingExitAction.run();
            return true;
        }
        // Continuar
        if (mx >= px + 200 && mx <= px + 290 && my >= py + 65 && my <= py + 85) {
            playClick(); showExitPopup = false; return true;
        }
        return true; // Consome o clique pra não clicar atrás da janela
    }

    protected void playClick() {
        try { Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F)); } catch (Exception ignored) {}
    }
}