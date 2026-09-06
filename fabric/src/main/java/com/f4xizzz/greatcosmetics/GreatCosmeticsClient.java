package com.f4xizzz.greatcosmetics;

import com.f4xizzz.greatcosmetics.client.GcModelOverrides;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.renderer.block.model.BlockModel;

/** Entrypoint client Fabric — delega pro {@link GreatCosmeticsClientInit} comum e pluga o
 *  model loading do Fabric ({@code ModelLoadingPlugin}) na lógica comum ({@link GcModelOverrides}). */
public final class GreatCosmeticsClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		GreatCosmeticsClientInit.initClient();

		// Auto-detect de CustomModelData no carved_pumpkin + models greatcosmetics:icon_* / greatcosmetics:*
		ModelLoadingPlugin.register(pluginContext -> {
			pluginContext.resolveModel().register(context ->
					GcModelOverrides.resolveGreatCosmeticsModel(context.id()));

			pluginContext.modifyModelOnLoad().register((unbakedModel, context) -> {
				if (GcModelOverrides.isCarvedPumpkinItemModel(context.resourceId())
						&& unbakedModel instanceof BlockModel jsonModel) {
					GcModelOverrides.applyCarvedPumpkinOverrides(jsonModel);
				}
				return unbakedModel;
			});
		});
	}
}
