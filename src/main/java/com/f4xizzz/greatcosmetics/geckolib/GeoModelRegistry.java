package com.f4xizzz.greatcosmetics.geckolib;

import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Mapa (chave inteira -> geo/textura/animação) pros cosméticos/armas/ferramentas que usam modelo
 * 3D de verdade via GeckoLib, em vez do ícone chapado (CustomModelData num carved_pumpkin). Mesma
 * chave que já é usada como CustomModelData hoje (ver AutoCMDManager) — reaproveita a mesma
 * convenção de "inteiro identifica o quê desenhar", só que apontando pra um modelo GeckoLib em vez
 * de um resourcepack model comum.
 *
 * Comum aos dois lados: no server só serve pra validar/gerar a chave ao salvar config; quem
 * realmente lê os Identifiers pra renderizar é sempre o client (GreatCosmeticsGeoModel).
 */
public class GeoModelRegistry {

    public record Entry(Identifier geoModel, Identifier texture, Identifier animation) {}

    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();

    public static void clear() {
        ENTRIES.clear();
    }

    public static void register(int key, Identifier geoModel, Identifier texture, Identifier animation) {
        if (key == 0) return;
        ENTRIES.put(key, new Entry(geoModel, texture, animation));
    }

    public static Entry get(int key) {
        return ENTRIES.get(key);
    }

    public static boolean has(int key) {
        return ENTRIES.containsKey(key);
    }
}
