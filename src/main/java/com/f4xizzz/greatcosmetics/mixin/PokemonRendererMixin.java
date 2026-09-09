package com.f4xizzz.greatcosmetics.mixin;

import com.cobblemon.mod.common.client.render.pokemon.PokemonRenderer;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.f4xizzz.greatcosmetics.GreatCosmetics;
import com.f4xizzz.greatcosmetics.client.ClientArmorCosmeticsCache;
import com.f4xizzz.greatcosmetics.client.ClientCosmeticCache;
import com.f4xizzz.greatcosmetics.client.ClientPokemonIvCache;
import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import com.f4xizzz.greatcosmetics.client.gui.pages.PartyPage;
import com.f4xizzz.greatcosmetics.config.CosmeticData;
import com.f4xizzz.greatcosmetics.util.EquippedCosmeticId;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PokemonRenderer.class)
public class    PokemonRendererMixin {

    private static long greatcosmetics$scannerCheckAtMs = 0L;
    private static boolean greatcosmetics$scannerActive = false;
    private static final String[] greatcosmetics$IV_LABELS = {"HP", "A", "D", "SpA", "SpD", "Spe"};

    /** IVs Scanner — desenha os IVs acima do nametag do Pokémon quando o player local veste um
     *  cosmético com ivScanner e o servidor mandou os dados (ver SyncPokemonIvsPayload). HEAD
     *  inject: só roda quando o Cobblemon já decidiu desenhar o label, então herda o gating de
     *  visibilidade/distância dele. */
    @Inject(method = "renderNameTag", at = @At("HEAD"))
    private void greatcosmetics$renderIvScanner(PokemonEntity entity, Text text, MatrixStack matrices,
                                                VertexConsumerProvider vertexConsumers, int light, float tickDelta, CallbackInfo ci) {
        if (!greatcosmetics$localPlayerHasScanner()) return;
        int[] iv = ClientPokemonIvCache.get(entity.getId());
        if (iv == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.textRenderer == null) return;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6 && i < iv.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append("§7").append(greatcosmetics$IV_LABELS[i]).append(greatcosmetics$ivColor(iv[i])).append(iv[i]);
        }
        Text line = Text.literal(sb.toString());

        double anchor = entity.getHeight() + 0.9;
        matrices.push();
        matrices.translate(0.0, anchor, 0.0);
        matrices.multiply(mc.getEntityRenderDispatcher().getRotation());
        matrices.scale(0.02f, -0.02f, 0.02f);

        TextRenderer tr = mc.textRenderer;
        float bgOpacity = mc.options.getTextBackgroundOpacity(0.25f);
        int bgColor = (int) (bgOpacity * 255.0f) << 24;
        Matrix4f m = matrices.peek().getPositionMatrix();
        float x = -tr.getWidth(line) / 2f;
        tr.draw(line, x, 0f, 0x20FFFFFF, false, m, vertexConsumers, TextRenderer.TextLayerType.SEE_THROUGH, bgColor, light);
        tr.draw(line, x, 0f, -1, false, m, vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0, light);
        matrices.pop();
    }

    /** §-code por faixa: 0-14 vermelho, 15-24 amarelo, 25-30 dourado, 31 verde. */
    private static String greatcosmetics$ivColor(int v) {
        if (v >= 31) return "§a";
        if (v >= 25) return "§6";
        if (v >= 15) return "§e";
        return "§c";
    }

    private static boolean greatcosmetics$localPlayerHasScanner() {
        long now = System.currentTimeMillis();
        if (now - greatcosmetics$scannerCheckAtMs < 500L) return greatcosmetics$scannerActive;
        greatcosmetics$scannerCheckAtMs = now;
        greatcosmetics$scannerActive = greatcosmetics$computeScanner();
        return greatcosmetics$scannerActive;
    }

    private static boolean greatcosmetics$computeScanner() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        for (String raw : ClientCosmeticCache.getEquipped(mc.player.getUuid())) {
            CosmeticData d = GreatCosmetics.getCosmeticById(EquippedCosmeticId.base(raw));
            if (d != null && d.ivScanner) return true;
        }
        if (!ClientArmorCosmeticsCache.armorCosmetics.isEmpty()) {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack stack = mc.player.getEquippedStack(slot);
                if (stack == null || stack.isEmpty()) continue;
                Identifier worn = Registries.ITEM.getId(stack.getItem());
                for (CosmeticData d : ClientArmorCosmeticsCache.armorCosmetics.values()) {
                    if (d == null || d.realItemId == null || !d.ivScanner) continue;
                    Identifier real = Identifier.tryParse(d.realItemId);
                    if (real != null && real.equals(worn)) return true;
                }
            }
        }
        return false;
    }

    // Injeta nossa matemática de encolhimento no final (RETURN) do método original "scale" do Cobblemon
    @Inject(method = "scale", at = @At("RETURN"))
    private void applyWardrobeShrink(PokemonEntity pEntity, MatrixStack pPoseStack, float pPartialTickTime, CallbackInfo ci) {
        if (Wardrobe3DScreen.isPartyTabActive) {
            // Garante que só vamos encolher o Pokémon que está no centro da tela
            if (pEntity == PartyPage.getCurrentPokemonEntity()) {
                float maxHeight = 1.2f;
                float trueHeight = pEntity.getHeight();

                if (trueHeight > maxHeight && trueHeight > 0) {
                    float shrink = maxHeight / trueHeight;
                    // Multiplica a matriz nativa deles pela nossa redução!
                    pPoseStack.scale(shrink, shrink, shrink);
                }
            }
        }
    }
}