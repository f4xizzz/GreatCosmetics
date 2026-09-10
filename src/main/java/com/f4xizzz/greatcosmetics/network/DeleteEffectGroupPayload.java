package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S — admin apaga um grupo de efeito (não apaga os efeitos membros). Espelha
 *  {@link DeleteEffectPayload}. */
public record DeleteEffectGroupPayload(String id) implements CustomPayload {
    public static final CustomPayload.Id<DeleteEffectGroupPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "delete_effect_group"));

    public static final PacketCodec<RegistryByteBuf, DeleteEffectGroupPayload> CODEC = PacketCodec.of(
            (payload, buf) -> buf.writeString(payload.id() != null ? payload.id() : ""),
            buf -> new DeleteEffectGroupPayload(buf.readString())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
