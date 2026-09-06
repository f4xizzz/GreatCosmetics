package com.f4xizzz.greatcosmetics.client;

import com.f4xizzz.greatcosmetics.GreatCosmeticsCommon;
import com.f4xizzz.greatcosmetics.config.MainConfig;
import com.f4xizzz.greatcosmetics.util.AutoCMDManager;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Sistema de geração de overrides de model (auto-detect de CustomModelData no {@code carved_pumpkin}
 * + models sintéticos {@code greatcosmetics:icon_*}). Chamado por {@code ModelManagerMixin} (mixin
 * comum) logo depois do vanilla carregar todos os {@code BlockModel} do resourcepack e antes do
 * bake — funciona igual nos dois loaders (era {@code ModelLoadingPlugin} só no Fabric).
 */
public final class GcModelOverrides {

	private GcModelOverrides() {}

	/** Pós-processa o mapa {@code id -> BlockModel} recém-carregado: adiciona os models de ícone
	 *  sintéticos e injeta os overrides de CMD no {@code minecraft:item/carved_pumpkin}. */
	public static void injectInto(Map<ResourceLocation, BlockModel> models, ResourceManager rm) {
		if (!MainConfig.config.autoDetectModels) return;

		// 1. Models de ícone sintéticos (greatcosmetics:icon_<name> -> item/generated com layer0
		//    = greatcosmetics:icons/<name>). Não existem como arquivo — os overrides apontam pra eles.
		for (String iconName : AutoCMDManager.registeredIcons.keySet()) {
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath("greatcosmetics", "icon_" + iconName);
			models.computeIfAbsent(id, k -> {
				String json = "{ \"parent\": \"minecraft:item/generated\", \"textures\": { \"layer0\": \"greatcosmetics:icons/" + iconName + "\" } }";
				return BlockModel.fromString(json);
			});
		}

		// 2. Overrides de CMD no carved_pumpkin.
		BlockModel pumpkin = models.get(ResourceLocation.withDefaultNamespace("item/carved_pumpkin"));
		if (pumpkin != null) {
			applyCarvedPumpkinOverrides(pumpkin, rm);
		}
	}

	/** Adiciona os overrides de CustomModelData (models + ícones registrados no AutoCMDManager) no
	 *  BlockModel do {@code carved_pumpkin}. */
	public static void applyCarvedPumpkinOverrides(BlockModel jsonModel, ResourceManager rm) {
		Map<String, String> modelMap = new HashMap<>();
		for (ResourceLocation resId : rm.listResources("models", res -> res.getNamespace().equals("greatcosmetics") && res.getPath().endsWith(".json")).keySet()) {
			String fullPath = resId.getPath().substring(7, resId.getPath().length() - 5);
			String shortName = fullPath.substring(fullPath.lastIndexOf('/') + 1);
			modelMap.put(shortName, fullPath);
		}

		StringBuilder overridesJson = new StringBuilder();
		overridesJson.append("{ \"parent\": \"minecraft:item/generated\", \"overrides\": [ ");

		TreeMap<Integer, String> sortedOverrides = new TreeMap<>();

		for (Map.Entry<String, Integer> entry : AutoCMDManager.registeredModels.entrySet()) {
			String rawId = entry.getKey();

			if (rawId.startsWith("exact:")) {
				// Modo "Caminho Exato" (CosmeticPart#useExactPath): busca o caminho LITERAL digitado
				// em QUALQUER namespace carregado, em vez do modo antigo (só o nome do arquivo,
				// restrito ao namespace greatcosmetics).
				String exactPath = rawId.substring("exact:".length());
				ResourceLocation found = findExactResource(rm, "models", "models/" + exactPath + ".json");
				String modelRef = found != null ? (found.getNamespace() + ":" + exactPath) : ("greatcosmetics:" + exactPath);
				sortedOverrides.put(entry.getValue(), modelRef);
				continue;
			}

			if (rawId.endsWith("_fallback")) {
				rawId = rawId.replace("_fallback", "");
			}
			String realModelPath = modelMap.getOrDefault(rawId, rawId);
			sortedOverrides.put(entry.getValue(), "greatcosmetics:" + realModelPath);
		}

		for (Map.Entry<String, Integer> entry : AutoCMDManager.registeredIcons.entrySet()) {
			sortedOverrides.put(entry.getValue(), "greatcosmetics:icon_" + entry.getKey());
		}

		boolean first = true;
		for (Map.Entry<Integer, String> entry : sortedOverrides.entrySet()) {
			if (!first) overridesJson.append(", ");
			overridesJson.append(String.format("{ \"predicate\": {\"custom_model_data\": %d}, \"model\": \"%s\" }", entry.getKey(), entry.getValue()));
			first = false;
		}

		overridesJson.append(" ] }");

		GreatCosmeticsCommon.debugLog("ModelManagerMixin(carved_pumpkin): generating " + sortedOverrides.size() + " overrides ("
				+ AutoCMDManager.registeredModels.size() + " models + " + AutoCMDManager.registeredIcons.size() + " icons).");

		try {
			BlockModel dummyModel = BlockModel.fromString(overridesJson.toString());
			jsonModel.getOverrides().addAll(dummyModel.getOverrides());
		} catch (Exception e) {
			GreatCosmeticsCommon.debugLog("ModelManagerMixin(carved_pumpkin): FAILED to apply overrides — " + e);
			e.printStackTrace();
		}
	}

	/** Acha, em QUALQUER namespace carregado, um recurso cujo caminho bate EXATO com
	 *  {@code exactRelativePath} (ex: "models/sas/cigarro.json"). {@code topFolder} é a raiz que
	 *  {@code listResources} pede (ex: "models", "geo", "textures", "animations"). */
	public static ResourceLocation findExactResource(ResourceManager rm, String topFolder, String exactRelativePath) {
		for (ResourceLocation resId : rm.listResources(topFolder, res -> res.getPath().equals(exactRelativePath)).keySet()) {
			return resId;
		}
		return null;
	}
}
