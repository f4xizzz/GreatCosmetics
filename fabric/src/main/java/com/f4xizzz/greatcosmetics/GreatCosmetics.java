package com.f4xizzz.greatcosmetics;

import net.fabricmc.api.ModInitializer;

/** Entrypoint Fabric — só delega pro {@link GreatCosmeticsServer} comum (Architectury). */
public final class GreatCosmetics implements ModInitializer {

	@Override
	public void onInitialize() {
		GreatCosmeticsServer.init();
	}
}
