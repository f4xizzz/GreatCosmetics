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

	private GreatCosmeticsCommon() {}

	public static void init() {
		LOGGER.info("[GreatCosmetics] common init (multiloader Phase 0 stub)");
	}
}
