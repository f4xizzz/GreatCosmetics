package com.f4xizzz.greatcosmetics.client.gui.pages;

import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public abstract class WardrobePage {
    protected final Wardrobe3DScreen parent;

    public WardrobePage(Wardrobe3DScreen parent) {
        this.parent = parent;
    }

    protected Font getTextRenderer() {
        return parent.getTextRenderer();
    }

    public void onOpen() {}

    /** Tamanho de painel que essa página prefere AGORA (pode mudar frame a frame, ex: DevPage
     *  trocando entre o menu inicial e uma subpágina) — {largura, altura}. Null = usa o tamanho
     *  padrão da aba (ver Wardrobe3DScreen#render), sem preferência própria. */
    public int[] preferredPanelSize() {
        return null;
    }

    public abstract void render(GuiGraphics context, int mouseX, int mouseY, float delta, int x, int y, int width, int height);

    // --- EVENTOS DE MOUSE ---
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return false;
    }

    // --- EVENTOS DE TECLADO (NOVOS) ---
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        return false;
    }

    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return false;
    }
}