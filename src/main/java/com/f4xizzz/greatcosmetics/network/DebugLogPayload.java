package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

// C2S: encaminha uma linha de debug do lado cliente (GUI de animação, mixin de render) para
// aparecer no console do servidor. Só é enviado pelo cliente quando o modo debug está ligado,
// e o servidor só imprime se o SEU próprio isDebugMode também estiver ligado.
public record DebugLogPayload(String message) implements CustomPayload {

    public static final CustomPayload.Id<DebugLogPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "debug_log"));

    public static final PacketCodec<RegistryByteBuf, DebugLogPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, DebugLogPayload::message,
            DebugLogPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
