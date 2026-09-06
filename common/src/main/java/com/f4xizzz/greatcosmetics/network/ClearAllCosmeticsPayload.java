package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClearAllCosmeticsPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClearAllCosmeticsPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "clear_all_cosmetics"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClearAllCosmeticsPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {},
            buf -> new ClearAllCosmeticsPayload()
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}