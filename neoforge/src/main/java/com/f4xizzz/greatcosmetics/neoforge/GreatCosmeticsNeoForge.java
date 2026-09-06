package com.f4xizzz.greatcosmetics.neoforge;

import com.f4xizzz.greatcosmetics.GreatCosmeticsCommon;
import com.f4xizzz.greatcosmetics.GreatCosmeticsServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/** Entrypoint NeoForge. Roda no construtor do mod (fase FMLConstructMod) — o Architectury
 *  bufferiza os {@code NetworkManager.registerReceiver}/{@code DeferredRegister}/
 *  {@code KeyMappingRegistry} feitos aqui e reproduz nos eventos de registro certos. */
@Mod(GreatCosmeticsCommon.MOD_ID)
public final class GreatCosmeticsNeoForge {

	public GreatCosmeticsNeoForge() {
		GreatCosmeticsServer.init();

		// GreatCosmeticsClientInit referencia net.minecraft.client.* — só carrega/roda no client.
		if (FMLEnvironment.dist == Dist.CLIENT) {
			GreatCosmeticsNeoForgeClient.init();
		}
	}
}
