package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S — esconde/mostra um cosmético ESPECÍFICO (por id), diferente de ToggleVisibilityPayload
 *  (que esconde a armadura VANILLA real inteira por slot). Sem isso, esconder um acessório
 *  escondia TODOS os outros equipados no mesmo slot virtual junto (ver player_hidden_cosmetics). */
public record ToggleCosmeticVisibilityPayload(String cosmeticId, boolean hidden) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToggleCosmeticVisibilityPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "toggle_cosmetic_visibility"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleCosmeticVisibilityPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeUtf(payload.cosmeticId());
                buf.writeBoolean(payload.hidden());
            },
            buf -> new ToggleCosmeticVisibilityPayload(buf.readUtf(), buf.readBoolean())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
