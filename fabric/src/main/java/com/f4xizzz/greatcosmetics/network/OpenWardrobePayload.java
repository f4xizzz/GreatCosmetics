package com.f4xizzz.greatcosmetics.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenWardrobePayload(boolean hasBackground, boolean hasAllUnlocked, List<String> unlockedIds) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenWardrobePayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "open_wardrobe"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenWardrobePayload> CODEC = StreamCodec.ofMember(
            // ESCREVER (Servidor -> Cliente)
            (payload, buf) -> {
                buf.writeBoolean(payload.hasBackground());
                buf.writeBoolean(payload.hasAllUnlocked());

                // Escreve o tamanho da lista e depois cada ID
                buf.writeInt(payload.unlockedIds().size());
                for (String id : payload.unlockedIds()) {
                    buf.writeUtf(id);
                }
            },
            // LER (Cliente recebe)
            buf -> {
                boolean hasBg = buf.readBoolean();
                boolean hasAll = buf.readBoolean();

                int size = buf.readInt();
                List<String> ids = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    ids.add(buf.readUtf());
                }

                return new OpenWardrobePayload(hasBg, hasAll, ids);
            }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}