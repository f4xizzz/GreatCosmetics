package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S — pedido de um admin (Dev Studio) pra persistir uma edição no mainconfig.conf. O servidor
 *  valida permissão, aplica o JSON recebido por cima de MainConfig.config, salva no disco DELE (o
 *  do servidor, não o do client que mandou) e rebroadcasta SyncMainConfigPayload pra todo mundo. */
public record SaveMainConfigPayload(String configJson) implements CustomPayload {
    public static final CustomPayload.Id<SaveMainConfigPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "save_main_config"));

    public static final PacketCodec<RegistryByteBuf, SaveMainConfigPayload> CODEC = PacketCodec.of(SaveMainConfigPayload::write, SaveMainConfigPayload::read);

    private void write(RegistryByteBuf buf) {
        buf.writeString(this.configJson != null ? this.configJson : "{}");
    }

    private static SaveMainConfigPayload read(RegistryByteBuf buf) {
        return new SaveMainConfigPayload(buf.readString());
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
