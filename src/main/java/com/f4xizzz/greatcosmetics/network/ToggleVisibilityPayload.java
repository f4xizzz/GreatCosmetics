package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ToggleVisibilityPayload(String setting, boolean state) implements CustomPayload {

    public static final CustomPayload.Id<ToggleVisibilityPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "toggle_visibility"));

    public static final PacketCodec<RegistryByteBuf, ToggleVisibilityPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeString(payload.setting());
                buf.writeBoolean(payload.state());
            },
            buf -> new ToggleVisibilityPayload(buf.readString(), buf.readBoolean())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}