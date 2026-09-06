package com.f4xizzz.greatcosmetics.client;

import com.f4xizzz.greatcosmetics.GreatCosmeticsCommon;
import com.f4xizzz.greatcosmetics.config.MainConfig;
import com.f4xizzz.greatcosmetics.util.AutoCMDManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Lógica de geração de overrides de model (auto-detect de CustomModelData no {@code carved_pumpkin}
 * + models {@code greatcosmetics:icon_*}/{@code greatcosmetics:*}). Loader-agnóstica: cada
 * plataforma pluga isso no seu próprio evento de model loading (Fabric {@code ModelLoadingPlugin},
 * NeoForge {@code ModelEvent}).
 */
public final class GcModelOverrides {

	private GcModelOverrides() {}

	/** Resolve um model {@code greatcosmetics:<path>} lido do resourcepack (ícone chapado ou
	 *  model json com texturas normalizadas). Devolve {@code null} se não for nosso / não existir. */
	public static BlockModel resolveGreatCosmeticsModel(ResourceLocation id) {
		if (!MainConfig.config.autoDetectModels) return null;
		if (id == null || !id.getNamespace().equals("greatcosmetics")) return null;

		if (id.getPath().startsWith("icon_")) {
			String iconName = id.getPath().replace("icon_", "");
			if (AutoCMDManager.registeredIcons.containsKey(iconName)) {
				String json = String.format("{ \"parent\": \"minecraft:item/generated\", \"textures\": { \"layer0\": \"greatcosmetics:icons/%s\" } }", iconName);
				return BlockModel.fromString(json);
			}
		}

		try {
			ResourceLocation fileId = ResourceLocation.fromNamespaceAndPath("greatcosmetics", "models/" + id.getPath() + ".json");
			ResourceManager rm = Minecraft.getInstance().getResourceManager();
			var resourceOpt = rm.getResource(fileId);

			if (resourceOpt.isPresent()) {
				try (java.io.Reader reader = resourceOpt.get().openAsReader()) {
					com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();

					if (json.has("textures")) {
						com.google.gson.JsonObject textures = json.getAsJsonObject("textures");
						for (Map.Entry<String, com.google.gson.JsonElement> entry : textures.entrySet()) {
							String texPath = entry.getValue().getAsString();

							if (!texPath.startsWith("#")) {
								String cleanPath = texPath.replace("greatcosmetics:", "");

								if (!cleanPath.startsWith("item/") && !cleanPath.startsWith("block/")) {
									textures.addProperty(entry.getKey(), "greatcosmetics:item/" + cleanPath);
								} else {
									textures.addProperty(entry.getKey(), "greatcosmetics:" + cleanPath);
								}
							}
						}
					}
					return BlockModel.fromString(json.toString());
				}
			}
		} catch (Exception e) {
			GreatCosmeticsCommon.debugLog("resolveModel: failed reading models/" + id.getPath() + ".json — " + e);
		}
		return null;
	}

	/** true se este model é o {@code minecraft:item/carved_pumpkin} onde injetamos os overrides. */
	public static boolean isCarvedPumpkinItemModel(ResourceLocation id) {
		return id != null && id.getNamespace().equals("minecraft") && id.getPath().equals("item/carved_pumpkin");
	}

	/** Adiciona os overrides de CustomModelData (models + ícones registrados no AutoCMDManager) no
	 *  BlockModel do {@code carved_pumpkin}. Chamado pelo hook de model loading de cada plataforma. */
	public static void applyCarvedPumpkinOverrides(BlockModel jsonModel) {
		ResourceManager rm = Minecraft.getInstance().getResourceManager();
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

		GreatCosmeticsCommon.debugLog("modifyModelOnLoad(carved_pumpkin): generating " + sortedOverrides.size() + " overrides ("
				+ AutoCMDManager.registeredModels.size() + " models + " + AutoCMDManager.registeredIcons.size() + " icons).");

		try {
			BlockModel dummyModel = BlockModel.fromString(overridesJson.toString());
			jsonModel.getOverrides().addAll(dummyModel.getOverrides());
		} catch (Exception e) {
			GreatCosmeticsCommon.debugLog("modifyModelOnLoad(carved_pumpkin): FAILED to apply overrides — " + e);
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
