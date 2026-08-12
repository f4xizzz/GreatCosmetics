package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S — dev apaga uma tag pela aba Dev do TagsPage (recusado no servidor se for tag de grupo). */
public record DeleteTagPayload(String id) implements CustomPayload {

    public static final CustomPayload.Id<DeleteTagPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "delete_tag"));

    public static final PacketCodec<RegistryByteBuf, DeleteTagPayload> CODEC = PacketCodec.of(
            (payload, buf) -> buf.writeString(payload.id()),
            buf -> new DeleteTagPayload(buf.readString())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
