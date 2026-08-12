package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C — mandado logo depois que o servidor abre (ou troca de página d)e uma mochila-cosmético
 *  pro jogador. O client precisa saber qual cosmeticId/página/total de páginas está vendo pra
 *  desenhar as setas + texto "Página X/Y" por cima do baú vanilla (ver ClientBackpackState) —
 *  GenericContainerScreenHandler é 100% genérico, não carrega nenhum dado nosso sozinho. */
public record BackpackPageInfoPayload(String cosmeticId, int page, int totalPages) implements CustomPayload {
    public static final Id<BackpackPageInfoPayload> ID = new Id<>(Identifier.of("greatcosmetics", "backpack_page_info"));

    public static final PacketCodec<RegistryByteBuf, BackpackPageInfoPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeString(payload.cosmeticId);
                buf.writeVarInt(payload.page);
                buf.writeVarInt(payload.totalPages);
            },
            buf -> new BackpackPageInfoPayload(buf.readString(), buf.readVarInt(), buf.readVarInt())
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
