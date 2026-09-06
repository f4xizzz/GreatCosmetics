package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClearPokemonSkinsPayload(int slot) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClearPokemonSkinsPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "clear_pokemon_skins"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClearPokemonSkinsPayload> CODEC = StreamCodec.ofMember(ClearPokemonSkinsPayload::write, ClearPokemonSkinsPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(this.slot);
    }

    private static ClearPokemonSkinsPayload read(RegistryFriendlyByteBuf buf) {
        return new ClearPokemonSkinsPayload(buf.readInt());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}