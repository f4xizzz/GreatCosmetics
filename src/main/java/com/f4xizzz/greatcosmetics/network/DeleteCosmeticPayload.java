package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S — pedido de um admin (Dev Studio) pra apagar um cosmético (normal ou armadura convertida)
 *  de vez, tanto do servidor quanto do disco. Ver SaveCosmeticPayload pro fluxo de criar/editar. */
public record DeleteCosmeticPayload(String id, boolean isArmor) implements CustomPayload {
    public static final CustomPayload.Id<DeleteCosmeticPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "delete_cosmetic"));

    public static final PacketCodec<RegistryByteBuf, DeleteCosmeticPayload> CODEC = PacketCodec.of(
            (payload, buf) -> { buf.writeString(payload.id()); buf.writeBoolean(payload.isArmor()); },
            buf -> new DeleteCosmeticPayload(buf.readString(), buf.readBoolean())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
