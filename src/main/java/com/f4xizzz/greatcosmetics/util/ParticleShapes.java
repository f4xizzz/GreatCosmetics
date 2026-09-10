package com.f4xizzz.greatcosmetics.util;

import com.f4xizzz.greatcosmetics.config.EffectData;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Gera os PONTOS LOCAIS (relativos ao player, antes de aplicar offset + rotação de bodyYaw) de
 * uma forma de partícula ({@link EffectData#shape}) num dado progresso de animação {@code anim}
 * (0..1; 1 = forma completa). {@code SIMPLE} devolve lista vazia — quem chama faz o spawn direto.
 *
 * <p>Client + server safe (só jOML + a config). Reimplementado do zero — a ideia de "spirais /
 * anéis / feixes / pulsos" é geometria padrão, não vem de código de nenhum mod.
 */
public final class ParticleShapes {

    private ParticleShapes() {}

    // Tetos pra uma forma não afogar rede/FPS.
    private static final int MAX_POINTS = 200;
    private static final int MAX_STRANDS = 12;
    private static final int MAX_RINGS = 24;
    private static final int MAX_TOTAL = 1200;

    public static List<Vector3d> points(EffectData e, double anim) {
        if (e == null || !e.isShape()) return List.of();
        double a = Math.max(0.0, Math.min(1.0, anim));
        String shape = e.shape.toUpperCase();
        List<Vector3d> out = new ArrayList<>();
        switch (shape) {
            case "CIRCLE" -> circle(e, a, out);
            case "HELIX" -> helix(e, a, out);
            case "BEAM" -> beam(e, a, out);
            case "PULSE" -> pulse(e, a, out);
            default -> { return List.of(); }
        }
        if (out.size() > MAX_TOTAL) out.subList(MAX_TOTAL, out.size()).clear();
        rotateAll(out, e.rotX, e.rotY, e.rotZ);
        return out;
    }

    private static void circle(EffectData e, double anim, List<Vector3d> out) {
        int pts = clamp(e.points, 1, MAX_POINTS);
        int strands = clamp(e.strands, 1, MAX_STRANDS);
        int shown = (int) Math.ceil(anim * pts);
        double dir = e.clockwise ? -1.0 : 1.0;
        for (int s = 0; s < strands; s++) {
            double sPhase = Math.toRadians(e.phase) + (s * 2 * Math.PI / strands);
            for (int i = 0; i < shown; i++) {
                double ang = sPhase + dir * (i * 2 * Math.PI / pts);
                out.add(new Vector3d(Math.cos(ang) * e.radius, 0.0, Math.sin(ang) * e.radius));
            }
        }
    }

    private static void helix(EffectData e, double anim, List<Vector3d> out) {
        int strands = clamp(e.strands, 1, MAX_STRANDS);
        double turns = Math.max(0.05, e.turns);
        int total = clamp((int) Math.round(e.points * turns), 1, MAX_POINTS);
        int shown = (int) Math.ceil(anim * total);
        double dir = e.clockwise ? -1.0 : 1.0;
        for (int s = 0; s < strands; s++) {
            double sPhase = Math.toRadians(e.phase) + (s * 2 * Math.PI / strands);
            for (int i = 0; i < shown; i++) {
                double t = total <= 1 ? 0.0 : (double) i / (total - 1);
                double tt = e.reverse ? 1.0 - t : t;
                double ang = sPhase + dir * (tt * turns * 2 * Math.PI);
                out.add(new Vector3d(Math.cos(ang) * e.radius, tt * e.helixHeight, Math.sin(ang) * e.radius));
            }
        }
    }

    private static void beam(EffectData e, double anim, List<Vector3d> out) {
        double sp = Math.max(0.02, e.spacing);
        int n = clamp((int) Math.round(e.beamHeight / sp), 1, MAX_POINTS);
        int strands = clamp(e.strands, 1, MAX_STRANDS);
        int shown = (int) Math.ceil(anim * n);
        double sign = e.upwards ? 1.0 : -1.0;
        for (int s = 0; s < strands; s++) {
            double ang = Math.toRadians(e.phase) + (strands == 1 ? 0.0 : s * 2 * Math.PI / strands);
            double ox = strands == 1 ? 0.0 : Math.cos(ang) * e.radius;
            double oz = strands == 1 ? 0.0 : Math.sin(ang) * e.radius;
            for (int i = 0; i < shown; i++) {
                out.add(new Vector3d(ox, sign * i * sp, oz));
            }
        }
    }

    private static void pulse(EffectData e, double anim, List<Vector3d> out) {
        int rings = clamp(e.rings, 1, MAX_RINGS);
        double dir = e.clockwise ? -1.0 : 1.0;
        // anel "ativo" avança com a animação; mostra ele + o vizinho pra dar espessura.
        double active = (e.outwards ? anim : (1.0 - anim)) * (rings - 1);
        for (int k = 0; k < rings; k++) {
            if (Math.abs(k - active) > 1.001) continue;
            double f = rings <= 1 ? 0.0 : (double) k / (rings - 1);
            double rr = lerp(e.radius, e.endRadius, f);
            int rp = clamp((int) Math.round(lerp(e.points, e.endPoints, f)), 1, MAX_POINTS);
            double sPhase = Math.toRadians(e.phase);
            for (int i = 0; i < rp; i++) {
                double ang = sPhase + dir * (i * 2 * Math.PI / rp);
                out.add(new Vector3d(Math.cos(ang) * rr, 0.0, Math.sin(ang) * rr));
            }
        }
    }

    private static void rotateAll(List<Vector3d> pts, double rotXDeg, double rotYDeg, double rotZDeg) {
        if (rotXDeg == 0 && rotYDeg == 0 && rotZDeg == 0) return;
        double rx = Math.toRadians(rotXDeg), ry = Math.toRadians(rotYDeg), rz = Math.toRadians(rotZDeg);
        for (Vector3d p : pts) {
            p.rotateZ(rz);
            p.rotateX(rx);
            p.rotateY(ry);
        }
    }

    private static int clamp(int v, int lo, int hi) { return v < lo ? lo : Math.min(v, hi); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}
