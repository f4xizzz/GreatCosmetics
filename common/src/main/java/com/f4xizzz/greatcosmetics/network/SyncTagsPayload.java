package com.f4xizzz.greatcosmetics.network;

import com.f4xizzz.greatcosmetics.config.TagData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** S2C — catálogo completo de tags (de grupo + criadas na GUI). */
public record SyncTagsPayload(Map<String, TagData> tagsMap) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncTagsPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "sync_tags"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncTagsPayload> CODEC = StreamCodec.ofMember(
            SyncTagsPayload::write,
            SyncTagsPayload::read
    );

    private static void write(SyncTagsPayload payload, RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(payload.tagsMap.size());
        for (Map.Entry<String, TagData> entry : payload.tagsMap.entrySet()) {
            TagData data = entry.getValue();
            buf.writeUtf(entry.getKey());
            buf.writeUtf(data.displayName != null ? data.displayName : "");
            buf.writeUtf(data.description != null ? data.description : "");
            buf.writeUtf(data.tag != null ? data.tag : "");
            buf.writeUtf(data.minecraftTag != null ? data.minecraftTag : "");
            buf.writeBoolean(data.isGroupTag);
            buf.writeInt(data.weight);

            List<String> permissions = data.permissions != null ? data.permissions : List.of();
            buf.writeVarInt(permissions.size());
            for (String permission : permissions) {
                buf.writeUtf(permission);
            }
        }
    }

    private static SyncTagsPayload read(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<String, TagData> map = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String id = buf.readUtf();
            TagData data = new TagData();
            data.id = id;
            data.displayName = buf.readUtf();
            data.description = buf.readUtf();
            data.tag = buf.readUtf();
            data.minecraftTag = buf.readUtf();
            data.isGroupTag = buf.readBoolean();
            data.weight = buf.readInt();

            int permCount = buf.readVarInt();
            List<String> permissions = new ArrayList<>();
            for (int j = 0; j < permCount; j++) permissions.add(buf.readUtf());
            data.permissions = permissions;

            map.put(id, data);
        }
        return new SyncTagsPayload(map);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
