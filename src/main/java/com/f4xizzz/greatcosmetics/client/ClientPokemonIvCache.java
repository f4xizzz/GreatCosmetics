package com.f4xizzz.greatcosmetics.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache client-side dos IVs mandados pelo servidor (ver SyncPokemonIvsPayload), chaveado por
 * {@code entity.getId()}. Entradas expiram — o servidor reenvia a cada ~1s enquanto o scanner
 * está ativo; se parar de vir (jogador tirou o cosmético, saiu de alcance), a entrada some sozinha.
 */
public final class ClientPokemonIvCache {

    private ClientPokemonIvCache() {}

    private static final long TTL_MS = 4_000L;

    private record Entry(int[] ivs, long expiresAtMs) {}

    private static final Map<Integer, Entry> CACHE = new ConcurrentHashMap<>();

    public static void put(Map<Integer, int[]> fresh) {
        long exp = System.currentTimeMillis() + TTL_MS;
        // substitui tudo: o payload é o retrato completo do que está em alcance agora
        CACHE.clear();
        for (Map.Entry<Integer, int[]> e : fresh.entrySet()) {
            CACHE.put(e.getKey(), new Entry(e.getValue(), exp));
        }
    }

    /** null se não tem dado (ou expirou) pra essa entidade. Array de 6: HP/Atk/Def/SpA/SpD/Spe. */
    public static int[] get(int entityId) {
        Entry e = CACHE.get(entityId);
        if (e == null) return null;
        if (System.currentTimeMillis() > e.expiresAtMs) { CACHE.remove(entityId); return null; }
        return e.ivs;
    }

    public static void clear() {
        CACHE.clear();
    }
}
