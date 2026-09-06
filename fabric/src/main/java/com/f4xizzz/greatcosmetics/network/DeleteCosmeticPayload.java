package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S — pedido de um admin (Dev Studio) pra apagar um cosmético (normal ou armadura convertida)
 *  de vez, tanto do servidor quanto do disco. Ver SaveCosmeticPayload pro fluxo de criar/editar. */
public record DeleteCosmeticPayload(String id, boolean isArmor) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DeleteCosmeticPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "delete_cosmetic"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteCosmeticPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> { buf.writeUtf(payload.id()); buf.writeBoolean(payload.isArmor()); },
            buf -> new DeleteCosmeticPayload(buf.readUtf(), buf.readBoolean())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
