package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CloseWardrobePayload() implements CustomPayload {
    public static final CustomPayload.Id<CloseWardrobePayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "close_wardrobe"));
    public static final PacketCodec<RegistryByteBuf, CloseWardrobePayload> CODEC = PacketCodec.unit(new CloseWardrobePayload());

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}