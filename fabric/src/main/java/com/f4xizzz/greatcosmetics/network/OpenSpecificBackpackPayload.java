package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// OLHA O "(String cosmeticId)" AQUI ABAIXO! É ele que resolve esse erro.
public record OpenSpecificBackpackPayload(String cosmeticId) implements CustomPacketPayload {
    public static final Type<OpenSpecificBackpackPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "open_specific_backpack"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenSpecificBackpackPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, OpenSpecificBackpackPayload::cosmeticId,
            OpenSpecificBackpackPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}