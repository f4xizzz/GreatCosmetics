package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ClearPokemonSkinsPayload(int slot) implements CustomPayload {
    public static final CustomPayload.Id<ClearPokemonSkinsPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "clear_pokemon_skins"));

    public static final PacketCodec<RegistryByteBuf, ClearPokemonSkinsPayload> CODEC = PacketCodec.of(ClearPokemonSkinsPayload::write, ClearPokemonSkinsPayload::read);

    private void write(RegistryByteBuf buf) {
        buf.writeInt(this.slot);
    }

    private static ClearPokemonSkinsPayload read(RegistryByteBuf buf) {
        return new ClearPokemonSkinsPayload(buf.readInt());
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}