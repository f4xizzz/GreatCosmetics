package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C — catálogo completo de efeitos de partícula (EffectConfig.effectsMap inteiro, como JSON via
 *  Gson pra não depender de escrever/ler campo-a-campo em ordem fixa — EffectData é só um POJO de
 *  config, sem necessidade de performance de payload binário). Mandado no JOIN e sempre que um
 *  admin salva/apaga um efeito pelo Dev Studio (ver SaveEffectPayload/DeleteEffectPayload), pra
 *  todo client (inclusive o que editou) ficar com o catálogo idêntico ao do servidor. */
public record SyncEffectsPayload(String jsonData) implements CustomPayload {
    public static final CustomPayload.Id<SyncEffectsPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "sync_effects"));

    public static final PacketCodec<RegistryByteBuf, SyncEffectsPayload> CODEC = PacketCodec.of(
            (payload, buf) -> buf.writeString(payload.jsonData() != null ? payload.jsonData() : "{}"),
            buf -> new SyncEffectsPayload(buf.readString())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
