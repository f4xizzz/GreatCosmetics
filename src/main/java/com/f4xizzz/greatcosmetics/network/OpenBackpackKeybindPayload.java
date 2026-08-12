package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record OpenBackpackKeybindPayload() implements CustomPayload {
    public static final Id<OpenBackpackKeybindPayload> ID = new Id<>(Identifier.of("greatcosmetics", "open_backpack_keybind"));
    public static final PacketCodec<RegistryByteBuf, OpenBackpackKeybindPayload> CODEC = PacketCodec.unit(new OpenBackpackKeybindPayload());

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}