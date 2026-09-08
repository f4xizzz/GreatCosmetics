package com.f4xizzz.greatcosmetics.command;

import com.f4xizzz.greatcosmetics.GreatCosmetics;
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
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

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
    private static boolean hasCmdPermission(ServerCommandSource source, String node) {
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            // isRealOperator() em vez de source.hasPermissionLevel(2) de propósito — com mods
            // tipo Vanilla Permissions instalados, hasPermissionLevel(2) podia vir true pra
            // qualquer jogador mesmo sem OP e sem permissão nenhuma.
            return GreatCosmetics.isRealOperator(player) || GreatCosmetics.checkPermission(player, node);
        }
        // Console/command block: não tem GameProfile pra checar ops.json, então confia no nível
        // vanilla mesmo (console sempre tem nível 4 de verdade, isso nunca foi o ponto quebrado).
        return source.hasPermissionLevel(2);
    }

    public static final SuggestionProvider<ServerCommandSource> SUGGEST_COSMETICS = (context, builder) -> {
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

    public static final SuggestionProvider<ServerCommandSource> SUGGEST_GLOW_COLORS = (context, builder) -> {
        for (net.minecraft.util.Formatting cor : net.minecraft.util.Formatting.values()) {
            if (cor.isColor() && cor.getName().toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest(cor.getName().toLowerCase());
            }
        }
        return builder.buildFuture();
    };

    public static final SuggestionProvider<ServerCommandSource> SUGGEST_TAGS = (context, builder) -> {
        for (String id : com.f4xizzz.greatcosmetics.config.TagsConfig.tagsMap.keySet()) {
            if (id.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    };

    public static final SuggestionProvider<ServerCommandSource> SUGGEST_SKINS = (context, builder) -> {
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
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            LiteralCommandNode<ServerCommandSource> mainNode = dispatcher.register(
                    CommandManager.literal("greatcosmetics")
                            // --- COMANDO: ACTIVATION (licença do mod — ver security.ActivationManager) ---
                            // Nunca pode ser bloqueado pelo próprio gate de licença, senão o dono não
                            // consegue ativar. Level 4 / node = dono/console.
                            .then(CommandManager.literal("activation")
                                    .requires(source -> hasCmdPermission(source, "gc.command.activation"))
                                    .then(CommandManager.argument("key", StringArgumentType.word())
                                            .executes(context -> executeActivation(context.getSource(), StringArgumentType.getString(context, "key")))
                                    )
                            )
                            .then(CommandManager.literal("reload")
                                    .requires(source -> hasCmdPermission(source, "gc.command.reload"))
                                    .executes(context -> executeReload(context.getSource()))
                            )
                            .then(CommandManager.literal("inspect")
                                    .requires(source -> hasCmdPermission(source, "gc.command.inspect"))
                                    .executes(context -> executeInspect(context.getSource()))
                            )
                            .then(CommandManager.literal("debug")
                                    .requires(source -> hasCmdPermission(source, "gc.command.debug"))
                                    .executes(context -> executeDebug(context.getSource()))
                            )
                            // --- COMANDO: UUID (Pegar UUID da entidade) ---
                            .then(CommandManager.literal("uuid")
                                    .requires(source -> hasCmdPermission(source, "gc.command.uuid"))
                                    .executes(context -> {
                                        ServerPlayerEntity executor = context.getSource().getPlayerOrThrow();
                                        return executeUuid(context.getSource(), executor);
                                    })
                            )
                            // --- COMANDO: GIVE (Desbloquear no Menu) ---
                            .then(CommandManager.literal("give")
                                    .requires(source -> hasCmdPermission(source, "gc.command.give"))
                                    .then(CommandManager.argument("cosmetic_id", StringArgumentType.word())
                                            .suggests(SUGGEST_COSMETICS)
                                            .executes(context -> {
                                                String id = StringArgumentType.getString(context, "cosmetic_id");
                                                ServerPlayerEntity executor = context.getSource().getPlayerOrThrow();
                                                return executeGive(context.getSource(), id, executor);
                                            })
                                            .then(CommandManager.argument("alvo", EntityArgumentType.player())
                                                    .executes(context -> {
                                                        String id = StringArgumentType.getString(context, "cosmetic_id");
                                                        ServerPlayerEntity alvo = EntityArgumentType.getPlayer(context, "alvo");
                                                        return executeGive(context.getSource(), id, alvo);
                                                    })
                                            )
                                    )
                            )
                            // --- COMANDO: GIVE ITEM (Dar o Item Físico) ---
                            .then(CommandManager.literal("giveitem")
                                    .requires(source -> hasCmdPermission(source, "gc.command.giveitem"))
                                    .then(CommandManager.argument("cosmetic_id", StringArgumentType.word())
                                            .suggests(SUGGEST_COSMETICS)
                                            .executes(context -> {
                                                String id = StringArgumentType.getString(context, "cosmetic_id");
                                                ServerPlayerEntity executor = context.getSource().getPlayerOrThrow();
                                                return executeGiveItem(context.getSource(), id, executor);
                                            })
                                            .then(CommandManager.argument("alvo", EntityArgumentType.player())
                                                    .executes(context -> {
                                                        String id = StringArgumentType.getString(context, "cosmetic_id");
                                                        ServerPlayerEntity alvo = EntityArgumentType.getPlayer(context, "alvo");
                                                        return executeGiveItem(context.getSource(), id, alvo);
                                                    })
                                            )
                                    )
                            )
                            // --- COMANDO: REMOVE ---
                            .then(CommandManager.literal("remove")
                                    .requires(source -> hasCmdPermission(source, "gc.command.remove"))
                                    .then(CommandManager.argument("cosmetic_id", StringArgumentType.word())
                                            .suggests(SUGGEST_COSMETICS)
                                            .executes(context -> {
                                                String id = StringArgumentType.getString(context, "cosmetic_id");
                                                ServerPlayerEntity executor = context.getSource().getPlayerOrThrow();
                                                return executeRemove(context.getSource(), id, executor);
                                            })
                                            .then(CommandManager.argument("alvo", EntityArgumentType.player())
                                                    .executes(context -> {
                                                        String id = StringArgumentType.getString(context, "cosmetic_id");
                                                        ServerPlayerEntity alvo = EntityArgumentType.getPlayer(context, "alvo");
                                                        return executeRemove(context.getSource(), id, alvo);
                                                    })
                                            )
                                    )
                            )
                            // --- COMANDO: GIVESKIN (Desbloquear Skin de Pokémon) ---
                            .then(CommandManager.literal("giveskin")
                                    .requires(source -> hasCmdPermission(source, "gc.command.giveskin"))
                                    .then(CommandManager.argument("skin_id", StringArgumentType.word())
                                            .suggests(SUGGEST_SKINS)
                                            .executes(context -> {
                                                String id = StringArgumentType.getString(context, "skin_id");
                                                ServerPlayerEntity executor = context.getSource().getPlayerOrThrow();
                                                return executeGiveSkin(context.getSource(), id, executor);
                                            })
                                            .then(CommandManager.argument("alvo", EntityArgumentType.player())
                                                    .executes(context -> {
                                                        String id = StringArgumentType.getString(context, "skin_id");
                                                        ServerPlayerEntity alvo = EntityArgumentType.getPlayer(context, "alvo");
                                                        return executeGiveSkin(context.getSource(), id, alvo);
                                                    })
                                            )
                                    )
                            )
                            // --- COMANDO: REMOVESKIN (Remover Skin de Pokémon) ---
                            .then(CommandManager.literal("removeskin")
                                    .requires(source -> hasCmdPermission(source, "gc.command.removeskin"))
                                    .then(CommandManager.argument("skin_id", StringArgumentType.word())
                                            .suggests(SUGGEST_SKINS)
                                            .executes(context -> {
                                                String id = StringArgumentType.getString(context, "skin_id");
                                                ServerPlayerEntity executor = context.getSource().getPlayerOrThrow();
                                                return executeRemoveSkin(context.getSource(), id, executor);
                                            })
                                            .then(CommandManager.argument("alvo", EntityArgumentType.player())
                                                    .executes(context -> {
                                                        String id = StringArgumentType.getString(context, "skin_id");
                                                        ServerPlayerEntity alvo = EntityArgumentType.getPlayer(context, "alvo");
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
                            .then(CommandManager.literal("npc")
                                    .requires(source -> hasCmdPermission(source, "gc.command.npc.equip")
                                            || hasCmdPermission(source, "gc.command.npc.remove"))
                                    .then(CommandManager.literal("equip")
                                            .requires(source -> hasCmdPermission(source, "gc.command.npc.equip"))
                                            .then(CommandManager.argument("cosmetic_id", StringArgumentType.word())
                                                    .suggests(SUGGEST_COSMETICS)
                                                    .executes(context -> {
                                                        String id = StringArgumentType.getString(context, "cosmetic_id");
                                                        ServerPlayerEntity executor = context.getSource().getPlayerOrThrow();
                                                        return executeNpcEquip(context.getSource(), id, executor);
                                                    })
                                            )
                                    )
                                    .then(CommandManager.literal("remove")
                                            .requires(source -> hasCmdPermission(source, "gc.command.npc.remove"))
                                            .then(CommandManager.argument("slot", StringArgumentType.word())
                                                    .executes(context -> {
                                                        String slot = StringArgumentType.getString(context, "slot");
                                                        ServerPlayerEntity executor = context.getSource().getPlayerOrThrow();
                                                        return executeNpcRemove(context.getSource(), slot, executor);
                                                    })
                                            )
                                    )
                            )
                            // --- COMANDO: COSMETICS EQUIP/UNEQUIP (Força equipar em outro player, ignorando limite de slot) ---
                            .then(CommandManager.literal("cosmetics")
                                    .requires(source -> hasCmdPermission(source, "gc.command.cosmetics.equip")
                                            || hasCmdPermission(source, "gc.command.cosmetics.unequip"))
                                    .then(CommandManager.literal("equip")
                                            .requires(source -> hasCmdPermission(source, "gc.command.cosmetics.equip"))
                                            .then(CommandManager.argument("cosmetic_id", StringArgumentType.word())
                                                    .suggests(SUGGEST_COSMETICS)
                                                    .then(CommandManager.argument("alvo", EntityArgumentType.player())
                                                            .executes(context -> {
                                                                String id = StringArgumentType.getString(context, "cosmetic_id");
                                                                ServerPlayerEntity alvo = EntityArgumentType.getPlayer(context, "alvo");
                                                                return executeForceEquip(context.getSource(), id, alvo);
                                                            })
                                                    )
                                            )
                                    )
                                    .then(CommandManager.literal("unequip")
                                            .requires(source -> hasCmdPermission(source, "gc.command.cosmetics.unequip"))
                                            .then(CommandManager.argument("cosmetic_id", StringArgumentType.word())
                                                    .suggests(SUGGEST_COSMETICS)
                                                    .then(CommandManager.argument("alvo", EntityArgumentType.player())
                                                            .executes(context -> {
                                                                String id = StringArgumentType.getString(context, "cosmetic_id");
                                                                ServerPlayerEntity alvo = EntityArgumentType.getPlayer(context, "alvo");
                                                                return executeForceUnequip(context.getSource(), id, alvo);
                                                            })
                                                    )
                                            )
                                    )
                            )
                            // --- COMANDO: TAGS GIVE/REMOVE (concede/remove posse de uma tag; remove não afeta tags de grupo) ---
                            .then(CommandManager.literal("tags")
                                    .requires(source -> hasCmdPermission(source, "gc.command.tags.give")
                                            || hasCmdPermission(source, "gc.command.tags.remove"))
                                    .then(CommandManager.literal("give")
                                            .requires(source -> hasCmdPermission(source, "gc.command.tags.give"))
                                            .then(CommandManager.argument("tag_id", StringArgumentType.word())
                                                    .suggests(SUGGEST_TAGS)
                                                    .then(CommandManager.argument("alvo", EntityArgumentType.player())
                                                            .executes(context -> {
                                                                String id = StringArgumentType.getString(context, "tag_id");
                                                                ServerPlayerEntity alvo = EntityArgumentType.getPlayer(context, "alvo");
                                                                return executeTagGive(context.getSource(), id, alvo);
                                                            })
                                                    )
                                            )
                                    )
                                    .then(CommandManager.literal("remove")
                                            .requires(source -> hasCmdPermission(source, "gc.command.tags.remove"))
                                            .then(CommandManager.argument("tag_id", StringArgumentType.word())
                                                    .suggests(SUGGEST_TAGS)
                                                    .then(CommandManager.argument("alvo", EntityArgumentType.player())
                                                            .executes(context -> {
                                                                String id = StringArgumentType.getString(context, "tag_id");
                                                                ServerPlayerEntity alvo = EntityArgumentType.getPlayer(context, "alvo");
                                                                return executeTagRemove(context.getSource(), id, alvo);
                                                            })
                                                    )
                                            )
                                    )
                            )
                            .then(CommandManager.literal("wardrobe")
                                    .requires(source -> hasCmdPermission(source, "gc.command.wardrobe.setbackground"))
                                    .then(CommandManager.literal("setbackground")
                                            .requires(source -> hasCmdPermission(source, "gc.command.wardrobe.setbackground"))
                                            .then(CommandManager.argument("nome", StringArgumentType.string())
                                                    .executes(context -> {
                                                        if (isBlockedByLicense(context.getSource(), "wardrobe")) return 0;
                                                        String bgName = StringArgumentType.getString(context, "nome");
                                                        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                                        WardrobeManager.setStudioLocation(player, bgName);
                                                        return 1;
                                                    })
                                            )
                                    )
                            )
                            // --- COMANDO: DISPLAY (armor stand invisível "vestindo" o cosmético, na posição do player) ---
                            .then(CommandManager.literal("display")
                                    .requires(source -> hasCmdPermission(source, "gc.command.display"))
                                    .then(CommandManager.literal("remove")
                                            .executes(context -> executeDisplayRemove(
                                                    context.getSource(), context.getSource().getPlayerOrThrow()))
                                    )
                                    .then(CommandManager.literal("clear")
                                            .executes(context -> executeDisplayClear(context.getSource()))
                                    )
                                    .then(CommandManager.argument("cosmetic_id", StringArgumentType.word())
                                            .suggests(SUGGEST_COSMETICS)
                                            .executes(context -> executeDisplaySpawn(
                                                    context.getSource(),
                                                    StringArgumentType.getString(context, "cosmetic_id"),
                                                    context.getSource().getPlayerOrThrow()))
                                    )
                            )
            );

            dispatcher.register(
                    CommandManager.literal("gc")
                            .redirect(mainNode)
            );

            // /wardrobe (self) e /wardrobe <target> [background] (outro jogador) ficam no MESMO
            // nó Brigadier (self tem .executes() direto, other é filho dele) — não dá pra usar
            // .requires() com permissões diferentes num nó compartilhado (o requires() do nó pai
            // bloquearia a descida pros filhos também), então a checagem de "gc.command.wardrobe.self"
            // vs "gc.command.wardrobe.other" é feita manualmente dentro de cada .executes().
            dispatcher.register(CommandManager.literal("wardrobe")
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
                        ServerCommandSource source = context.getSource();
                        if (!hasCmdPermission(source, "gc.command.wardrobe.self")) {
                            return denyPermission(source);
                        }
                        WardrobeManager.openWardrobe(source.getPlayerOrThrow(), null);
                        return 1;
                    })
                    .then(CommandManager.argument("target", EntityArgumentType.player())
                            // .requires() aqui além do check manual dentro do executes() — sem isso
                            // o Brigadier não tem como saber que esse nó é restrito, e sugere
                            // "/wardrobe <nome>" no chat pra QUALQUER jogador (mesmo sem
                            // gc.command.wardrobe.other), já que só bloquear na hora de executar
                            // não impede o autocomplete de aparecer.
                            .requires(source -> hasCmdPermission(source, "gc.command.wardrobe.other"))
                            .executes(context -> {
                                ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");
                                WardrobeManager.openWardrobe(target, null);
                                return 1;
                            })
                            .then(CommandManager.argument("background", StringArgumentType.string())
                                    .executes(context -> {
                                        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");
                                        String bgName = StringArgumentType.getString(context, "background");
                                        WardrobeManager.openWardrobe(target, bgName);
                                        return 1;
                                    })
                            )
                    )
            );
        });
    }

    private static int denyPermission(ServerCommandSource source) {
        source.sendFeedback(() -> LangConfig.chat("general.error.no_permission", source.getWorld().getRegistryManager()), false);
        return 0;
    }

    // ==========================================
    // SISTEMA DE LICENÇA (ver security.ActivationManager)
    // ==========================================
    /** true = comando bloqueado por falta de licença (servidor dedicado). O subcomando
     *  {@code activation} NUNCA é bloqueado. Singleplayer nunca bloqueia. */
    private static boolean isBlockedByLicense(ServerCommandSource source, String sub) {
        if ("activation".equalsIgnoreCase(sub)) return false;
        net.minecraft.server.MinecraftServer server = source.getServer();
        if (server == null || !server.isDedicated()) return false;
        if (com.f4xizzz.greatcosmetics.security.ActivationManager.isModActivated()) return false;
        RegistryWrapper.WrapperLookup regs = source.getWorld().getRegistryManager();
        source.sendError(LangConfig.chat("general.license.locked_cmd_1", regs));
        return true;
    }

    private static int executeActivation(ServerCommandSource source, String key) {
        net.minecraft.server.MinecraftServer server = source.getServer();
        RegistryWrapper.WrapperLookup regs = source.getWorld().getRegistryManager();

        if (server == null || !server.isDedicated()) {
            source.sendError(LangConfig.chat("general.license.singleplayer_only", regs));
            return 0;
        }

        source.sendFeedback(() -> LangConfig.chat("general.license.activating", regs), false);

        com.f4xizzz.greatcosmetics.security.ActivationManager.tryActivateAsync(key, server).thenAccept(success -> {
            if (success) {
                source.sendFeedback(() -> LangConfig.chat("general.license.activate_success", regs), false);
            } else {
                source.sendError(LangConfig.chat("general.license.activate_fail", regs));
            }
        });
        return 1;
    }

    // ==========================================
    // MÉTODO NOVO: PEGAR UUID DA ENTIDADE
    // ==========================================
    private static int executeUuid(ServerCommandSource source, ServerPlayerEntity executor) {
        if (isBlockedByLicense(source, "uuid")) return 0;
        RegistryWrapper.WrapperLookup regs = source.getWorld().getRegistryManager();
        net.minecraft.entity.LivingEntity target = getTargetEntity(executor);

        if (target == null) {
            source.sendFeedback(() -> LangConfig.chat("commands.uuid.no_target", regs), false);
            return 0;
        }

        String uuidStr = target.getUuidAsString();
        String entityName = target.getName().getString();

        source.sendFeedback(() -> LangConfig.chat("commands.uuid.result", regs, "name", entityName, "uuid", uuidStr), false);
        return 1;
    }

    private static int executeGive(ServerCommandSource source, String idProcurado, ServerPlayerEntity target) {
        if (isBlockedByLicense(source, "give")) return 0;
        if (target == null) return 0;
        RegistryWrapper.WrapperLookup regs = source.getWorld().getRegistryManager();
        CosmeticData data = getCosmeticById(idProcurado);

        if (data == null) {
            source.sendFeedback(() -> LangConfig.chat("general.error.cosmetic_not_found", regs, "id", idProcurado), false);
            return 0;
        }

        boolean success = DatabaseManager.unlockCosmetic(target.getUuid(), idProcurado);
        GreatCosmetics.debugLog("/gc give: '" + idProcurado + "' -> " + target.getName().getString() + " (executor=" + source.getName() + ") success=" + success);

        if (success) {
            target.sendMessage(LangConfig.chat("commands.give.received", regs, "item", data.getChatSafeDisplayName()), false);

            if (source.getPlayer() != target) {
                source.sendFeedback(() -> LangConfig.chat("commands.give.success_other", regs, "id", idProcurado, "player", target.getName().getString()), false);
            }
        } else {
            source.sendFeedback(() -> LangConfig.chat("commands.give.already_has", regs, "id", idProcurado, "player", target.getName().getString()), false);
        }

        return 1;
    }

    // ==========================================
    // MÉTODO NOVO: FORÇAR EQUIPAR/DESEQUIPAR EM OUTRO PLAYER (ignora limite de slot/tipo)
    // Diferente do payload EquipCosmeticPayload (usado pelo próprio player via GUI), aqui
    // chamamos o DatabaseManager direto — sem checar slotLimit/typeLimit — porque é uma
    // ação administrativa explícita (/gc cosmetics equip), não uma troca normal do jogador.
    // ==========================================
    private static int executeForceEquip(ServerCommandSource source, String idProcurado, ServerPlayerEntity target) {
        if (isBlockedByLicense(source, "cosmetics")) return 0;
        if (target == null) return 0;
        RegistryWrapper.WrapperLookup regs = source.getWorld().getRegistryManager();
        CosmeticData data = getCosmeticById(idProcurado);

        if (data == null) {
            source.sendFeedback(() -> LangConfig.chat("general.error.cosmetic_not_found", regs, "id", idProcurado), false);
            return 0;
        }

        DatabaseManager.unlockCosmetic(target.getUuid(), idProcurado);
        DatabaseManager.equipCosmetic(target.getUuid(), idProcurado, data.slot.name(), data.type);
        DatabaseManager.broadcastPlayerCosmetics(target);
        GreatCosmetics.debugLog("/gc cosmetics equip: '" + idProcurado + "' force-equipped on " + target.getName().getString() + " (executor=" + source.getName() + ").");

        source.sendFeedback(() -> LangConfig.chat("commands.forceequip.success", regs, "id", idProcurado, "player", target.getName().getString()), false);
        return 1;
    }

    private static int executeForceUnequip(ServerCommandSource source, String idProcurado, ServerPlayerEntity target) {
        if (isBlockedByLicense(source, "cosmetics")) return 0;
        if (target == null) return 0;
        RegistryWrapper.WrapperLookup regs = source.getWorld().getRegistryManager();
        CosmeticData data = getCosmeticById(idProcurado);

        if (data == null) {
            source.sendFeedback(() -> LangConfig.chat("general.error.cosmetic_not_found", regs, "id", idProcurado), false);
            return 0;
        }

        boolean success = DatabaseManager.unequipCosmetic(target.getUuid(), idProcurado);
        DatabaseManager.broadcastPlayerCosmetics(target);
        GreatCosmetics.debugLog("/gc cosmetics unequip: '" + idProcurado + "' from " + target.getName().getString() + " (executor=" + source.getName() + ") success=" + success);

        if (success) {
            source.sendFeedback(() -> LangConfig.chat("commands.forceunequip.success", regs, "id", idProcurado, "player", target.getName().getString()), false);
        } else {
            source.sendFeedback(() -> LangConfig.chat("commands.forceunequip.not_equipped", regs, "player", target.getName().getString()), false);
        }
        return 1;
    }

    // ==========================================
    // MÉTODOS NOVOS: TAGS GIVE/REMOVE
    // ==========================================
    private static int executeTagGive(ServerCommandSource source, String idProcurado, ServerPlayerEntity target) {
        if (isBlockedByLicense(source, "tags")) return 0;
        if (target == null) return 0;
        RegistryWrapper.WrapperLookup regs = source.getWorld().getRegistryManager();
        com.f4xizzz.greatcosmetics.config.TagData data = com.f4xizzz.greatcosmetics.config.TagsConfig.getById(idProcurado);

        if (data == null) {
            source.sendFeedback(() -> LangConfig.chat("general.error.tag_not_found", regs, "id", idProcurado), false);
            return 0;
        }

        if (data.isGroupTag) {
            source.sendFeedback(() -> LangConfig.chat("commands.tag.group_cannot_give", regs), false);
            return 0;
        }

        boolean success = com.f4xizzz.greatcosmetics.database.DatabaseManager.unlockTag(target.getUuid(), idProcurado);
        GreatCosmetics.debugLog("/gc tags give: '" + idProcurado + "' -> " + target.getName().getString() + " (executor=" + source.getName() + ") success=" + success);

        if (success) {
            source.sendFeedback(() -> LangConfig.chat("commands.tag.given", regs, "id", idProcurado, "player", target.getName().getString()), false);
            target.sendMessage(LangConfig.chat("commands.tag.received", regs, "name", data.displayName), false);
            com.f4xizzz.greatcosmetics.GreatCosmetics.syncPlayerTags(target);
        } else {
            source.sendFeedback(() -> LangConfig.chat("commands.tag.already_has", regs, "id", idProcurado, "player", target.getName().getString()), false);
        }
        return 1;
    }

    private static int executeTagRemove(ServerCommandSource source, String idProcurado, ServerPlayerEntity target) {
        if (isBlockedByLicense(source, "tags")) return 0;
        if (target == null) return 0;
        RegistryWrapper.WrapperLookup regs = source.getWorld().getRegistryManager();
        com.f4xizzz.greatcosmetics.config.TagData data = com.f4xizzz.greatcosmetics.config.TagsConfig.getById(idProcurado);

        if (data == null) {
            source.sendFeedback(() -> LangConfig.chat("general.error.tag_not_found", regs, "id", idProcurado), false);
            return 0;
        }

        if (data.isGroupTag) {
            source.sendFeedback(() -> LangConfig.chat("commands.tag.group_cannot_remove", regs), false);
            return 0;
        }

        boolean success = com.f4xizzz.greatcosmetics.database.DatabaseManager.removeTagOwnership(target.getUuid(), idProcurado);
        GreatCosmetics.debugLog("/gc tags remove: '" + idProcurado + "' from " + target.getName().getString() + " (executor=" + source.getName() + ") success=" + success);

        if (success) {
            String equipped = com.f4xizzz.greatcosmetics.database.DatabaseManager.getEquippedTagId(target.getUuid());
            if (idProcurado.equalsIgnoreCase(equipped)) {
                com.f4xizzz.greatcosmetics.database.DatabaseManager.unequipAllForPlayer(target.getUuid());
                com.f4xizzz.greatcosmetics.util.LuckPermsTagManager.removeTag(target, data);
            }

            source.sendFeedback(() -> LangConfig.chat("commands.tag.removed", regs, "id", idProcurado, "player", target.getName().getString()), false);
            com.f4xizzz.greatcosmetics.GreatCosmetics.syncPlayerTags(target);
        } else {
            source.sendFeedback(() -> LangConfig.chat("commands.tag.not_has", regs, "id", idProcurado, "player", target.getName().getString()), false);
        }
        return 1;
    }

    private static int executeGiveItem(ServerCommandSource source, String idProcurado, ServerPlayerEntity target) {
        if (isBlockedByLicense(source, "giveitem")) return 0;
        if (target == null) return 0;
        RegistryWrapper.WrapperLookup regs = source.getWorld().getRegistryManager();
        CosmeticData data = getCosmeticById(idProcurado);

        if (data == null) {
            source.sendFeedback(() -> LangConfig.chat("general.error.cosmetic_not_found", regs, "id", idProcurado), false);
            return 0;
        }

        ItemStack item = buildCosmeticIcon(data, regs);
        GreatCosmetics.debugLog("/gc giveitem: physical item of '" + idProcurado + "' -> " + target.getName().getString() + " (executor=" + source.getName() + ").");

        if (!target.getInventory().insertStack(item)) {
            target.dropItem(item, false);
            source.sendFeedback(() -> LangConfig.chat("commands.giveitem.inv_full", regs, "id", idProcurado, "player", target.getName().getString()), false);
        } else {
            source.sendFeedback(() -> LangConfig.chat("commands.giveitem.success", regs, "id", idProcurado, "player", target.getName().getString()), false);
        }

        return 1;
    }

    private static int executeRemove(ServerCommandSource source, String idProcurado, ServerPlayerEntity target) {
        if (isBlockedByLicense(source, "remove")) return 0;
        if (target == null) return 0;
        RegistryWrapper.WrapperLookup regs = source.getWorld().getRegistryManager();
        CosmeticData data = getCosmeticById(idProcurado);

        if (data == null) {
            source.sendFeedback(() -> LangConfig.chat("general.error.cosmetic_not_found", regs, "id", idProcurado), false);
            return 0;
        }

        boolean success = DatabaseManager.removeCosmetic(target.getUuid(), idProcurado);
        GreatCosmetics.debugLog("/gc remove: '" + idProcurado + "' from " + target.getName().getString() + " (executor=" + source.getName() + ") success=" + success);

        if (success) {
            source.sendFeedback(() -> LangConfig.chat("commands.remove.success", regs, "id", idProcurado, "player", target.getName().getString()), false);
        } else {
            source.sendFeedback(() -> LangConfig.chat("commands.remove.not_has", regs, "id", idProcurado, "player", target.getName().getString()), false);
        }

        return 1;
    }

    // ==========================================
    // MÉTODO NOVO: GIVE SKIN
    // ==========================================
    private static int executeGiveSkin(ServerCommandSource source, String idProcurado, ServerPlayerEntity target) {
        if (isBlockedByLicense(source, "giveskin")) return 0;
        if (target == null) return 0;
        RegistryWrapper.WrapperLookup regs = source.getWorld().getRegistryManager();
        PokemonSkin skin = SkinConfigManager.getSkin(idProcurado);

        if (skin == null) {
            source.sendFeedback(() -> LangConfig.chat("general.error.skin_not_found", regs, "id", idProcurado), false);
            return 0;
        }

        boolean success = DatabaseManager.unlockPokemonSkin(target.getUuid(), idProcurado);
        GreatCosmetics.debugLog("/gc giveskin: '" + idProcurado + "' -> " + target.getName().getString() + " (executor=" + source.getName() + ") success=" + success);

        if (success) {
            syncPlayerSkins(target);
            target.sendMessage(LangConfig.chat("commands.giveskin.received", regs, "name", skin.getDisplayName()), false);

            if (source.getPlayer() != target) {
                source.sendFeedback(() -> LangConfig.chat("commands.giveskin.success_other", regs, "id", idProcurado, "player", target.getName().getString()), false);
            }
        } else {
            source.sendFeedback(() -> LangConfig.chat("commands.giveskin.already_has", regs, "id", idProcurado, "player", target.getName().getString()), false);
        }

        return 1;
    }

    // ==========================================
    // MÉTODOS NOVOS: EQUIPAR/REMOVER EM NPCs
    // ==========================================
    private static int executeNpcEquip(ServerCommandSource source, String idProcurado, ServerPlayerEntity executor) {
        if (isBlockedByLicense(source, "npc")) return 0;
        RegistryWrapper.WrapperLookup regs = source.getWorld().getRegistryManager();
        CosmeticData data = getCosmeticById(idProcurado);

        if (data == null) {
            source.sendFeedback(() -> LangConfig.chat("commands.npc.not_found", regs), false);
            return 0;
        }

        net.minecraft.entity.LivingEntity target = getTargetEntity(executor);
        if (target == null || target instanceof ServerPlayerEntity) {
            source.sendFeedback(() -> LangConfig.chat("commands.npc.equip_no_target", regs), false);
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
        NpcCosmeticsConfig.equip(target.getUuid(), idProcurado);
        NpcCosmeticsConfig.broadcast(source.getServer(), target.getUuid());
        GreatCosmetics.debugLog("/gc npc equip: '" + idProcurado + "' -> NPC/entity " + target.getUuid() + " (executor=" + source.getName() + ").");

        source.sendFeedback(() -> LangConfig.chat("commands.npc.equipped", regs), false);
        return 1;
    }

    private static int executeNpcRemove(ServerCommandSource source, String slotName, ServerPlayerEntity executor) {
        if (isBlockedByLicense(source, "npc")) return 0;
        RegistryWrapper.WrapperLookup regs = source.getWorld().getRegistryManager();
        net.minecraft.entity.LivingEntity target = getTargetEntity(executor);
        if (target == null || target instanceof ServerPlayerEntity) {
            source.sendFeedback(() -> LangConfig.chat("commands.npc.remove_no_target", regs), false);
            return 0;
        }

        CosmeticData.VirtualSlot virtualSlot;
        try {
            virtualSlot = CosmeticData.VirtualSlot.valueOf(slotName.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendFeedback(() -> LangConfig.chat("commands.npc.invalid_slot", regs), false);
            return 0;
        }

        int removedCount = NpcCosmeticsConfig.unequipSlot(target.getUuid(), virtualSlot);
        if (removedCount > 0) {
            NpcCosmeticsConfig.broadcast(source.getServer(), target.getUuid());
        }
        GreatCosmetics.debugLog("/gc npc remove: slot " + virtualSlot.name() + " on " + target.getUuid() + " — " + removedCount + " removed (executor=" + source.getName() + ").");
        source.sendFeedback(() -> LangConfig.chat("commands.npc.slot_cleared", regs, "slot", virtualSlot.name()), false);
        return 1;
    }

    // ==========================================
    // COMANDO: DISPLAY (armor stand invisível vestindo o cosmético)
    // ==========================================
    private static final String DISPLAY_TAG = "gc_display";

    private static int executeDisplaySpawn(ServerCommandSource source, String idProcurado, ServerPlayerEntity executor) {
        if (isBlockedByLicense(source, "display")) return 0;
        RegistryWrapper.WrapperLookup regs = source.getWorld().getRegistryManager();
        CosmeticData data = getCosmeticById(idProcurado);

        if (data == null) {
            source.sendFeedback(() -> LangConfig.chat("commands.display.not_found", regs, "id", idProcurado), false);
            return 0;
        }

        // MESMO caminho do /gc npc equip: o cosmético NUNCA vem do slot de equipamento real —
        // é 100% renderizado pelo ArmorFeatureRendererMixin lendo o ClientCosmeticCache (por UUID
        // da entidade). Um armor stand invisível com braços aparece renderizando só as "features"
        // (equip/cosmético) — ou seja, só a model 3D do cosmético fica flutuando, como se um
        // corpo invisível estivesse usando a peça.
        net.minecraft.server.world.ServerWorld world = source.getWorld();
        float yaw = executor.getYaw() + 180.0f; // encara o player
        net.minecraft.entity.decoration.ArmorStandEntity stand =
                new net.minecraft.entity.decoration.ArmorStandEntity(world, executor.getX(), executor.getY(), executor.getZ());
        stand.refreshPositionAndAngles(executor.getX(), executor.getY(), executor.getZ(), yaw, 0.0f);
        stand.setYaw(yaw);
        stand.setBodyYaw(yaw);
        stand.setHeadYaw(yaw);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setShowArms(true);
        stand.setHideBasePlate(true);
        stand.addCommandTag(DISPLAY_TAG);

        if (!world.spawnEntity(stand)) {
            source.sendFeedback(() -> LangConfig.chat("commands.display.spawn_failed", regs), false);
            return 0;
        }

        NpcCosmeticsConfig.equip(stand.getUuid(), idProcurado);
        NpcCosmeticsConfig.broadcast(source.getServer(), stand.getUuid());
        GreatCosmetics.debugLog("/gc display: '" + idProcurado + "' -> stand " + stand.getUuid() + " (executor=" + source.getName() + ").");

        source.sendFeedback(() -> LangConfig.chat("commands.display.spawned", regs, "id", idProcurado), false);
        return 1;
    }

    private static int executeDisplayRemove(ServerCommandSource source, ServerPlayerEntity executor) {
        if (isBlockedByLicense(source, "display")) return 0;
        RegistryWrapper.WrapperLookup regs = source.getWorld().getRegistryManager();

        net.minecraft.entity.LivingEntity target = getTargetEntity(executor);
        if (!(target instanceof net.minecraft.entity.decoration.ArmorStandEntity stand)
                || !stand.getCommandTags().contains(DISPLAY_TAG)) {
            source.sendFeedback(() -> LangConfig.chat("commands.display.remove_no_target", regs), false);
            return 0;
        }

        NpcCosmeticsConfig.removeAll(stand.getUuid());
        NpcCosmeticsConfig.broadcast(source.getServer(), stand.getUuid());
        stand.discard();
        GreatCosmetics.debugLog("/gc display remove: stand " + stand.getUuid() + " (executor=" + source.getName() + ").");
        source.sendFeedback(() -> LangConfig.chat("commands.display.removed", regs), false);
        return 1;
    }

    private static int executeDisplayClear(ServerCommandSource source) {
        if (isBlockedByLicense(source, "display")) return 0;
        RegistryWrapper.WrapperLookup regs = source.getWorld().getRegistryManager();

        int removed = 0;
        for (net.minecraft.server.world.ServerWorld world : source.getServer().getWorlds()) {
            for (net.minecraft.entity.decoration.ArmorStandEntity stand : world.getEntitiesByType(
                    net.minecraft.entity.EntityType.ARMOR_STAND,
                    e -> e.getCommandTags().contains(DISPLAY_TAG))) {
                NpcCosmeticsConfig.removeAll(stand.getUuid());
                NpcCosmeticsConfig.broadcast(source.getServer(), stand.getUuid());
                stand.discard();
                removed++;
            }
        }

        final int total = removed;
        GreatCosmetics.debugLog("/gc display clear: " + total + " display(s) removed (executor=" + source.getName() + ").");
        source.sendFeedback(() -> LangConfig.chat("commands.display.cleared", regs, "count", String.valueOf(total)), false);
        return total;
    }

    // Dispara um "laser" invisível da câmera do jogador para pegar a entidade mais próxima que ele está olhando
    private static net.minecraft.entity.LivingEntity getTargetEntity(ServerPlayerEntity player) {
        double maxDistance = 5.0;
        net.minecraft.util.math.Vec3d cameraPos = player.getCameraPosVec(1.0F);
        net.minecraft.util.math.Vec3d rotation = player.getRotationVec(1.0F);
        net.minecraft.util.math.Vec3d endPos = cameraPos.add(rotation.x * maxDistance, rotation.y * maxDistance, rotation.z * maxDistance);
        net.minecraft.util.math.Box box = player.getBoundingBox().stretch(rotation.multiply(maxDistance)).expand(1.0D, 1.0D, 1.0D);

        net.minecraft.entity.LivingEntity closestEntity = null;
        double closestDistance = maxDistance * maxDistance;

        for (net.minecraft.entity.Entity entity : player.getWorld().getOtherEntities(player, box)) {
            if (entity instanceof net.minecraft.entity.LivingEntity livingEntity) {
                net.minecraft.util.math.Box entityBox = entity.getBoundingBox().expand(0.3F);
                java.util.Optional<net.minecraft.util.math.Vec3d> hit = entityBox.raycast(cameraPos, endPos);
                if (hit.isPresent()) {
                    double dist = cameraPos.squaredDistanceTo(hit.get());
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
    private static int executeRemoveSkin(ServerCommandSource source, String idProcurado, ServerPlayerEntity target) {
        if (isBlockedByLicense(source, "removeskin")) return 0;
        if (target == null) return 0;
        RegistryWrapper.WrapperLookup regs = source.getWorld().getRegistryManager();
        PokemonSkin skin = SkinConfigManager.getSkin(idProcurado);

        if (skin == null) {
            source.sendFeedback(() -> LangConfig.chat("general.error.skin_not_found", regs, "id", idProcurado), false);
            return 0;
        }

        boolean success = DatabaseManager.removePokemonSkin(target.getUuid(), idProcurado);
        GreatCosmetics.debugLog("/gc removeskin: '" + idProcurado + "' from " + target.getName().getString() + " (executor=" + source.getName() + ") success=" + success);

        if (success) {
            syncPlayerSkins(target);
            source.sendFeedback(() -> LangConfig.chat("commands.removeskin.success", regs, "id", idProcurado, "player", target.getName().getString()), false);
        } else {
            source.sendFeedback(() -> LangConfig.chat("commands.removeskin.not_has", regs, "id", idProcurado, "player", target.getName().getString()), false);
        }

        return 1;
    }

    // Delega pra GreatCosmetics.getCosmeticById() — que também cai pro ArmorCosmeticsConfig como
    // fallback (ver GreatCosmetics.java) — em vez de reimplementar só a busca em
    // CosmeticsConfig.cosmeticsMap. Essa cópia local ficava sem o fallback de armadura convertida
    // em cosmético, então TODO comando daqui (give, giveitem, cosmetics equip/unequip, remove...)
    // respondia "não encontrado" pra qualquer id de armor_cosmetics.json, mesmo o autocomplete
    // (SUGGEST_COSMETICS, que checa os dois lugares) sugerindo o id certinho.
    private static CosmeticData getCosmeticById(String idProcurado) {
        return GreatCosmetics.getCosmeticById(idProcurado);
    }

    public static ItemStack buildCosmeticIcon(CosmeticData data, RegistryWrapper.WrapperLookup regs) {
        // Armadura convertida em cosmético (ver ArmorCosmeticsConfig/CosmeticData.realItemId): o
        // item físico dado precisa ser o item REAL (ex: minecraft:diamond_helmet), não o ghost
        // carved_pumpkin — sem isso o jogador nunca tem o item de verdade no inventário, e
        // AcessoriesPage.hasRealItem() (que exige posse física do item real) nunca é satisfeito,
        // fazendo a armadura sumir do GUI de Acessórios mesmo depois de dada.
        ItemStack item;
        if (data.realItemId != null && !data.realItemId.isBlank()) {
            net.minecraft.util.Identifier realId = net.minecraft.util.Identifier.tryParse(data.realItemId);
            net.minecraft.item.Item realItem = realId != null ? net.minecraft.registry.Registries.ITEM.get(realId) : null;
            item = new ItemStack(realItem != null && realItem != Items.AIR ? realItem : Items.CARVED_PUMPKIN);
        } else {
            item = new ItemStack(Items.CARVED_PUMPKIN);
            item.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(data.cmd));
        }

        // Grava o id de cosmético (separado do id do item real) direto no item físico, via NBT
        // customizado — assim /gc giveitem sempre deixa os dois ids disponíveis no stack: o id
        // real do item (data.realItemId, já é o próprio tipo do item) e o id de cosmético
        // (data.id, usado por /gc give, tags, equip, etc), sem depender de nenhum outro sistema
        // pra saber a qual entrada do armor_cosmetics.json esse item específico corresponde.
        if (data.id != null && !data.id.isBlank()) {
            net.minecraft.nbt.NbtCompound customData = new net.minecraft.nbt.NbtCompound();
            customData.putString("greatcosmetics_id", data.id);
            item.set(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.of(customData));
        }

        if (data.maxDurability > 0) {
            item.set(DataComponentTypes.MAX_DAMAGE, data.maxDurability);
            item.set(DataComponentTypes.DAMAGE, 0);
        }

        Text displayName = BackpackManager.parseMiniMessage(data.DisplayName, regs);
        item.set(DataComponentTypes.CUSTOM_NAME, displayName);

        List<Text> loreLines = new ArrayList<>();
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

        if (!loreLines.isEmpty()) item.set(DataComponentTypes.LORE, new LoreComponent(loreLines));
        return item;
    }

    private static int executeReload(ServerCommandSource source) {
        if (isBlockedByLicense(source, "reload")) return 0;
        RegistryWrapper.WrapperLookup regs = source.getWorld().getRegistryManager();

        try {
            source.sendFeedback(() -> LangConfig.chat("general.reload.start", regs), true);
            GreatCosmetics.debugLog("/gc reload: triggered by " + source.getName() + ".");

            com.f4xizzz.greatcosmetics.config.MainConfig.loadConfig();
            LangConfig.load();
            CosmeticsConfig.loadConfig();
            com.f4xizzz.greatcosmetics.config.EffectConfig.loadEffects();
            com.f4xizzz.greatcosmetics.GreatCosmetics.broadcastEffectsCatalog(source.getServer());

            // --- RECARREGA A LISTA DE SKINS E NPCs ---
            SkinConfigManager.load();
            com.f4xizzz.greatcosmetics.config.LegacyCosmeticMigrationConfig.load();
            com.f4xizzz.greatcosmetics.config.SkinGroupConfigManager.load();
            com.f4xizzz.greatcosmetics.config.ArmorCosmeticsConfig.load();
            com.f4xizzz.greatcosmetics.config.LegacyCosmeticMigrationConfig.load();
            com.f4xizzz.greatcosmetics.config.TagsConfig.load();
            com.f4xizzz.greatcosmetics.GreatCosmetics.broadcastTagsCatalog(source.getServer());

            // Esse config era carregado só no boot (SERVER_STARTING) e nunca no /gc reload
            // — reload não pegava mudança nenhuma nele até reiniciar o server de verdade.
            NpcCosmeticsConfig.load();
            com.f4xizzz.greatcosmetics.config.SoundConfig.loadSounds();

            // Recalcula o SHA1 real do resource pack forçado (ver GreatCosmetics#refreshTextureHashForReload)
            // ANTES do resend abaixo — assim o resend já usa o hash atualizado se a textura mudou,
            // em vez de mandar o pacote velho agora e um segundo logo depois quando o hash mudasse
            // em background. Bloqueia aqui (comando manual do admin, não o join de ninguém).
            String textureHashError = com.f4xizzz.greatcosmetics.GreatCosmetics.refreshTextureHashForReload();
            if (textureHashError != null) {
                source.sendFeedback(() -> LangConfig.chat("general.reload.texture_hash_fail", regs, "error", textureHashError), true);
            }

            // Ressincroniza animação/cosméticos de NPC pros jogadores já online (mesma lógica do JOIN).
            for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
                // Mesmo princípio do JOIN (ver GreatCosmetics#sendCatalogStateSnapshot): informativo
                // antes do pacote de resource pack — nunca vai suprimir nada aqui de qualquer jeito
                // (todo mundo nesse loop já está com player != null, ver ClientJoinReloadState/
                // MinecraftClientMixin), mas atualiza o hash do catálogo que o client vai gravar no
                // cache assim que aplicar os payloads abaixo, pra próxima ENTRADA nesse servidor
                // já sair silenciosa.
                com.f4xizzz.greatcosmetics.GreatCosmetics.sendCatalogStateSnapshot(player, false);

                // Reenvia o resource pack forçado (MainConfig#forceTexture) — assim trocar a URL/hash
                // e dar /gc reload já atualiza a textura de quem já está online, sem precisar relogar.
                com.f4xizzz.greatcosmetics.GreatCosmetics.sendForcedResourcePack(player);

                NpcCosmeticsConfig.syncAllTo(player);

                // Catálogo de skins (PartyPage) — sem isso o /gc reload recarregava o
                // pokeskins.json só no servidor, e quem já tava conectado nunca via as
                // skins novas/editadas até relogar.
                ServerPlayNetworking.send(player, new com.f4xizzz.greatcosmetics.network.SyncSkinCatalogPayload(SkinConfigManager.getAllSkins(), com.f4xizzz.greatcosmetics.config.SkinGroupConfigManager.getAllColors()));
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
            com.f4xizzz.greatcosmetics.GreatCosmetics.broadcastCosmeticsCatalog(source.getServer(), true);
            com.f4xizzz.greatcosmetics.GreatCosmetics.broadcastArmorCosmeticsCatalog(source.getServer(), true);

            // mainconfig.conf (slots/types/config geral) também nunca era resincronizado no
            // /gc reload — Dev Studio e EquippedSlotsWidget ficavam com o valor de quando o
            // client entrou até relogar.
            com.f4xizzz.greatcosmetics.GreatCosmetics.broadcastMainConfig(source.getServer());

            GreatCosmetics.debugLog("/gc reload: completed successfully — " + CosmeticsConfig.cosmeticsMap.size() + " cosmetics, "
                    + SkinConfigManager.getAllSkins().size() + " skins, " + com.f4xizzz.greatcosmetics.config.TagsConfig.tagsMap.size() + " tags.");
            source.sendFeedback(() -> LangConfig.chat("general.reload.done", regs, "count", source.getServer().getPlayerManager().getCurrentPlayerCount()), true);

        } catch (Exception e) {
            GreatCosmetics.debugLog("/gc reload: FAILED — " + e);
            source.sendFeedback(() -> LangConfig.chat("general.reload.fail", regs), true);
            e.printStackTrace();
        }

        return 1;
    }

    public static void syncPlayerSkins(ServerPlayerEntity target) {
        List<String> unlocked = DatabaseManager.getPlayerUnlockedSkins(target.getUuid());
        Map<String, Long> cooldowns = new java.util.HashMap<>();

        for (String skinId : unlocked) {
            cooldowns.put(skinId, DatabaseManager.getSkinLastApplied(target.getUuid(), skinId));
        }

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(target, new com.f4xizzz.greatcosmetics.network.SyncPokemonSkinsPayload(unlocked, cooldowns));

        // Catálogo completo de skins (não só as desbloqueadas) — ver SyncSkinCatalogPayload. Sem
        // isso o client só enxergava as skins que já tinha salvas localmente em disco, nunca as
        // que o servidor tem de verdade.
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(target, new com.f4xizzz.greatcosmetics.network.SyncSkinCatalogPayload(SkinConfigManager.getAllSkins(), com.f4xizzz.greatcosmetics.config.SkinGroupConfigManager.getAllColors()));
    }

    private static int executeInspect(ServerCommandSource source) {
        if (isBlockedByLicense(source, "inspect")) return 0;
        return 1;
    }

    private static int executeDebug(ServerCommandSource source) {
        if (isBlockedByLicense(source, "debug")) return 0;
        RegistryWrapper.WrapperLookup regs = source.getWorld().getRegistryManager();

        GreatCosmetics.isDebugMode = !GreatCosmetics.isDebugMode;
        boolean enabled = GreatCosmetics.isDebugMode;

        com.f4xizzz.greatcosmetics.network.DebugModePayload payload = new com.f4xizzz.greatcosmetics.network.DebugModePayload(enabled);
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }

        source.sendFeedback(() -> LangConfig.chat(enabled ? "general.debug.enabled" : "general.debug.disabled", regs), true);

        return 1;
    }
}