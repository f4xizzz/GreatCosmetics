package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.List;

public record ShowBackpackSelectorPayload(List<String> backpackIds) implements CustomPayload {
    public static final Id<ShowBackpackSelectorPayload> ID = new Id<>(Identifier.of("greatcosmetics", "show_backpack_selector"));

    public static final PacketCodec<RegistryByteBuf, ShowBackpackSelectorPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING.collect(PacketCodecs.toList()), ShowBackpackSelectorPayload::backpackIds,
            ShowBackpackSelectorPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}