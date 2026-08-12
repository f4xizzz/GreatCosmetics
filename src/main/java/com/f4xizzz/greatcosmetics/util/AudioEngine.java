package com.f4xizzz.greatcosmetics.util;

import com.f4xizzz.greatcosmetics.config.SoundConfig;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class AudioEngine {

    public static void playSound(ServerPlayerEntity player, SoundConfig.SoundEntry soundEntry) {
        if (soundEntry == null || soundEntry.id == null || soundEntry.id.isEmpty() || soundEntry.id.equalsIgnoreCase("none")) {
            return; // Permite desativar o som colocando "none" no config
        }

        Identifier soundId = Identifier.tryParse(soundEntry.id);
        if (soundId == null) return;

        SoundEvent event = Registries.SOUND_EVENT.get(soundId);
        if (event != null) {
            // Toca o som apenas para o jogador (Server Side)
            player.networkHandler.sendPacket(
                    new net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket(
                            Registries.SOUND_EVENT.getEntry(event),
                            SoundCategory.PLAYERS,
                            player.getX(), player.getY(), player.getZ(),
                            soundEntry.volume, soundEntry.pitch, player.getRandom().nextLong()
                    )
            );
        }
    }
}