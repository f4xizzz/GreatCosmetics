package com.f4xizzz.greatcosmetics.mixin.client;

import com.f4xizzz.greatcosmetics.config.CosmeticData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    @Shadow public abstract void render(ItemStack stack, ItemDisplayContext renderMode, boolean leftHanded, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay, BakedModel model);
    @Shadow public abstract BakedModel getModel(ItemStack stack, @Nullable net.minecraft.world.level.Level world, @Nullable net.minecraft.world.entity.LivingEntity entity, int seed);

    private static final ThreadLocal<Boolean> IS_RENDERING_COSMETIC = ThreadLocal.withInitial(() -> false);

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V", at = @At("HEAD"), cancellable = true)
    private void greatcosmetics$onRenderItemInventory(ItemStack stack, ItemDisplayContext renderMode, boolean leftHanded, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        if (IS_RENDERING_COSMETIC.get()) return;

        // O ArmorFeatureRendererMixin cuida de renderizar na cabeça
        if (renderMode == ItemDisplayContext.HEAD) return;

        if (stack == null || stack.isEmpty() || !stack.is(Items.CARVED_PUMPKIN)) return;

        CustomModelData cmdComp = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmdComp == null) return;

        CosmeticData data = com.f4xizzz.greatcosmetics.config.CosmeticsConfig.getCosmeticData(cmdComp.value());
        if (data == null) return;

        // Se o número atual da stack foi resolvido como um ÍCONE de verdade (campo "Nome do Ícone"
        // do Dev Studio, ver GreatCosmeticsClient#registeredIcons), o BakedModel que o Minecraft já
        // passou pra cá (`model`) já é o ícone chapado certo (montado pelos overrides do
        // carved_pumpkin) — não cancela nada, deixa o render vanilla normal acontecer. Sem esse
        // check, este mixin sempre forçava a model 3D da Part por baixo do pano (ver comentário
        // abaixo), então "Nome do Ícone" nunca tinha efeito nenhum aqui, mesmo com o arquivo certo
        // no resourcepack e o cmd resolvido certinho pro ícone.
        if (com.f4xizzz.greatcosmetics.util.AutoCMDManager.registeredIcons.containsValue((int) cmdComp.value())) {
            return;
        }

        IS_RENDERING_COSMETIC.set(true);
        ci.cancel();

        // =================================================================================
        // SEM ÍCONE PRÓPRIO: LÊ EXATAMENTE A MODEL 3D DA PART (ex: "cigarro", "faxihat")
        // =================================================================================

        if (data.parts != null && !data.parts.isEmpty()) {
            matrices.pushPose();

            // Se o cosmético for uma fusão de várias parts, reduz a escala pra caber no quadrado
            float scale = data.parts.size() > 1 ? 0.8F : 1.0F;
            matrices.scale(scale, scale, scale);

            for (CosmeticData.CosmeticPart part : data.parts) {
                // Pega diretamente o número limpo calculado pelo AutoCMDManager ("cigarro" ou "faxihat")
                int modelToRender = part.resolvedCmd != 0 ? part.resolvedCmd : cmdComp.value();

                boolean isGeoModel = com.f4xizzz.greatcosmetics.geckolib.GeoModelRegistry.has(modelToRender);
                ItemStack partStack = isGeoModel
                        ? new ItemStack(com.f4xizzz.greatcosmetics.geckolib.GreatCosmeticsItems.GEO_DISPLAY)
                        : stack.copy();
                partStack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(modelToRender));
                BakedModel partModel = this.getModel(partStack, null, null, 0);

                this.render(partStack, renderMode, leftHanded, matrices, vertexConsumers, light, overlay, partModel);
            }
            matrices.popPose();
        }
        else {
            // Fallback caso o cosmético não tenha Parts listadas
            this.render(stack, renderMode, leftHanded, matrices, vertexConsumers, light, overlay, model);
        }

        IS_RENDERING_COSMETIC.set(false);
    }
}