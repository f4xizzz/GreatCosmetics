package com.f4xizzz.greatcosmetics.config;

import java.util.ArrayList;
import java.util.List;

/** Um "grupo de efeito" — só um bundle de ids de efeitos (de {@link EffectConfig#effectsMap}) que
 *  disparam JUNTOS. Cada efeito membro mantém os próprios ajustes (partícula, count, tickInterval,
 *  offset, cor). Usar o id de um grupo onde se usaria um id de efeito (campo Effect Visual / Fly
 *  Particle de um cosmético) faz o mod disparar todos os membros — ver
 *  {@link EffectGroupConfig#resolve(String)}. */
public class EffectGroupData {
    /** Ids de efeitos COMPARTILHADOS (aparecem na aba Effects) que este grupo referencia. */
    public List<String> effectIds = new ArrayList<>();

    /** Efeitos PRÓPRIOS do grupo — dados completos, guardados dentro do grupo. NÃO aparecem na
     *  aba Effects; só existem dentro deste grupo. Criados por "+ Create New Effect" ou por um
     *  preset de grupo. */
    public List<EffectData> ownedEffects = new ArrayList<>();
}
