package com.f4xizzz.greatcosmetics.util;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.entity.SpawnBucketChosenEvent;
import com.cobblemon.mod.common.api.events.fishing.BobberSpawnPokemonEvent;
import com.cobblemon.mod.common.api.events.fishing.PokerodCastEvent;
import com.cobblemon.mod.common.entity.fishing.PokeRodFishingBobberEntity;
import com.cobblemon.mod.common.api.events.pokeball.PokeBallCaptureCalculatedEvent;
import com.cobblemon.mod.common.api.events.pokemon.ExperienceGainedEvent;
import com.cobblemon.mod.common.api.events.pokemon.FriendshipUpdatedEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.item.ability.AbilityChanger;
import com.cobblemon.mod.common.api.pokeball.catching.CaptureContext;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.spawning.SpawnBucket;
import com.cobblemon.mod.common.api.spawning.SpawnCause;
import com.cobblemon.mod.common.pokemon.IVs;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.f4xizzz.greatcosmetics.GreatCosmetics;
import com.f4xizzz.greatcosmetics.config.ArmorCosmeticsConfig;
import com.f4xizzz.greatcosmetics.config.CosmeticData;
import com.f4xizzz.greatcosmetics.database.DatabaseManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * Aplica de verdade os bônus de CosmeticData.LureStats nos eventos do Cobblemon. Antes deste
 * arquivo, "lure" era só dado — aparecia no lore do item e era sincronizado pro cliente, mas
 * nenhum código lia esses números pra afetar shiny/IV/captura/exp/amizade de verdade (ver
 * conversa que motivou isso: "coloquei o lure no item e ele não está funcionando").
 *
 * Hooks usados (shiny/IV são SEMPRE decididos na CAPTURA, nunca no spawn — decisão explícita do
 * dev: só "o que Pokémon vai spawnar" — ultra raro, tipo — deve mexer no spawn):
 *   - SPAWN_BUCKET_CHOSEN: só o bônus de ultra raro (qual BUCKET de raridade é escolhido). Dá pra
 *     amarrar a um player porque SpawnCause.getEntity(), pra spawn natural via PlayerSpawner, É o
 *     player dono daquele spawner.
 *   - POKEMON_CAPTURED: shiny (reroll se ainda não é), IV garantido/chance, hidden ability — tudo
 *     no momento da captura.
 *   - BOBBER_SPAWN_POKEMON_POST: mesma coisa (shiny/IV), só que os campos *Pesca*, quando o
 *     Pokémon veio de vara — bônus somado, independente do de POKEMON_CAPTURED que roda depois.
 *   - POKEROD_CAST_PRE: velocidade de pesca (lurePescaVelocidade).
 *   - POKE_BALL_CAPTURE_CALCULATED: chance de captura (reroll uma captura que falhou).
 *   - EXPERIENCE_GAINED_EVENT_PRE / FRIENDSHIP_UPDATED: multiplicadores de exp/amizade.
 *
 * NÃO implementados ainda:
 *   - lureTYPE ("Tipo Afetado") — sem uso nenhum por enquanto. Pelo pedido do dev, isso e outros
 *     campos que decidem QUAL Pokémon spawna precisam mexer no SPAWN, não na captura — mas isso
 *     exige reponderar a escolha de ESPÉCIE dentro do spawn pool (não só o bucket de raridade),
 *     e ainda não ficou claro se é "só Pokémon desse tipo têm chance de vir com os outros bônus"
 *     ou "aumenta a chance de spawnar Pokémon desse tipo" — não implementado até isso ficar claro.
 *   - lurePescaUltraRare (a pesca não passa pelo SpawnBucketChosenEvent — não achei equivalente
 *     de "bucket"/raridade exposto nos eventos de vara).
 *   - lureDePesca (campo ambíguo — sem um objetivo mecânico claro, ver conversa).
 *   - lureEV (ganho de EV acontece dentro da lógica de batalha, sem evento público pra isso).
 */
public class LureManager {

    private static final Random RANDOM = new Random();

    public static void register() {
        CobblemonEvents.SPAWN_BUCKET_CHOSEN.subscribe(LureManager::onSpawnBucketChosen);
        CobblemonEvents.POKEMON_CAPTURED.subscribe(LureManager::onPokemonCaptured);
        CobblemonEvents.BOBBER_SPAWN_POKEMON_POST.subscribe(LureManager::onFishCaught);
        CobblemonEvents.POKEROD_CAST_PRE.subscribe(LureManager::onPokerodCast);
        CobblemonEvents.POKE_BALL_CAPTURE_CALCULATED.subscribe(LureManager::onCaptureCalculated);
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe(LureManager::onExperienceGained);
        CobblemonEvents.FRIENDSHIP_UPDATED.subscribe(LureManager::onFriendshipUpdated);
    }

    // === Spawn: bônus de ultra raro (por pedido explícito, shiny/IV NÃO mexem aqui — só na
    // captura, ver onPokemonCaptured) ===
    private static void onSpawnBucketChosen(SpawnBucketChosenEvent event) {
        SpawnCause cause = event.getSpawnCause();
        Entity causeEntity = cause != null ? cause.getEntity() : null;
        if (!(causeEntity instanceof ServerPlayerEntity player)) return;

        List<CosmeticData.LureStats> lures = collectActiveLureStats(player);
        if (lures.isEmpty()) return;

        double bonus = sumOf(lures, l -> l.lureUltraRAREMultiplier);
        if (bonus <= 0 || RANDOM.nextDouble() >= bonus) return;

        SpawnBucket current = event.getBucket();
        if (current != null && "ultra-rare".equals(current.getName())) return;

        for (SpawnBucket candidate : event.getBucketWeights().keySet()) {
            if ("ultra-rare".equals(candidate.getName())) {
                event.setBucket(candidate);
                break;
            }
        }
    }

    // === Captura: shiny (reroll), IV garantido/chance, hidden ability — por pedido explícito do
    // dev, shiny/IV são decididos AQUI (na captura), não no spawn. ===
    private static void onPokemonCaptured(PokemonCapturedEvent event) {
        ServerPlayerEntity player = event.getPlayer();
        Pokemon pokemon = event.getPokemon();
        List<CosmeticData.LureStats> lures = collectActiveLureStats(player);
        if (lures.isEmpty()) return;

        if (!pokemon.getShiny()) {
            double shinyChance = sumOf(lures, l -> l.lureShinyMultiplier);
            if (shinyChance > 0 && RANDOM.nextDouble() < shinyChance) {
                pokemon.setShiny(true);
            }
        }

        int guaranteedIv = (int) Math.round(sumOf(lures, l -> l.lureIV));
        double ivChance = sumOf(lures, l -> l.lureChanceIV);
        grantPerfectIvs(pokemon, guaranteedIv, ivChance);

        double hiddenAbilityChance = sumOf(lures, l -> l.lureHiddenAbilityMultiplier);
        if (hiddenAbilityChance > 0 && RANDOM.nextDouble() < hiddenAbilityChance) {
            AbilityChanger.Companion.getHIDDEN_ABILITY().performChange(pokemon);
        }
    }

    // === Pesca: bônus extra de shiny/IV específico de vara, somado ao de POKEMON_CAPTURED ===
    private static void onFishCaught(BobberSpawnPokemonEvent.Post event) {
        GreatCosmetics.debugLog("LureManager.onFishCaught: evento disparado.");

        PlayerEntity ownerRaw = event.getBobber().getPlayerOwner();
        if (!(ownerRaw instanceof ServerPlayerEntity player)) {
            GreatCosmetics.debugLog("LureManager.onFishCaught: bobber with no ServerPlayerEntity owner (owner=" + ownerRaw + ") — aborting.");
            return;
        }

        Pokemon pokemon = event.getPokemon().getPokemon();
        List<CosmeticData.LureStats> lures = collectActiveLureStats(player);
        GreatCosmetics.debugLog("LureManager.onFishCaught: player=" + player.getName().getString()
                + " lures ativos=" + lures.size() + " shinyAntes=" + pokemon.getShiny());
        if (lures.isEmpty()) return;

        if (!pokemon.getShiny()) {
            double shinyChance = sumOf(lures, l -> l.lurePescaShiny);
            boolean hit = shinyChance > 0 && RANDOM.nextDouble() < shinyChance;
            GreatCosmetics.debugLog("LureManager.onFishCaught: lurePescaShiny=" + shinyChance + " hit=" + hit);
            if (hit) pokemon.setShiny(true);
        }

        int guaranteedIv = (int) Math.round(sumOf(lures, l -> l.lurePescaIv));
        double ivChance = sumOf(lures, l -> l.lurePescaIvChance);
        GreatCosmetics.debugLog("LureManager.onFishCaught: lurePescaIv=" + guaranteedIv + " lurePescaIvChance=" + ivChance);
        grantPerfectIvs(pokemon, guaranteedIv, ivChance);
    }

    // === Velocidade de pesca: soma "níveis" equivalentes ao encantamento Lure do Cobblemon —
    // é o único jeito PÚBLICO de reduzir o tempo de espera da fisgada (waitCountdown é privado,
    // sem setter; lureLevel tem getter/setter público e é o que o Cobblemon usa por baixo dos
    // panos pra calcular o countdown). lurePescaVelocidade é 0.0-1.0 (%) no lore do item — convertido
    // aqui pra níveis inteiros (cada ~33% ≈ +1 nível, até um teto de +10, pra não virar 0 tick fixo
    // com valor exagerado de propósito ou engano). ===
    private static void onPokerodCast(PokerodCastEvent.Pre event) {
        PokeRodFishingBobberEntity bobber = event.getBobber();
        if (bobber == null) {
            GreatCosmetics.debugLog("LureManager.onPokerodCast: event with no bobber — aborting.");
            return;
        }

        PlayerEntity ownerRaw = bobber.getPlayerOwner();
        if (!(ownerRaw instanceof ServerPlayerEntity player)) {
            GreatCosmetics.debugLog("LureManager.onPokerodCast: bobber with no ServerPlayerEntity owner (owner=" + ownerRaw + ") — aborting.");
            return;
        }

        double bonusPercent = sumOf(collectActiveLureStats(player), l -> l.lurePescaVelocidade);
        int extraLevels = (int) Math.min(10, Math.round(bonusPercent * 3));
        GreatCosmetics.debugLog("LureManager.onPokerodCast: player=" + player.getName().getString()
                + " lurePescaVelocidade=" + bonusPercent + " extraLevels=" + extraLevels
                + " lureLevelAntes=" + bobber.getLureLevel());
        if (extraLevels > 0) {
            bobber.setLureLevel(bobber.getLureLevel() + extraLevels);
        }
    }

    // === Chance de captura: reroll de uma captura que falhou ===
    private static void onCaptureCalculated(PokeBallCaptureCalculatedEvent event) {
        if (event.getCaptureResult().isSuccessfulCapture()) return;

        LivingEntity thrower = event.getThrower();
        if (!(thrower instanceof ServerPlayerEntity player)) return;

        double bonus = sumOf(collectActiveLureStats(player), l -> l.lureChanceDeCaptura);
        if (bonus > 0 && RANDOM.nextDouble() < bonus) {
            event.setCaptureResult(CaptureContext.Companion.successful(false));
        }
    }

    // === Exp: multiplica o ganho de experiência do Pokémon do dono ===
    private static void onExperienceGained(ExperienceGainedEvent.Pre event) {
        Pokemon pokemon = event.getPokemon();
        ServerPlayerEntity owner = pokemon.getOwnerPlayer();
        if (owner == null) return;

        List<CosmeticData.LureStats> lures = collectActiveLureStats(owner);
        if (lures.isEmpty()) return;

        double bonus = sumOf(lures, l -> l.lureExpAllMultiplier) + sumOf(lures, l -> l.lureEXP);
        if (bonus > 0) {
            event.setExperience((int) Math.round(event.getExperience() * (1.0 + bonus)));
        }
    }

    // === Amizade: multiplica o ganho positivo de amizade do Pokémon do dono ===
    private static void onFriendshipUpdated(FriendshipUpdatedEvent event) {
        Pokemon pokemon = event.getPokemon();
        ServerPlayerEntity owner = pokemon.getOwnerPlayer();
        if (owner == null) return;

        int delta = event.getNewFriendship() - pokemon.getFriendship();
        if (delta <= 0) return;

        double bonus = sumOf(collectActiveLureStats(owner), l -> l.lureAmizadeMultiplier);
        if (bonus > 0) {
            int boosted = pokemon.getFriendship() + (int) Math.round(delta * (1.0 + bonus));
            event.setNewFriendship(Math.min(boosted, 255));
        }
    }

    /** Sorteia IVs perfeitas (31) em stats aleatórios: {@code guaranteed} garantidas + 1 extra
     *  com chance {@code bonusChance} (0.0-1.0), sem repetir stat nem passar de 6 (todas). */
    private static void grantPerfectIvs(Pokemon pokemon, int guaranteed, double bonusChance) {
        if (guaranteed <= 0 && bonusChance <= 0) return;

        List<Stat> stats = new ArrayList<>(Stats.Companion.getPERMANENT());
        Collections.shuffle(stats, RANDOM);

        int toPerfect = Math.min(Math.max(guaranteed, 0), stats.size());
        if (bonusChance > 0 && toPerfect < stats.size() && RANDOM.nextDouble() < bonusChance) {
            toPerfect++;
        }

        for (int i = 0; i < toPerfect; i++) {
            pokemon.getIvs().set(stats.get(i), IVs.MAX_VALUE);
        }
    }

    private static double sumOf(List<CosmeticData.LureStats> lures, ToDoubleFunction<CosmeticData.LureStats> extractor) {
        double total = 0.0;
        for (CosmeticData.LureStats l : lures) total += extractor.applyAsDouble(l);
        return total;
    }

    /** Junta os LureStats ATIVOS (enabled=true) de tudo que o player tem equipado agora: tanto
     *  cosméticos virtuais (slots do wardrobe, via player_equipped_cosmetics) quanto armaduras
     *  reais convertidas em cosmético que ele está vestindo de verdade (ver ArmorCosmeticsConfig
     *  — essas não passam pela tabela de equip, "equipada" é literalmente estar no slot). */
    private static List<CosmeticData.LureStats> collectActiveLureStats(ServerPlayerEntity player) {
        List<CosmeticData.LureStats> result = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (String id : DatabaseManager.getPlayerEquippedCosmetics(player.getUuid())) {
            if (id == null || !seenIds.add(id.toLowerCase())) continue;
            addIfActive(result, GreatCosmetics.getCosmeticById(id));
        }

        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getEquippedStack(slot);
            if (stack.isEmpty()) continue;
            String id = ArmorCosmeticsConfig.getCosmeticIdForItem(stack.getItem());
            if (id == null || !seenIds.add(id.toLowerCase())) continue;
            addIfActive(result, ArmorCosmeticsConfig.getSyntheticCosmetic(id));
        }

        return result;
    }

    private static void addIfActive(List<CosmeticData.LureStats> result, CosmeticData data) {
        if (data != null && data.lure != null && data.lure.enabled) {
            result.add(data.lure);
        }
    }
}
