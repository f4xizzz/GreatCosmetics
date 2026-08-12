package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S — esconde/mostra um cosmético ESPECÍFICO (por id), diferente de ToggleVisibilityPayload
 *  (que esconde a armadura VANILLA real inteira por slot). Sem isso, esconder um acessório
 *  escondia TODOS os outros equipados no mesmo slot virtual junto (ver player_hidden_cosmetics). */
public record ToggleCosmeticVisibilityPayload(String cosmeticId, boolean hidden) implements CustomPayload {

    public static final CustomPayload.Id<ToggleCosmeticVisibilityPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "toggle_cosmetic_visibility"));

    public static final PacketCodec<RegistryByteBuf, ToggleCosmeticVisibilityPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeString(payload.cosmeticId());
                buf.writeBoolean(payload.hidden());
            },
            buf -> new ToggleCosmeticVisibilityPayload(buf.readString(), buf.readBoolean())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
