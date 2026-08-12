package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S — clique numa tag no TagsPage: equipa (ou desequipa, se já for a atual).
 * {@code devMode} é a "chavinha de Dev" (ClientCosmeticCache.isDevModeActive) pedindo pra
 * ignorar a posse da tag — o servidor SEMPRE reconfere a permissão antes de confiar nisso.
 */
public record EquipTagPayload(String tagId, boolean devMode) implements CustomPayload {

    public static final CustomPayload.Id<EquipTagPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "equip_tag"));

    public static final PacketCodec<RegistryByteBuf, EquipTagPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeString(payload.tagId());
                buf.writeBoolean(payload.devMode());
            },
            buf -> new EquipTagPayload(buf.readString(), buf.readBoolean())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
