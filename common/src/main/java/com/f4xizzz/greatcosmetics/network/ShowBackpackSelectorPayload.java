package com.f4xizzz.greatcosmetics.network;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShowBackpackSelectorPayload(List<String> backpackIds) implements CustomPacketPayload {
    public static final Type<ShowBackpackSelectorPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "show_backpack_selector"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShowBackpackSelectorPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ShowBackpackSelectorPayload::backpackIds,
            ShowBackpackSelectorPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}