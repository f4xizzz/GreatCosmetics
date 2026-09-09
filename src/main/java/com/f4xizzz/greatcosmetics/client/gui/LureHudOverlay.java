package com.f4xizzz.greatcosmetics.client.gui;

import com.f4xizzz.greatcosmetics.GreatCosmetics;
import com.f4xizzz.greatcosmetics.client.ClientArmorCosmeticsCache;
import com.f4xizzz.greatcosmetics.client.ClientCosmeticCache;
import com.f4xizzz.greatcosmetics.client.ClientMainConfigCache;
import com.f4xizzz.greatcosmetics.config.CosmeticData;
import com.f4xizzz.greatcosmetics.config.LangConfig;
import com.f4xizzz.greatcosmetics.util.EquippedCosmeticId;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * HUD dos bônus dos cosméticos equipados — canto INFERIOR ESQUERDO, fundo transparente. Lista
 * TUDO que os cosméticos/armaduras-cosmético equipados dão (habilidades, atributos, efeitos de
 * poção, Lure, pesca, IVs Scanner), agrupado em bullets aninhados. Encolhe sozinho quando fica
 * alto demais. Some quando não há nada pra mostrar.
 *
 * <p>100% client-side: o client já tem o catálogo (SyncCosmeticsPayload/SyncArmorCosmeticsPayload)
 * e o que o próprio jogador tem equipado (ClientCosmeticCache + slots de armadura reais). Ligado
 * por {@code mainconfig.conf → lureHud} (default true).
 */
public final class LureHudOverlay {

    private LureHudOverlay() {}

    private record Line(int indent, Text text, boolean header) {}

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || mc.currentScreen != null) return;
        if (ClientMainConfigCache.config == null || !ClientMainConfigCache.config.lureHud) return;

        List<CosmeticData> active = collectActive(mc.player.getUuid());
        if (active.isEmpty()) return;

        List<Line> lines = buildLines(active);
        if (lines.isEmpty()) return;

        int lineH = mc.textRenderer.fontHeight + 1;
        int screenH = mc.getWindow().getScaledHeight();
        int total = lines.size() * lineH;

        // Auto-shrink: nunca passa de ~55% da altura da tela; piso 0.5.
        float scale = 1.0f;
        float maxH = screenH * 0.55f;
        if (total > maxH) scale = Math.max(0.5f, maxH / total);

        int x = 4;
        int bottom = screenH - 4;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, bottom, 0);
        ctx.getMatrices().scale(scale, scale, 1f);

        int y = -total; // desenha de cima pra baixo dentro do bloco, ancorado no rodapé
        for (Line l : lines) {
            if (l.text != null) {
                String prefix = l.header ? "* " : "  * ";
                Text render = Text.literal("§7" + " ".repeat(l.indent * 2) + prefix)
                        .append(l.header ? Text.literal("§6§l").append(l.text) : Text.literal("§f").append(l.text));
                ctx.drawTextWithShadow(mc.textRenderer, render, 0, y, 0xFFFFFFFF);
            }
            y += lineH;
        }

        ctx.getMatrices().pop();
    }

    // ── coleta ──────────────────────────────────────────────────────────────────────────────

    /** Cosméticos virtuais equipados + armaduras reais convertidas em cosmético, deduplicados. */
    private static List<CosmeticData> collectActive(UUID selfUuid) {
        List<CosmeticData> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        Set<String> equipped = ClientCosmeticCache.getEquipped(selfUuid);
        if (equipped != null) {
            for (String raw : equipped) {
                if (raw == null) continue;
                String base = EquippedCosmeticId.base(raw);
                if (base == null || !seen.add(base.toLowerCase())) continue;
                CosmeticData d = GreatCosmetics.getCosmeticById(base);
                if (d != null) result.add(d);
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
                        if (data.id == null || seen.add(data.id.toLowerCase())) result.add(data);
                        break;
                    }
                }
            }
        }
        return result;
    }

    // ── linhas ──────────────────────────────────────────────────────────────────────────────

    private static List<Line> buildLines(List<CosmeticData> active) {
        // agrega
        int armor = 0; double toughness = 0;
        boolean fly = false, backpack = false, autofeed = false, ivScanner = false, particleTrail = false;
        int backpackRows = 0;
        double groundMult = 1.0, flyMult = 1.0, swimMult = 1.0;
        Set<String> effects = new LinkedHashSet<>();
        CosmeticData.LureStats lure = new CosmeticData.LureStats();
        boolean anyLure = false;

        for (CosmeticData d : active) {
            armor += d.armor;
            toughness += d.toughness;
            if (d.EnableFly) fly = true;
            if (d.isBackpack) { backpack = true; backpackRows = Math.max(backpackRows, d.backpackRows); }
            if (d.AutoFeed) autofeed = true;
            if (d.ivScanner) ivScanner = true;
            if ((d.effectVisual != null && !d.effectVisual.isEmpty()) || (d.flyParticle != null && !d.flyParticle.isEmpty())) particleTrail = true;
            groundMult = Math.max(groundMult, d.groundSpeedMultiplier);
            flyMult = Math.max(flyMult, d.flySpeedMultiplier);
            swimMult = Math.max(swimMult, d.swimSpeedMultiplier);
            if (d.effects != null) effects.addAll(d.effects);
            if (d.lure != null && d.lure.enabled) {
                anyLure = true;
                lure.lureShinyMultiplier += d.lure.lureShinyMultiplier;
                lure.lureUltraRAREMultiplier += d.lure.lureUltraRAREMultiplier;
                lure.lureHiddenAbilityMultiplier += d.lure.lureHiddenAbilityMultiplier;
                lure.lureIV += d.lure.lureIV;
                lure.lureChanceIV += d.lure.lureChanceIV;
                lure.lureExpAllMultiplier += d.lure.lureExpAllMultiplier;
                lure.lureEXP += d.lure.lureEXP;
                lure.lureEV += d.lure.lureEV;
                lure.lureAmizadeMultiplier += d.lure.lureAmizadeMultiplier;
                lure.lureChanceDeCaptura += d.lure.lureChanceDeCaptura;
                lure.lurePescaShiny += d.lure.lurePescaShiny;
                lure.lurePescaIv += d.lure.lurePescaIv;
                lure.lurePescaIvChance += d.lure.lurePescaIvChance;
                lure.lurePescaVelocidade += d.lure.lurePescaVelocidade;
                if ((lure.lureTYPE == null || lure.lureTYPE.isEmpty()) && d.lure.lureTYPE != null && !d.lure.lureTYPE.isEmpty())
                    lure.lureTYPE = d.lure.lureTYPE;
            }
        }

        List<Line> out = new ArrayList<>();

        // Abilities
        List<Text> abilities = new ArrayList<>();
        if (fly) abilities.add(LangConfig.text("hud.cos.flight"));
        if (backpack) abilities.add(LangConfig.text("hud.cos.backpack", "rows", backpackRows));
        if (autofeed) abilities.add(LangConfig.text("hud.cos.autofeed"));
        section(out, "hud.cos.section.abilities", abilities);

        // Attributes
        List<Text> attrs = new ArrayList<>();
        if (armor > 0) attrs.add(LangConfig.text("hud.cos.armor", "value", armor));
        if (toughness > 0) attrs.add(LangConfig.text("hud.cos.toughness", "value", fmt(toughness)));
        if (groundMult != 1.0) attrs.add(LangConfig.text("hud.cos.ground_speed", "value", fmt(groundMult)));
        if (flyMult != 1.0) attrs.add(LangConfig.text("hud.cos.fly_speed", "value", fmt(flyMult)));
        if (swimMult != 1.0) attrs.add(LangConfig.text("hud.cos.swim_speed", "value", fmt(swimMult)));
        section(out, "hud.cos.section.attributes", attrs);

        // Effects
        List<Text> fx = new ArrayList<>();
        for (String e : effects) fx.add(formatEffect(e));
        if (particleTrail) fx.add(LangConfig.text("hud.cos.particle_trail"));
        section(out, "hud.cos.section.effects", fx);

        // Lure
        if (anyLure) {
            List<Text> lu = new ArrayList<>();
            if (lure.lureTYPE != null && !lure.lureTYPE.isEmpty()) lu.add(LangConfig.text("hud.lure.type", "value", lure.lureTYPE.toUpperCase()));
            if (lure.lureShinyMultiplier > 0) lu.add(LangConfig.text("hud.lure.shiny", "value", fmt(lure.lureShinyMultiplier)));
            if (lure.lureUltraRAREMultiplier > 0) lu.add(LangConfig.text("hud.lure.ultrarare", "value", fmt(lure.lureUltraRAREMultiplier)));
            if (lure.lureHiddenAbilityMultiplier > 0) lu.add(LangConfig.text("hud.lure.hidden_ability", "value", fmt(lure.lureHiddenAbilityMultiplier)));
            if (lure.lureIV > 0) lu.add(LangConfig.text("hud.lure.iv", "value", lure.lureIV));
            if (lure.lureChanceIV > 0) lu.add(LangConfig.text("hud.lure.iv_chance", "value", pctNum(lure.lureChanceIV)));
            if (lure.lureExpAllMultiplier > 0) lu.add(LangConfig.text("hud.lure.expall", "value", fmt(lure.lureExpAllMultiplier)));
            if (lure.lureEXP > 0) lu.add(LangConfig.text("hud.lure.exp", "value", fmt(lure.lureEXP)));
            if (lure.lureEV > 0) lu.add(LangConfig.text("hud.lure.ev", "value", fmt(lure.lureEV)));
            if (lure.lureAmizadeMultiplier > 0) lu.add(LangConfig.text("hud.lure.friendship", "value", fmt(lure.lureAmizadeMultiplier)));
            if (lure.lureChanceDeCaptura > 0) lu.add(LangConfig.text("hud.lure.capture", "value", fmt(lure.lureChanceDeCaptura)));
            section(out, "hud.cos.section.lure", lu);

            List<Text> fish = new ArrayList<>();
            if (lure.lurePescaShiny > 0) fish.add(LangConfig.text("hud.lure.fishing_shiny", "value", fmt(lure.lurePescaShiny)));
            if (lure.lurePescaIv > 0) fish.add(LangConfig.text("hud.lure.fishing_iv", "value", lure.lurePescaIv));
            if (lure.lurePescaIvChance > 0) fish.add(LangConfig.text("hud.lure.fishing_iv_chance", "value", pctNum(lure.lurePescaIvChance)));
            if (lure.lurePescaVelocidade > 0) fish.add(LangConfig.text("hud.lure.fishing_speed", "value", pctNum(lure.lurePescaVelocidade)));
            section(out, "hud.cos.section.fishing", fish);
        }

        // Scanner
        if (ivScanner) section(out, "hud.cos.section.scanner", List.of(LangConfig.text("hud.cos.ivs_scanner")));

        // remove a linha em branco final
        if (!out.isEmpty() && out.get(out.size() - 1).text() == null) out.remove(out.size() - 1);
        return out;
    }

    /** Adiciona "* Header" + "  * item" por item + 1 linha em branco, só se houver ≥1 item. */
    private static void section(List<Line> out, String headerKey, List<Text> items) {
        if (items.isEmpty()) return;
        out.add(new Line(0, LangConfig.text(headerKey), true));
        for (Text t : items) out.add(new Line(1, t, false));
        out.add(new Line(0, null, false)); // blank separator
    }

    private static Text formatEffect(String eff) {
        if (eff == null || eff.isEmpty()) return Text.literal("");
        String[] parts = eff.split(":");
        if (parts.length < 2) return Text.literal(eff.toUpperCase());
        Identifier id = Identifier.tryParse(parts[0] + ":" + parts[1]);
        Text name = id != null
                ? Registries.STATUS_EFFECT.getEntry(id).map(e -> e.value().getName()).orElse(Text.literal(eff.toUpperCase()))
                : Text.literal(eff.toUpperCase());
        String level = parts.length > 2 ? parts[2] : "0";
        Text out = Text.literal("").append(name);
        try { int lv = Integer.parseInt(level); if (lv > 0) out.getSiblings().add(Text.literal(" " + (lv + 1))); } catch (Exception ignored) {}
        return out;
    }

    // Duplicado de AcessoriesPage.formatLureNumber de propósito.
    private static String fmt(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
        return java.math.BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String pctNum(double chance01) {
        return fmt(chance01 * 100.0);
    }
}
