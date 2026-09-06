package com.f4xizzz.greatcosmetics.fabric;

import com.f4xizzz.greatcosmetics.GreatCosmeticsCommon;
import net.fabricmc.api.ModInitializer;

public final class GreatCosmeticsFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		GreatCosmeticsCommon.init();
	}
}
