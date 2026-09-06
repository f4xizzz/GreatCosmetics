package com.f4xizzz.greatcosmetics.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** S2C — quais tags o jogador local possui e qual está equipada agora (String vazia = nenhuma). */
public record SyncPlayerTagsPayload(UUID playerUuid, List<String> ownedTagIds, String equippedTagId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncPlayerTagsPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "sync_player_tags"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerTagsPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeUUID(payload.playerUuid());
                buf.writeUtf(payload.equippedTagId() != null ? payload.equippedTagId() : "");
                buf.writeVarInt(payload.ownedTagIds().size());
                for (String id : payload.ownedTagIds()) {
                    buf.writeUtf(id);
                }
            },
            buf -> {
                UUID uuid = buf.readUUID();
                String equipped = buf.readUtf();
                int size = buf.readVarInt();
                List<String> owned = new ArrayList<>();
                for (int i = 0; i < size; i++) owned.add(buf.readUtf());
                return new SyncPlayerTagsPayload(uuid, owned, equipped.isEmpty() ? null : equipped);
            }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
