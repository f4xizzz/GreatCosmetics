package com.f4xizzz.greatcosmetics.client.gui;

import com.f4xizzz.greatcosmetics.client.ClientBackpackState;
import com.f4xizzz.greatcosmetics.mixin.client.HandledScreenAccessor;
import com.f4xizzz.greatcosmetics.network.SwitchBackpackPagePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

/** Desenha as setas "< / >" + o texto "Página X/Y" coladas na borda do baú vanilla (ver
 *  HandledScreenAccessor) quando a mochila-cosmético aberta tem mais de 1 página (ver
 *  CosmeticData#backpackPages/BackpackManager#openSpecificBackpackPage). Hooks registrados por
 *  GreatCosmeticsClient via ScreenEvents.AFTER_INIT — só existe enquanto ESSA instância de tela
 *  específica estiver aberta (Fabric desliga sozinho ao fechar/trocar de tela). */
public final class BackpackPageOverlay {

    private BackpackPageOverlay() {}

    private static final int ARROW_SIZE = 20;
    private static final int ARROW_GAP = 6;

    // Recalculado a cada render() — usado por handleClick() logo em seguida (mesmo padrão de
    // "última posição desenhada" já usado no resto da GUI do mod, ex: PartyPage#lastListX).
    private static int lastArrowLeftX, lastArrowRightX, lastArrowY;

    public static void render(DrawContext c, HandledScreen<?> screen) {
        HandledScreenAccessor accessor = (HandledScreenAccessor) screen;
        int x = accessor.greatcosmetics$getX();
        int y = accessor.greatcosmetics$getY();
        int bw = accessor.greatcosmetics$getBackgroundWidth();
        int bh = accessor.greatcosmetics$getBackgroundHeight();

        lastArrowY = y + (bh / 2) - (ARROW_SIZE / 2);
        lastArrowLeftX = x - ARROW_SIZE - ARROW_GAP;
        lastArrowRightX = x + bw + ARROW_GAP;

        int page = ClientBackpackState.currentPage;
        int total = ClientBackpackState.totalPages;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        drawArrow(c, tr, lastArrowLeftX, lastArrowY, "<", page > 0);
        drawArrow(c, tr, lastArrowRightX, lastArrowY, ">", page < total - 1);

        String label = "Página " + (page + 1) + "/" + total;
        c.drawCenteredTextWithShadow(tr, label, x + (bw / 2), y - 12, 0xFFFFFFFF);
    }

    private static void drawArrow(DrawContext c, TextRenderer tr, int ax, int ay, String symbol, boolean enabled) {
        c.fill(ax, ay, ax + ARROW_SIZE, ay + ARROW_SIZE, enabled ? 0xEE222222 : 0x77222222);
        c.drawBorder(ax, ay, ARROW_SIZE, ARROW_SIZE, enabled ? 0xFFFFFFFF : 0xFF555555);
        c.drawCenteredTextWithShadow(tr, symbol, ax + (ARROW_SIZE / 2), ay + (ARROW_SIZE - 8) / 2, enabled ? 0xFFFFFFFF : 0xFF888888);
    }

    /** Retornado pro ScreenMouseEvents.AllowMouseClick — false CONSOME o clique (impede o vanilla
     *  de tentar interpretar como clique num slot atrás da seta), true deixa passar normal. */
    public static boolean handleClick(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        if (ClientBackpackState.currentCosmeticId == null || ClientBackpackState.totalPages <= 1) return true;

        boolean overLeft = mouseX >= lastArrowLeftX && mouseX <= lastArrowLeftX + ARROW_SIZE
                && mouseY >= lastArrowY && mouseY <= lastArrowY + ARROW_SIZE;
        boolean overRight = mouseX >= lastArrowRightX && mouseX <= lastArrowRightX + ARROW_SIZE
                && mouseY >= lastArrowY && mouseY <= lastArrowY + ARROW_SIZE;
        if (!overLeft && !overRight) return true;

        int page = ClientBackpackState.currentPage;
        int newPage = overLeft ? page - 1 : page + 1;
        if (newPage < 0 || newPage >= ClientBackpackState.totalPages) return false; // consome o clique, mas não manda nada (seta "apagada")

        MinecraftClient client = MinecraftClient.getInstance();
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
        // Atualiza a página otimisticamente JÁ no clique — sem isso as setas/texto só refletiam a
        // página nova depois de um SEGUNDO clique, porque dependiam do round-trip completo (payload
        // até o servidor + BackpackPageInfoPayload de volta + fechar/reabrir a tela) só pra esse
        // render() aqui em cima enxergar o valor certo. O servidor continua sendo autoridade — se ele
        // mandar uma página diferente da prevista aqui, o AFTER_INIT client (GreatCosmeticsClient)
        // sobrescreve currentPage normalmente quando a tela reabrir.
        ClientBackpackState.currentPage = newPage;
        ClientPlayNetworking.send(new SwitchBackpackPagePayload(ClientBackpackState.currentCosmeticId, newPage));
        return false;
    }
}
