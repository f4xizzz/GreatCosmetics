package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S — pedido de um admin (Dev Studio > Effects) pra persistir a criação/edição de um efeito de
 *  partícula. {@code jsonData} é o EffectData inteiro serializado via Gson. {@code oldId} !=
 *  {@code newId} só quando o efeito foi renomeado. Sem esse payload, criar/editar um efeito só
 *  gravava no EffectConfig.effectsMap e no effects.json do CLIENT que abriu o Dev Studio — o
 *  servidor (que é quem de fato spawna a partícula no tick loop) nunca ficava sabendo do efeito
 *  novo, então cosméticos referenciando esse ID nunca spawnavam nada. */
public record SaveEffectPayload(String oldId, String newId, String jsonData) implements CustomPayload {
    public static final CustomPayload.Id<SaveEffectPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "save_effect"));

    public static final PacketCodec<RegistryByteBuf, SaveEffectPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeString(payload.oldId() != null ? payload.oldId() : "");
                buf.writeString(payload.newId() != null ? payload.newId() : "");
                buf.writeString(payload.jsonData() != null ? payload.jsonData() : "{}");
            },
            buf -> new SaveEffectPayload(buf.readString(), buf.readString(), buf.readString())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
