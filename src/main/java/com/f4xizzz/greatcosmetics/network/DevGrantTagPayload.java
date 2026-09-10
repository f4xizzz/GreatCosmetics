package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S — o DEV (perm {@code gc.dev}), com o Dev Mode ligado na aba Tags, pede pra RECEBER de verdade
 * a tag/grupo que está testando, mesmo sem ter. {@code set = false} → ADD (entra no grupo, mantém
 * os outros / ganha a posse da tag custom + equipa). {@code set = true} → SET (só pra tag de
 * GRUPO: {@code /lp user X parent set} — perde todos os outros grupos).
 */
public record DevGrantTagPayload(String tagId, boolean set) implements CustomPayload {
    public static final Id<DevGrantTagPayload> ID = new Id<>(Identifier.of("greatcosmetics", "dev_grant_tag"));

    public static final PacketCodec<RegistryByteBuf, DevGrantTagPayload> CODEC = PacketCodec.of(
            (payload, buf) -> { buf.writeString(payload.tagId()); buf.writeBoolean(payload.set()); },
            buf -> new DevGrantTagPayload(buf.readString(), buf.readBoolean())
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
