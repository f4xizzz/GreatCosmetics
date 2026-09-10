package com.f4xizzz.greatcosmetics.util;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.entity.SpawnBucketChosenEvent;
import com.cobblemon.mod.common.api.events.fishing.BobberSpawnPokemonEvent;
import com.cobblemon.mod.common.api.events.fishing.PokerodCastEvent;
import com.cobblemon.mod.common.entity.fishing.PokeRodFishingBobberEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.api.events.pokeball.PokeBallCaptureCalculatedEvent;
import com.cobblemon.mod.common.api.events.pokemon.EvGainedEvent;
import com.cobblemon.mod.common.api.events.pokemon.ExperienceGainedEvent;
import com.cobblemon.mod.common.api.events.pokemon.FriendshipUpdatedEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.item.ability.AbilityChanger;
import com.cobblemon.mod.common.api.pokeball.catching.CaptureContext;
import com.cobblemon.mod.common.api.pokemon.experience.CandyExperienceSource;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.spawning.SpawnCause;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence;
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawnerFactory;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.IVs;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.jvm.functions.Function1;
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
 * dev):
 *   - SPAWN_BUCKET_CHOSEN: bônus de ultra raro (qual BUCKET de raridade é escolhido) em spawn de
 *     terra. Dá pra amarrar a um player porque SpawnCause.getEntity(), pra spawn natural via
 *     PlayerSpawner, É o player dono daquele spawner.
 *   - lureTYPE ("Tipo Afetado"): influência de spawn POR JOGADOR no PlayerSpawner do Cobblemon
 *     (registerTypeLureSpawnInfluence) — com lure de tipo ativo, só espécies desse tipo entram
 *     na seleção do Cobblemon, que spawna normalmente entre elas; bioma sem nenhuma espécie do
 *     tipo → nada spawna. POKEMON_ENTITY_SPAWN fica como rede de segurança: cancela um Pokémon
 *     SELVAGEM de tipo errado que tenha escapado da influência (spawn de outra fonte).
 *   - POKEMON_CAPTURED: shiny (reroll se ainda não é), IV garantido + chance POR IV, hidden
 *     ability — tudo no momento da captura.
 *   - BOBBER_SPAWN_POKEMON_POST: mesma coisa (shiny/IV), só que os campos *Pesca*, quando o
 *     Pokémon veio de vara — bônus somado, independente do de POKEMON_CAPTURED que roda depois.
 *   - POKEROD_CAST_PRE: velocidade de pesca (lurePescaVelocidade).
 *   - POKE_BALL_CAPTURE_CALCULATED: chance de captura (reroll uma captura que falhou).
 *   - EXPERIENCE_GAINED_EVENT_PRE: multiplicador de exp (lureEXP) + "Exp Share" (lureExpAllMultiplier
 *     > 0 = liga; o valor é a fração que CADA outro Pokémon da party recebe do total que o ativo
 *     ganhou, 1.0 = igual). NÃO compartilha quando a fonte é doce (CandyExperienceSource).
 *   - FRIENDSHIP_UPDATED: multiplicador de amizade.
 *
 *   - EV_GAINED_EVENT_PRE: multiplicador de EV (lureEV) — Cobblemon 1.8 passou a expor esse hook
 *     com setAmount().
 */
public class LureManager {

    private static final Random RANDOM = new Random();

    /** Enquanto true, estamos redistribuindo XP pra party (Exp Share) — o handler de XP ignora
     *  esses eventos re-disparados pra não recursionar nem re-multiplicar. */
    private static final ThreadLocal<Boolean> SHARING_XP = ThreadLocal.withInitial(() -> false);

    public static void register() {
        CobblemonEvents.SPAWN_BUCKET_CHOSEN.subscribe(LureManager::onSpawnBucketChosen);
        CobblemonEvents.POKEMON_CAPTURED.subscribe(LureManager::onPokemonCaptured);
        CobblemonEvents.BOBBER_SPAWN_POKEMON_POST.subscribe(LureManager::onFishCaught);
        CobblemonEvents.POKEROD_CAST_PRE.subscribe(LureManager::onPokerodCast);
        CobblemonEvents.POKE_BALL_CAPTURE_CALCULATED.subscribe(LureManager::onCaptureCalculated);
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe(LureManager::onExperienceGained);
        CobblemonEvents.EV_GAINED_EVENT_PRE.subscribe(LureManager::onEvGained);
        CobblemonEvents.FRIENDSHIP_UPDATED.subscribe(LureManager::onFriendshipUpdated);
        registerTypeLureSpawnInfluence();
    }

    /** Injeta uma influência de spawn POR JOGADOR no PlayerSpawner do Cobblemon: com um lure de
     *  tipo ativo, o PESO dos SpawnDetail do(s) tipo(s) do lure é multiplicado MUITO e o do resto é
     *  reduzido bastante (não zerado). Resultado: ~95% dos spawns em volta do jogador são do tipo,
     *  mas o spawn continua rápido (sem "buracos" — sempre sobra algo pro spawner escolher). A
     *  influência lê os lures ao vivo (cache de 2s), então equipar/desequipar não precisa de relog. */
    private static void registerTypeLureSpawnInfluence() {
        try {
            PlayerSpawnerFactory factory = PlayerSpawnerFactory.INSTANCE;
            List<Function1<net.minecraft.server.network.ServerPlayerEntity, SpawningInfluence>> builders =
                    new ArrayList<>(factory.getInfluenceBuilders());
            builders.add(new Function1<>() {
                @Override
                public SpawningInfluence invoke(net.minecraft.server.network.ServerPlayerEntity p) {
                    return new TypeLureInfluence(p);
                }
            });
            factory.setInfluenceBuilders(builders);
        } catch (Throwable t) {
            GreatCosmetics.debugLog("LureManager: falhou ao registrar a influência de spawn de tipo — " + t);
        }
    }

    /** Influência de spawn do lure de tipo, uma por jogador. Em vez de VETAR (que criava buracos de
     *  10-15s sem spawn), multiplica o peso: tipo do lure ×{@link #MATCH_BOOST}, resto
     *  ×{@link #MISS_SUPPRESS}. */
    private static final class TypeLureInfluence implements SpawningInfluence {
        private static final float MATCH_BOOST = 30.0f;
        private static final float MISS_SUPPRESS = 0.02f;

        private final net.minecraft.server.network.ServerPlayerEntity player;
        private long cachedAtMs = 0L;
        private Set<String> cachedTypes = Collections.emptySet();

        TypeLureInfluence(net.minecraft.server.network.ServerPlayerEntity player) {
            this.player = player;
        }

        private Set<String> wantedTypes() {
            long now = System.currentTimeMillis();
            if (now - cachedAtMs < 2000L) return cachedTypes;
            cachedAtMs = now;
            Set<String> w = new HashSet<>();
            for (CosmeticData.LureStats l : collectActiveLureStats(player)) {
                if (l.lureTYPE != null && !l.lureTYPE.isBlank()) w.add(l.lureTYPE.trim().toLowerCase(java.util.Locale.ROOT));
            }
            cachedTypes = w;
            return w;
        }

        private boolean matchesLure(SpawnDetail detail) {
            Set<String> wanted = wantedTypes();
            if (wanted.isEmpty()) return true;
            for (String label : detail.getLabels()) {
                if (label != null && wanted.contains(label.toLowerCase(java.util.Locale.ROOT))) return true;
            }
            return false;
        }

        @Override
        public float affectWeight(SpawnDetail detail, SpawnablePosition position, float weight) {
            if (wantedTypes().isEmpty()) return weight;
            return matchesLure(detail) ? weight * MATCH_BOOST : weight * MISS_SUPPRESS;
        }

        @Override
        public boolean isExpired() {
            return player.isRemoved();
        }
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

        // Cobblemon 1.8 removeu a classe SpawnBucket — os buckets viraram só o nome (String) e
        // getBucketWeights() é um Map<String, Float>. "ultra-rare" continua sendo o nome do bucket
        // de raridade mais alta nos spawn pools do Cobblemon.
        if ("ultra-rare".equals(event.getBucket())) return;
        if (event.getBucketWeights().containsKey("ultra-rare")) {
            event.setBucket("ultra-rare");
        }
    }

    // NOTA: o filtro de TIPO (lureTYPE) agora é 100% via TypeLureInfluence#affectWeight (boost de
    // peso, não veto). O antigo hook POKEMON_ENTITY_SPAWN que CANCELAVA spawns de tipo errado foi
    // removido de propósito — era ele que deixava o jogador sem NENHUM spawn por 10-15s em bioma
    // pobre no tipo, e o usuário pediu "~95% do tipo, mas spawn rápido".

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

    // === Exp: multiplica o ganho do Pokémon que lutou (lureEXP) e, se Exp Share estiver ligado
    // (lureExpAllMultiplier > 0), passa uma fração desse total pra cada outro Pokémon vivo da
    // party. Doce (Candy) não é afetado. ===
    private static void onExperienceGained(ExperienceGainedEvent.Pre event) {
        if (SHARING_XP.get()) return; // é XP que a gente mesmo redistribuiu — não reprocessa

        Pokemon pokemon = event.getPokemon();
        ServerPlayerEntity owner = pokemon.getOwnerPlayer();
        if (owner == null) return;
        if (event.getSource() instanceof CandyExperienceSource) return; // lure não mexe em doce

        List<CosmeticData.LureStats> lures = collectActiveLureStats(owner);
        if (lures.isEmpty()) return;

        // 1) multiplicador de XP do Pokémon que ganhou
        double mult = 1.0 + sumOf(lures, l -> l.lureEXP);
        int finalXp = (int) Math.round(event.getExperience() * mult);
        if (finalXp != event.getExperience()) event.setExperience(finalXp);

        // 2) Exp Share: cada outro Pokémon vivo da party recebe (finalXp * fração)
        double shareFrac = sumOf(lures, l -> l.lureExpAllMultiplier);
        if (shareFrac <= 0) return;
        int shareXp = (int) Math.round(finalXp * Math.min(shareFrac, 1.0));
        if (shareXp <= 0) return;

        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(owner);
        if (party == null) return;

        java.util.UUID gainerId = pokemon.getUuid();
        SHARING_XP.set(true);
        try {
            for (Pokemon p : party) {
                if (p == null || p.getUuid().equals(gainerId) || p.isFainted()) continue;
                p.addExperienceWithPlayer(owner, event.getSource(), shareXp);
            }
        } finally {
            SHARING_XP.set(false);
        }
    }

    // === EV: multiplica os EVs ganhos em batalha pelo Pokémon do dono (lureEV). Cobblemon 1.8
    // finalmente expõe EV_GAINED_EVENT_PRE com setAmount() — em 1.7 não tinha hook (era "impossível").
    // O valor de lureEV é o EXTRA (0.5 = +50%); 0 = sem efeito. ===
    private static void onEvGained(EvGainedEvent.Pre event) {
        Pokemon pokemon = event.getPokemon();
        ServerPlayerEntity owner = pokemon.getOwnerPlayer();
        if (owner == null) return;

        double extra = sumOf(collectActiveLureStats(owner), l -> l.lureEV);
        if (extra <= 0) return;

        int boosted = (int) Math.round(event.getAmount() * (1.0 + extra));
        if (boosted != event.getAmount()) event.setAmount(Math.max(0, boosted));
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

    /** Perfeição de IVs: {@code guaranteed} stats aleatórios sempre viram 31, e CADA um dos stats
     *  restantes tem chance {@code perIvChance} (0.0-1.0) de também virar 31 (rolagem por IV). */
    private static void grantPerfectIvs(Pokemon pokemon, int guaranteed, double perIvChance) {
        if (guaranteed <= 0 && perIvChance <= 0) return;

        List<Stat> stats = new ArrayList<>(Stats.Companion.getPERMANENT());
        Collections.shuffle(stats, RANDOM);

        int g = Math.min(Math.max(guaranteed, 0), stats.size());
        for (int i = 0; i < stats.size(); i++) {
            boolean perfect = i < g || (perIvChance > 0 && RANDOM.nextDouble() < perIvChance);
            if (perfect) pokemon.getIvs().set(stats.get(i), IVs.MAX_VALUE);
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
        // Bloqueio de efeitos por grupo (MainConfig.effectBlockGroups) — nenhum lure vale.
        if (GreatCosmetics.cosmeticEffectsBlockedFor(player)) return java.util.Collections.emptyList();

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
