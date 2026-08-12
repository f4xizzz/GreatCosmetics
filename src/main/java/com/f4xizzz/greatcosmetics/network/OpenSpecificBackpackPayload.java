package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

// OLHA O "(String cosmeticId)" AQUI ABAIXO! É ele que resolve esse erro.
public record OpenSpecificBackpackPayload(String cosmeticId) implements CustomPayload {
    public static final Id<OpenSpecificBackpackPayload> ID = new Id<>(Identifier.of("greatcosmetics", "open_specific_backpack"));

    public static final PacketCodec<RegistryByteBuf, OpenSpecificBackpackPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, OpenSpecificBackpackPayload::cosmeticId,
            OpenSpecificBackpackPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}