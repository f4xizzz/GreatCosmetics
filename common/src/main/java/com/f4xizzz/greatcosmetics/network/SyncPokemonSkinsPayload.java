package com.f4xizzz.greatcosmetics.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncPokemonSkinsPayload(List<String> unlockedSkins, Map<String, Long> cooldowns) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncPokemonSkinsPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "sync_pokemon_skins"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPokemonSkinsPayload> CODEC = StreamCodec.ofMember(SyncPokemonSkinsPayload::write, SyncPokemonSkinsPayload::read);

    // O método deixa de ser estático!
    // O Java vai mapear "SyncPokemonSkinsPayload::write" perfeitamente para o ValueFirstEncoder.
    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(this.unlockedSkins.size());
        for (String s : this.unlockedSkins) {
            buf.writeUtf(s);
        }

        buf.writeInt(this.cooldowns.size());
        for (Map.Entry<String, Long> entry : this.cooldowns.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeLong(entry.getValue());
        }
    }

    private static SyncPokemonSkinsPayload read(RegistryFriendlyByteBuf buf) {
        int listSize = buf.readInt();
        List<String> list = new ArrayList<>();
        for (int i = 0; i < listSize; i++) {
            list.add(buf.readUtf());
        }

        int mapSize = buf.readInt();
        Map<String, Long> map = new HashMap<>();
        for (int i = 0; i < mapSize; i++) {
            map.put(buf.readUtf(), buf.readLong());
        }

        return new SyncPokemonSkinsPayload(list, map);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}