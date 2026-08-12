package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S — botão "PC" da PartyPage (dentro do wardrobe). Em vez de simular "/pc" (que passa pelo
 * Brigadier e pode ser bloqueado por qualquer plugin de permissão de comando, ex: VanillaPermissions),
 * o servidor abre o PC diretamente pela API do Cobblemon — exatamente como se o player tivesse
 * clicado com o botão direito num bloco de PC de verdade, sem depender de permissão de comando.
 */
public record OpenPcFromWardrobePayload() implements CustomPayload {

    public static final CustomPayload.Id<OpenPcFromWardrobePayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "open_pc_from_wardrobe"));

    public static final PacketCodec<RegistryByteBuf, OpenPcFromWardrobePayload> CODEC = PacketCodec.of(
            (payload, buf) -> {},
            buf -> new OpenPcFromWardrobePayload()
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
