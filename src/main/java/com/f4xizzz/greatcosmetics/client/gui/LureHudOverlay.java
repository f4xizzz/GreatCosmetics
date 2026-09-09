package com.f4xizzz.greatcosmetics.client.gui;

import com.f4xizzz.greatcosmetics.GreatCosmetics;
import com.f4xizzz.greatcosmetics.client.ClientArmorCosmeticsCache;
import com.f4xizzz.greatcosmetics.client.ClientCosmeticCache;
import com.f4xizzz.greatcosmetics.client.ClientMainConfigCache;
import com.f4xizzz.greatcosmetics.config.CosmeticData;
import com.f4xizzz.greatcosmetics.config.LangConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * HUD dos bônus de Lure — uma coluna à direita da hotbar com os bônus AGREGADOS (soma de todos os
 * cosméticos/armaduras-cosmético equipados que dão Lure com {@code enabled=true}, igual o
 * {@code LureManager} aplica de verdade no servidor). Some quando não há nenhum Lure ativo.
 *
 * <p>100% client-side: o client já tem o catálogo de cosméticos com os {@code LureStats}
 * (SyncCosmeticsPayload/SyncArmorCosmeticsPayload) e sabe o que o próprio jogador tem equipado
 * (ClientCosmeticCache + os slots de armadura reais). Ligado por
 * {@code mainconfig.conf → lureHud} (default true, sincronizado via SyncMainConfigPayload).
 */
public final class LureHudOverlay {

    private LureHudOverlay() {}

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || mc.currentScreen != null) return;
        if (ClientMainConfigCache.config == null || !ClientMainConfigCache.config.lureHud) return;

        CosmeticData.LureStats agg = aggregate(mc.player.getUuid());
        if (agg == null) return;

        List<Text> lines = buildLines(agg);
        if (lines.isEmpty()) return;

        int lineH = mc.textRenderer.fontHeight + 1;
        int hotbarRight = mc.getWindow().getScaledWidth() / 2 + 91;
        int x = hotbarRight + 5;
        int bottom = mc.getWindow().getScaledHeight() - 3;
        int y = bottom - lines.size() * lineH;
        if (y < 3) y = 3;

        int maxW = 0;
        for (Text line : lines) maxW = Math.max(maxW, mc.textRenderer.getWidth(line));
        ctx.fill(x - 3, y - 2, x + maxW + 3, bottom + 1, 0x66000000);

        for (Text line : lines) {
            ctx.drawTextWithShadow(mc.textRenderer, line, x, y, 0xFFFFFFFF);
            y += lineH;
        }
    }

    private static CosmeticData.LureStats aggregate(UUID selfUuid) {
        List<CosmeticData.LureStats> lures = collectActive(selfUuid);
        if (lures.isEmpty()) return null;

        CosmeticData.LureStats a = new CosmeticData.LureStats();
        a.enabled = true;
        for (CosmeticData.LureStats l : lures) {
            a.lureShinyMultiplier += l.lureShinyMultiplier;
            a.lureUltraRAREMultiplier += l.lureUltraRAREMultiplier;
            a.lureHiddenAbilityMultiplier += l.lureHiddenAbilityMultiplier;
            a.lureIV += l.lureIV;
            a.lureChanceIV += l.lureChanceIV;
            a.lureExpAllMultiplier += l.lureExpAllMultiplier;
            a.lureEXP += l.lureEXP;
            a.lureEV += l.lureEV;
            a.lureAmizadeMultiplier += l.lureAmizadeMultiplier;
            a.lureChanceDeCaptura += l.lureChanceDeCaptura;
            a.lurePescaShiny += l.lurePescaShiny;
            a.lurePescaIv += l.lurePescaIv;
            a.lurePescaIvChance += l.lurePescaIvChance;
            a.lurePescaVelocidade += l.lurePescaVelocidade;
            if ((a.lureTYPE == null || a.lureTYPE.isEmpty()) && l.lureTYPE != null && !l.lureTYPE.isEmpty()) {
                a.lureTYPE = l.lureTYPE;
            }
        }
        return a;
    }

    /** Espelho client-side de {@code LureManager.collectActiveLureStats}: cosméticos virtuais
     *  equipados (ClientCosmeticCache) + armaduras reais convertidas em cosmético que o jogador
     *  está vestindo agora. */
    private static List<CosmeticData.LureStats> collectActive(UUID selfUuid) {
        List<CosmeticData.LureStats> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        Set<String> equipped = ClientCosmeticCache.getEquipped(selfUuid);
        if (equipped != null) {
            for (String id : equipped) {
                if (id == null || !seen.add(id.toLowerCase())) continue;
                addIfActive(result, GreatCosmetics.getCosmeticById(id));
            }
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && !ClientArmorCosmeticsCache.armorCosmetics.isEmpty()) {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack stack = mc.player.getEquippedStack(slot);
                if (stack == null || stack.isEmpty()) continue;
                Identifier wornId = Registries.ITEM.getId(stack.getItem());
                for (CosmeticData data : ClientArmorCosmeticsCache.armorCosmetics.values()) {
                    if (data == null || data.realItemId == null) continue;
                    Identifier realId = Identifier.tryParse(data.realItemId);
                    if (realId != null && realId.equals(wornId)) {
                        if (data.id == null || seen.add(data.id.toLowerCase())) addIfActive(result, data);
                        break;
                    }
                }
            }
        }
        return result;
    }

    private static void addIfActive(List<CosmeticData.LureStats> out, CosmeticData data) {
        if (data != null && data.lure != null && data.lure.enabled) out.add(data.lure);
    }

    private static List<Text> buildLines(CosmeticData.LureStats l) {
        List<Text> lines = new ArrayList<>();
        lines.add(LangConfig.text("hud.lure.header"));

        if (l.lureTYPE != null && !l.lureTYPE.isEmpty()) lines.add(LangConfig.text("hud.lure.type", "value", l.lureTYPE.toUpperCase()));
        if (l.lureShinyMultiplier > 0) lines.add(LangConfig.text("hud.lure.shiny", "value", fmt(l.lureShinyMultiplier)));
        if (l.lureUltraRAREMultiplier > 0) lines.add(LangConfig.text("hud.lure.ultrarare", "value", fmt(l.lureUltraRAREMultiplier)));
        if (l.lureHiddenAbilityMultiplier > 0) lines.add(LangConfig.text("hud.lure.hidden_ability", "value", fmt(l.lureHiddenAbilityMultiplier)));
        if (l.lureIV > 0) lines.add(LangConfig.text("hud.lure.iv", "value", l.lureIV));
        if (l.lureChanceIV > 0) lines.add(LangConfig.text("hud.lure.iv_chance", "value", pctNum(l.lureChanceIV)));
        if (l.lureExpAllMultiplier > 0) lines.add(LangConfig.text("hud.lure.expall", "value", fmt(l.lureExpAllMultiplier)));
        if (l.lureEXP > 0) lines.add(LangConfig.text("hud.lure.exp", "value", fmt(l.lureEXP)));
        if (l.lureEV > 0) lines.add(LangConfig.text("hud.lure.ev", "value", fmt(l.lureEV)));
        if (l.lureAmizadeMultiplier > 0) lines.add(LangConfig.text("hud.lure.friendship", "value", fmt(l.lureAmizadeMultiplier)));
        if (l.lureChanceDeCaptura > 0) lines.add(LangConfig.text("hud.lure.capture", "value", fmt(l.lureChanceDeCaptura)));

        boolean fishing = l.lurePescaShiny > 0 || l.lurePescaIv > 0
                || l.lurePescaIvChance > 0 || l.lurePescaVelocidade > 0;
        if (fishing) {
            lines.add(LangConfig.text("hud.lure.fishing_header"));
            if (l.lurePescaShiny > 0) lines.add(LangConfig.text("hud.lure.fishing_shiny", "value", fmt(l.lurePescaShiny)));
            if (l.lurePescaIv > 0) lines.add(LangConfig.text("hud.lure.fishing_iv", "value", l.lurePescaIv));
            if (l.lurePescaIvChance > 0) lines.add(LangConfig.text("hud.lure.fishing_iv_chance", "value", pctNum(l.lurePescaIvChance)));
            if (l.lurePescaVelocidade > 0) lines.add(LangConfig.text("hud.lure.fishing_speed", "value", pctNum(l.lurePescaVelocidade)));
        }

        if (lines.size() == 1) lines.clear(); // só o header, nenhum bônus numérico > 0
        return lines;
    }

    // Duplicado de AcessoriesPage.formatLureNumber/formatLurePercent de propósito — não acopla as
    // duas telas por causa de dois helpers de 3 linhas.
    private static String fmt(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
        return java.math.BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** Chance 0.0-1.0 -> número em pontos percentuais SEM o "%" (as strings de hud.lure.* já têm o "%"). */
    private static String pctNum(double chance01) {
        return fmt(chance01 * 100.0);
    }
}
