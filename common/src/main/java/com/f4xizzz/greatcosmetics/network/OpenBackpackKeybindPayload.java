package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenBackpackKeybindPayload() implements CustomPacketPayload {
    public static final Type<OpenBackpackKeybindPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "open_backpack_keybind"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBackpackKeybindPayload> CODEC = StreamCodec.unit(new OpenBackpackKeybindPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}