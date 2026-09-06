package com.f4xizzz.greatcosmetics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ponto de entrada COMUM (sem loader) do GreatCosmetics — chamado por
 *  {@code com.f4xizzz.greatcosmetics.fabric.GreatCosmeticsFabric} e
 *  {@code com.f4xizzz.greatcosmetics.neoforge.GreatCosmeticsNeoForge}.
 *
 *  <p>Phase 0: só um stub que loga. Phase 1+ migra a lógica real de
 *  {@code src/} (Fabric/Yarn) pra cá (Mojmap), com a cola de eventos/rede/registro atrás das
 *  APIs do Architectury. */
public final class GreatCosmeticsCommon {

	public static final String MOD_ID = "greatcosmetics";
	public static final Logger LOGGER = LoggerFactory.getLogger("GreatCosmetics");

	/** Ligado/desligado pelo servidor (payload DebugModePayload) e pelo comando {@code /gc debug}.
	 *  Era {@code GreatCosmetics.isDebugMode} no lado Fabric — moveu pra cá porque código comum
	 *  (config/, database/, util/, ...) loga por {@link #debugLog(String)}. */
	public static volatile boolean debugMode = false;

	/** Hook de rebuild do GeoModelRegistry (client). Ligado pelo entrypoint client
	 *  ({@code GreatCosmeticsClient::rebuildAllGeoModels}); código comum client-side (Dev Studio)
	 *  dispara por aqui em vez de referenciar o entrypoint Fabric direto. Phase 3 move a lógica. */
	public static Runnable clientGeoRebuild = () -> {};

	private GreatCosmeticsCommon() {}

	public static void init() {
		LOGGER.info("[GreatCosmetics] common init (multiloader Phase 2)");
	}

	/** Log condicional de depuração. Só imprime quando {@link #debugMode} está ligado. */
	public static void debugLog(String message) {
		if (debugMode) {
			LOGGER.info("[GreatCosmetics DEBUG] " + message);
		}
	}
}
