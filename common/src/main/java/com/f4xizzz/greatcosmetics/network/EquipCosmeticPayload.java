package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EquipCosmeticPayload(String cosmeticId, boolean isDevMode) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EquipCosmeticPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "equip_cosmetic"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EquipCosmeticPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeUtf(payload.cosmeticId());
                buf.writeBoolean(payload.isDevMode());
            },
            buf -> new EquipCosmeticPayload(buf.readUtf(), buf.readBoolean())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}