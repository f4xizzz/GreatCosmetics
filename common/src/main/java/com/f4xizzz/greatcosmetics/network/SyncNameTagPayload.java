package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C — prefixo/sufixo do LuckPerms do próprio jogador (formatados com '&'/'§'), usados pra montar
 * o nametag exibido acima da cabeça do jogador na aba Party do wardrobe. LuckPerms só existe no
 * servidor, então o client precisa receber esse texto já pronto. Mandado quando o wardrobe abre.
 */
public record SyncNameTagPayload(String prefix, String suffix) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncNameTagPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "sync_name_tag"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncNameTagPayload> CODEC = StreamCodec.ofMember(SyncNameTagPayload::write, SyncNameTagPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(this.prefix != null ? this.prefix : "");
        buf.writeUtf(this.suffix != null ? this.suffix : "");
    }

    private static SyncNameTagPayload read(RegistryFriendlyByteBuf buf) {
        return new SyncNameTagPayload(buf.readUtf(), buf.readUtf());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
