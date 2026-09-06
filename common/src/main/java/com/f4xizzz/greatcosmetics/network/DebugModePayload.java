package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// S2C: avisa o cliente se o modo debug do servidor está ligado ou desligado (enviado ao
// alternar via /gc debug e também no join, pra quem entra depois já ficar sincronizado).
public record DebugModePayload(boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DebugModePayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "debug_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DebugModePayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> buf.writeBoolean(payload.enabled()),
            buf -> new DebugModePayload(buf.readBoolean())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
