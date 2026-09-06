package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S — clique numa tag no TagsPage: equipa (ou desequipa, se já for a atual).
 * {@code devMode} é a "chavinha de Dev" (ClientCosmeticCache.isDevModeActive) pedindo pra
 * ignorar a posse da tag — o servidor SEMPRE reconfere a permissão antes de confiar nisso.
 */
public record EquipTagPayload(String tagId, boolean devMode) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EquipTagPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "equip_tag"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EquipTagPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeUtf(payload.tagId());
                buf.writeBoolean(payload.devMode());
            },
            buf -> new EquipTagPayload(buf.readUtf(), buf.readBoolean())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
