package com.f4xizzz.greatcosmetics.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Presets de UMA forma pro editor de efeito (dropdown "Preset"). Aplicar um preset copia TODOS
 *  os campos do template pro EffectData sendo editado; o admin edita por cima. São valores
 *  próprios — geometria padrão, sem código/asset de nenhum mod. */
public final class EffectPresets {

    private EffectPresets() {}

    private static final Map<String, EffectData> PRESETS = new LinkedHashMap<>();

    static {
        // SIMPLE
        PRESETS.put("Simple Aura", simple("minecraft:soul_fire_flame", 5, 0.05, 0.5, 1.0, 10));

        // CIRCLE
        PRESETS.put("Ring", circle("minecraft:end_rod", 1.2, 48, 1, 0, 0, 0, 0));
        PRESETS.put("Ring (Tilted)", circle("minecraft:end_rod", 1.2, 48, 1, 30, 0, 0, 0));
        PRESETS.put("Halo", circle("minecraft:electric_spark", 0.9, 40, 1, 80, 0, 0, 0));
        PRESETS.put("Sweeping Ring", circle("minecraft:wax_off", 1.3, 60, 1, 0, 0, 0, 24)); // anim: traça

        // HELIX
        PRESETS.put("Helix", helix("minecraft:flame", 0.7, 30, 2.0, 2.0, 1, false));
        PRESETS.put("Double Helix", helix("minecraft:soul_fire_flame", 0.7, 30, 2.2, 2.0, 2, false));
        PRESETS.put("Rising Helix", helixAnim("minecraft:end_rod", 0.6, 26, 2.4, 3.0, 1, 40));

        // BEAM
        PRESETS.put("Beam Up", beam("minecraft:end_rod", 3.0, 0.15, true, 1, 0));
        PRESETS.put("Beam Down", beam("minecraft:falling_obsidian_tear", 3.0, 0.15, false, 1, 0));
        PRESETS.put("Rising Beam", beamAnim("minecraft:electric_spark", 4.0, 0.14, true, 1, 20));

        // PULSE
        PRESETS.put("Pulse Out", pulse("minecraft:end_rod", 0.4, 3.2, 24, 56, 12, true, 40));
        PRESETS.put("Pulse In", pulse("minecraft:soul_fire_flame", 0.4, 3.2, 24, 56, 12, false, 40));
        PRESETS.put("Nova", pulse("minecraft:firework", 0.3, 4.0, 20, 60, 14, true, 24));
    }

    public static List<String> names() {
        return List.copyOf(PRESETS.keySet());
    }

    /** Cópia fresca do template de {@code presetName} (todos os campos, offset incluso). null se não existe. */
    public static EffectData instantiate(String presetName) {
        EffectData src = PRESETS.get(presetName);
        if (src == null) return null;
        EffectData e = new EffectData();
        copyInto(e, src);
        return e;
    }

    /** Copia todos os campos do preset pro alvo. No-op se o nome não existe. */
    public static void apply(EffectData target, String presetName) {
        EffectData src = PRESETS.get(presetName);
        if (src == null || target == null) return;
        copyInto(target, src);
        // offset NÃO vem do preset — o admin posiciona com o gizmo; só zera lados/frente.
        target.offsetX = 0; target.offsetZ = 0;
    }

    /** Copia TODOS os campos de {@code src} pra {@code dst} (inclusive offset). */
    public static void copyInto(EffectData dst, EffectData src) {
        dst.particleId = src.particleId;
        dst.count = src.count;
        dst.speed = src.speed;
        dst.spreadX = src.spreadX; dst.spreadY = src.spreadY; dst.spreadZ = src.spreadZ;
        dst.offsetX = src.offsetX; dst.offsetY = src.offsetY; dst.offsetZ = src.offsetZ;
        dst.tickInterval = src.tickInterval;
        dst.colorR = src.colorR; dst.colorG = src.colorG; dst.colorB = src.colorB;
        dst.shape = src.shape;
        dst.radius = src.radius; dst.points = src.points; dst.strands = src.strands;
        dst.phase = src.phase; dst.clockwise = src.clockwise;
        dst.rotX = src.rotX; dst.rotY = src.rotY; dst.rotZ = src.rotZ;
        dst.helixHeight = src.helixHeight; dst.turns = src.turns; dst.reverse = src.reverse;
        dst.beamHeight = src.beamHeight; dst.spacing = src.spacing; dst.upwards = src.upwards;
        dst.endRadius = src.endRadius; dst.endPoints = src.endPoints; dst.rings = src.rings; dst.outwards = src.outwards;
        dst.animTicks = src.animTicks;
    }

    // ---- builders ----
    static EffectData simple(String particle, int count, double speed, double spread, double offY, int interval) {
        EffectData e = new EffectData();
        e.particleId = particle; e.shape = "SIMPLE"; e.count = count; e.speed = speed;
        e.spreadX = spread; e.spreadY = spread; e.spreadZ = spread; e.offsetY = offY; e.tickInterval = interval;
        return e;
    }

    static EffectData circle(String particle, double radius, int points, int strands,
                             double rotX, double rotY, double rotZ, int animTicks) {
        EffectData e = base(particle, "CIRCLE", animTicks == 0 ? 8 : 1);
        e.radius = radius; e.points = points; e.strands = strands;
        e.rotX = rotX; e.rotY = rotY; e.rotZ = rotZ; e.animTicks = animTicks; e.offsetY = 0.1;
        return e;
    }

    static EffectData helix(String particle, double radius, int points, double height, double turns,
                            int strands, boolean reverse) {
        EffectData e = base(particle, "HELIX", 6);
        e.radius = radius; e.points = points; e.helixHeight = height; e.turns = turns;
        e.strands = strands; e.reverse = reverse; e.offsetY = 0.0;
        return e;
    }

    static EffectData helixAnim(String particle, double radius, int points, double height, double turns,
                                int strands, int animTicks) {
        EffectData e = helix(particle, radius, points, height, turns, strands, false);
        e.animTicks = animTicks; e.tickInterval = 8;
        return e;
    }

    static EffectData beam(String particle, double height, double spacing, boolean upwards, int strands, int animTicks) {
        EffectData e = base(particle, "BEAM", animTicks == 0 ? 4 : 8);
        e.beamHeight = height; e.spacing = spacing; e.upwards = upwards; e.strands = strands;
        e.radius = 0.4; e.animTicks = animTicks; e.offsetY = upwards ? 0.0 : height;
        return e;
    }

    static EffectData beamAnim(String particle, double height, double spacing, boolean upwards, int strands, int animTicks) {
        return beam(particle, height, spacing, upwards, strands, animTicks);
    }

    static EffectData pulse(String particle, double startR, double endR, int startP, int endP,
                            int rings, boolean outwards, int animTicks) {
        EffectData e = base(particle, "PULSE", 20);
        e.radius = startR; e.endRadius = endR; e.points = startP; e.endPoints = endP;
        e.rings = rings; e.outwards = outwards; e.animTicks = animTicks; e.offsetY = 0.1;
        return e;
    }

    private static EffectData base(String particle, String shape, int interval) {
        EffectData e = new EffectData();
        e.particleId = particle; e.shape = shape; e.count = 1; e.speed = 0.0;
        e.spreadX = 0; e.spreadY = 0; e.spreadZ = 0; e.tickInterval = interval;
        return e;
    }
}
