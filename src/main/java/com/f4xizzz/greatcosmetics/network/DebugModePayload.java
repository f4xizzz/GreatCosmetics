package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

// S2C: avisa o cliente se o modo debug do servidor está ligado ou desligado (enviado ao
// alternar via /gc debug e também no join, pra quem entra depois já ficar sincronizado).
public record DebugModePayload(boolean enabled) implements CustomPayload {

    public static final CustomPayload.Id<DebugModePayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "debug_mode"));

    public static final PacketCodec<RegistryByteBuf, DebugModePayload> CODEC = PacketCodec.of(
            (payload, buf) -> buf.writeBoolean(payload.enabled()),
            buf -> new DebugModePayload(buf.readBoolean())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
