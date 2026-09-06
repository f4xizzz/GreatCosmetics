package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EquipPokemonSkinPayload(int slot, String skinId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EquipPokemonSkinPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "equip_pokemon_skin"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EquipPokemonSkinPayload> CODEC = StreamCodec.ofMember(EquipPokemonSkinPayload::write, EquipPokemonSkinPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(this.slot);
        buf.writeUtf(this.skinId);
    }

    private static EquipPokemonSkinPayload read(RegistryFriendlyByteBuf buf) {
        int slot = buf.readInt();
        String skinId = buf.readUtf();
        return new EquipPokemonSkinPayload(slot, skinId);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}