package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CloseWardrobePayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CloseWardrobePayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "close_wardrobe"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CloseWardrobePayload> CODEC = StreamCodec.unit(new CloseWardrobePayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}