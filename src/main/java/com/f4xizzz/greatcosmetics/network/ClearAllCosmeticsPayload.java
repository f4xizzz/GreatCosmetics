package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ClearAllCosmeticsPayload() implements CustomPayload {
    public static final CustomPayload.Id<ClearAllCosmeticsPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "clear_all_cosmetics"));

    public static final PacketCodec<RegistryByteBuf, ClearAllCosmeticsPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {},
            buf -> new ClearAllCosmeticsPayload()
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}