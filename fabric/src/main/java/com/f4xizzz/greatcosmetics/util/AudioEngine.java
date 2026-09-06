package com.f4xizzz.greatcosmetics.util;

import com.f4xizzz.greatcosmetics.config.SoundConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class AudioEngine {

    public static void playSound(ServerPlayer player, SoundConfig.SoundEntry soundEntry) {
        if (soundEntry == null || soundEntry.id == null || soundEntry.id.isEmpty() || soundEntry.id.equalsIgnoreCase("none")) {
            return; // Permite desativar o som colocando "none" no config
        }

        ResourceLocation soundId = ResourceLocation.tryParse(soundEntry.id);
        if (soundId == null) return;

        SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(soundId);
        if (event != null) {
            // Toca o som apenas para o jogador (Server Side)
            player.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                            BuiltInRegistries.SOUND_EVENT.wrapAsHolder(event),
                            SoundSource.PLAYERS,
                            player.getX(), player.getY(), player.getZ(),
                            soundEntry.volume, soundEntry.pitch, player.getRandom().nextLong()
                    )
            );
        }
    }
}