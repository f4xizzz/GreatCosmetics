package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// C2S: encaminha uma linha de debug do lado cliente (GUI de animação, mixin de render) para
// aparecer no console do servidor. Só é enviado pelo cliente quando o modo debug está ligado,
// e o servidor só imprime se o SEU próprio isDebugMode também estiver ligado.
public record DebugLogPayload(String message) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DebugLogPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "debug_log"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DebugLogPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DebugLogPayload::message,
            DebugLogPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
