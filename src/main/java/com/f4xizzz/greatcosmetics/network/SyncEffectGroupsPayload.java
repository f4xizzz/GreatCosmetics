package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C — catálogo completo de grupos de efeito ({@code EffectGroupConfig.groupsMap} como JSON).
 *  Mandado no JOIN e sempre que um admin salva/apaga um grupo pelo Dev Studio. Espelha
 *  {@link SyncEffectsPayload}. */
public record SyncEffectGroupsPayload(String jsonData) implements CustomPayload {
    public static final CustomPayload.Id<SyncEffectGroupsPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "sync_effect_groups"));

    public static final PacketCodec<RegistryByteBuf, SyncEffectGroupsPayload> CODEC = PacketCodec.of(
            (payload, buf) -> buf.writeString(payload.jsonData() != null ? payload.jsonData() : "{}"),
            buf -> new SyncEffectGroupsPayload(buf.readString())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
