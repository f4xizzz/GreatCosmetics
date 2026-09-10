package com.f4xizzz.greatcosmetics.mixin;

import com.cobblemon.mod.common.client.render.pokemon.PokemonRenderer;
import com.cobblemon.mod.common.client.settings.ServerSettings;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.f4xizzz.greatcosmetics.GreatCosmetics;
import com.f4xizzz.greatcosmetics.client.ClientArmorCosmeticsCache;
import com.f4xizzz.greatcosmetics.client.ClientCosmeticCache;
import com.f4xizzz.greatcosmetics.client.ClientPokemonScanCache;
import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import com.f4xizzz.greatcosmetics.client.gui.pages.PartyPage;
import com.f4xizzz.greatcosmetics.config.CosmeticData;
import com.f4xizzz.greatcosmetics.network.SyncPokemonScanPayload;
import com.f4xizzz.greatcosmetics.util.EquippedCosmeticId;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scanners de Pokémon (IV / Nature / Ability / Size) — desenhados EXATAMENTE no mesmo espaço de
 * matriz que o nametag/level do Cobblemon: injetamos LOGO ANTES do {@code matrices.pop()} no final
 * de {@code PokemonRenderer.renderNameTag}, então herdamos o billboard, a escala compensada por
 * distância e a posição (topo da hitbox + 0.5) que o Cobblemon montou. Só desenhamos linhas extras
 * em offsets de Y relativos ao nome (que fica em y=0). Mesma fonte, mesma luz (full-bright), mesmo
 * fundo translúcido, mesmo passe duplo SEE_THROUGH + NORMAL.
 */
@Mixin(PokemonRenderer.class)
public class PokemonRendererMixin {

    private static long greatcosmetics$scannerCheckAtMs = 0L;
    // iv, nature, ability, size — recalculado no máx 2x/s.
    private static final boolean[] greatcosmetics$scan = {false, false, false, false};

    // Uma linha por IV, na ordem que o servidor manda (HP, ATTACK, DEFENCE, SPECIAL_ATTACK,
    // SPECIAL_DEFENCE, SPEED). Label colorida + negrito, valor branco.
    private static final String[] greatcosmetics$IV_LABELS = {
            "§a§lHealth", "§c§lAttack", "§6§lDefense", "§9§lSp. Attack", "§e§lSp. Defense", "§b§lSpeed"
    };

    @Inject(method = "renderNameTag", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V"))
    private void greatcosmetics$renderScanners(PokemonEntity entity, Text text, MatrixStack matrices,
                                               VertexConsumerProvider vertexConsumers, int light, float tickDelta, CallbackInfo ci) {
        greatcosmetics$refreshScannerState();
        boolean[] scan = greatcosmetics$scan;
        if (!scan[0] && !scan[1] && !scan[2] && !scan[3]) return;

        SyncPokemonScanPayload.Entry data = ClientPokemonScanCache.get(entity.getId());
        if (data == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        if (tr == null) return;

        // matrices ainda está no espaço billboard+escala do nametag (o pop() ainda não rodou).
        Matrix4f m = matrices.peek().getPositionMatrix();
        int bgColor = (int) (mc.options.getTextBackgroundOpacity(0.25f) * 255.0f) << 24;
        int fullBright = LightmapTextureManager.pack(15, 15);
        final int lineH = 10;

        // Largura do rótulo do nome (nome + " Lv.X" se o Cobblemon mostrar o level) — pra ancorar
        // ability à direita e size à esquerda.
        MutableText nameLabel = text.copy();
        try {
            Integer lvl = entity.labelLevel();
            if (ServerSettings.INSTANCE.getDisplayEntityLevelLabel() && lvl != null && lvl > 0) {
                if (ServerSettings.INSTANCE.getDisplayEntityNameLabel()) nameLabel.append(" ");
                nameLabel.append(Text.translatable("cobblemon.label.lv", lvl));
            }
        } catch (Throwable ignored) {}
        float nameHalf = tr.getWidth(nameLabel) / 2f;

        // ---- Bloco ACIMA do nick: (de baixo pra cima) nature, depois as 6 linhas de IV ----
        boolean showIvs = scan[0] && data.ivs() != null;
        boolean showNature = scan[1] && data.nature() != null && !data.nature().isEmpty();
        if (showIvs || showNature) {
            java.util.List<Text> up = new java.util.ArrayList<>();
            if (showNature) up.add(Text.literal("§d§l").append(Text.translatable(data.nature())));
            if (showIvs) {
                int[] iv = data.ivs();
                int n = Math.min(6, iv.length);
                for (int i = n - 1; i >= 0; i--) up.add(Text.literal(greatcosmetics$IV_LABELS[i] + "§r§f: " + iv[i]));
            }
            for (int i = 0; i < up.size(); i++) {
                Text row = up.get(i);
                float x = -tr.getWidth(row) / 2f;
                float y = -(i + 1) * (float) lineH;   // i=0 = linha logo acima do nome
                greatcosmetics$draw(tr, row, x, y, m, vertexConsumers, bgColor, fullBright);
            }
        }

        // ---- Ability (direita) / Size (esquerda) — na MESMA linha do nick (y=0) ----
        boolean showAbility = scan[2] && data.ability() != null && !data.ability().isEmpty();
        boolean showSize = scan[3] && data.hasSize();
        if (showSize) {
            String cat;
            try {
                cat = com.cobblemon.mod.common.pokemon.PokemonSizeCategory.Companion
                        .fromScale(data.scaleModifier()).name();
            } catch (Throwable t) { cat = "?"; }
            Text sizeText = Text.literal("§e§l" + cat);
            greatcosmetics$draw(tr, sizeText, -nameHalf - 3f - tr.getWidth(sizeText), 0f, m, vertexConsumers, bgColor, fullBright);
        }
        if (showAbility) {
            Text abilityText = Text.literal("§c").append(Text.translatable(data.ability()));
            greatcosmetics$draw(tr, abilityText, nameHalf + 3f, 0f, m, vertexConsumers, bgColor, fullBright);
        }
    }

    private static void greatcosmetics$draw(TextRenderer tr, Text t, float x, float y, Matrix4f m,
                                            VertexConsumerProvider vc, int bg, int light) {
        tr.draw(t, x, y, 0x20FFFFFF, false, m, vc, TextRenderer.TextLayerType.SEE_THROUGH, bg, light);
        tr.draw(t, x, y, -1, false, m, vc, TextRenderer.TextLayerType.NORMAL, 0, light);
    }

    private static void greatcosmetics$refreshScannerState() {
        long now = System.currentTimeMillis();
        if (now - greatcosmetics$scannerCheckAtMs < 500L) return;
        greatcosmetics$scannerCheckAtMs = now;
        boolean[] s = {false, false, false, false};
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            for (String raw : ClientCosmeticCache.getEquipped(mc.player.getUuid())) {
                greatcosmetics$mark(s, GreatCosmetics.getCosmeticById(EquippedCosmeticId.base(raw)));
            }
            if (!ClientArmorCosmeticsCache.armorCosmetics.isEmpty()) {
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    ItemStack stack = mc.player.getEquippedStack(slot);
                    if (stack == null || stack.isEmpty()) continue;
                    Identifier worn = Registries.ITEM.getId(stack.getItem());
                    for (CosmeticData d : ClientArmorCosmeticsCache.armorCosmetics.values()) {
                        if (d == null || d.realItemId == null) continue;
                        Identifier real = Identifier.tryParse(d.realItemId);
                        if (real != null && real.equals(worn)) greatcosmetics$mark(s, d);
                    }
                }
            }
        }
        System.arraycopy(s, 0, greatcosmetics$scan, 0, 4);
    }

    private static void greatcosmetics$mark(boolean[] s, CosmeticData d) {
        if (d == null) return;
        if (d.ivScanner) s[0] = true;
        if (d.natureScanner) s[1] = true;
        if (d.abilityScanner) s[2] = true;
        if (d.sizeScanner) s[3] = true;
    }

    // Injeta nossa matemática de encolhimento no final (RETURN) do método original "scale" do Cobblemon
    @Inject(method = "scale", at = @At("RETURN"))
    private void applyWardrobeShrink(PokemonEntity pEntity, MatrixStack pPoseStack, float pPartialTickTime, CallbackInfo ci) {
        if (Wardrobe3DScreen.isPartyTabActive) {
            if (pEntity == PartyPage.getCurrentPokemonEntity()) {
                float maxHeight = 1.2f;
                float trueHeight = pEntity.getHeight();
                if (trueHeight > maxHeight && trueHeight > 0) {
                    float shrink = maxHeight / trueHeight;
                    pPoseStack.scale(shrink, shrink, shrink);
                }
            }
        }
    }
}
