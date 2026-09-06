package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ToggleVisibilityPayload(String setting, boolean state) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToggleVisibilityPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "toggle_visibility"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleVisibilityPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeUtf(payload.setting());
                buf.writeBoolean(payload.state());
            },
            buf -> new ToggleVisibilityPayload(buf.readUtf(), buf.readBoolean())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}