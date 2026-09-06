package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S — pedido de um admin (Dev Studio > Effects) pra persistir a criação/edição de um efeito de
 *  partícula. {@code jsonData} é o EffectData inteiro serializado via Gson. {@code oldId} !=
 *  {@code newId} só quando o efeito foi renomeado. Sem esse payload, criar/editar um efeito só
 *  gravava no EffectConfig.effectsMap e no effects.json do CLIENT que abriu o Dev Studio — o
 *  servidor (que é quem de fato spawna a partícula no tick loop) nunca ficava sabendo do efeito
 *  novo, então cosméticos referenciando esse ID nunca spawnavam nada. */
public record SaveEffectPayload(String oldId, String newId, String jsonData) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SaveEffectPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "save_effect"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveEffectPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeUtf(payload.oldId() != null ? payload.oldId() : "");
                buf.writeUtf(payload.newId() != null ? payload.newId() : "");
                buf.writeUtf(payload.jsonData() != null ? payload.jsonData() : "{}");
            },
            buf -> new SaveEffectPayload(buf.readUtf(), buf.readUtf(), buf.readUtf())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
