package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S — mandado ao clicar numa seta de página da mochila (ver ClientBackpackState/mixin de
 *  render do baú). O servidor é quem valida de verdade (jogador ainda com a mochila equipada,
 *  newPage dentro de [0, backpackPages-1]) — newPage aqui é só um PEDIDO. */
public record SwitchBackpackPagePayload(String cosmeticId, int newPage) implements CustomPayload {
    public static final Id<SwitchBackpackPagePayload> ID = new Id<>(Identifier.of("greatcosmetics", "switch_backpack_page"));

    public static final PacketCodec<RegistryByteBuf, SwitchBackpackPagePayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeString(payload.cosmeticId);
                buf.writeVarInt(payload.newPage);
            },
            buf -> new SwitchBackpackPagePayload(buf.readString(), buf.readVarInt())
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
