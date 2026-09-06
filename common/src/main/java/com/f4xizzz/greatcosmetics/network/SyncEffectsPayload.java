package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** S2C — catálogo completo de efeitos de partícula (EffectConfig.effectsMap inteiro, como JSON via
 *  Gson pra não depender de escrever/ler campo-a-campo em ordem fixa — EffectData é só um POJO de
 *  config, sem necessidade de performance de payload binário). Mandado no JOIN e sempre que um
 *  admin salva/apaga um efeito pelo Dev Studio (ver SaveEffectPayload/DeleteEffectPayload), pra
 *  todo client (inclusive o que editou) ficar com o catálogo idêntico ao do servidor. */
public record SyncEffectsPayload(String jsonData) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncEffectsPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "sync_effects"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncEffectsPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> buf.writeUtf(payload.jsonData() != null ? payload.jsonData() : "{}"),
            buf -> new SyncEffectsPayload(buf.readUtf())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
