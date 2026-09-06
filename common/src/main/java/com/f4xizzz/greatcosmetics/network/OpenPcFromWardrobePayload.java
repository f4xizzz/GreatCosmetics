package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S — botão "PC" da PartyPage (dentro do wardrobe). Em vez de simular "/pc" (que passa pelo
 * Brigadier e pode ser bloqueado por qualquer plugin de permissão de comando, ex: VanillaPermissions),
 * o servidor abre o PC diretamente pela API do Cobblemon — exatamente como se o player tivesse
 * clicado com o botão direito num bloco de PC de verdade, sem depender de permissão de comando.
 */
public record OpenPcFromWardrobePayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenPcFromWardrobePayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "open_pc_from_wardrobe"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenPcFromWardrobePayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {},
            buf -> new OpenPcFromWardrobePayload()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
