package com.f4xizzz.greatcosmetics.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Presets COMBO (2+ formas) pro editor de grupo. Aplicar cria um efeito novo por template e
 *  substitui os membros do grupo. */
public final class EffectGroupPresets {

    private EffectGroupPresets() {}

    private static final Map<String, List<EffectData>> PRESETS = new LinkedHashMap<>();

    static {
        PRESETS.put("Twin Halo", List.of(
                EffectPresets.circle("minecraft:end_rod", 1.1, 44, 1, 32, 0, 0, 0),
                clockwise(EffectPresets.circle("minecraft:end_rod", 1.1, 44, 1, -32, 0, 0, 0))
        ));

        PRESETS.put("Vortex", List.of(
                EffectPresets.helix("minecraft:soul_fire_flame", 0.8, 28, 2.4, 2.0, 2, false),
                EffectPresets.circle("minecraft:flame", 1.3, 40, 1, 0, 0, 0, 0)
        ));

        PRESETS.put("Smash", List.of(
                EffectPresets.beam("minecraft:flame", 2.5, 0.14, true, 1, 0),
                EffectPresets.pulse("minecraft:end_rod", 0.8, 3.0, 20, 50, 10, true, 30),
                EffectPresets.pulse("minecraft:soul_fire_flame", 0.8, 3.0, 20, 50, 10, false, 30),
                EffectPresets.helix("minecraft:electric_spark", 1.0, 30, 1.5, 1.0, 4, false),
                EffectPresets.circle("minecraft:crit", 1.2, 40, 4, 0, 0, 0, 0)
        ));
    }

    private static EffectData clockwise(EffectData e) { e.clockwise = true; return e; }

    public static List<String> names() {
        return List.copyOf(PRESETS.keySet());
    }

    /** Cópias frescas dos templates do preset (ou vazio se o nome não existe). */
    public static List<EffectData> templates(String name) {
        List<EffectData> src = PRESETS.get(name);
        if (src == null) return List.of();
        List<EffectData> out = new ArrayList<>();
        for (EffectData t : src) {
            EffectData copy = new EffectData();
            EffectPresets.copyInto(copy, t);
            out.add(copy);
        }
        return out;
    }
}
