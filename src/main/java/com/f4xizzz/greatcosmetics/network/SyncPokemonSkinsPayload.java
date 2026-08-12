package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record SyncPokemonSkinsPayload(List<String> unlockedSkins, Map<String, Long> cooldowns) implements CustomPayload {
    public static final CustomPayload.Id<SyncPokemonSkinsPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "sync_pokemon_skins"));

    public static final PacketCodec<RegistryByteBuf, SyncPokemonSkinsPayload> CODEC = PacketCodec.of(SyncPokemonSkinsPayload::write, SyncPokemonSkinsPayload::read);

    // O método deixa de ser estático!
    // O Java vai mapear "SyncPokemonSkinsPayload::write" perfeitamente para o ValueFirstEncoder.
    private void write(RegistryByteBuf buf) {
        buf.writeInt(this.unlockedSkins.size());
        for (String s : this.unlockedSkins) {
            buf.writeString(s);
        }

        buf.writeInt(this.cooldowns.size());
        for (Map.Entry<String, Long> entry : this.cooldowns.entrySet()) {
            buf.writeString(entry.getKey());
            buf.writeLong(entry.getValue());
        }
    }

    private static SyncPokemonSkinsPayload read(RegistryByteBuf buf) {
        int listSize = buf.readInt();
        List<String> list = new ArrayList<>();
        for (int i = 0; i < listSize; i++) {
            list.add(buf.readString());
        }

        int mapSize = buf.readInt();
        Map<String, Long> map = new HashMap<>();
        for (int i = 0; i < mapSize; i++) {
            map.put(buf.readString(), buf.readLong());
        }

        return new SyncPokemonSkinsPayload(list, map);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}