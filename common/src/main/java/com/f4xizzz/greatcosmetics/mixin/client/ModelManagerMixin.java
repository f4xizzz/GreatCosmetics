package com.f4xizzz.greatcosmetics.mixin.client;

import com.f4xizzz.greatcosmetics.GreatCosmeticsCommon;
import com.f4xizzz.greatcosmetics.client.GcModelOverrides;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Injeção do sistema de overrides do GreatCosmetics no carregamento de models do vanilla —
 * equivalente multiloader do {@code ModelLoadingPlugin} do Fabric (ver {@link GcModelOverrides}).
 * Roda logo depois do vanilla ter carregado todos os {@code BlockModel} do resourcepack e antes
 * do bake: adiciona os models de ícone sintéticos e injeta os overrides de CustomModelData no
 * {@code minecraft:item/carved_pumpkin}.
 */
@Mixin(ModelManager.class)
public class ModelManagerMixin {

	@Inject(method = "loadBlockModels", at = @At("RETURN"), cancellable = true)
	private static void greatcosmetics$injectCosmeticModels(
			ResourceManager resourceManager, Executor executor,
			CallbackInfoReturnable<CompletableFuture<Map<ResourceLocation, BlockModel>>> cir) {
		cir.setReturnValue(cir.getReturnValue().thenApply(loaded -> {
			Map<ResourceLocation, BlockModel> mutable = new HashMap<>(loaded);
			try {
				GcModelOverrides.injectInto(mutable, resourceManager);
			} catch (Exception e) {
				GreatCosmeticsCommon.debugLog("ModelManagerMixin: injectInto failed — " + e);
			}
			return mutable;
		}));
	}
}
