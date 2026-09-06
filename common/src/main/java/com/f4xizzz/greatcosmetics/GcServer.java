package com.f4xizzz.greatcosmetics;

import com.f4xizzz.greatcosmetics.config.LangConfig;
import com.f4xizzz.greatcosmetics.config.SoundConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Helpers server-side que estavam no entrypoint {@code GreatCosmetics} (Fabric). Moveram pra
 * common no split multiloader porque código comum (util/, config/) chama {@code checkPermission},
 * {@code isRealOperator}, {@code licenseBlocked} e {@code playCosmeticSound}.
 *
 * <p>{@code GreatCosmetics} (Fabric) mantém delegadores com a mesma assinatura pra não tocar nos
 * ~40 receivers dele; Phase 3 termina de mover a cola de entrypoint.
 */
public final class GcServer {

	private GcServer() {}

	/** OP de verdade na ops.json (via PlayerList), ignorando hasPermissionLevel de propósito — com
	 *  "Vanilla Permissions" instalado, hasPermissionLevel(2) resolvia true pra qualquer um em nodes
	 *  não gerenciados. */
	public static boolean isRealOperator(ServerPlayer player) {
		return player.getServer().getPlayerList().isOp(player.getGameProfile());
	}

	public static void sendOpMessage(ServerPlayer player, Component message, boolean actionBar) {
		if (isRealOperator(player)) {
			player.displayClientMessage(message, actionBar);
		}
	}

	/** Gate de licença pros payloads server-side (ver security.ActivationManager). Num servidor
	 *  dedicado sem licença válida, avisa o jogador e devolve true (o receiver deve dar return).
	 *  Singleplayer nunca bloqueia. */
	public static boolean licenseBlocked(ServerPlayer player) {
		MinecraftServer server = player.getServer();
		if (server == null || !server.isDedicatedServer()) return false;
		if (com.f4xizzz.greatcosmetics.security.ActivationManager.isModActivated()) return false;
		player.displayClientMessage(LangConfig.chat("general.license.locked", server.registryAccess()), false);
		return true;
	}

	/** Checagem de permissão booleana via reflection no fabric-permissions-api (me.lucko...). Nunca
	 *  toca em hasPermissionLevel(). Em loaders/servidores sem esse mod, devolve false. */
	public static boolean checkPermission(ServerPlayer player, String permission) {
		if (permission == null || permission.isEmpty()) return false;
		try {
			Class<?> permsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
			java.lang.reflect.Method checkMethod = permsClass.getMethod("check", net.minecraft.world.entity.Entity.class, String.class, boolean.class);
			return (boolean) checkMethod.invoke(null, player, permission, false);
		} catch (Exception e) {
			return false;
		}
	}

	// Toca som baseado na antiga SoundConfig
	public static void playCustomSound(ServerPlayer player, String soundKey) {
		var soundData = SoundConfig.getSoundData(soundKey);
		if (soundData == null || soundData.id == null || soundData.id.isEmpty() || soundData.id.equalsIgnoreCase("none")) return;
		playCosmeticSound(player, soundData.id, (float) soundData.volume, (float) soundData.pitch);
	}

	public static void playCosmeticSound(ServerPlayer player, String soundId) {
		playCosmeticSound(player, soundId, 1.0f, 1.0f);
	}

	public static void playCosmeticSound(ServerPlayer player, String soundId, float volume, float pitch) {
		if (soundId == null || soundId.trim().isEmpty() || soundId.equalsIgnoreCase("none")) return;
		ResourceLocation id = ResourceLocation.tryParse(soundId);
		if (id != null) {
			var optionalSound = BuiltInRegistries.SOUND_EVENT.getHolder(id);
			Holder<SoundEvent> soundToPlay;
			if (optionalSound.isPresent()) {
				soundToPlay = optionalSound.get();
			} else {
				soundToPlay = Holder.direct(SoundEvent.createVariableRangeEvent(id));
			}

			player.connection.send(new ClientboundSoundPacket(
					soundToPlay,
					SoundSource.PLAYERS,
					player.getX(), player.getY(), player.getZ(),
					volume, pitch,
					player.level().getRandom().nextLong()
			));
		}
	}
}
