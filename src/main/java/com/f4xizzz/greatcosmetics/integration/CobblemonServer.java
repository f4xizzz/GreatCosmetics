package com.f4xizzz.greatcosmetics.integration;

import com.f4xizzz.greatcosmetics.GreatCosmetics;
import com.f4xizzz.greatcosmetics.config.LangConfig;
import com.f4xizzz.greatcosmetics.config.PokemonSkin;
import com.f4xizzz.greatcosmetics.network.ClearPokemonSkinsPayload;
import com.f4xizzz.greatcosmetics.network.EquipPokemonSkinPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * TODO o código server-side que toca {@code com.cobblemon.*}: os receivers de skin/PC de Pokémon
 * e o scanner (IV/nature/ability/size). Só é carregado/tocado quando o Cobblemon está instalado
 * — {@link GreatCosmetics#onInitialize()} chama {@link #init()} atrás de
 * {@code ModCompat.cobblemon()}. NENHUMA classe sempre-carregada pode referenciar esta aqui a
 * não ser pelo {@code invokestatic} de {@code init()} (lazy).
 *
 * <p>Os TIPOS de payload continuam registrados incondicionalmente no {@code onInitialize}
 * (protocolo simétrico, sem Cobblemon); só os RECEIVERS moram aqui.
 */
public final class CobblemonServer {

    private CobblemonServer() {}

    private static final double SCAN_RANGE = 48.0;

    /** Liga o LureManager, planta o hook de scanner e registra os receivers de skin/PC. */
    public static void init() {
        com.f4xizzz.greatcosmetics.util.LureManager.register();
        GreatCosmetics.scanPushHook = CobblemonServer::pushScanData;

        // ── REMOVER SKINS (MODO DEV) ───────────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(ClearPokemonSkinsPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                if (GreatCosmetics.licenseBlocked(player)) return;
                if (!GreatCosmetics.isRealOperator(player) && !GreatCosmetics.checkPermission(player, "gc.dev")) {
                    player.sendMessage(LangConfig.chat("messages.devstudio.no_perm_clear_skins", context.server().getRegistryManager()), false);
                    return;
                }

                com.cobblemon.mod.common.api.storage.party.PlayerPartyStore party =
                        com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player);
                boolean changed = false;

                if (payload.slot() == -1) {
                    for (int i = 0; i < party.size(); i++) {
                        com.cobblemon.mod.common.pokemon.Pokemon p = party.get(i);
                        if (p != null && removeAllSkinAspects(p)) changed = true;
                    }
                } else {
                    com.cobblemon.mod.common.pokemon.Pokemon p = party.get(payload.slot());
                    if (p != null && removeAllSkinAspects(p)) changed = true;
                }

                if (changed) {
                    GreatCosmetics.playCustomSound(player, "equip_item");
                    player.sendMessage(LangConfig.chat("messages.skin.removed_success", context.server().getRegistryManager()), false);
                } else {
                    player.sendMessage(LangConfig.chat("messages.skin.none_detected", context.server().getRegistryManager()), false);
                }
            });
        });

        // ── EQUIPAR SKIN NO POKEMON DA PARTY ──────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(EquipPokemonSkinPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                if (GreatCosmetics.licenseBlocked(player)) return;

                GreatCosmetics.debugLog("EquipPokemonSkinPayload from " + player.getName().getString() + ": skinId='" + payload.skinId() + "' slot=" + payload.slot());

                PokemonSkin skin = com.f4xizzz.greatcosmetics.config.SkinConfigManager.getSkin(payload.skinId());
                if (skin == null) {
                    GreatCosmetics.debugLog("EquipPokemonSkinPayload: skin '" + payload.skinId() + "' not found in the catalog.");
                    return;
                }

                com.cobblemon.mod.common.api.storage.party.PlayerPartyStore party =
                        com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player);
                com.cobblemon.mod.common.pokemon.Pokemon targetPokemon = party.get(payload.slot());
                if (targetPokemon == null) {
                    GreatCosmetics.debugLog("EquipPokemonSkinPayload: slot " + payload.slot() + " empty in the party of " + player.getName().getString() + ".");
                    return;
                }

                boolean isOp = GreatCosmetics.isRealOperator(player);

                if (!isOp) {
                    java.util.List<String> unlocked = com.f4xizzz.greatcosmetics.database.DatabaseManager.getPlayerUnlockedSkins(player.getUuid());
                    if (!unlocked.contains(skin.getId())) return;
                }

                if (!targetPokemon.getSpecies().getName().equalsIgnoreCase(skin.getSpecies())) {
                    player.sendMessage(LangConfig.chat("messages.skin.incompatible", context.server().getRegistryManager()), true);
                    return;
                }

                long now = System.currentTimeMillis();

                if (!isOp) {
                    long lastApplied = com.f4xizzz.greatcosmetics.database.DatabaseManager.getSkinLastApplied(player.getUuid(), skin.getId());
                    long cooldownMs = skin.getCooldownMinutes() * 60 * 1000L;
                    if (now - lastApplied < cooldownMs) {
                        player.sendMessage(LangConfig.chat("messages.skin.on_cooldown", context.server().getRegistryManager()), true);
                        return;
                    }
                }

                removeAllSkinAspects(targetPokemon);

                String rawAspect = skin.getAspect().toLowerCase().trim();

                if (rawAspect.startsWith("f=") || rawAspect.startsWith("form=") || rawAspect.startsWith("f:") || rawAspect.startsWith("form:")) {
                    String expectedForm = rawAspect.replace("form=", "").replace("f=", "").replace("form:", "").replace("f:", "").trim();
                    com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse("f=" + expectedForm).apply(targetPokemon);
                } else {
                    java.util.Set<String> newAspects = new java.util.HashSet<>(targetPokemon.getForcedAspects());
                    for (String part : rawAspect.split(" ")) {
                        String cleanPart = part.trim();
                        if (!cleanPart.isEmpty()) {
                            if (cleanPart.contains(":")) cleanPart = cleanPart.substring(cleanPart.indexOf(":") + 1).trim();
                            else if (cleanPart.contains("=")) cleanPart = cleanPart.substring(cleanPart.indexOf("=") + 1).trim();
                            newAspects.add(cleanPart);
                        }
                    }
                    targetPokemon.setForcedAspects(newAspects);
                }

                targetPokemon.updateAspects();

                com.f4xizzz.greatcosmetics.database.DatabaseManager.setSkinCooldown(player.getUuid(), skin.getId(), now);
                com.f4xizzz.greatcosmetics.command.CosmeticsCommand.syncPlayerSkins(player);
                GreatCosmetics.debugLog("EquipPokemonSkinPayload: skin '" + skin.getId() + "' applied to the Pokémon in slot " + payload.slot() + " of " + player.getName().getString() + ".");

                GreatCosmetics.playCustomSound(player, "equip_item");
                player.sendMessage(LangConfig.chat("messages.skin.applied", context.server().getRegistryManager()), true);
            });
        });

        // ── BOTÃO PC DA PARTYPAGE ─────────────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.OpenPcFromWardrobePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                if (GreatCosmetics.licenseBlocked(player)) return;
                try {
                    if (com.cobblemon.mod.common.util.PlayerExtensionsKt.isInBattle(player)) return;

                    com.cobblemon.mod.common.api.storage.pc.PCStore pcStore =
                            com.cobblemon.mod.common.util.PlayerExtensionsKt.pc(player);

                    // PCLink puro (sem PermissiblePcLink) de propósito — ver comentário no callsite antigo.
                    com.cobblemon.mod.common.api.storage.pc.link.PCLinkManager.INSTANCE.addLink(
                            new com.cobblemon.mod.common.api.storage.pc.link.PCLink(pcStore, player.getUuid()));
                    new com.cobblemon.mod.common.net.messages.client.storage.pc.OpenPCPacket(pcStore, null).sendToPlayer(player);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        });
    }

    /** Zera os aspectos de skin que o {@code SkinConfigManager} conhece para a espécie do Pokémon.
     *  Portado 1:1 do antigo {@code GreatCosmetics.removeAllSkinAspects}. true se mudou algo. */
    public static boolean removeAllSkinAspects(com.cobblemon.mod.common.pokemon.Pokemon pokemon) {
        boolean changed = false;
        String currentSpecies = pokemon.getSpecies().getName().toLowerCase();
        java.util.Set<String> forcedAspects = new java.util.HashSet<>(pokemon.getForcedAspects());

        for (PokemonSkin skin : com.f4xizzz.greatcosmetics.config.SkinConfigManager.getAllSkins()) {
            if (skin.getSpecies().toLowerCase().equals(currentSpecies)) {
                String rawAspect = skin.getAspect().toLowerCase().trim();

                if (rawAspect.startsWith("f=") || rawAspect.startsWith("form=") || rawAspect.startsWith("f:") || rawAspect.startsWith("form:")) {
                    String expectedForm = rawAspect.replace("form=", "").replace("f=", "").replace("form:", "").replace("f:", "").trim();
                    if (pokemon.getForm().getName().toLowerCase().contains(expectedForm.toLowerCase())) {
                        pokemon.setForm(pokemon.getSpecies().getStandardForm());
                        changed = true;
                    }
                } else {
                    String[] parts = rawAspect.split(" ");
                    for (String part : parts) {
                        String cleanPart = part.trim();
                        if (!cleanPart.isEmpty()) {
                            String afterColon = cleanPart.contains(":") ? cleanPart.substring(cleanPart.indexOf(":") + 1).trim() : cleanPart;
                            String afterEquals = cleanPart.contains("=") ? cleanPart.substring(cleanPart.indexOf("=") + 1).trim() : cleanPart;

                            java.util.Set<String> toRemove = new java.util.HashSet<>();
                            for (String aspect : forcedAspects) {
                                if (aspect.equalsIgnoreCase(cleanPart) || aspect.equalsIgnoreCase(afterColon) || aspect.equalsIgnoreCase(afterEquals)) {
                                    toRemove.add(aspect);
                                }
                            }
                            if (!toRemove.isEmpty()) {
                                forcedAspects.removeAll(toRemove);
                                changed = true;
                            }
                        }
                    }
                }
            }
        }

        if (changed) {
            pokemon.setForcedAspects(forcedAspects);
            pokemon.updateAspects();
        }
        return changed;
    }

    /** Hook de {@code GreatCosmetics.scanPushHook}: varre os Pokémon num raio do jogador e empurra
     *  o {@code SyncPokemonScanPayload}. {@code want} = {iv, nature, ability, size}. Portado 1:1
     *  do antigo {@code GreatCosmetics.pushNearbyPokemonScanData}. */
    public static void pushScanData(ServerPlayerEntity player, boolean[] want) {
        try {
            ServerWorld world = player.getServerWorld();
            net.minecraft.util.math.Box box = player.getBoundingBox().expand(SCAN_RANGE);
            java.util.List<com.cobblemon.mod.common.entity.pokemon.PokemonEntity> mons =
                    world.getEntitiesByClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity.class, box, e -> true);
            com.cobblemon.mod.common.api.pokemon.stats.Stat[] stats = {
                    com.cobblemon.mod.common.api.pokemon.stats.Stats.HP,
                    com.cobblemon.mod.common.api.pokemon.stats.Stats.ATTACK,
                    com.cobblemon.mod.common.api.pokemon.stats.Stats.DEFENCE,
                    com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_ATTACK,
                    com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_DEFENCE,
                    com.cobblemon.mod.common.api.pokemon.stats.Stats.SPEED };
            java.util.Map<Integer, com.f4xizzz.greatcosmetics.network.SyncPokemonScanPayload.Entry> out = new java.util.HashMap<>();
            for (com.cobblemon.mod.common.entity.pokemon.PokemonEntity pe : mons) {
                com.cobblemon.mod.common.pokemon.Pokemon pk = pe.getPokemon();
                if (pk == null) continue;
                int[] iv = null;
                if (want[0]) {
                    iv = new int[6];
                    for (int i = 0; i < 6; i++) iv[i] = pk.getIvs().getOrDefault(stats[i]);
                }
                String nature = "";
                if (want[1]) try { nature = pk.getNature().getDisplayName(); } catch (Throwable ignored) {}
                String ability = "";
                if (want[2]) try { ability = pk.getAbility().getDisplayName(); } catch (Throwable ignored) {}
                boolean hasSize = false;
                float scale = 0f;
                // scaleModifier CRU — a categoria (XS/S/...) é calculada no client (fromScale()),
                // pra bater com a Pokédex (o cálculo depende de ServerSettings, só populado no client).
                if (want[3]) try { scale = pk.getScaleModifier(); hasSize = true; } catch (Throwable ignored) {}
                out.put(pe.getId(), new com.f4xizzz.greatcosmetics.network.SyncPokemonScanPayload.Entry(iv, nature, ability, hasSize, scale));
            }
            ServerPlayNetworking.send(player, new com.f4xizzz.greatcosmetics.network.SyncPokemonScanPayload(out));
        } catch (Throwable t) {
            GreatCosmetics.debugLog("CobblemonServer.pushScanData failed: " + t);
        }
    }
}
