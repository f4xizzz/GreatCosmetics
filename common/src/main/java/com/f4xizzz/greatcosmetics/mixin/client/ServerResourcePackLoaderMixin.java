package com.f4xizzz.greatcosmetics.mixin.client;

import com.f4xizzz.greatcosmetics.client.ClientForcedTextureCache;
import com.f4xizzz.greatcosmetics.client.ClientJoinReloadState;
import com.f4xizzz.greatcosmetics.client.ClientServerIdentity;
import com.f4xizzz.greatcosmetics.client.ServerTextureCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URL;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.client.resources.server.DownloadedPackSource;

/** Faz a textura de QUALQUER servidor (resource-pack/resource-pack-sha1 do server.properties) só
 *  baixar de verdade da rede na PRIMEIRA vez — dali em diante, reaproveita a cópia local guardada
 *  em ServerTextureCache, sem depender do servidor declarar um sha1 certo (bytecode confirma que
 *  ServerResourcePackLoader#addResourcePack(UUID,URL,String) é uma delegação pura pro
 *  ServerResourcePackManager; a sobrecarga irmã addResourcePack(UUID,Path) trata o arquivo como já
 *  local — sem fila de download, sem checagem de hash — exatamente o "já ativa" que queremos).
 *  Como o cache é por URL, entrar em outro server/mundo (URL diferente ou nenhuma) nunca reusa
 *  esse arquivo — a reversão pro padrão continua sendo o comportamento vanilla normal.
 *
 *  <p>Ramo NOVO adicionado antes da lógica genérica acima (intocada): se o UUID desse pacote for
 *  o da textura FORÇADA do GreatCosmetics (comparado com ClientJoinReloadState#getTexturePackId,
 *  vindo de SyncCatalogStatePayload — mandado ANTES desse pacote vanilla chegar), consulta o cache
 *  criptografado por servidor (ClientForcedTextureCache) em vez do genérico. Falha ao
 *  decifrar/sem cache válido = cai pro download normal (fail-open, nunca trava a conexão) — e o
 *  próprio ClientForcedTextureCache cuida de baixar+criptografar em background pra próxima vez. */
@Mixin(DownloadedPackSource.class)
public abstract class ServerResourcePackLoaderMixin {

    @Shadow
    public abstract void pushLocalPack(UUID id, Path path);

    @Inject(method = "pushPack(Ljava/util/UUID;Ljava/net/URL;Ljava/lang/String;)V",
            at = @At("HEAD"), cancellable = true)
    private void greatcosmetics$useLocalTextureCache(UUID id, URL url, String hash, CallbackInfo ci) {
        String urlStr = url.toString();

        boolean isForced = ClientJoinReloadState.isForceTextureEnabled() && id.equals(ClientJoinReloadState.getTexturePackId());
        com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("ServerResourcePackLoader.addResourcePack: id=" + id
                + " forcedTexture=" + isForced + " (forceEnabled=" + ClientJoinReloadState.isForceTextureEnabled()
                + " expectedId=" + ClientJoinReloadState.getTexturePackId() + ") hash=" + hash + " url=" + urlStr);

        if (isForced) {
            String serverKey = ClientServerIdentity.currentServerKey();
            if (serverKey != null) {
                ClientForcedTextureCache.Entry entry = ClientForcedTextureCache.getEntry(serverKey);
                boolean hashMatches = entry != null && hash != null && hash.equalsIgnoreCase(entry.declaredTextureSha1);
                boolean hasFile = ClientForcedTextureCache.hasEncryptedFile(entry);
                com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("  forced texture: serverKey=" + serverKey
                        + " entry=" + (entry == null ? "AUSENTE" : ("presente(declaredSha1=" + entry.declaredTextureSha1 + " file=" + entry.encryptedFileName + ")"))
                        + " hashMatches=" + hashMatches + " hasEncryptedFile=" + hasFile);
                if (hashMatches && hasFile) {
                    Path decrypted = ClientForcedTextureCache.decryptToTempFile(serverKey, entry);
                    if (decrypted != null) {
                        com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("  forced texture: CACHE HIT — aplicando do arquivo criptografado local, sem baixar da rede.");
                        ci.cancel();
                        this.pushLocalPack(id, decrypted);
                        return;
                    }
                    com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("  forced texture: arquivo em cache não decifrou — caindo pro download normal.");
                }
                // Sem cache válido (primeira vez nesse servidor, hash mudou, ou falha ao
                // decifrar) — deixa o vanilla baixar normalmente e prepara o cache criptografado
                // pra próxima vez. Não cancela ci — o resto do método (cache genérico abaixo)
                // continuaria rodando também, então retorna aqui pra não cachear a mesma URL nos
                // dois sistemas ao mesmo tempo.
                com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("  forced texture: CACHE MISS — baixando normal (com tela) e preparando o cache criptografado pra próxima vez.");
                ServerTextureCache.evict(urlStr);
                ClientForcedTextureCache.cacheInBackground(serverKey, ClientServerIdentity.currentServerAddressRaw(), urlStr, hash);
                return;
            }
            com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("  forced texture: serverKey nulo (singleplayer/LAN?) — sem cache.");
        }

        Path cached = ServerTextureCache.getCachedFile(urlStr, hash);
        if (cached != null) {
            ci.cancel();
            this.pushLocalPack(id, cached);
        } else {
            ServerTextureCache.cacheInBackground(urlStr, hash);
        }
    }
}
