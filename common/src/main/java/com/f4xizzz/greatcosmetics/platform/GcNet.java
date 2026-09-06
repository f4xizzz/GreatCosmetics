package com.f4xizzz.greatcosmetics.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Ponte de rede loader-agnóstica. O código comum (config/, util/, database/, client/gui/, ...)
 * manda pacotes por aqui em vez de chamar {@code ServerPlayNetworking}/{@code ClientPlayNetworking}
 * do Fabric direto.
 *
 * <p>O REGISTRO dos payloads e os RECEIVERS continuam nos entrypoints de cada plataforma
 * ({@code GreatCosmetics}/{@code GreatCosmeticsClient} no Fabric) — Phase 3 migra isso pra cá.
 * Nesta fase (2) só o lado de ENVIO é abstraído, que é o que estava espalhado pelo código leaf.
 *
 * <p>Dois slots separados de propósito: {@link ClientSender} referencia API client-only e só é
 * ligado pelo entrypoint de client; ligar tudo num handler só faria o servidor dedicado carregar
 * classes {@code net.minecraft.client.*} e quebrar.
 */
public final class GcNet {

	@FunctionalInterface
	public interface ServerSender {
		void send(ServerPlayer player, CustomPacketPayload payload);
	}

	@FunctionalInterface
	public interface ClientSender {
		void send(CustomPacketPayload payload);
	}

	private static volatile ServerSender serverSender;
	private static volatile ClientSender clientSender;

	private GcNet() {}

	public static void bindServer(ServerSender sender) {
		serverSender = sender;
	}

	public static void bindClient(ClientSender sender) {
		clientSender = sender;
	}

	/** Envia um pacote do servidor pra um jogador. No-op silencioso se nada foi ligado ainda. */
	public static void toPlayer(ServerPlayer player, CustomPacketPayload payload) {
		ServerSender s = serverSender;
		if (s != null) {
			s.send(player, payload);
		}
	}

	/** Envia um pacote do client pro servidor. No-op silencioso fora de um client conectado. */
	public static void toServer(CustomPacketPayload payload) {
		ClientSender s = clientSender;
		if (s != null) {
			s.send(payload);
		}
	}
}
