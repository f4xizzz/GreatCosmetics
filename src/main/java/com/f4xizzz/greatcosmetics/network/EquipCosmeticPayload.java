package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record EquipCosmeticPayload(String cosmeticId, boolean isDevMode) implements CustomPayload {

    public static final CustomPayload.Id<EquipCosmeticPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "equip_cosmetic"));

    public static final PacketCodec<RegistryByteBuf, EquipCosmeticPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeString(payload.cosmeticId());
                buf.writeBoolean(payload.isDevMode());
            },
            buf -> new EquipCosmeticPayload(buf.readString(), buf.readBoolean())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}