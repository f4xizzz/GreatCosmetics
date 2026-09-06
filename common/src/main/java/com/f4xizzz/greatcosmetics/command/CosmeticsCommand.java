package com.f4xizzz.greatcosmetics.command;

import com.f4xizzz.greatcosmetics.config.CosmeticData;
import com.f4xizzz.greatcosmetics.config.CosmeticsConfig;
import com.f4xizzz.greatcosmetics.config.LangConfig;
import com.f4xizzz.greatcosmetics.config.NpcCosmeticsConfig;
import com.f4xizzz.greatcosmetics.config.PokemonSkin;
import com.f4xizzz.greatcosmetics.config.SkinConfigManager;
import com.f4xizzz.greatcosmetics.database.DatabaseManager;
import com.f4xizzz.greatcosmetics.network.SyncCosmeticsPayload;
import com.f4xizzz.greatcosmetics.util.BackpackManager;
import com.f4xizzz.greatcosmetics.util.WardrobeManager;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.networking.NetworkManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CosmeticsCommand {

    /**
     * Gate de permissão POR COMANDO: OP (nível 2) OU a permission node específica desse
     * comando. Sem OP e sem a permissão explícita concedida (ex: via LuckPerms), o comando fica
     * bloqueado — não existe "permissão genérica do mod", cada subcomando tem a sua própria
     * (ex: "gc.command.give", "gc.command.npc.glow"), então dar acesso a um não libera os outros.
     */
    private static boolean hasCmdPermission(CommandSourceStack source, String node) {
        if (source.getEntity() instanceof ServerPlayer player) {
            // isRealOperator() em vez de source.hasPermissionLevel(2) de propósito — com mods
            // tipo Vanilla Permissions instalados, hasPermissionLevel(2) podia vir true pra
            // qualquer jogador mesmo sem OP e sem permissão nenhuma.
            return com.f4xizzz.greatcosmetics.GcServer.isRealOperator(player) || com.f4xizzz.greatcosmetics.GcServer.checkPermission(player, node);
        }
        // Console/command block: não tem GameProfile pra checar ops.json, então confia no nível
        // vanilla mesmo (console sempre tem nível 4 de verdade, isso nunca foi o ponto quebrado).
        return source.hasPermission(2);
    }

    public static final SuggestionProvider<CommandSourceStack> SUGGEST_COSMETICS = (context, builder) -> {
        for (CosmeticData data : CosmeticsConfig.cosmeticsMap.values()) {
            if (data.id != null && !data.id.isEmpty()) {
                if (data.id.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                    builder.suggest(data.id);
                }
            }
        }
        for (String id : com.f4xizzz.greatcosmetics.config.ArmorCosmeticsConfig.armorCosmetics.keySet()) {
            if (id.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    };

    public static final SuggestionProvider<CommandSourceStack> SUGGEST_GLOW_COLORS = (context, builder) -> {
        for (net.minecraft.ChatFormatting cor : net.minecraft.ChatFormatting.values()) {
            if (cor.isColor() && cor.getName().toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest(cor.getName().toLowerCase());
            }
        }
        return builder.buildFuture();
    };

    public static final SuggestionProvider<CommandSourceStack> SUGGEST_TAGS = (context, builder) -> {
        for (String id : com.f4xizzz.greatcosmetics.config.TagsConfig.tagsMap.keySet()) {
            if (id.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    };

    public static final SuggestionProvider<CommandSourceStack> SUGGEST_SKINS = (context, builder) -> {
        for (PokemonSkin skin : SkinConfigManager.getAllSkins()) {
            if (skin.getId() != null && !skin.getId().isEmpty()) {
                if (skin.getId().toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                    builder.suggest(skin.getId());
                }
            }
        }
        return builder.buildFuture();
    };

    public static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, environment) -> {

            LiteralCommandNode<CommandSourceStack> mainNode = dispatcher.register(
                    Commands.literal("greatcosmetics")
                            // --- COMANDO: ACTIVATION (licença do mod — ver security.ActivationManager) ---
                            // Nunca pode ser bloqueado pelo próprio gate de licença, senão o dono não
                            // consegue ativar. Level 4 / node = dono/console.
                            .then(Commands.literal("activation")
                                    .requires(source -> hasCmdPermission(source, "gc.command.activation"))
                                    .then(Commands.argument("key", StringArgumentType.word())
                                            .executes(context -> executeActivation(context.getSource(), StringArgumentType.getString(context, "key")))
                                    )
                            )
                            .then(Commands.literal("reload")
                                    .requires(source -> hasCmdPermission(source, "gc.command.reload"))
                                    .executes(context -> executeReload(context.getSource()))
                            )
                            .then(Commands.literal("inspect")
                                    .requires(source -> hasCmdPermission(source, "gc.command.inspect"))
                                    .executes(context -> executeInspect(context.getSource()))
                            )
                            .then(Commands.literal("debug")
                                    .requires(source -> hasCmdPermission(source, "gc.command.debug"))
                                    .executes(context -> executeDebug(context.getSource()))
                            )
                            // --- COMANDO: UUID (Pegar UUID da entidade) ---
                            .then(Commands.literal("uuid")
                                    .requires(source -> hasCmdPermission(source, "gc.command.uuid"))
                                    .executes(context -> {
                                        ServerPlayer executor = context.getSource().getPlayerOrException();
                                        return executeUuid(context.getSource(), executor);
                                    })
                            )
                            // --- COMANDO: GIVE (Desbloquear no Menu) ---
                            .then(Commands.literal("give")
                                    .requires(source -> hasCmdPermission(source, "gc.command.give"))
                                    .then(Commands.argument("cosmetic_id", StringArgumentType.word())
                                            .suggests(SUGGEST_COSMETICS)
                                            .executes(context -> {
                                                String id = StringArgumentType.getString(context, "cosmetic_id");
                                                ServerPlayer executor = context.getSource().getPlayerOrException();
                                                return executeGive(context.getSource(), id, executor);
                                            })
                                            .then(Commands.argument("alvo", EntityArgument.player())
                                                    .executes(context -> {
                                                        String id = StringArgumentType.getString(context, "cosmetic_id");
                                                        ServerPlayer alvo = EntityArgument.getPlayer(context, "alvo");
                                                        return executeGive(context.getSource(), id, alvo);
                                                    })
                                            )
                                    )
                            )
                            // --- COMANDO: GIVE ITEM (Dar o Item Físico) ---
                            .then(Commands.literal("giveitem")
                                    .requires(source -> hasCmdPermission(source, "gc.command.giveitem"))
                                    .then(Commands.argument("cosmetic_id", StringArgumentType.word())
                                            .suggests(SUGGEST_COSMETICS)
                                            .executes(context -> {
                                                String id = StringArgumentType.getString(context, "cosmetic_id");
                                                ServerPlayer executor = context.getSource().getPlayerOrException();
                                                return executeGiveItem(context.getSource(), id, executor);
                                            })
                                            .then(Commands.argument("alvo", EntityArgument.player())
                                                    .executes(context -> {
                                                        String id = StringArgumentType.getString(context, "cosmetic_id");
                                                        ServerPlayer alvo = EntityArgument.getPlayer(context, "alvo");
                                                        return executeGiveItem(context.getSource(), id, alvo);
                                                    })
                                            )
                                    )
                            )
                            // --- COMANDO: REMOVE ---
                            .then(Commands.literal("remove")
                                    .requires(source -> hasCmdPermission(source, "gc.command.remove"))
                                    .then(Commands.argument("cosmetic_id", StringArgumentType.word())
                                            .suggests(SUGGEST_COSMETICS)
                                            .executes(context -> {
                                                String id = StringArgumentType.getString(context, "cosmetic_id");
                                                ServerPlayer executor = context.getSource().getPlayerOrException();
                                                return executeRemove(context.getSource(), id, executor);
                                            })
                                            .then(Commands.argument("alvo", EntityArgument.player())
                                                    .executes(context -> {
                                                        String id = StringArgumentType.getString(context, "cosmetic_id");
                                                        ServerPlayer alvo = EntityArgument.getPlayer(context, "alvo");
                                                        return executeRemove(context.getSource(), id, alvo);
                                                    })
                                            )
                                    )
                            )
                            // --- COMANDO: GIVESKIN (Desbloquear Skin de Pokémon) ---
                            .then(Commands.literal("giveskin")
                                    .requires(source -> hasCmdPermission(source, "gc.command.giveskin"))
                                    .then(Commands.argument("skin_id", StringArgumentType.word())
                                            .suggests(SUGGEST_SKINS)
                                            .executes(context -> {
                                                String id = StringArgumentType.getString(context, "skin_id");
                                                ServerPlayer executor = context.getSource().getPlayerOrException();
                                                return executeGiveSkin(context.getSource(), id, executor);
                                            })
                                            .then(Commands.argument("alvo", EntityArgument.player())
                                                    .executes(context -> {
                                                        String id = StringArgumentType.getString(context, "skin_id");
                                                        ServerPlayer alvo = EntityArgument.getPlayer(context, "alvo");
                                                        return executeGiveSkin(context.getSource(), id, alvo);
                                                    })
                                            )
                                    )
                            )
                            // --- COMANDO: REMOVESKIN (Remover Skin de Pokémon) ---
                            .then(Commands.literal("removeskin")
                                    .requires(source -> hasCmdPermission(source, "gc.command.removeskin"))
                                    .then(Commands.argument("skin_id", StringArgumentType.word())
                                            .suggests(SUGGEST_SKINS)
                                            .executes(context -> {
                                                String id = StringArgumentType.getString(context, "skin_id");
                                                ServerPlayer executor = context.getSource().getPlayerOrException();
                                                return executeRemoveSkin(context.getSource(), id, executor);
                                            })
                                            .then(Commands.argument("alvo", EntityArgument.player())
                                                    .executes(context -> {
                                                        String id = StringArgumentType.getString(context, "skin_id");
                                                        ServerPlayer alvo = EntityArgument.getPlayer(context, "alvo");
                                                        return executeRemoveSkin(context.getSource(), id, alvo);
                                                    })
                                            )
                                    )
                            )
                            // --- COMANDO: NPC (Equipar/Remover olhando para a entidade) ---
                            // .requires() no agrupador também — cada subcomando (equip/remove/glow)
                            // já tinha o seu próprio, mas o nó "npc" em si não tinha nenhum, então
                            // aparecia sugerido no chat pra QUALQUER jogador mesmo sem ter nenhuma
                            // das três permissões (mesmo bug do /wardrobe, só que no agrupador em
                            // vez do comando raiz).
                            .then(Commands.literal("npc")
                                    .requires(source -> hasCmdPermission(source, "gc.command.npc.equip")
                                            || hasCmdPermission(source, "gc.command.npc.remove"))
                                    .then(Commands.literal("equip")
                                            .requires(source -> hasCmdPermission(source, "gc.command.npc.equip"))
                                            .then(Commands.argument("cosmetic_id", StringArgumentType.word())
                                                    .suggests(SUGGEST_COSMETICS)
                                                    .executes(context -> {
                                                        String id = StringArgumentType.getString(context, "cosmetic_id");
                                                        ServerPlayer executor = context.getSource().getPlayerOrException();
                                                        return executeNpcEquip(context.getSource(), id, executor);
                                                    })
                                            )
                                    )
                                    .then(Commands.literal("remove")
                                            .requires(source -> hasCmdPermission(source, "gc.command.npc.remove"))
                                            .then(Commands.argument("slot", StringArgumentType.word())
                                                    .executes(context -> {
                                                        String slot = StringArgumentType.getString(context, "slot");
                                                        ServerPlayer executor = context.getSource().getPlayerOrException();
                                                        return executeNpcRemove(context.getSource(), slot, executor);
                                                    })
                                            )
                                    )
                            )
                            // --- COMANDO: COSMETICS EQUIP/UNEQUIP (Força equipar em outro player, ignorando limite de slot) ---
                            .then(Commands.literal("cosmetics")
                                    .requires(source -> hasCmdPermission(source, "gc.command.cosmetics.equip")
                                            || hasCmdPermission(source, "gc.command.cosmetics.unequip"))
                                    .then(Commands.literal("equip")
                                            .requires(source -> hasCmdPermission(source, "gc.command.cosmetics.equip"))
                                            .then(Commands.argument("cosmetic_id", StringArgumentType.word())
                                                    .suggests(SUGGEST_COSMETICS)
                                                    .then(Commands.argument("alvo", EntityArgument.player())
                                                            .executes(context -> {
                                                                String id = StringArgumentType.getString(context, "cosmetic_id");
                                                                ServerPlayer alvo = EntityArgument.getPlayer(context, "alvo");
                                                                return executeForceEquip(context.getSource(), id, alvo);
                                                            })
                                                    )
                                            )
                                    )
                                    .then(Commands.literal("unequip")
                                            .requires(source -> hasCmdPermission(source, "gc.command.cosmetics.unequip"))
                                            .then(Commands.argument("cosmetic_id", StringArgumentType.word())
                                                    .suggests(SUGGEST_COSMETICS)
                                                    .then(Commands.argument("alvo", EntityArgument.player())
                                                            .executes(context -> {
                                                                String id = StringArgumentType.getString(context, "cosmetic_id");
                                                                ServerPlayer alvo = EntityArgument.getPlayer(context, "alvo");
                                                                return executeForceUnequip(context.getSource(), id, alvo);
                                                            })
                                                    )
                                            )
                                    )
                            )
                            // --- COMANDO: TAGS GIVE/REMOVE (concede/remove posse de uma tag; remove não afeta tags de grupo) ---
                            .then(Commands.literal("tags")
                                    .requires(source -> hasCmdPermission(source, "gc.command.tags.give")
                                            || hasCmdPermission(source, "gc.command.tags.remove"))
                                    .then(Commands.literal("give")
                                            .requires(source -> hasCmdPermission(source, "gc.command.tags.give"))
                                            .then(Commands.argument("tag_id", StringArgumentType.word())
                                                    .suggests(SUGGEST_TAGS)
                                                    .then(Commands.argument("alvo", EntityArgument.player())
                                                            .executes(context -> {
                                                                String id = StringArgumentType.getString(context, "tag_id");
                                                                ServerPlayer alvo = EntityArgument.getPlayer(context, "alvo");
                                                                return executeTagGive(context.getSource(), id, alvo);
                                                            })
                                                    )
                                            )
                                    )
                                    .then(Commands.literal("remove")
                                            .requires(source -> hasCmdPermission(source, "gc.command.tags.remove"))
                                            .then(Commands.argument("tag_id", StringArgumentType.word())
                                                    .suggests(SUGGEST_TAGS)
                                                    .then(Commands.argument("alvo", EntityArgument.player())
                                                            .executes(context -> {
                                                                String id = StringArgumentType.getString(context, "tag_id");
                                                                ServerPlayer alvo = EntityArgument.getPlayer(context, "alvo");
                                                                return executeTagRemove(context.getSource(), id, alvo);
                                                            })
                                                    )
                                            )
                                    )
                            )
                            .then(Commands.literal("wardrobe")
                                    .requires(source -> hasCmdPermission(source, "gc.command.wardrobe.setbackground"))
                                    .then(Commands.literal("setbackground")
                                            .requires(source -> hasCmdPermission(source, "gc.command.wardrobe.setbackground"))
                                            .then(Commands.argument("nome", StringArgumentType.string())
                                                    .executes(context -> {
                                                        if (isBlockedByLicense(context.getSource(), "wardrobe")) return 0;
                                                        String bgName = StringArgumentType.getString(context, "nome");
                                                        ServerPlayer player = context.getSource().getPlayerOrException();
                                                        WardrobeManager.setStudioLocation(player, bgName);
                                                        return 1;
                                                    })
                                            )
                                    )
                            )
            );

            dispatcher.register(
                    Commands.literal("gc")
                            .redirect(mainNode)
            );

            // /wardrobe (self) e /wardrobe <target> [background] (outro jogador) ficam no MESMO
            // nó Brigadier (self tem .executes() direto, other é filho dele) — não dá pra usar
            // .requires() com permissões diferentes num nó compartilhado (o requires() do nó pai
            // bloquearia a descida pros filhos também), então a checagem de "gc.command.wardrobe.self"
            // vs "gc.command.wardrobe.other" é feita manualmente dentro de cada .executes().
            dispatcher.register(Commands.literal("wardrobe")
                    // Gate no nó raiz com o OR das duas permissões — sem isso "/wardrobe" (sem
                    // alvo) aparecia sugerido pra QUALQUER jogador, mesmo sem nenhuma das duas
                    // permissões, porque só existia checagem manual dentro do executes() de baixo
                    // (que bloqueia a execução, mas não esconde o autocomplete). Um jogador com só
                    // "other" (ex: staff que só abre wardrobe alheio) ainda consegue descer pro nó
                    // "<target>" abaixo, que tem seu próprio .requires() já certo; um com só "self"
                    // consegue executar o nó raiz normalmente (checagem manual abaixo continua
                    // valendo pra decidir exatamente qual permissão faltou).
                    .requires(source -> hasCmdPermission(source, "gc.command.wardrobe.self") || hasCmdPermission(source, "gc.command.wardrobe.other"))
                    .executes(context -> {
                        CommandSourceStack source = context.getSource();
                        if (!hasCmdPermission(source, "gc.command.wardrobe.self")) {
                            return denyPermission(source);
                        }
                        WardrobeManager.openWardrobe(source.getPlayerOrException(), null);
                        return 1;
                    })
                    .then(Commands.argument("target", EntityArgument.player())
                            // .requires() aqui além do check manual dentro do executes() — sem isso
                            // o Brigadier não tem como saber que esse nó é restrito, e sugere
                            // "/wardrobe <nome>" no chat pra QUALQUER jogador (mesmo sem
                            // gc.command.wardrobe.other), já que só bloquear na hora de executar
                            // não impede o autocomplete de aparecer.
                            .requires(source -> hasCmdPermission(source, "gc.command.wardrobe.other"))
                            .executes(context -> {
                                ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                WardrobeManager.openWardrobe(target, null);
                                return 1;
                            })
                            .then(Commands.argument("background", StringArgumentType.string())
                                    .executes(context -> {
                                        ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                        String bgName = StringArgumentType.getString(context, "background");
                                        WardrobeManager.openWardrobe(target, bgName);
                                        return 1;
                                    })
                            )
                    )
            );
        });
    }

    private static int denyPermission(CommandSourceStack source) {
        source.sendSuccess(() -> LangConfig.chat("general.error.no_permission", source.getLevel().registryAccess()), false);
        return 0;
    }

    // ==========================================
    // SISTEMA DE LICENÇA (ver security.ActivationManager)
    // ==========================================
    /** true = comando bloqueado por falta de licença (servidor dedicado). O subcomando
     *  {@code activation} NUNCA é bloqueado. Singleplayer nunca bloqueia. */
    private static boolean isBlockedByLicense(CommandSourceStack source, String sub) {
        if ("activation".equalsIgnoreCase(sub)) return false;
        net.minecraft.server.MinecraftServer server = source.getServer();
        if (server == null || !server.isDedicatedServer()) return false;
        if (com.f4xizzz.greatcosmetics.security.ActivationManager.isModActivated()) return false;
        HolderLookup.Provider regs = source.getLevel().registryAccess();
        source.sendFailure(LangConfig.chat("general.license.locked_cmd_1", regs));
        return true;
    }

    private static int executeActivation(CommandSourceStack source, String key) {
        net.minecraft.server.MinecraftServer server = source.getServer();
        HolderLookup.Provider regs = source.getLevel().registryAccess();

        if (server == null || !server.isDedicatedServer()) {
            source.sendFailure(LangConfig.chat("general.license.singleplayer_only", regs));
            return 0;
        }

        source.sendSuccess(() -> LangConfig.chat("general.license.activating", regs), false);

        com.f4xizzz.greatcosmetics.security.ActivationManager.tryActivateAsync(key, server).thenAccept(success -> {
            if (success) {
                source.sendSuccess(() -> LangConfig.chat("general.license.activate_success", regs), false);
            } else {
                source.sendFailure(LangConfig.chat("general.license.activate_fail", regs));
            }
        });
        return 1;
    }

    // ==========================================
    // MÉTODO NOVO: PEGAR UUID DA ENTIDADE
    // ==========================================
    private static int executeUuid(CommandSourceStack source, ServerPlayer executor) {
        if (isBlockedByLicense(source, "uuid")) return 0;
        HolderLookup.Provider regs = source.getLevel().registryAccess();
        net.minecraft.world.entity.LivingEntity target = getTargetEntity(executor);

        if (target == null) {
            source.sendSuccess(() -> LangConfig.chat("commands.uuid.no_target", regs), false);
            return 0;
        }

        String uuidStr = target.getStringUUID();
        String entityName = target.getName().getString();

        source.sendSuccess(() -> LangConfig.chat("commands.uuid.result", regs, "name", entityName, "uuid", uuidStr), false);
        return 1;
    }

    private static int executeGive(CommandSourceStack source, String idProcurado, ServerPlayer target) {
        if (isBlockedByLicense(source, "give")) return 0;
        if (target == null) return 0;
        HolderLookup.Provider regs = source.getLevel().registryAccess();
        CosmeticData data = CosmeticsConfig.getCosmeticById(idProcurado);

        if (data == null) {
            source.sendSuccess(() -> LangConfig.chat("general.error.cosmetic_not_found", regs, "id", idProcurado), false);
            return 0;
        }

        boolean success = DatabaseManager.unlockCosmetic(target.getUUID(), idProcurado);
        com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("/gc give: '" + idProcurado + "' -> " + target.getName().getString() + " (executor=" + source.getTextName() + ") success=" + success);

        if (success) {
            target.displayClientMessage(LangConfig.chat("commands.give.received", regs, "item", data.getChatSafeDisplayName()), false);

            if (source.getPlayer() != target) {
                source.sendSuccess(() -> LangConfig.chat("commands.give.success_other", regs, "id", idProcurado, "player", target.getName().getString()), false);
            }
        } else {
            source.sendSuccess(() -> LangConfig.chat("commands.give.already_has", regs, "id", idProcurado, "player", target.getName().getString()), false);
        }

        return 1;
    }

    // ==========================================
    // MÉTODO NOVO: FORÇAR EQUIPAR/DESEQUIPAR EM OUTRO PLAYER (ignora limite de slot/tipo)
    // Diferente do payload EquipCosmeticPayload (usado pelo próprio player via GUI), aqui
    // chamamos o DatabaseManager direto — sem checar slotLimit/typeLimit — porque é uma
    // ação administrativa explícita (/gc cosmetics equip), não uma troca normal do jogador.
    // ==========================================
    private static int executeForceEquip(CommandSourceStack source, String idProcurado, ServerPlayer target) {
        if (isBlockedByLicense(source, "cosmetics")) return 0;
        if (target == null) return 0;
        HolderLookup.Provider regs = source.getLevel().registryAccess();
        CosmeticData data = CosmeticsConfig.getCosmeticById(idProcurado);

        if (data == null) {
            source.sendSuccess(() -> LangConfig.chat("general.error.cosmetic_not_found", regs, "id", idProcurado), false);
            return 0;
        }

        DatabaseManager.unlockCosmetic(target.getUUID(), idProcurado);
        DatabaseManager.equipCosmetic(target.getUUID(), idProcurado, data.slot.name(), data.type);
        DatabaseManager.broadcastPlayerCosmetics(target);
        com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("/gc cosmetics equip: '" + idProcurado + "' force-equipped on " + target.getName().getString() + " (executor=" + source.getTextName() + ").");

        source.sendSuccess(() -> LangConfig.chat("commands.forceequip.success", regs, "id", idProcurado, "player", target.getName().getString()), false);
        return 1;
    }

    private static int executeForceUnequip(CommandSourceStack source, String idProcurado, ServerPlayer target) {
        if (isBlockedByLicense(source, "cosmetics")) return 0;
        if (target == null) return 0;
        HolderLookup.Provider regs = source.getLevel().registryAccess();
        CosmeticData data = CosmeticsConfig.getCosmeticById(idProcurado);

        if (data == null) {
            source.sendSuccess(() -> LangConfig.chat("general.error.cosmetic_not_found", regs, "id", idProcurado), false);
            return 0;
        }

        boolean success = DatabaseManager.unequipCosmetic(target.getUUID(), idProcurado);
        DatabaseManager.broadcastPlayerCosmetics(target);
        com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("/gc cosmetics unequip: '" + idProcurado + "' from " + target.getName().getString() + " (executor=" + source.getTextName() + ") success=" + success);

        if (success) {
            source.sendSuccess(() -> LangConfig.chat("commands.forceunequip.success", regs, "id", idProcurado, "player", target.getName().getString()), false);
        } else {
            source.sendSuccess(() -> LangConfig.chat("commands.forceunequip.not_equipped", regs, "player", target.getName().getString()), false);
        }
        return 1;
    }

    // ==========================================
    // MÉTODOS NOVOS: TAGS GIVE/REMOVE
    // ==========================================
    private static int executeTagGive(CommandSourceStack source, String idProcurado, ServerPlayer target) {
        if (isBlockedByLicense(source, "tags")) return 0;
        if (target == null) return 0;
        HolderLookup.Provider regs = source.getLevel().registryAccess();
        com.f4xizzz.greatcosmetics.config.TagData data = com.f4xizzz.greatcosmetics.config.TagsConfig.getById(idProcurado);

        if (data == null) {
            source.sendSuccess(() -> LangConfig.chat("general.error.tag_not_found", regs, "id", idProcurado), false);
            return 0;
        }

        if (data.isGroupTag) {
            source.sendSuccess(() -> LangConfig.chat("commands.tag.group_cannot_give", regs), false);
            return 0;
        }

        boolean success = com.f4xizzz.greatcosmetics.database.DatabaseManager.unlockTag(target.getUUID(), idProcurado);
        com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("/gc tags give: '" + idProcurado + "' -> " + target.getName().getString() + " (executor=" + source.getTextName() + ") success=" + success);

        if (success) {
            source.sendSuccess(() -> LangConfig.chat("commands.tag.given", regs, "id", idProcurado, "player", target.getName().getString()), false);
            target.displayClientMessage(LangConfig.chat("commands.tag.received", regs, "name", data.displayName), false);
            com.f4xizzz.greatcosmetics.GreatCosmeticsServer.syncPlayerTags(target);
        } else {
            source.sendSuccess(() -> LangConfig.chat("commands.tag.already_has", regs, "id", idProcurado, "player", target.getName().getString()), false);
        }
        return 1;
    }

    private static int executeTagRemove(CommandSourceStack source, String idProcurado, ServerPlayer target) {
        if (isBlockedByLicense(source, "tags")) return 0;
        if (target == null) return 0;
        HolderLookup.Provider regs = source.getLevel().registryAccess();
        com.f4xizzz.greatcosmetics.config.TagData data = com.f4xizzz.greatcosmetics.config.TagsConfig.getById(idProcurado);

        if (data == null) {
            source.sendSuccess(() -> LangConfig.chat("general.error.tag_not_found", regs, "id", idProcurado), false);
            return 0;
        }

        if (data.isGroupTag) {
            source.sendSuccess(() -> LangConfig.chat("commands.tag.group_cannot_remove", regs), false);
            return 0;
        }

        boolean success = com.f4xizzz.greatcosmetics.database.DatabaseManager.removeTagOwnership(target.getUUID(), idProcurado);
        com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("/gc tags remove: '" + idProcurado + "' from " + target.getName().getString() + " (executor=" + source.getTextName() + ") success=" + success);

        if (success) {
            String equipped = com.f4xizzz.greatcosmetics.database.DatabaseManager.getEquippedTagId(target.getUUID());
            if (idProcurado.equalsIgnoreCase(equipped)) {
                com.f4xizzz.greatcosmetics.database.DatabaseManager.unequipAllForPlayer(target.getUUID());
                com.f4xizzz.greatcosmetics.util.LuckPermsTagManager.removeTag(target, data);
            }

            source.sendSuccess(() -> LangConfig.chat("commands.tag.removed", regs, "id", idProcurado, "player", target.getName().getString()), false);
            com.f4xizzz.greatcosmetics.GreatCosmeticsServer.syncPlayerTags(target);
        } else {
            source.sendSuccess(() -> LangConfig.chat("commands.tag.not_has", regs, "id", idProcurado, "player", target.getName().getString()), false);
        }
        return 1;
    }

    private static int executeGiveItem(CommandSourceStack source, String idProcurado, ServerPlayer target) {
        if (isBlockedByLicense(source, "giveitem")) return 0;
        if (target == null) return 0;
        HolderLookup.Provider regs = source.getLevel().registryAccess();
        CosmeticData data = CosmeticsConfig.getCosmeticById(idProcurado);

        if (data == null) {
            source.sendSuccess(() -> LangConfig.chat("general.error.cosmetic_not_found", regs, "id", idProcurado), false);
            return 0;
        }

        ItemStack item = buildCosmeticIcon(data, regs);
        com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("/gc giveitem: physical item of '" + idProcurado + "' -> " + target.getName().getString() + " (executor=" + source.getTextName() + ").");

        if (!target.getInventory().add(item)) {
            target.drop(item, false);
            source.sendSuccess(() -> LangConfig.chat("commands.giveitem.inv_full", regs, "id", idProcurado, "player", target.getName().getString()), false);
        } else {
            source.sendSuccess(() -> LangConfig.chat("commands.giveitem.success", regs, "id", idProcurado, "player", target.getName().getString()), false);
        }

        return 1;
    }

    private static int executeRemove(CommandSourceStack source, String idProcurado, ServerPlayer target) {
        if (isBlockedByLicense(source, "remove")) return 0;
        if (target == null) return 0;
        HolderLookup.Provider regs = source.getLevel().registryAccess();
        CosmeticData data = CosmeticsConfig.getCosmeticById(idProcurado);

        if (data == null) {
            source.sendSuccess(() -> LangConfig.chat("general.error.cosmetic_not_found", regs, "id", idProcurado), false);
            return 0;
        }

        boolean success = DatabaseManager.removeCosmetic(target.getUUID(), idProcurado);
        com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("/gc remove: '" + idProcurado + "' from " + target.getName().getString() + " (executor=" + source.getTextName() + ") success=" + success);

        if (success) {
            source.sendSuccess(() -> LangConfig.chat("commands.remove.success", regs, "id", idProcurado, "player", target.getName().getString()), false);
        } else {
            source.sendSuccess(() -> LangConfig.chat("commands.remove.not_has", regs, "id", idProcurado, "player", target.getName().getString()), false);
        }

        return 1;
    }

    // ==========================================
    // MÉTODO NOVO: GIVE SKIN
    // ==========================================
    private static int executeGiveSkin(CommandSourceStack source, String idProcurado, ServerPlayer target) {
        if (isBlockedByLicense(source, "giveskin")) return 0;
        if (target == null) return 0;
        HolderLookup.Provider regs = source.getLevel().registryAccess();
        PokemonSkin skin = SkinConfigManager.getSkin(idProcurado);

        if (skin == null) {
            source.sendSuccess(() -> LangConfig.chat("general.error.skin_not_found", regs, "id", idProcurado), false);
            return 0;
        }

        boolean success = DatabaseManager.unlockPokemonSkin(target.getUUID(), idProcurado);
        com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("/gc giveskin: '" + idProcurado + "' -> " + target.getName().getString() + " (executor=" + source.getTextName() + ") success=" + success);

        if (success) {
            syncPlayerSkins(target);
            target.displayClientMessage(LangConfig.chat("commands.giveskin.received", regs, "name", skin.getDisplayName()), false);

            if (source.getPlayer() != target) {
                source.sendSuccess(() -> LangConfig.chat("commands.giveskin.success_other", regs, "id", idProcurado, "player", target.getName().getString()), false);
            }
        } else {
            source.sendSuccess(() -> LangConfig.chat("commands.giveskin.already_has", regs, "id", idProcurado, "player", target.getName().getString()), false);
        }

        return 1;
    }

    // ==========================================
    // MÉTODOS NOVOS: EQUIPAR/REMOVER EM NPCs
    // ==========================================
    private static int executeNpcEquip(CommandSourceStack source, String idProcurado, ServerPlayer executor) {
        if (isBlockedByLicense(source, "npc")) return 0;
        HolderLookup.Provider regs = source.getLevel().registryAccess();
        CosmeticData data = CosmeticsConfig.getCosmeticById(idProcurado);

        if (data == null) {
            source.sendSuccess(() -> LangConfig.chat("commands.npc.not_found", regs), false);
            return 0;
        }

        net.minecraft.world.entity.LivingEntity target = getTargetEntity(executor);
        if (target == null || target instanceof ServerPlayer) {
            source.sendSuccess(() -> LangConfig.chat("commands.npc.equip_no_target", regs), false);
            return 0;
        }

        // IMPORTANTE: o sistema de cosméticos do mod NUNCA lê o ItemStack real do slot de
        // equipamento — ele é 100% renderizado via ArmorFeatureRendererMixin lendo o
        // ClientCosmeticCache (o mesmo caminho usado pros jogadores). Usar target.equipStack()
        // aqui era o bug: no NPC do EasyNPC isso não aparece em lugar nenhum (o renderer dele
        // ignora o slot), e no Armor Stand aparece o item "cru" (textura quebrada), porque
        // NENHUM dos dois sabe desenhar a parte 3D customizada do cosmético a partir do
        // ItemStack puro. Registrando por UUID igual ao sistema de jogador, o mesmo
        // ArmorFeatureRendererMixin passa a desenhar certinho em ambos.
        NpcCosmeticsConfig.equip(target.getUUID(), idProcurado);
        NpcCosmeticsConfig.broadcast(source.getServer(), target.getUUID());
        com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("/gc npc equip: '" + idProcurado + "' -> NPC/entity " + target.getUUID() + " (executor=" + source.getTextName() + ").");

        source.sendSuccess(() -> LangConfig.chat("commands.npc.equipped", regs), false);
        return 1;
    }

    private static int executeNpcRemove(CommandSourceStack source, String slotName, ServerPlayer executor) {
        if (isBlockedByLicense(source, "npc")) return 0;
        HolderLookup.Provider regs = source.getLevel().registryAccess();
        net.minecraft.world.entity.LivingEntity target = getTargetEntity(executor);
        if (target == null || target instanceof ServerPlayer) {
            source.sendSuccess(() -> LangConfig.chat("commands.npc.remove_no_target", regs), false);
            return 0;
        }

        CosmeticData.VirtualSlot virtualSlot;
        try {
            virtualSlot = CosmeticData.VirtualSlot.valueOf(slotName.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendSuccess(() -> LangConfig.chat("commands.npc.invalid_slot", regs), false);
            return 0;
        }

        int removedCount = NpcCosmeticsConfig.unequipSlot(target.getUUID(), virtualSlot);
        if (removedCount > 0) {
            NpcCosmeticsConfig.broadcast(source.getServer(), target.getUUID());
        }
        com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("/gc npc remove: slot " + virtualSlot.name() + " on " + target.getUUID() + " — " + removedCount + " removed (executor=" + source.getTextName() + ").");
        source.sendSuccess(() -> LangConfig.chat("commands.npc.slot_cleared", regs, "slot", virtualSlot.name()), false);
        return 1;
    }

    // Dispara um "laser" invisível da câmera do jogador para pegar a entidade mais próxima que ele está olhando
    private static net.minecraft.world.entity.LivingEntity getTargetEntity(ServerPlayer player) {
        double maxDistance = 5.0;
        net.minecraft.world.phys.Vec3 cameraPos = player.getEyePosition(1.0F);
        net.minecraft.world.phys.Vec3 rotation = player.getViewVector(1.0F);
        net.minecraft.world.phys.Vec3 endPos = cameraPos.add(rotation.x * maxDistance, rotation.y * maxDistance, rotation.z * maxDistance);
        net.minecraft.world.phys.AABB box = player.getBoundingBox().expandTowards(rotation.scale(maxDistance)).inflate(1.0D, 1.0D, 1.0D);

        net.minecraft.world.entity.LivingEntity closestEntity = null;
        double closestDistance = maxDistance * maxDistance;

        for (net.minecraft.world.entity.Entity entity : player.level().getEntities(player, box)) {
            if (entity instanceof net.minecraft.world.entity.LivingEntity livingEntity) {
                net.minecraft.world.phys.AABB entityBox = entity.getBoundingBox().inflate(0.3F);
                java.util.Optional<net.minecraft.world.phys.Vec3> hit = entityBox.clip(cameraPos, endPos);
                if (hit.isPresent()) {
                    double dist = cameraPos.distanceToSqr(hit.get());
                    if (dist < closestDistance) {
                        closestDistance = dist;
                        closestEntity = livingEntity;
                    }
                }
            }
        }
        return closestEntity;
    }

    // ==========================================
    // MÉTODO NOVO: REMOVE SKIN
    // ==========================================
    private static int executeRemoveSkin(CommandSourceStack source, String idProcurado, ServerPlayer target) {
        if (isBlockedByLicense(source, "removeskin")) return 0;
        if (target == null) return 0;
        HolderLookup.Provider regs = source.getLevel().registryAccess();
        PokemonSkin skin = SkinConfigManager.getSkin(idProcurado);

        if (skin == null) {
            source.sendSuccess(() -> LangConfig.chat("general.error.skin_not_found", regs, "id", idProcurado), false);
            return 0;
        }

        boolean success = DatabaseManager.removePokemonSkin(target.getUUID(), idProcurado);
        com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("/gc removeskin: '" + idProcurado + "' from " + target.getName().getString() + " (executor=" + source.getTextName() + ") success=" + success);

        if (success) {
            syncPlayerSkins(target);
            source.sendSuccess(() -> LangConfig.chat("commands.removeskin.success", regs, "id", idProcurado, "player", target.getName().getString()), false);
        } else {
            source.sendSuccess(() -> LangConfig.chat("commands.removeskin.not_has", regs, "id", idProcurado, "player", target.getName().getString()), false);
        }

        return 1;
    }

    private static CosmeticData getCosmeticById(String idProcurado) {
        // CosmeticsConfig.getCosmeticById já cai pro ArmorCosmeticsConfig/ClientArmorCosmeticsCache
        // como fallback (armadura convertida em cosmético).
        return CosmeticsConfig.getCosmeticById(idProcurado);
    }

    public static ItemStack buildCosmeticIcon(CosmeticData data, HolderLookup.Provider regs) {
        // Armadura convertida em cosmético (ver ArmorCosmeticsConfig/CosmeticData.realItemId): o
        // item físico dado precisa ser o item REAL (ex: minecraft:diamond_helmet), não o ghost
        // carved_pumpkin — sem isso o jogador nunca tem o item de verdade no inventário, e
        // AcessoriesPage.hasRealItem() (que exige posse física do item real) nunca é satisfeito,
        // fazendo a armadura sumir do GUI de Acessórios mesmo depois de dada.
        ItemStack item;
        if (data.realItemId != null && !data.realItemId.isBlank()) {
            net.minecraft.resources.ResourceLocation realId = net.minecraft.resources.ResourceLocation.tryParse(data.realItemId);
            net.minecraft.world.item.Item realItem = realId != null ? net.minecraft.core.registries.BuiltInRegistries.ITEM.get(realId) : null;
            item = new ItemStack(realItem != null && realItem != Items.AIR ? realItem : Items.CARVED_PUMPKIN);
        } else {
            item = new ItemStack(Items.CARVED_PUMPKIN);
            item.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(data.cmd));
        }

        // Grava o id de cosmético (separado do id do item real) direto no item físico, via NBT
        // customizado — assim /gc giveitem sempre deixa os dois ids disponíveis no stack: o id
        // real do item (data.realItemId, já é o próprio tipo do item) e o id de cosmético
        // (data.id, usado por /gc give, tags, equip, etc), sem depender de nenhum outro sistema
        // pra saber a qual entrada do armor_cosmetics.json esse item específico corresponde.
        if (data.id != null && !data.id.isBlank()) {
            net.minecraft.nbt.CompoundTag customData = new net.minecraft.nbt.CompoundTag();
            customData.putString("greatcosmetics_id", data.id);
            item.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(customData));
        }

        if (data.maxDurability > 0) {
            item.set(DataComponents.MAX_DAMAGE, data.maxDurability);
            item.set(DataComponents.DAMAGE, 0);
        }

        Component displayName = BackpackManager.parseMiniMessage(data.DisplayName, regs);
        item.set(DataComponents.CUSTOM_NAME, displayName);

        List<Component> loreLines = new ArrayList<>();
        if (data.slot != null) {
            loreLines.add(LangConfig.chat("items.lore.slot", regs, "slot", data.slot.name()));
        }

        if (data.lure != null && data.lure.enabled) {
            loreLines.add(LangConfig.chat("items.lure.divider", regs));
            loreLines.add(LangConfig.chat("items.lure.header", regs));

            if (data.lure.lureTYPE != null && !data.lure.lureTYPE.isEmpty()) loreLines.add(LangConfig.chat("items.lure.type", regs, "type", data.lure.lureTYPE.toUpperCase()));
            if (data.lure.lureShinyMultiplier > 0) loreLines.add(LangConfig.chat("items.lure.shiny", regs, "value", data.lure.lureShinyMultiplier));
            if (data.lure.lureExpAllMultiplier > 0) loreLines.add(LangConfig.chat("items.lure.expall", regs, "value", data.lure.lureExpAllMultiplier));
            if (data.lure.lureAmizadeMultiplier > 0) loreLines.add(LangConfig.chat("items.lure.friendship", regs, "value", data.lure.lureAmizadeMultiplier));
            if (data.lure.lureEV > 0) loreLines.add(LangConfig.chat("items.lure.ev", regs, "value", data.lure.lureEV));
            if (data.lure.lureChanceDeCaptura > 0) loreLines.add(LangConfig.chat("items.lure.capture", regs, "value", data.lure.lureChanceDeCaptura));
            if (data.lure.lureIV > 0) loreLines.add(LangConfig.chat("items.lure.iv", regs, "value", data.lure.lureIV));
            if (data.lure.lureChanceIV > 0) loreLines.add(LangConfig.chat("items.lure.iv_chance", regs, "value", (data.lure.lureChanceIV * 100)));
            if (data.lure.lureUltraRAREMultiplier > 0) loreLines.add(LangConfig.chat("items.lure.ultrarare", regs, "value", data.lure.lureUltraRAREMultiplier));
            if (data.lure.lureHiddenAbilityMultiplier > 0) loreLines.add(LangConfig.chat("items.lure.hidden_ability", regs, "value", data.lure.lureHiddenAbilityMultiplier));

            if (data.lure.lurePescaShiny > 0 || data.lure.lurePescaIvChance > 0 || data.lure.lurePescaVelocidade > 0) {
                loreLines.add(LangConfig.chat("items.lure.fishing_header", regs));
                if (data.lure.lurePescaShiny > 0) loreLines.add(LangConfig.chat("items.lure.fishing_shiny", regs, "value", data.lure.lurePescaShiny));
                if (data.lure.lurePescaIvChance > 0) loreLines.add(LangConfig.chat("items.lure.fishing_iv_chance", regs, "value", (data.lure.lurePescaIvChance * 100)));
                if (data.lure.lurePescaVelocidade > 0) loreLines.add(LangConfig.chat("items.lure.fishing_speed", regs, "value", (data.lure.lurePescaVelocidade * 100)));
            }
            loreLines.add(LangConfig.chat("items.lure.divider", regs));
        }

        if (!loreLines.isEmpty()) item.set(DataComponents.LORE, new ItemLore(loreLines));
        return item;
    }

    private static int executeReload(CommandSourceStack source) {
        if (isBlockedByLicense(source, "reload")) return 0;
        HolderLookup.Provider regs = source.getLevel().registryAccess();

        try {
            source.sendSuccess(() -> LangConfig.chat("general.reload.start", regs), true);
            com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("/gc reload: triggered by " + source.getTextName() + ".");

            com.f4xizzz.greatcosmetics.config.MainConfig.loadConfig();
            LangConfig.load();
            CosmeticsConfig.loadConfig();
            com.f4xizzz.greatcosmetics.config.EffectConfig.loadEffects();
            com.f4xizzz.greatcosmetics.GreatCosmeticsServer.broadcastEffectsCatalog(source.getServer());

            // --- RECARREGA A LISTA DE SKINS E NPCs ---
            SkinConfigManager.load();
            com.f4xizzz.greatcosmetics.config.LegacyCosmeticMigrationConfig.load();
            com.f4xizzz.greatcosmetics.config.SkinGroupConfigManager.load();
            com.f4xizzz.greatcosmetics.config.ArmorCosmeticsConfig.load();
            com.f4xizzz.greatcosmetics.config.LegacyCosmeticMigrationConfig.load();
            com.f4xizzz.greatcosmetics.config.TagsConfig.load();
            com.f4xizzz.greatcosmetics.GreatCosmeticsServer.broadcastTagsCatalog(source.getServer());

            // Esse config era carregado só no boot (SERVER_STARTING) e nunca no /gc reload
            // — reload não pegava mudança nenhuma nele até reiniciar o server de verdade.
            NpcCosmeticsConfig.load();
            com.f4xizzz.greatcosmetics.config.SoundConfig.loadSounds();

            // Recalcula o SHA1 real do resource pack forçado (ver GreatCosmetics#refreshTextureHashForReload)
            // ANTES do resend abaixo — assim o resend já usa o hash atualizado se a textura mudou,
            // em vez de mandar o pacote velho agora e um segundo logo depois quando o hash mudasse
            // em background. Bloqueia aqui (comando manual do admin, não o join de ninguém).
            String textureHashError = com.f4xizzz.greatcosmetics.GreatCosmeticsServer.refreshTextureHashForReload();
            if (textureHashError != null) {
                source.sendSuccess(() -> LangConfig.chat("general.reload.texture_hash_fail", regs, "error", textureHashError), true);
            }

            // Ressincroniza animação/cosméticos de NPC pros jogadores já online (mesma lógica do JOIN).
            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                // Mesmo princípio do JOIN (ver GreatCosmetics#sendCatalogStateSnapshot): informativo
                // antes do pacote de resource pack — nunca vai suprimir nada aqui de qualquer jeito
                // (todo mundo nesse loop já está com player != null, ver ClientJoinReloadState/
                // MinecraftClientMixin), mas atualiza o hash do catálogo que o client vai gravar no
                // cache assim que aplicar os payloads abaixo, pra próxima ENTRADA nesse servidor
                // já sair silenciosa.
                com.f4xizzz.greatcosmetics.GreatCosmeticsServer.sendCatalogStateSnapshot(player, false);

                // Reenvia o resource pack forçado (MainConfig#forceTexture) — assim trocar a URL/hash
                // e dar /gc reload já atualiza a textura de quem já está online, sem precisar relogar.
                com.f4xizzz.greatcosmetics.GreatCosmeticsServer.sendForcedResourcePack(player);

                NpcCosmeticsConfig.syncAllTo(player);

                // Catálogo de skins (PartyPage) — sem isso o /gc reload recarregava o
                // pokeskins.json só no servidor, e quem já tava conectado nunca via as
                // skins novas/editadas até relogar.
                NetworkManager.sendToPlayer(player, new com.f4xizzz.greatcosmetics.network.SyncSkinCatalogPayload(SkinConfigManager.getAllSkins(), com.f4xizzz.greatcosmetics.config.SkinGroupConfigManager.getAllColors()));
            }

            // ORDEM IMPORTA: SyncCosmeticsPayload precisa chegar (e ser processado) ANTES de
            // SyncArmorCosmeticsPayload. No client, o PRIMEIRO desses dois payloads a ser processado
            // é quem de fato dispara o reloadResources() de verdade (ver runAfterResourceReload em
            // GreatCosmeticsClient) — o segundo só encadeia no mesmo reload já em andamento. Só o
            // handler de SyncCosmeticsPayload faz AutoCMDManager.clear()+repopulação (com os cmd/
            // resolvedCmd atuais). Se o payload de armadura chegasse primeiro (ordem antiga) e
            // disparasse o reload ANTES desse clear()+repopulação rodar, o modifyModelOnLoad do
            // carved_pumpkin (que roda em thread de fundo assim que o reload começa) podia ler o
            // AutoCMDManager ainda com o estado VELHO/incompleto — exatamente o bug clássico de "todo
            // cosmético colapsa pro último custom_model_data registrado". Mandando cosméticos normais
            // primeiro, o reload real só começa depois do AutoCMDManager já estar 100% atualizado.
            com.f4xizzz.greatcosmetics.GreatCosmeticsServer.broadcastCosmeticsCatalog(source.getServer(), true);
            com.f4xizzz.greatcosmetics.GreatCosmeticsServer.broadcastArmorCosmeticsCatalog(source.getServer(), true);

            // mainconfig.conf (slots/types/config geral) também nunca era resincronizado no
            // /gc reload — Dev Studio e EquippedSlotsWidget ficavam com o valor de quando o
            // client entrou até relogar.
            com.f4xizzz.greatcosmetics.GreatCosmeticsServer.broadcastMainConfig(source.getServer());

            com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("/gc reload: completed successfully — " + CosmeticsConfig.cosmeticsMap.size() + " cosmetics, "
                    + SkinConfigManager.getAllSkins().size() + " skins, " + com.f4xizzz.greatcosmetics.config.TagsConfig.tagsMap.size() + " tags.");
            source.sendSuccess(() -> LangConfig.chat("general.reload.done", regs, "count", source.getServer().getPlayerList().getPlayerCount()), true);

        } catch (Exception e) {
            com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("/gc reload: FAILED — " + e);
            source.sendSuccess(() -> LangConfig.chat("general.reload.fail", regs), true);
            e.printStackTrace();
        }

        return 1;
    }

    public static void syncPlayerSkins(ServerPlayer target) {
        List<String> unlocked = DatabaseManager.getPlayerUnlockedSkins(target.getUUID());
        Map<String, Long> cooldowns = new java.util.HashMap<>();

        for (String skinId : unlocked) {
            cooldowns.put(skinId, DatabaseManager.getSkinLastApplied(target.getUUID(), skinId));
        }

        NetworkManager.sendToPlayer(target, new com.f4xizzz.greatcosmetics.network.SyncPokemonSkinsPayload(unlocked, cooldowns));

        // Catálogo completo de skins (não só as desbloqueadas) — ver SyncSkinCatalogPayload. Sem
        // isso o client só enxergava as skins que já tinha salvas localmente em disco, nunca as
        // que o servidor tem de verdade.
        NetworkManager.sendToPlayer(target, new com.f4xizzz.greatcosmetics.network.SyncSkinCatalogPayload(SkinConfigManager.getAllSkins(), com.f4xizzz.greatcosmetics.config.SkinGroupConfigManager.getAllColors()));
    }

    private static int executeInspect(CommandSourceStack source) {
        if (isBlockedByLicense(source, "inspect")) return 0;
        return 1;
    }

    private static int executeDebug(CommandSourceStack source) {
        if (isBlockedByLicense(source, "debug")) return 0;
        HolderLookup.Provider regs = source.getLevel().registryAccess();

        com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugMode = !com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugMode;
        boolean enabled = com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugMode;

        com.f4xizzz.greatcosmetics.network.DebugModePayload payload = new com.f4xizzz.greatcosmetics.network.DebugModePayload(enabled);
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            NetworkManager.sendToPlayer(player, payload);
        }

        source.sendSuccess(() -> LangConfig.chat(enabled ? "general.debug.enabled" : "general.debug.disabled", regs), true);

        return 1;
    }
}