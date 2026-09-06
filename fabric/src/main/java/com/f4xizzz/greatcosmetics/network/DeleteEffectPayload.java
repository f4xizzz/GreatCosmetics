package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S — pedido de um admin (Dev Studio > Effects) pra apagar um efeito de partícula de vez no
 *  servidor (ver SaveEffectPayload pro motivo de precisar ir pro servidor). */
public record DeleteEffectPayload(String id) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DeleteEffectPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "delete_effect"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteEffectPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> buf.writeUtf(payload.id() != null ? payload.id() : ""),
            buf -> new DeleteEffectPayload(buf.readUtf())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
