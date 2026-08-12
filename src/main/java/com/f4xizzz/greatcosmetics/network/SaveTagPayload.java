package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** C2S — dev cria ou edita uma tag pela aba Dev do TagsPage (id existente = edição). */
public record SaveTagPayload(
        String id, String displayName, String description, String tag,
        List<String> permissions, String minecraftTag
) implements CustomPayload {

    public static final CustomPayload.Id<SaveTagPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "save_tag"));

    public static final PacketCodec<RegistryByteBuf, SaveTagPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeString(payload.id());
                buf.writeString(payload.displayName());
                buf.writeString(payload.description());
                buf.writeString(payload.tag());
                buf.writeString(payload.minecraftTag());
                buf.writeVarInt(payload.permissions().size());
                for (String permission : payload.permissions()) buf.writeString(permission);
            },
            buf -> {
                String id = buf.readString();
                String displayName = buf.readString();
                String description = buf.readString();
                String tag = buf.readString();
                String minecraftTag = buf.readString();
                int size = buf.readVarInt();
                List<String> permissions = new ArrayList<>();
                for (int i = 0; i < size; i++) permissions.add(buf.readString());
                return new SaveTagPayload(id, displayName, description, tag, permissions, minecraftTag);
            }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
