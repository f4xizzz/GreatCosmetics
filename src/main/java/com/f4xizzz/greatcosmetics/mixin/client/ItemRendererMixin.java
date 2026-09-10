package com.f4xizzz.greatcosmetics.mixin.client;

import com.f4xizzz.greatcosmetics.GreatCosmetics;
import com.f4xizzz.greatcosmetics.config.CosmeticData;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    @Shadow public abstract void renderItem(ItemStack stack, ModelTransformationMode renderMode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, BakedModel model);
    @Shadow public abstract BakedModel getModel(ItemStack stack, @Nullable net.minecraft.world.World world, @Nullable net.minecraft.entity.LivingEntity entity, int seed);

    private static final ThreadLocal<Boolean> IS_RENDERING_COSMETIC = ThreadLocal.withInitial(() -> false);

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V", at = @At("HEAD"), cancellable = true)
    private void greatcosmetics$onRenderItemInventory(ItemStack stack, ModelTransformationMode renderMode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        if (IS_RENDERING_COSMETIC.get()) return;

        // O ArmorFeatureRendererMixin cuida de renderizar na cabeça
        if (renderMode == ModelTransformationMode.HEAD) return;

        if (stack == null || stack.isEmpty() || !stack.isOf(Items.CARVED_PUMPKIN)) return;

        CustomModelDataComponent cmdComp = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (cmdComp == null) return;

        CosmeticData data = GreatCosmetics.getCosmeticData(cmdComp.value());
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
        // SEM ÍCONE PRÓPRIO: LÊ EXATAMENTE A MODEL 3D DA PART (ex: "wizard_hat", "dragon_wings")
        // =================================================================================

        if (data.parts != null && !data.parts.isEmpty()) {
            matrices.push();

            // Se o cosmético for uma fusão de várias parts, reduz a escala pra caber no quadrado
            float scale = data.parts.size() > 1 ? 0.8F : 1.0F;
            matrices.scale(scale, scale, scale);

            for (CosmeticData.CosmeticPart part : data.parts) {
                // Pega diretamente o número limpo calculado pelo AutoCMDManager ("wizard_hat" ou "dragon_wings")
                int modelToRender = part.resolvedCmd != 0 ? part.resolvedCmd : cmdComp.value();

                boolean isGeoModel = com.f4xizzz.greatcosmetics.geckolib.GeoModelRegistry.has(modelToRender);
                ItemStack partStack = isGeoModel
                        ? new ItemStack(com.f4xizzz.greatcosmetics.geckolib.GreatCosmeticsItems.GEO_DISPLAY)
                        : stack.copy();
                partStack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(modelToRender));
                BakedModel partModel = this.getModel(partStack, null, null, 0);

                this.renderItem(partStack, renderMode, leftHanded, matrices, vertexConsumers, light, overlay, partModel);
            }
            matrices.pop();
        }
        else {
            // Fallback caso o cosmético não tenha Parts listadas
            this.renderItem(stack, renderMode, leftHanded, matrices, vertexConsumers, light, overlay, model);
        }

        IS_RENDERING_COSMETIC.set(false);
    }
}