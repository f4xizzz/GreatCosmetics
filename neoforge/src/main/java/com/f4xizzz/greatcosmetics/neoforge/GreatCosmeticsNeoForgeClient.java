package com.f4xizzz.greatcosmetics.neoforge;

import com.f4xizzz.greatcosmetics.GreatCosmeticsClientInit;

/** Parte client-only do entrypoint NeoForge. Isolada numa classe própria porque
 *  {@link GreatCosmeticsClientInit} referencia {@code net.minecraft.client.*} — o servidor
 *  dedicado nunca chega a carregar esta classe (guard {@code FMLEnvironment.dist} no construtor). */
public final class GreatCosmeticsNeoForgeClient {

	private GreatCosmeticsNeoForgeClient() {}

	public static void init() {
		GreatCosmeticsClientInit.initClient();

		// TODO Phase 4: pluga GcModelOverrides no ModelEvent do NeoForge (equivalente do
		// ModelLoadingPlugin do Fabric — ver GreatCosmeticsClient no fabric/). Sem isso, os
		// overrides de CustomModelData no carved_pumpkin não são gerados no NeoForge.
	}
}
