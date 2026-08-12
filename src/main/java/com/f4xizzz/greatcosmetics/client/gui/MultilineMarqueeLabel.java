package com.f4xizzz.greatcosmetics.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.List;

/** Desenha um label de várias linhas (ver CosmeticData#getFormattedNameLines) ANCORADO POR BAIXO —
 *  a ÚLTIMA linha sempre cai exatamente onde um label de 1 linha só ficaria (bottomY), e cada linha
 *  extra empurra as anteriores pra CIMA, sem nunca deslocar bottomY. Isso mantém o "item" (ícone do
 *  grid, hover de Acessórios etc) e o que vem embaixo dele intocados quando o admin usa "\n" — só o
 *  espaço ACIMA cresce.
 *
 *  Cada linha, individualmente, faz o mesmo marquee de ida-e-volta que já existia pra nome de 1
 *  linha só quando ela sozinha não cabe em maxWidth (em vez de cortar/reduzir fonte) — reaproveitado
 *  aqui em vez de reimplementado em cada tela que usa isso. */
public final class MultilineMarqueeLabel {

    private MultilineMarqueeLabel() {}

    /** @param centered true = linhas que cabem em maxWidth são centralizadas em centerX (linhas em
     *                  overflow sempre alinham à esquerda em leftX, senão o marquee não tem "pra
     *                  onde andar" dentro do recorte). false = sempre alinhado à esquerda em leftX. */
    public static void draw(DrawContext c, Wardrobe3DScreen parent, TextRenderer tr, List<Text> lines,
                             int leftX, int centerX, boolean centered, int bottomY, int maxWidth,
                             int lineHeight, int color) {
        if (lines.isEmpty()) return;

        int n = lines.size();
        int topY = bottomY - (n - 1) * lineHeight;

        parent.enableScissorStacked(c, leftX - 2, topY - 2, maxWidth + 4, (n * lineHeight) + 4);
        try {
            long time = Util.getMeasuringTimeMs();
            for (int i = 0; i < n; i++) {
                Text line = lines.get(i);
                int lineY = topY + (i * lineHeight);
                int lineW = tr.getWidth(line);

                if (lineW <= maxWidth) {
                    if (centered) c.drawCenteredTextWithShadow(tr, line, centerX, lineY, color);
                    else c.drawTextWithShadow(tr, line, leftX, lineY, color);
                } else {
                    // Pequena defasagem por linha (i * 137ms) — evita que várias linhas em overflow
                    // ao mesmo tempo balancem exatamente em uníssono, o que lia como uma coisa só
                    // "piscando" em vez de linhas independentes.
                    int overflow = lineW - maxWidth;
                    double progress = (Math.sin((time + (i * 137L)) / 700.0) + 1.0) / 2.0;
                    int offset = (int) (progress * overflow);
                    c.drawTextWithShadow(tr, line, leftX - offset, lineY, color);
                }
            }
        } finally {
            c.disableScissor();
        }
    }
}
