package com.f4xizzz.greatcosmetics;

import net.fabricmc.api.ClientModInitializer;

/** Entrypoint client Fabric — só delega pro {@link GreatCosmeticsClientInit} comum (Architectury).
 *  O sistema de overrides de model roda por {@code ModelManagerMixin} (mixin comum), não mais
 *  pelo {@code ModelLoadingPlugin} do Fabric. */
public final class GreatCosmeticsClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		GreatCosmeticsClientInit.initClient();
	}
}
