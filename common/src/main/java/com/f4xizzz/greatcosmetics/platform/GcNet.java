package com.f4xizzz.greatcosmetics.platform;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Atalho fino de rede pro código comum leaf (config/, util/, database/, client/gui/, ...). Só
 * delega pro {@link NetworkManager} do Architectury — o registro dos payloads e os receivers
 * ficam nos entrypoints ({@code GreatCosmeticsServer} / {@code GreatCosmeticsClientInit}).
 */
public final class GcNet {

	private GcNet() {}

	/** Envia um pacote do servidor pra um jogador. */
	public static void toPlayer(ServerPlayer player, CustomPacketPayload payload) {
		NetworkManager.sendToPlayer(player, payload);
	}

	/** Envia um pacote do client pro servidor. */
	public static void toServer(CustomPacketPayload payload) {
		NetworkManager.sendToServer(payload);
	}
}
