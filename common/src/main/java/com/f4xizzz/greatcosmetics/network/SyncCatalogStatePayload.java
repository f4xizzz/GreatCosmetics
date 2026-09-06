package com.f4xizzz.greatcosmetics.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** S2C — mandado ANTES do resource pack forçado (ver GreatCosmetics#sendForcedResourcePack) e
 *  antes de SyncCosmeticsPayload/SyncArmorCosmeticsPayload, tanto no join quanto no /gc reload
 *  (princípio de "payload informativo antes do pacote vanilla que age"). O client usa
 *  isso pra decidir, ANTES de qualquer coisa acontecer, se o que já tem em cache pra ESSE servidor
 *  (ver ClientForcedTextureCache) ainda bate com o que o servidor tem agora — se bater, evita
 *  mostrar a SplashOverlay de novo (ver MinecraftClientMixin) só durante a entrada no servidor.
 *
 *  {@code catalogHash} é um SHA-256 (hex) do catálogo inteiro de cosméticos+armaduras convertidas
 *  (ver GreatCosmetics#computeCosmeticsCatalogHash) — muda sozinho quando o admin adiciona/edita
 *  qualquer model/config, sem precisar de nenhum flag manual. {@code textureSha1}/
 *  {@code texturePackId} espelham MainConfig#textureSha1/textureId (vazio/id qualquer se
 *  {@code forceTextureEnabled} for false) — o client precisa saber ISSO ANTES do pacote vanilla
 *  de resource pack chegar, pra já ter decidido o que fazer quando ele chegar.
 *
 *  {@code joinSync} = true quando este payload sai do handler de JOIN (entrada no servidor),
 *  false quando sai do loop de {@code /gc reload}. É o discriminador DEFINITIVO (não heurística de
 *  tempo no client) de "posso suprimir a tela / pular o reload?" — só quando true. Num /gc reload
 *  o admin PEDIU o recarregamento pra todo mundo online, então nunca se suprime. */
public record SyncCatalogStatePayload(String catalogHash, boolean forceTextureEnabled, String textureSha1, UUID texturePackId, boolean joinSync) implements CustomPacketPayload {
    public static final Type<SyncCatalogStatePayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "sync_catalog_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncCatalogStatePayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeUtf(payload.catalogHash);
                buf.writeBoolean(payload.forceTextureEnabled);
                buf.writeUtf(payload.textureSha1);
                buf.writeUUID(payload.texturePackId);
                buf.writeBoolean(payload.joinSync);
            },
            buf -> new SyncCatalogStatePayload(buf.readUtf(), buf.readBoolean(), buf.readUtf(), buf.readUUID(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
