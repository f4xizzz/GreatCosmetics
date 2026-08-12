package com.f4xizzz.greatcosmetics.network;

import com.f4xizzz.greatcosmetics.config.TagData;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** S2C — catálogo completo de tags (de grupo + criadas na GUI). */
public record SyncTagsPayload(Map<String, TagData> tagsMap) implements CustomPayload {

    public static final CustomPayload.Id<SyncTagsPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "sync_tags"));

    public static final PacketCodec<RegistryByteBuf, SyncTagsPayload> CODEC = PacketCodec.of(
            SyncTagsPayload::write,
            SyncTagsPayload::read
    );

    private static void write(SyncTagsPayload payload, RegistryByteBuf buf) {
        buf.writeVarInt(payload.tagsMap.size());
        for (Map.Entry<String, TagData> entry : payload.tagsMap.entrySet()) {
            TagData data = entry.getValue();
            buf.writeString(entry.getKey());
            buf.writeString(data.displayName != null ? data.displayName : "");
            buf.writeString(data.description != null ? data.description : "");
            buf.writeString(data.tag != null ? data.tag : "");
            buf.writeString(data.minecraftTag != null ? data.minecraftTag : "");
            buf.writeBoolean(data.isGroupTag);
            buf.writeInt(data.weight);

            List<String> permissions = data.permissions != null ? data.permissions : List.of();
            buf.writeVarInt(permissions.size());
            for (String permission : permissions) {
                buf.writeString(permission);
            }
        }
    }

    private static SyncTagsPayload read(RegistryByteBuf buf) {
        int size = buf.readVarInt();
        Map<String, TagData> map = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String id = buf.readString();
            TagData data = new TagData();
            data.id = id;
            data.displayName = buf.readString();
            data.description = buf.readString();
            data.tag = buf.readString();
            data.minecraftTag = buf.readString();
            data.isGroupTag = buf.readBoolean();
            data.weight = buf.readInt();

            int permCount = buf.readVarInt();
            List<String> permissions = new ArrayList<>();
            for (int j = 0; j < permCount; j++) permissions.add(buf.readString());
            data.permissions = permissions;

            map.put(id, data);
        }
        return new SyncTagsPayload(map);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
