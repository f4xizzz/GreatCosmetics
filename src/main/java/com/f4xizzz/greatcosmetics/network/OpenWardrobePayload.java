package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record OpenWardrobePayload(boolean hasBackground, boolean hasAllUnlocked, List<String> unlockedIds) implements CustomPayload {

    public static final CustomPayload.Id<OpenWardrobePayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "open_wardrobe"));

    public static final PacketCodec<RegistryByteBuf, OpenWardrobePayload> CODEC = PacketCodec.of(
            // ESCREVER (Servidor -> Cliente)
            (payload, buf) -> {
                buf.writeBoolean(payload.hasBackground());
                buf.writeBoolean(payload.hasAllUnlocked());

                // Escreve o tamanho da lista e depois cada ID
                buf.writeInt(payload.unlockedIds().size());
                for (String id : payload.unlockedIds()) {
                    buf.writeString(id);
                }
            },
            // LER (Cliente recebe)
            buf -> {
                boolean hasBg = buf.readBoolean();
                boolean hasAll = buf.readBoolean();

                int size = buf.readInt();
                List<String> ids = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    ids.add(buf.readString());
                }

                return new OpenWardrobePayload(hasBg, hasAll, ids);
            }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}