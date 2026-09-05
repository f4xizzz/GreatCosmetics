package com.f4xizzz.greatcosmetics.client;

import java.util.UUID;

/** Estado efêmero (nunca persistido em disco) da conexão atual — resetado em todo DISCONNECT
 *  (ver GreatCosmeticsClient), nunca sobrevive entre servidores nem entre sessões.
 *
 *  <p><b>"É entrada no servidor?" ({@link #isJoinSync(long)}):</b> NÃO é mais heurística de tempo.
 *  O servidor diz explicitamente: {@link com.f4xizzz.greatcosmetics.network.SyncCatalogStatePayload}
 *  carrega um {@code joinSync} — {@code true} quando mandado do handler de JOIN, {@code false}
 *  quando mandado do loop de {@code /gc reload}. O receiver desse payload chama
 *  {@link #markJoinSync(long)} ou {@link #clearJoinSync()} conforme o caso. (A tentativa anterior
 *  usava ClientPlayConnectionEvents.JOIN + um tick handler pra fechar a "janela"; na prática o
 *  Fabric dispara JOIN cedo demais e o tick handler fechava a janela ANTES dos payloads do
 *  servidor sequer serem processados no client — que pode demorar segundos num modpack pesado,
 *  com o render thread ocupado recarregando JEI/etc. — então a supressão nunca armava.)
 *
 *  <p>Há ainda um teto de validade ({@link #STALE_AFTER_MS}) sobre o {@code joinSync} e um
 *  sinalizador de supressão da SplashOverlay de "descarte único" — camadas independentes pra nunca
 *  ficar armado por engano muito depois da entrada. */
public final class ClientJoinReloadState {

    private ClientJoinReloadState() {}

    private static final long STALE_AFTER_MS = 60_000L;

    /** Armado pelo receiver de SyncCatalogStatePayload SÓ quando {@code payload.joinSync()} — o
     *  servidor confirmou que este sync é uma ENTRADA no servidor, não um /gc reload ao vivo. */
    private static volatile boolean joinSyncActive = false;
    private static volatile long joinSyncAtMillis = 0L;

    /** true = esta entrada NÃO precisa de reloadResources() (catálogo idêntico ao já bakeado e
     *  sem textura forçada) — o receiver de cosmético/armadura só reconstrói o GeoModelRegistry
     *  direto. Nunca armado num /gc reload (joinSync=false limpa tudo). */
    private static volatile boolean skipNextJoinReload = false;

    /** Sinalizador de descarte único pra cancelar a próxima SplashOverlay (caso da textura
     *  forçada em cache — o vanilla recarrega pro pacote de qualquer jeito, e é essa tela que
     *  queremos esconder). */
    private static volatile boolean suppressNextSplashOverlay = false;
    private static volatile long armedAtMillis = 0L;

    // --- espelho do SyncCatalogStatePayload, lido por ServerResourcePackLoaderMixin ---
    private static volatile String pendingCatalogHash = null;
    private static volatile boolean forceTextureEnabled = false;
    private static volatile String textureSha1 = "";
    private static volatile UUID texturePackId = null;

    /** Receiver de SyncCatalogStatePayload, quando {@code payload.joinSync()} é true. */
    public static void markJoinSync(long nowMillis) {
        joinSyncActive = true;
        joinSyncAtMillis = nowMillis;
    }

    /** Receiver de SyncCatalogStatePayload, quando {@code payload.joinSync()} é false (/gc reload
     *  ao vivo) — nada é suprimido nem pulado, tudo se comporta normal. */
    public static void clearJoinSync() {
        joinSyncActive = false;
        skipNextJoinReload = false;
        suppressNextSplashOverlay = false;
    }

    public static boolean isJoinSync(long nowMillis) {
        return joinSyncActive && (nowMillis - joinSyncAtMillis) <= STALE_AFTER_MS;
    }

    public static void armSkipReload() {
        skipNextJoinReload = true;
    }

    /** Lido pelos receivers de cosmético/armadura — só true se armado E ainda dentro da janela
     *  de entrada (joinSync ativo e não vencido). */
    public static boolean shouldSkipJoinReload(long nowMillis) {
        return skipNextJoinReload && isJoinSync(nowMillis);
    }

    public static void armSuppress(long nowMillis) {
        suppressNextSplashOverlay = true;
        armedAtMillis = nowMillis;
    }

    /** Consome (zera) o sinalizador — devolve true só se estava armado E ainda dentro da janela
     *  de validade. Quem chama (MinecraftClientMixin) ainda confere isJoinSync() por conta própria. */
    public static boolean consumeSuppressFlag(long nowMillis) {
        if (!suppressNextSplashOverlay) return false;
        suppressNextSplashOverlay = false;
        return (nowMillis - armedAtMillis) <= STALE_AFTER_MS;
    }

    /** Chamado pelo receiver de SyncCatalogStatePayload assim que o payload chega. */
    public static void stash(String catalogHash, boolean forceTextureEnabledNow, String textureSha1Now, UUID texturePackIdNow) {
        pendingCatalogHash = catalogHash;
        forceTextureEnabled = forceTextureEnabledNow;
        textureSha1 = textureSha1Now == null ? "" : textureSha1Now;
        texturePackId = texturePackIdNow;
    }

    /** Hash do catálogo que veio no SyncCatalogStatePayload dessa conexão — NÃO consome. */
    public static String peekCatalogHash() {
        return pendingCatalogHash;
    }

    public static boolean isForceTextureEnabled() { return forceTextureEnabled; }
    public static String getTextureSha1() { return textureSha1; }
    public static UUID getTexturePackId() { return texturePackId; }

    /** Chamado em DISCONNECT — nada pode sobreviver de uma conexão pra outra. */
    public static void resetAll() {
        joinSyncActive = false;
        joinSyncAtMillis = 0L;
        skipNextJoinReload = false;
        suppressNextSplashOverlay = false;
        armedAtMillis = 0L;
        pendingCatalogHash = null;
        forceTextureEnabled = false;
        textureSha1 = "";
        texturePackId = null;
    }
}
