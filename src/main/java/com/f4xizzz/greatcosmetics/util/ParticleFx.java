package com.f4xizzz.greatcosmetics.util;

import com.f4xizzz.greatcosmetics.config.EffectData;
import net.minecraft.particle.DustColorTransitionParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.EntityEffectParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

/**
 * Resolve o {@code particleId} + a cor RGB de um {@link EffectData} num {@link ParticleEffect}
 * pronto pra {@code world.spawnParticles}/{@code world.addParticle}. Client + server safe
 * ({@code net.minecraft.particle.*} + {@code Registries} são comuns).
 *
 * <p>Substitui o padrão antigo (duplicado em GreatCosmetics#spawnCosmeticParticle e
 * DevEffectsSubPage#spawnPreviewParticles) {@code Registries.PARTICLE_TYPE.get(id) instanceof
 * ParticleEffect} — que só funcionava pra {@code SimpleParticleType} (flame, end_rod...) e
 * deixava as partículas coloríveis (dust, dust_color_transition, entity_effect) invisíveis.
 */
public final class ParticleFx {

    private ParticleFx() {}

    /** {@code null} = id inválido ou tipo de partícula não suportado (não é um ParticleEffect). */
    @SuppressWarnings("unchecked")
    public static ParticleEffect resolve(EffectData e) {
        if (e == null || e.particleId == null) return null;
        Identifier id = Identifier.tryParse(e.particleId);
        if (id == null) return null;

        ParticleType<?> type = Registries.PARTICLE_TYPE.get(id);
        String key = id.toString();

        if (e.hasColor()) {
            Vector3f c = new Vector3f(e.colorR / 255f, e.colorG / 255f, e.colorB / 255f);
            if (key.equals("minecraft:dust")) return new DustParticleEffect(c, 1.0f);
            if (key.equals("minecraft:dust_color_transition")) return new DustColorTransitionParticleEffect(c, c, 1.0f);
            if (type == ParticleTypes.ENTITY_EFFECT) {
                int argb = 0xFF000000 | (clamp(e.colorR) << 16) | (clamp(e.colorG) << 8) | clamp(e.colorB);
                return EntityEffectParticleEffect.create((ParticleType<EntityEffectParticleEffect>) type, argb);
            }
        }

        // dust SEM cor: o ParticleType de dust não é um ParticleEffect, então cairia no null
        // abaixo e a partícula sumiria — dá um branco padrão em vez disso.
        if (key.equals("minecraft:dust")) return new DustParticleEffect(new Vector3f(1f, 1f, 1f), 1.0f);

        return type instanceof ParticleEffect pe ? pe : null;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }
}
