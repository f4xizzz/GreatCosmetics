package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S — admin (Dev Studio > Effects > grupo) cria/edita um grupo de efeito. {@code jsonData} é o
 *  EffectGroupData inteiro via Gson. {@code oldId} != {@code newId} só ao renomear. Espelha
 *  {@link SaveEffectPayload}. */
public record SaveEffectGroupPayload(String oldId, String newId, String jsonData) implements CustomPayload {
    public static final CustomPayload.Id<SaveEffectGroupPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "save_effect_group"));

    public static final PacketCodec<RegistryByteBuf, SaveEffectGroupPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeString(payload.oldId() != null ? payload.oldId() : "");
                buf.writeString(payload.newId() != null ? payload.newId() : "");
                buf.writeString(payload.jsonData() != null ? payload.jsonData() : "{}");
            },
            buf -> new SaveEffectGroupPayload(buf.readString(), buf.readString(), buf.readString())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
