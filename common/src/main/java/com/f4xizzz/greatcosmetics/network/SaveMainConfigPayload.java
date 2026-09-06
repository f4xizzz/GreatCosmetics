package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S — pedido de um admin (Dev Studio) pra persistir uma edição no mainconfig.conf. O servidor
 *  valida permissão, aplica o JSON recebido por cima de MainConfig.config, salva no disco DELE (o
 *  do servidor, não o do client que mandou) e rebroadcasta SyncMainConfigPayload pra todo mundo. */
public record SaveMainConfigPayload(String configJson) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SaveMainConfigPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "save_main_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveMainConfigPayload> CODEC = StreamCodec.ofMember(SaveMainConfigPayload::write, SaveMainConfigPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(this.configJson != null ? this.configJson : "{}");
    }

    private static SaveMainConfigPayload read(RegistryFriendlyByteBuf buf) {
        return new SaveMainConfigPayload(buf.readUtf());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
