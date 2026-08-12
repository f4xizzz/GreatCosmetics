package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C — prefixo/sufixo do LuckPerms do próprio jogador (formatados com '&'/'§'), usados pra montar
 * o nametag exibido acima da cabeça do jogador na aba Party do wardrobe. LuckPerms só existe no
 * servidor, então o client precisa receber esse texto já pronto. Mandado quando o wardrobe abre.
 */
public record SyncNameTagPayload(String prefix, String suffix) implements CustomPayload {
    public static final CustomPayload.Id<SyncNameTagPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "sync_name_tag"));

    public static final PacketCodec<RegistryByteBuf, SyncNameTagPayload> CODEC = PacketCodec.of(SyncNameTagPayload::write, SyncNameTagPayload::read);

    private void write(RegistryByteBuf buf) {
        buf.writeString(this.prefix != null ? this.prefix : "");
        buf.writeString(this.suffix != null ? this.suffix : "");
    }

    private static SyncNameTagPayload read(RegistryByteBuf buf) {
        return new SyncNameTagPayload(buf.readString(), buf.readString());
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
