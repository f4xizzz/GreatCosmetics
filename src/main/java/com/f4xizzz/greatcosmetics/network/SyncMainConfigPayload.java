package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C — snapshot completo do mainconfig.conf (slots/types/server config) em JSON. Mandado no
 *  join, no /gc reload, e toda vez que um admin salva uma edição pelo Dev Studio (ver
 *  SaveMainConfigPayload) — sem isso, o mainconfig.conf só existia de verdade no disco do
 *  SERVIDOR, e o client (numa conexão remota de verdade) nunca tinha como saber o valor atual de
 *  slots/limites/tipos, nem refletir edições feitas por outro admin. */
public record SyncMainConfigPayload(String configJson) implements CustomPayload {
    public static final CustomPayload.Id<SyncMainConfigPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "sync_main_config"));

    public static final PacketCodec<RegistryByteBuf, SyncMainConfigPayload> CODEC = PacketCodec.of(SyncMainConfigPayload::write, SyncMainConfigPayload::read);

    private void write(RegistryByteBuf buf) {
        buf.writeString(this.configJson != null ? this.configJson : "{}");
    }

    private static SyncMainConfigPayload read(RegistryByteBuf buf) {
        return new SyncMainConfigPayload(buf.readString());
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
