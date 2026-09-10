package com.f4xizzz.greatcosmetics.client;

import com.f4xizzz.greatcosmetics.network.SyncPokemonScanPayload;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache client-side dos dados de scanner (IVs / nature / ability / size) mandados pelo servidor
 * (ver SyncPokemonScanPayload), chaveado por {@code entity.getId()}. Entradas expiram — o servidor
 * reenvia a cada ~1s enquanto algum scanner está ativo; se parar de vir (tirou o cosmético, saiu
 * de alcance), a entrada some sozinha.
 */
public final class ClientPokemonScanCache {

    private ClientPokemonScanCache() {}

    private static final long TTL_MS = 4_000L;

    private record Stored(SyncPokemonScanPayload.Entry data, long expiresAtMs) {}

    private static final Map<Integer, Stored> CACHE = new ConcurrentHashMap<>();

    /** Substitui TUDO — o payload é o retrato completo do que está em alcance agora. */
    public static void put(Map<Integer, SyncPokemonScanPayload.Entry> fresh) {
        long exp = System.currentTimeMillis() + TTL_MS;
        CACHE.clear();
        for (Map.Entry<Integer, SyncPokemonScanPayload.Entry> e : fresh.entrySet()) {
            CACHE.put(e.getKey(), new Stored(e.getValue(), exp));
        }
    }

    /** null se não tem dado (ou expirou) pra essa entidade. */
    public static SyncPokemonScanPayload.Entry get(int entityId) {
        Stored e = CACHE.get(entityId);
        if (e == null) return null;
        if (System.currentTimeMillis() > e.expiresAtMs) { CACHE.remove(entityId); return null; }
        return e.data;
    }

    public static void clear() {
        CACHE.clear();
    }
}
