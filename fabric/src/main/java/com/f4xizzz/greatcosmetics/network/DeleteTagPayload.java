package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S — dev apaga uma tag pela aba Dev do TagsPage (recusado no servidor se for tag de grupo). */
public record DeleteTagPayload(String id) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DeleteTagPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "delete_tag"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteTagPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> buf.writeUtf(payload.id()),
            buf -> new DeleteTagPayload(buf.readUtf())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
