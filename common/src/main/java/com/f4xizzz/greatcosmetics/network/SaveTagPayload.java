package com.f4xizzz.greatcosmetics.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S — dev cria ou edita uma tag pela aba Dev do TagsPage (id existente = edição). */
public record SaveTagPayload(
        String id, String displayName, String description, String tag,
        List<String> permissions, String minecraftTag
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SaveTagPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "save_tag"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveTagPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeUtf(payload.id());
                buf.writeUtf(payload.displayName());
                buf.writeUtf(payload.description());
                buf.writeUtf(payload.tag());
                buf.writeUtf(payload.minecraftTag());
                buf.writeVarInt(payload.permissions().size());
                for (String permission : payload.permissions()) buf.writeUtf(permission);
            },
            buf -> {
                String id = buf.readUtf();
                String displayName = buf.readUtf();
                String description = buf.readUtf();
                String tag = buf.readUtf();
                String minecraftTag = buf.readUtf();
                int size = buf.readVarInt();
                List<String> permissions = new ArrayList<>();
                for (int i = 0; i < size; i++) permissions.add(buf.readUtf());
                return new SaveTagPayload(id, displayName, description, tag, permissions, minecraftTag);
            }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
