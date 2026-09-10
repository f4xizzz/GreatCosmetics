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
 * HUD dos bônus dos cosméticos equipados — canto INFERIOR DIREITO, fundo transparente, texto
 * reduzido. Lista só o que é útil no dia a dia: habilidades (voo, mochila, auto-feed, IVs
 * Scanner), efeitos de poção / rastro de partícula, e os bônus de Lure/pesca — agrupado em
 * bullets aninhados. NÃO mostra atributos de combate (armor/toughness/velocidade) pra não poluir.
 * Encolhe sozinho quando fica alto demais. Some quando não há nada pra mostrar.
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

        // Pré-monta o Text de cada linha (pra medir a largura e desenhar o mesmo objeto).
        List<Text> rendered = new ArrayList<>(lines.size());
        int maxWidth = 0;
        for (Line l : lines) {
            if (l.text == null) { rendered.add(null); continue; }
            String prefix = l.header ? "* " : "  * ";
            Text t = Text.literal("§7" + " ".repeat(l.indent * 2) + prefix)
                    .append(l.header ? Text.literal("§6§l").append(l.text) : Text.literal("§f").append(l.text));
            rendered.add(t);
            maxWidth = Math.max(maxWidth, mc.textRenderer.getWidth(t));
        }

        int lineH = mc.textRenderer.fontHeight + 1;
        int screenH = mc.getWindow().getScaledHeight();
        int screenW = mc.getWindow().getScaledWidth();
        int total = lines.size() * lineH;

        // Base menor (0.8) + auto-shrink: nunca passa de ~50% da altura da tela; piso 0.4.
        float scale = 0.8f;
        float maxH = screenH * 0.5f;
        if (total * scale > maxH) scale = Math.max(0.4f, maxH / total);

        int pad = 4;
        int bottom = screenH - pad;
        double left = screenW - pad - maxWidth * scale; // ancorado no canto inferior DIREITO

        ctx.getMatrices().push();
        ctx.getMatrices().translate(left, bottom, 0);
        ctx.getMatrices().scale(scale, scale, 1f);

        int y = -total; // desenha de cima pra baixo dentro do bloco, ancorado no rodapé
        for (Text t : rendered) {
            if (t != null) ctx.drawTextWithShadow(mc.textRenderer, t, 0, y, 0xFFFFFFFF);
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
        // agrega (só o que a HUD mostra — atributos de combate ficaram de fora de propósito)
        boolean fly = false, backpack = false, autofeed = false, particleTrail = false;
        boolean ivScanner = false, natureScanner = false, abilityScanner = false, sizeScanner = false;
        int backpackRows = 0;
        Set<String> effects = new LinkedHashSet<>();
        CosmeticData.LureStats lure = new CosmeticData.LureStats();
        boolean anyLure = false;

        for (CosmeticData d : active) {
            if (d.EnableFly) fly = true;
            if (d.isBackpack) { backpack = true; backpackRows = Math.max(backpackRows, d.backpackRows); }
            if (d.AutoFeed) autofeed = true;
            if (d.ivScanner) ivScanner = true;
            if (d.natureScanner) natureScanner = true;
            if (d.abilityScanner) abilityScanner = true;
            if (d.sizeScanner) sizeScanner = true;
            if ((d.effectVisual != null && !d.effectVisual.isEmpty()) || (d.flyParticle != null && !d.flyParticle.isEmpty())) particleTrail = true;
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

        // Abilities (voo, mochila, auto-feed, IVs Scanner)
        List<Text> abilities = new ArrayList<>();
        if (fly) abilities.add(LangConfig.text("hud.cos.flight"));
        if (backpack) abilities.add(LangConfig.text("hud.cos.backpack", "rows", backpackRows));
        if (autofeed) abilities.add(LangConfig.text("hud.cos.autofeed"));
        // Scanners: junta os nomes ativos com vírgula + um "Scanner" só no fim
        // (ex: 1 → "IVs Scanner"; 3 → "IVs, Nature, Size Scanner").
        java.util.List<String> scannerNames = new ArrayList<>();
        if (ivScanner) scannerNames.add(LangConfig.legacy("hud.cos.scanner.ivs"));
        if (natureScanner) scannerNames.add(LangConfig.legacy("hud.cos.scanner.nature"));
        if (sizeScanner) scannerNames.add(LangConfig.legacy("hud.cos.scanner.size"));
        if (abilityScanner) scannerNames.add(LangConfig.legacy("hud.cos.scanner.ability"));
        if (!scannerNames.isEmpty()) {
            abilities.add(LangConfig.text("hud.cos.scanner.line", "names", String.join(", ", scannerNames)));
        }
        section(out, "hud.cos.section.abilities", abilities);

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
