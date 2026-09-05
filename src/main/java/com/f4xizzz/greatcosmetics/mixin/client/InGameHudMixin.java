package com.f4xizzz.greatcosmetics.mixin.client;

import com.f4xizzz.greatcosmetics.client.ClientMainConfigCache;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Barras que se multiplicam": quando a vida MÁXIMA (ou a armadura, ou a absorção) passa de 2
 *  barras cheias (20 pontos = 10 ícones), em vez de empilhar fileiras cada vez mais achatadas pra
 *  cima (vida) ou simplesmente sumir com o excedente (armadura, que o vanilla trava em 20), o HUD
 *  mostra UMA barra + um "xN" do lado direito.
 *
 *  <p><b>Vida</b> — o "xN" reflete o estado ATUAL: os corações soltos de cima (o resto de
 *  {@code maxHearts % 10}) esvaziam primeiro; depois o "xN" cai (x3 → x2 → x1) conforme cada barra
 *  inteira de 20 HP some, com a barra visível drenando dentro de cada segmento de 20; quando sobra
 *  1 barra o "xN" some e ela esvazia normal. Perder 20 HP faz o número cair e a barra visível
 *  volta a encher (pop), igual gastar de uma pilha de itens.
 *
 *  <p><b>Detalhes vanilla replicados</b> (v2): (1) o coração que "pula" 2px durante a regeneração
 *  — o índice do vanilla ({@code regeneratingHeartIndex}) é remapeado pra ciclar pelos corações
 *  VISÍVEIS da versão compacta; (2) o "fantasma"/flash do dano recente — o vanilla desenha a barra
 *  sólida pela vida ATUAL (param 8) e, nos frames de "blink", uma camada extra pela vida DEFASADA
 *  (param 9, que segura o valor antigo por ~1s), então os corações recém-perdidos piscam antes de
 *  sumir; (3) absorção com multiplicador próprio (corações dourados).
 *
 *  <p>Ligado por {@code mainconfig.conf → compactStatusBars} (default true). Só faz QUALQUER coisa
 *  quando vida/armadura/absorção passa de 20 — num servidor com atributos vanilla nunca dispara.
 *  Coordenadas/IDs de textura confirmados por javap no jar mergeado do Loom. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    private static final int GC$ICON = 8;   // espaçamento horizontal entre ícones
    private static final int GC$ROW = 10;   // espaçamento vertical entre fileiras (fixo, sem o "achatamento" vanilla)

    private static boolean gc$enabled() {
        try {
            return ClientMainConfigCache.config != null && ClientMainConfigCache.config.compactStatusBars;
        } catch (Exception e) {
            return false;
        }
    }

    // ==========================================================================================
    // VIDA
    // ==========================================================================================
    @Inject(method = "renderHealthBar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/entity/player/PlayerEntity;IIIIFIIIZ)V",
            at = @At("HEAD"), cancellable = true)
    private void greatcosmetics$compactHealthBar(DrawContext ctx, PlayerEntity player, int x, int y, int lines,
                                                 int regeneratingHeartIndex, float maxHealth, int currentHealth,
                                                 int laggingHealth, int absorption, boolean blinking, CallbackInfo ci) {
        if (!gc$enabled()) return;

        int maxHearts = MathHelper.ceil(maxHealth / 2.0f);
        int fullBars = maxHearts / 10;
        if (fullBars < 2) return; // ≤ 1 barra cheia — deixa o vanilla desenhar normal

        ci.cancel();

        boolean hardcore = player.getWorld().getLevelProperties().isHardcore();
        int looseHearts = maxHearts % 10;

        // --- segmentação pela vida ATUAL (é ela que define qual barra aparece + o xN) ---
        int hpInLoose = MathHelper.clamp(currentHealth - fullBars * 20, 0, looseHearts * 2);
        int barsInPlay;
        int visibleBarHp;
        if (hpInLoose > 0) {
            barsInPlay = fullBars;
            visibleBarHp = 20; // barras de baixo todas cheias, vida sobrando nos corações soltos
        } else {
            int hpInBars = MathHelper.clamp(currentHealth, 0, fullBars * 20);
            barsInPlay = Math.max(1, MathHelper.ceil(hpInBars / 20.0));
            visibleBarHp = hpInBars - (barsInPlay - 1) * 20; // 1..20 (ou 0 se hpInBars == 0)
        }

        // --- camada de "blink" (fantasma do dano recente), pela vida DEFASADA, presa aos MESMOS
        //     segmentos que estão sendo mostrados ---
        int lagBottomHp = MathHelper.clamp(laggingHealth - (barsInPlay - 1) * 20, 0, 20);
        int lagLooseHp = MathHelper.clamp(laggingHealth - fullBars * 20, 0, looseHearts * 2);

        // --- pulo do coração em regeneração: remapeia pro espaço de corações VISÍVEIS ---
        int absHearts = MathHelper.ceil(absorption / 2.0f);
        int absBars = absorption / 20;
        int absLooseIcons = MathHelper.ceil((absorption % 20) / 2.0f);
        int absVisible = (absorption >= 20) ? (10 + absLooseIcons) : absHearts;
        int totalVisible = 10 + looseHearts + absVisible;
        int wobble = regeneratingHeartIndex >= 0 ? regeneratingHeartIndex % (totalVisible + 2) : -1;

        int bottomRowY = y;
        int looseRowY = y - GC$ROW;

        // fileira de baixo: a barra "visível" (a barra de reserva atual)
        gc$drawHeartRow(ctx, player, hardcore, blinking, x, bottomRowY, 10, visibleBarHp, lagBottomHp, 0, wobble);
        // fileira de cima: corações soltos (só se maxHearts não for múltiplo de 10)
        if (looseHearts > 0) {
            gc$drawHeartRow(ctx, player, hardcore, blinking, x, looseRowY, looseHearts, hpInLoose, lagLooseHp, 10, wobble);
        }
        // "xN" à direita — só quando há 2+ barras em jogo
        if (barsInPlay >= 2) {
            gc$drawMultiplier(ctx, x, bottomRowY, barsInPlay);
        }
        // absorção acima de tudo (com multiplicador próprio se >= 2 barras)
        if (absHearts > 0) {
            int absBaseY = (looseHearts > 0 ? looseRowY : bottomRowY) - GC$ROW;
            gc$drawAbsorption(ctx, hardcore, x, absBaseY, absorption, 10 + looseHearts, wobble);
        }
    }

    // ==========================================================================================
    // ARMADURA
    // ==========================================================================================
    @Inject(method = "renderArmor(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/entity/player/PlayerEntity;IIII)V",
            at = @At("HEAD"), cancellable = true)
    private static void greatcosmetics$compactArmorBar(DrawContext ctx, PlayerEntity player, int y,
                                                       int heartRows, int lines, int x, CallbackInfo ci) {
        if (!gc$enabled()) return;

        int armor = player.getArmor();
        if (armor <= 0) return; // o vanilla também sai cedo nesse caso

        int maxHearts = MathHelper.ceil(player.getMaxHealth() / 2.0f);
        boolean healthCompacted = maxHearts / 10 >= 2;
        boolean armorCompacted = armor > 20;
        if (!healthCompacted && !armorCompacted) return; // armadura ≤ 20 e vida normal → vanilla

        ci.cancel();

        // Altura (em fileiras) do bloco vida+absorção, pra encostar a armadura logo acima dele.
        int absorption = MathHelper.ceil(player.getAbsorptionAmount());
        int healthRows;
        if (healthCompacted) {
            int looseHearts = maxHearts % 10;
            int absRows = absorption <= 0 ? 0
                    : (absorption < 20 ? 1 : (absorption % 20 > 0 ? 2 : 1));
            healthRows = (looseHearts > 0 ? 2 : 1) + absRows;
        } else {
            int absHearts = MathHelper.ceil(absorption / 2.0f);
            healthRows = Math.max(1, MathHelper.ceil((maxHearts + absHearts) / 10.0));
        }
        int armorBottomRowY = y - healthRows * GC$ROW;

        RenderSystem.enableBlend();
        if (!armorCompacted) {
            gc$drawArmorRow(ctx, x, armorBottomRowY, 10, armor); // só reposicionar (armadura ≤ 20)
        } else {
            int fullBars = armor / 20;
            int remainder = armor % 20;
            int looseIcons = MathHelper.ceil(remainder / 2.0f);

            gc$drawArmorRow(ctx, x, armorBottomRowY, 10, 20); // barra cheia embaixo
            if (looseIcons > 0) {
                gc$drawArmorRow(ctx, x, armorBottomRowY - GC$ROW, looseIcons, remainder);
            }
            if (fullBars >= 2) {
                gc$drawMultiplier(ctx, x, armorBottomRowY, fullBars);
            }
        }
        RenderSystem.disableBlend();
    }

    // ==========================================================================================
    // HELPERS
    // ==========================================================================================
    private static void gc$drawMultiplier(DrawContext ctx, int x, int rowY, int n) {
        ctx.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, "x" + n,
                x + 10 * GC$ICON + 2, rowY + 1, 0xFFFFFFFF);
    }

    /** Uma fileira de {@code count} corações. {@code curHp} = camada sólida (vida atual desse
     *  segmento); {@code lagHp} = camada de "blink"/fantasma (vida defasada), desenhada só nos
     *  frames de blink e SÓ por baixo da sólida — igual o vanilla: coração recém-perdido
     *  (cur <= n < lag) só tem a camada blink, então pisca antes de sumir. {@code baseVisibleIdx}
     *  posiciona essa fileira no espaço de índice do "pulo" da regeneração ({@code wobble}). */
    private static void gc$drawHeartRow(DrawContext ctx, PlayerEntity player, boolean hardcore, boolean blinking,
                                        int x, int rowY, int count, int curHp, int lagHp, int baseVisibleIdx, int wobble) {
        RenderSystem.enableBlend();
        Identifier container = gc$heartId((hardcore ? "container_hardcore" : "container") + (blinking ? "_blinking" : ""));
        for (int i = 0; i < count; i++) {
            int hx = x + i * GC$ICON;
            int hy = rowY - (baseVisibleIdx + i == wobble ? 2 : 0);
            ctx.drawGuiTexture(container, hx, hy, 9, 9);
            int c = curHp - i * 2;
            int l = lagHp - i * 2;
            if (blinking && l > 0) {
                ctx.drawGuiTexture(gc$heartTex(player, hardcore, true, l == 1), hx, hy, 9, 9);
            }
            if (c > 0) {
                ctx.drawGuiTexture(gc$heartTex(player, hardcore, false, c == 1), hx, hy, 9, 9);
            }
        }
        RenderSystem.disableBlend();
    }

    /** Absorção (corações dourados). {@code >= 40} pontos → barra cheia + "xN" (mesma semântica da
     *  armadura: sem "atual vs máx", o xN é sempre {@code absorção / 20}). {@code 20..39} → 2
     *  fileiras. {@code < 20} → fileira solta simples, empilhando pra cima. */
    private static void gc$drawAbsorption(DrawContext ctx, boolean hardcore, int x, int baseRowY,
                                          int absorption, int baseVisibleIdx, int wobble) {
        RenderSystem.enableBlend();
        Identifier container = gc$heartId(hardcore ? "container_hardcore" : "container");
        Identifier full = gc$heartId(hardcore ? "absorbing_hardcore_full" : "absorbing_full");
        Identifier half = gc$heartId(hardcore ? "absorbing_hardcore_half" : "absorbing_half");

        if (absorption < 20) {
            int absHearts = MathHelper.ceil(absorption / 2.0f);
            for (int i = 0; i < absHearts; i++) {
                int hx = x + (i % 10) * GC$ICON;
                int hy = baseRowY - (i / 10) * GC$ROW - (baseVisibleIdx + i == wobble ? 2 : 0);
                ctx.drawGuiTexture(container, hx, hy, 9, 9);
                int hp = absorption - i * 2;
                if (hp > 0) ctx.drawGuiTexture(hp == 1 ? half : full, hx, hy, 9, 9);
            }
        } else {
            int fullBars = absorption / 20;
            int remainder = absorption % 20;
            int looseIcons = MathHelper.ceil(remainder / 2.0f);
            gc$drawGoldRow(ctx, container, full, half, x, baseRowY, 10, 20, baseVisibleIdx, wobble);
            if (looseIcons > 0) {
                gc$drawGoldRow(ctx, container, full, half, x, baseRowY - GC$ROW, looseIcons, remainder, baseVisibleIdx + 10, wobble);
            }
            if (fullBars >= 2) {
                gc$drawMultiplier(ctx, x, baseRowY, fullBars);
            }
        }
        RenderSystem.disableBlend();
    }

    private static void gc$drawGoldRow(DrawContext ctx, Identifier container, Identifier full, Identifier half,
                                       int x, int rowY, int count, int points, int baseVisibleIdx, int wobble) {
        for (int i = 0; i < count; i++) {
            int hx = x + i * GC$ICON;
            int hy = rowY - (baseVisibleIdx + i == wobble ? 2 : 0);
            ctx.drawGuiTexture(container, hx, hy, 9, 9);
            int hp = points - i * 2;
            if (hp > 0) ctx.drawGuiTexture(hp == 1 ? half : full, hx, hy, 9, 9);
        }
    }

    /** {@code count} slots de armadura full/half/empty, mesma regra do vanilla (p = i*2+1). */
    private static void gc$drawArmorRow(DrawContext ctx, int x, int rowY, int count, int points) {
        Identifier full = Identifier.ofVanilla("hud/armor_full");
        Identifier half = Identifier.ofVanilla("hud/armor_half");
        Identifier empty = Identifier.ofVanilla("hud/armor_empty");
        for (int i = 0; i < count; i++) {
            int ax = x + i * GC$ICON;
            int p = i * 2 + 1;
            Identifier tex = p < points ? full : (p == points ? half : empty);
            ctx.drawGuiTexture(tex, ax, rowY, 9, 9);
        }
    }

    private static Identifier gc$heartId(String path) {
        return Identifier.ofVanilla("hud/heart/" + path);
    }

    /** Mesma prioridade do vanilla InGameHud.HeartType#fromPlayerState: veneno > wither > congelado
     *  > normal (InGameHud.HeartType é package-private, não dá pra chamar direto de outro pacote).
     *  Convenção de nome das texturas vanilla: {@code hud/heart/[<state>_][hardcore_]<full|half>[_blinking]}. */
    private static Identifier gc$heartTex(PlayerEntity player, boolean hardcore, boolean blinking, boolean half) {
        String state;
        if (player.hasStatusEffect(StatusEffects.POISON)) state = "poisoned";
        else if (player.hasStatusEffect(StatusEffects.WITHER)) state = "withered";
        else if (player.isFrozen()) state = "frozen";
        else state = null;

        String path = (state == null ? "" : state + "_")
                + (hardcore ? "hardcore_" : "")
                + (half ? "half" : "full")
                + (blinking ? "_blinking" : "");
        return gc$heartId(path);
    }
}
