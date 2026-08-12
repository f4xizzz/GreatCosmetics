package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S — pedido de um admin (Dev Studio > Effects) pra apagar um efeito de partícula de vez no
 *  servidor (ver SaveEffectPayload pro motivo de precisar ir pro servidor). */
public record DeleteEffectPayload(String id) implements CustomPayload {
    public static final CustomPayload.Id<DeleteEffectPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "delete_effect"));

    public static final PacketCodec<RegistryByteBuf, DeleteEffectPayload> CODEC = PacketCodec.of(
            (payload, buf) -> buf.writeString(payload.id() != null ? payload.id() : ""),
            buf -> new DeleteEffectPayload(buf.readString())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
