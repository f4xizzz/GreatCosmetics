package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record EquipPokemonSkinPayload(int slot, String skinId) implements CustomPayload {
    public static final CustomPayload.Id<EquipPokemonSkinPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "equip_pokemon_skin"));

    public static final PacketCodec<RegistryByteBuf, EquipPokemonSkinPayload> CODEC = PacketCodec.of(EquipPokemonSkinPayload::write, EquipPokemonSkinPayload::read);

    private void write(RegistryByteBuf buf) {
        buf.writeInt(this.slot);
        buf.writeString(this.skinId);
    }

    private static EquipPokemonSkinPayload read(RegistryByteBuf buf) {
        int slot = buf.readInt();
        String skinId = buf.readString();
        return new EquipPokemonSkinPayload(slot, skinId);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}