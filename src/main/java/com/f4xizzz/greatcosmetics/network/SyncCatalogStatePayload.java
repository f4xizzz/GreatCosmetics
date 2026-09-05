package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

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
public record SyncCatalogStatePayload(String catalogHash, boolean forceTextureEnabled, String textureSha1, UUID texturePackId, boolean joinSync) implements CustomPayload {
    public static final Id<SyncCatalogStatePayload> ID = new Id<>(Identifier.of("greatcosmetics", "sync_catalog_state"));

    public static final PacketCodec<RegistryByteBuf, SyncCatalogStatePayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeString(payload.catalogHash);
                buf.writeBoolean(payload.forceTextureEnabled);
                buf.writeString(payload.textureSha1);
                buf.writeUuid(payload.texturePackId);
                buf.writeBoolean(payload.joinSync);
            },
            buf -> new SyncCatalogStatePayload(buf.readString(), buf.readBoolean(), buf.readString(), buf.readUuid(), buf.readBoolean())
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
