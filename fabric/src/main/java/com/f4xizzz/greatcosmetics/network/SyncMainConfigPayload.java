package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** S2C — snapshot completo do mainconfig.conf (slots/types/server config) em JSON. Mandado no
 *  join, no /gc reload, e toda vez que um admin salva uma edição pelo Dev Studio (ver
 *  SaveMainConfigPayload) — sem isso, o mainconfig.conf só existia de verdade no disco do
 *  SERVIDOR, e o client (numa conexão remota de verdade) nunca tinha como saber o valor atual de
 *  slots/limites/tipos, nem refletir edições feitas por outro admin. */
public record SyncMainConfigPayload(String configJson) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncMainConfigPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "sync_main_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncMainConfigPayload> CODEC = StreamCodec.ofMember(SyncMainConfigPayload::write, SyncMainConfigPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(this.configJson != null ? this.configJson : "{}");
    }

    private static SyncMainConfigPayload read(RegistryFriendlyByteBuf buf) {
        return new SyncMainConfigPayload(buf.readUtf());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
