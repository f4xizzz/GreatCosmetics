package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** S2C — quais tags o jogador local possui e qual está equipada agora (String vazia = nenhuma). */
public record SyncPlayerTagsPayload(UUID playerUuid, List<String> ownedTagIds, String equippedTagId) implements CustomPayload {

    public static final CustomPayload.Id<SyncPlayerTagsPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "sync_player_tags"));

    public static final PacketCodec<RegistryByteBuf, SyncPlayerTagsPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeUuid(payload.playerUuid());
                buf.writeString(payload.equippedTagId() != null ? payload.equippedTagId() : "");
                buf.writeVarInt(payload.ownedTagIds().size());
                for (String id : payload.ownedTagIds()) {
                    buf.writeString(id);
                }
            },
            buf -> {
                UUID uuid = buf.readUuid();
                String equipped = buf.readString();
                int size = buf.readVarInt();
                List<String> owned = new ArrayList<>();
                for (int i = 0; i < size; i++) owned.add(buf.readString());
                return new SyncPlayerTagsPayload(uuid, owned, equipped.isEmpty() ? null : equipped);
            }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
