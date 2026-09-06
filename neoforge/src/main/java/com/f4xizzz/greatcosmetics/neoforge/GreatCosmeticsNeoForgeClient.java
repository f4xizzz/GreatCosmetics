package com.f4xizzz.greatcosmetics.neoforge;

import com.f4xizzz.greatcosmetics.GreatCosmeticsClientInit;

/** Parte client-only do entrypoint NeoForge. Isolada numa classe própria porque
 *  {@link GreatCosmeticsClientInit} referencia {@code net.minecraft.client.*} — o servidor
 *  dedicado nunca chega a carregar esta classe (guard {@code FMLEnvironment.dist} no construtor).
 *
 *  <p>O sistema de overrides de model roda por {@code ModelManagerMixin} (mixin comum) nos dois
 *  loaders — não precisa de wiring por plataforma aqui. */
public final class GreatCosmeticsNeoForgeClient {

	private GreatCosmeticsNeoForgeClient() {}

	public static void init() {
		GreatCosmeticsClientInit.initClient();
	}
}
