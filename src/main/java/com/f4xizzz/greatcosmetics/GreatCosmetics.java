package com.f4xizzz.greatcosmetics;

import com.f4xizzz.greatcosmetics.command.CosmeticsCommand;
import com.f4xizzz.greatcosmetics.config.*;
import com.f4xizzz.greatcosmetics.network.*;
import com.f4xizzz.greatcosmetics.util.BackpackManager;

import com.f4xizzz.greatcosmetics.util.WardrobeManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GreatCosmetics implements ModInitializer {
	public static final String MOD_ID = "great-cosmetics";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static boolean isDebugMode = false;
	public static final Set<UUID> activeFlyPlayers = new HashSet<>();
	// Jogadores cujo PlayerAbilities#flySpeed está sendo controlado por um cosmético de
	// flySpeedMultiplier AGORA (ver handleSpeedLogic) — só existe pra saber quando devolver o
	// campo pro padrão vanilla (1x) depois de desequipar; sem isso, ver bug documentado ali.
	private static final Set<UUID> activeFlySpeedBoostPlayers = new HashSet<>();

	// --- RASTREADORES PARA OS SONS E CACHE DE ANIMAÇÃO ---
	public static final Map<UUID, net.minecraft.util.math.Vec3d> lastPositions = new HashMap<>();
	public static final Set<UUID> wasSneaking = new HashSet<>();

	// Efeitos de status (ex: night vision) concedidos por cosméticos ATUALMENTE equipados — ver
	// syncCosmeticStatusEffects(). Guarda quais IDs de efeito cada player tem "por causa de um
	// cosmético" agora, pra dar remove() nos que saírem da lista quando o jogador desequipar (em
	// vez de deixar só a duração curta acabar sozinha, que causava o ícone piscando "acabando").
	public static final Map<UUID, Set<Identifier>> activeCosmeticStatusEffects = new HashMap<>();

	// UUID -> tick alvo em que a re-validação de tags de grupo deve rodar de novo, um pouco depois
	// do join. O LuckPerms carrega os dados do usuário de forma assíncrona no login: rodar
	// validateEquippedGroupTag/autoEquipCurrentGroupTag imediato no JOIN podia acontecer ANTES
	// desse carregamento terminar, fazendo isInGroup() responder errado (ou "não pertence a nada
	// ainda") bem na hora que mais importa. A tentativa imediata continua existindo (cobre o caso
	// comum onde o LuckPerms já carregou a tempo), essa é só uma segunda passada de segurança.
	private static final Map<UUID, Integer> pendingJoinTagSync = new ConcurrentHashMap<>();

	// Tick alvo em que MainConfig#startupCommands deve rodar — null = já rodou (ou ainda não foi
	// agendado). SERVER_STARTED sozinho não é tarde o suficiente em server HÍBRIDO (Fabric +
	// plugins Bukkit/Spigot, ex: Mohist/Cardboard): os mods Fabric já terminaram de carregar
	// nesse ponto, mas plugins Bukkit (ex: o "styledsidebar" do exemplo do campo) podem ainda
	// estar no meio do próprio onEnable(), então um comando que dependa deles falhava
	// silenciosamente por rodar cedo demais. Agenda pra alguns segundos DEPOIS de SERVER_STARTED
	// em vez de rodar na hora.
	private static Integer pendingStartupCommandsTick = null;

	public static void debugLog(String message) {
		if (isDebugMode) {
			LOGGER.info("[GreatCosmetics DEBUG] " + message);
		}
	}

	/**
	 * Checa OP de verdade direto na ops.json (via PlayerManager), IGNORANDO
	 * player.hasPermissionLevel(N) de propósito — com o mod "Vanilla Permissions" instalado,
	 * hasPermissionLevel(2) podia resolver como true pra qualquer jogador (mesmo sem OP e sem
	 * permissão nenhuma) pra checagens de nível que ele não gerencia especificamente. isOperator()
	 * lê a lista real de operadores, sem passar por nenhuma camada de permission plugin.
	 */
	public static boolean isRealOperator(ServerPlayerEntity player) {
		return player.getServer().getPlayerManager().isOperator(player.getGameProfile());
	}

	public static void sendOpMessage(ServerPlayerEntity player, Text message, boolean actionBar) {
		if (isRealOperator(player)) {
			player.sendMessage(message, actionBar);
		}
	}

	/**
	 * Gate de licença pros payloads server-side (ver security.ActivationManager). Num servidor
	 * dedicado sem licença válida, avisa o jogador e devolve true (o receiver deve dar return).
	 * Singleplayer nunca bloqueia (o ActivationManager já marca isActivated=true nesse caso).
	 */
	public static boolean licenseBlocked(ServerPlayerEntity player) {
		net.minecraft.server.MinecraftServer server = player.getServer();
		if (server == null || !server.isDedicated()) return false;
		if (com.f4xizzz.greatcosmetics.security.ActivationManager.isModActivated()) return false;
		player.sendMessage(LangConfig.chat("general.license.locked", server.getRegistryManager()), false);
		return true;
	}

	// Toca som baseado na antiga SoundConfig
	public static void playCustomSound(ServerPlayerEntity player, String soundKey) {
		var soundData = SoundConfig.getSoundData(soundKey);
		if (soundData == null || soundData.id == null || soundData.id.isEmpty() || soundData.id.equalsIgnoreCase("none")) return;
		playCosmeticSound(player, soundData.id, (float) soundData.volume, (float) soundData.pitch);
	}

	// --- NOVO: Toca o som puro das configurações do Cosmético ---
	public static void playCosmeticSound(ServerPlayerEntity player, String soundId) {
		playCosmeticSound(player, soundId, 1.0f, 1.0f);
	}

	public static void playCosmeticSound(ServerPlayerEntity player, String soundId, float volume, float pitch) {
		if (soundId == null || soundId.trim().isEmpty() || soundId.equalsIgnoreCase("none")) return;
		Identifier id = Identifier.tryParse(soundId);
		if (id != null) {
			var optionalSound = Registries.SOUND_EVENT.getEntry(id);
			net.minecraft.registry.entry.RegistryEntry<net.minecraft.sound.SoundEvent> soundToPlay;
			if (optionalSound.isPresent()) {
				soundToPlay = optionalSound.get();
			} else {
				soundToPlay = net.minecraft.registry.entry.RegistryEntry.of(net.minecraft.sound.SoundEvent.of(id));
			}

			player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket(
					soundToPlay,
					net.minecraft.sound.SoundCategory.PLAYERS,
					player.getX(), player.getY(), player.getZ(),
					volume, pitch,
					player.getWorld().getRandom().nextLong()
			));
		}
	}

	@Override
	public void onInitialize() {
		com.f4xizzz.greatcosmetics.geckolib.GreatCosmeticsItems.register();

		PayloadTypeRegistry.playS2C().register(OpenWardrobePayload.ID, OpenWardrobePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CloseWardrobePayload.ID, CloseWardrobePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncCosmeticsPayload.ID, SyncCosmeticsPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.f4xizzz.greatcosmetics.network.SyncPlayerCosmeticsPayload.ID, com.f4xizzz.greatcosmetics.network.SyncPlayerCosmeticsPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(EquipCosmeticPayload.ID, EquipCosmeticPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(com.f4xizzz.greatcosmetics.network.ToggleVisibilityPayload.ID, com.f4xizzz.greatcosmetics.network.ToggleVisibilityPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(com.f4xizzz.greatcosmetics.network.ToggleCosmeticVisibilityPayload.ID, com.f4xizzz.greatcosmetics.network.ToggleCosmeticVisibilityPayload.CODEC);

		PayloadTypeRegistry.playC2S().register(com.f4xizzz.greatcosmetics.network.OpenBackpackKeybindPayload.ID, com.f4xizzz.greatcosmetics.network.OpenBackpackKeybindPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.f4xizzz.greatcosmetics.network.ShowBackpackSelectorPayload.ID, com.f4xizzz.greatcosmetics.network.ShowBackpackSelectorPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(com.f4xizzz.greatcosmetics.network.OpenSpecificBackpackPayload.ID, com.f4xizzz.greatcosmetics.network.OpenSpecificBackpackPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.f4xizzz.greatcosmetics.network.SyncCatalogStatePayload.ID, com.f4xizzz.greatcosmetics.network.SyncCatalogStatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(com.f4xizzz.greatcosmetics.network.OpenPcFromWardrobePayload.ID, com.f4xizzz.greatcosmetics.network.OpenPcFromWardrobePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncPokemonSkinsPayload.ID, SyncPokemonSkinsPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.f4xizzz.greatcosmetics.network.SyncSkinCatalogPayload.ID, com.f4xizzz.greatcosmetics.network.SyncSkinCatalogPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(EquipPokemonSkinPayload.ID, EquipPokemonSkinPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ClearPokemonSkinsPayload.ID, ClearPokemonSkinsPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ClearAllCosmeticsPayload.ID, ClearAllCosmeticsPayload.CODEC);

		// --- SISTEMA DE TAGS ---
		PayloadTypeRegistry.playS2C().register(SyncTagsPayload.ID, SyncTagsPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncPlayerTagsPayload.ID, SyncPlayerTagsPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(EquipTagPayload.ID, EquipTagPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SaveTagPayload.ID, SaveTagPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DeleteTagPayload.ID, DeleteTagPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.f4xizzz.greatcosmetics.network.SyncArmorCosmeticsPayload.ID, com.f4xizzz.greatcosmetics.network.SyncArmorCosmeticsPayload.CODEC);

		// --- SISTEMA DE EFEITOS (Dev Studio > Effects) ---
		PayloadTypeRegistry.playS2C().register(com.f4xizzz.greatcosmetics.network.SyncEffectsPayload.ID, com.f4xizzz.greatcosmetics.network.SyncEffectsPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(com.f4xizzz.greatcosmetics.network.SaveEffectPayload.ID, com.f4xizzz.greatcosmetics.network.SaveEffectPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(com.f4xizzz.greatcosmetics.network.DeleteEffectPayload.ID, com.f4xizzz.greatcosmetics.network.DeleteEffectPayload.CODEC);

		WardrobeManager.loadStudios();
		WardrobeManager.loadSessions();
		MainConfig.loadConfig();
		CosmeticsConfig.loadConfig();
		SkinConfigManager.load();
		com.f4xizzz.greatcosmetics.config.SkinGroupConfigManager.load();
		com.f4xizzz.greatcosmetics.config.ArmorCosmeticsConfig.load();
		com.f4xizzz.greatcosmetics.config.LegacyCosmeticMigrationConfig.load();
		TagsConfig.load();

		// Liga de verdade os bônus de CosmeticData.lure nos eventos do Cobblemon (shiny, IV,
		// hidden ability, chance de captura, exp, amizade) — sem isso "lure" era só dado exibido
		// no lore do item, sem nenhum efeito real no jogo. Ver LureManager pro que dá e o que NÃO
		// dá pra aplicar com a API pública do Cobblemon 1.7.3.
		com.f4xizzz.greatcosmetics.util.LureManager.register();

		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			com.f4xizzz.greatcosmetics.database.DatabaseManager.initialize();
			com.f4xizzz.greatcosmetics.config.NpcCosmeticsConfig.load();
		});

		// SISTEMA DE TAGS: o import dos grupos do LuckPerms roda de novo aqui (SERVER_STARTED,
		// o evento mais tardio do boot) porque na hora do onInitialize (acima) o LuckPerms pode
		// ainda não ter terminado de carregar os grupos dele — resultando num catálogo de tags
		// de grupo vazio mesmo com o LuckPerms instalado. A essa altura o boot já terminou, então
		// getLoadedGroups() já reflete os grupos de verdade.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			TagsConfig.load();
			broadcastTagsCatalog(server);

			// Passa a escutar troca de cargo do LuckPerms em tempo real — ver
			// LuckPermsTagManager.registerRankChangeListener().
			com.f4xizzz.greatcosmetics.util.LuckPermsTagManager.registerRankChangeListener(server);

			// Força /sr toda vez que o server termina de iniciar.
			server.getCommandManager().executeWithPrefix(server.getCommandSource(), "sr");

			// Comandos configuráveis (MainConfig#startupCommands, editável pelo Dev Studio) — igual
			// o /sr acima, mas pra qualquer comando que dependa de OUTRO mod/plugin já ter terminado
			// de carregar (ex: "styledsidebar reload"). Não roda na hora aqui — ver
			// pendingStartupCommandsTick pro motivo (server híbrido com plugins Bukkit que ainda não
			// terminaram o próprio onEnable() nesse ponto).
			pendingStartupCommandsTick = server.getTicks() + 100; // ~5s (20 ticks/s)

			// Calcula o SHA1 real do resource pack forçado (ver refreshTextureHashAsync) — só baixa
			// de novo em joins futuros quando o CONTEÚDO do arquivo mudar de verdade.
			refreshTextureHashAsync(server);

			// SISTEMA DE LICENÇA (ver security.ActivationManager) — singleplayer é sempre ativo;
			// servidor dedicado precisa de license.json válido (/gc activation <key>).
			com.f4xizzz.greatcosmetics.security.ActivationManager.checkLicenseOnStartup(server);
		});

		LangConfig.loadLang();
		SoundConfig.loadSounds();
		EffectConfig.loadEffects();

		CosmeticsCommand.register();

		// ==========================================
		// MODO DEBUG (server-authoritative, sincronizado com os clientes)
		// ==========================================
		PayloadTypeRegistry.playS2C().register(com.f4xizzz.greatcosmetics.network.DebugModePayload.ID, com.f4xizzz.greatcosmetics.network.DebugModePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.f4xizzz.greatcosmetics.network.SyncDevPermissionsPayload.ID, com.f4xizzz.greatcosmetics.network.SyncDevPermissionsPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.f4xizzz.greatcosmetics.network.SyncNameTagPayload.ID, com.f4xizzz.greatcosmetics.network.SyncNameTagPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(com.f4xizzz.greatcosmetics.network.DebugLogPayload.ID, com.f4xizzz.greatcosmetics.network.DebugLogPayload.CODEC);

		// ==========================================
		// DEV STUDIO: EDIÇÕES PERSISTINDO DE VERDADE NO SERVIDOR (ver SaveCosmeticPayload /
		// SaveMainConfigPayload / SyncMainConfigPayload)
		// ==========================================
		PayloadTypeRegistry.playS2C().register(com.f4xizzz.greatcosmetics.network.SyncMainConfigPayload.ID, com.f4xizzz.greatcosmetics.network.SyncMainConfigPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.f4xizzz.greatcosmetics.network.GrantCosmeticPayload.ID, com.f4xizzz.greatcosmetics.network.GrantCosmeticPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(com.f4xizzz.greatcosmetics.network.SaveMainConfigPayload.ID, com.f4xizzz.greatcosmetics.network.SaveMainConfigPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(com.f4xizzz.greatcosmetics.network.SaveCosmeticPayload.ID, com.f4xizzz.greatcosmetics.network.SaveCosmeticPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(com.f4xizzz.greatcosmetics.network.DeleteCosmeticPayload.ID, com.f4xizzz.greatcosmetics.network.DeleteCosmeticPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.SaveMainConfigPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayerEntity player = context.player();
				if (licenseBlocked(player)) return;
				if (!isRealOperator(player) && !checkPermission(player, "gc.dev")) return;

				try {
					com.google.gson.Gson gson = new com.google.gson.Gson();
					com.f4xizzz.greatcosmetics.config.MainConfig.ConfigData parsed =
							gson.fromJson(payload.configJson(), com.f4xizzz.greatcosmetics.config.MainConfig.ConfigData.class);
					if (parsed == null) return;

					com.f4xizzz.greatcosmetics.config.MainConfig.config = parsed;
					com.f4xizzz.greatcosmetics.config.MainConfig.saveConfig();
					broadcastMainConfig(context.server());
					debugLog("SaveMainConfigPayload: MainConfig saved by " + player.getName().getString() + " e sincronizado.");
				} catch (Exception e) {
					debugLog("SaveMainConfigPayload: FAILED to save by " + player.getName().getString() + " — " + e);
					player.sendMessage(LangConfig.chat("messages.devstudio.save_config_fail", context.server().getRegistryManager(), "error", e.getMessage()), false);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.SaveCosmeticPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayerEntity player = context.player();
				if (licenseBlocked(player)) return;
				if (!isRealOperator(player) && !checkPermission(player, "gc.dev")) return;

				try {
					if (payload.newId() == null || payload.newId().isBlank()) return;

					if (payload.isArmor()) {
						// Armadura convertida nunca é renomeável (ver DevCosmeticsSubPage) — o id
						// é sempre o mesmo de quando foi criada. Reconstrói como ArmorCosmeticEntry
						// (não CosmeticData genérico) pra saveAll() conseguir persistir TUDO (parts
						// incluso) de volta no armor_cosmetics.json, não só id/realItemId.
						com.f4xizzz.greatcosmetics.config.ArmorCosmeticsConfig.saveFromJson(payload.newId(), payload.jsonData(), payload.realItemId());
						// false: salvar pelo Dev Studio NUNCA dispara reload de textura/model no
						// client sozinho — só o /gc reload explícito faz isso.
						broadcastArmorCosmeticsCatalog(context.server(), false);
						debugLog("SaveCosmeticPayload(armor): '" + payload.newId() + "' saved by " + player.getName().getString() + ".");
					} else {
						com.google.gson.Gson gson = new com.google.gson.Gson();
						CosmeticData data = gson.fromJson(payload.jsonData(), CosmeticData.class);
						if (data == null) return;
						data.id = payload.newId();
						CosmeticsConfig.resolveModelIds(data);

						if (payload.oldId() != null && !payload.oldId().isBlank() && !payload.oldId().equals(payload.newId())) {
							CosmeticsConfig.cosmeticsMap.remove(payload.oldId());
						}
						CosmeticsConfig.cosmeticsMap.put(payload.newId(), data);
						invalidateCosmeticIndex();
						CosmeticsConfig.saveConfig();
						broadcastCosmeticsCatalog(context.server(), false);
						debugLog("SaveCosmeticPayload: '" + payload.oldId() + "' -> '" + payload.newId() + "' saved by " + player.getName().getString() + ".");
					}
				} catch (Exception e) {
					debugLog("SaveCosmeticPayload: FAILED to save '" + payload.newId() + "' by " + player.getName().getString() + " — " + e);
					player.sendMessage(LangConfig.chat("messages.devstudio.save_cosmetic_fail", context.server().getRegistryManager(), "error", e.getMessage()), false);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.DeleteCosmeticPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayerEntity player = context.player();
				if (licenseBlocked(player)) return;
				if (!(isRealOperator(player) || checkPermission(player, "gc.dev"))) {
					player.sendMessage(LangConfig.chat("messages.devstudio.no_perm_delete_cosmetic", context.server().getRegistryManager()), true);
					return;
				}

				String id = payload.id();
				if (id == null || id.isBlank()) return;

				try {
					if (payload.isArmor()) {
						com.f4xizzz.greatcosmetics.config.ArmorCosmeticsConfig.remove(id);
						broadcastArmorCosmeticsCatalog(context.server(), false);
					} else {
						CosmeticsConfig.cosmeticsMap.remove(id);
						invalidateCosmeticIndex();
						CosmeticsConfig.saveConfig();
						broadcastCosmeticsCatalog(context.server(), false);
					}
					debugLog("DeleteCosmeticPayload: '" + id + "' (armor=" + payload.isArmor() + ") deleted by " + player.getName().getString() + ".");
					player.sendMessage(LangConfig.chat("messages.devstudio.cosmetic_deleted", context.server().getRegistryManager(), "id", id), true);
				} catch (Exception e) {
					debugLog("DeleteCosmeticPayload: FAILED to delete '" + id + "' by " + player.getName().getString() + " — " + e);
					player.sendMessage(LangConfig.chat("messages.devstudio.delete_cosmetic_fail", context.server().getRegistryManager(), "error", e.getMessage()), false);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.DebugLogPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				if (isDebugMode) {
					LOGGER.info("[GreatCosmetics DEBUG] [Client:" + context.player().getName().getString() + "] " + payload.message());
				}
			});
		});

		// ==========================================
		// REMOVER SKINS (MODO DEV)
		// ==========================================
		ServerPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.ClearPokemonSkinsPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayerEntity player = context.player();
				if (licenseBlocked(player)) return;

				// "gc.permission.dev" (antes usado aqui) nunca foi o node certo — em TODO o resto
				// do mod (linhas 249/269/847/883, TagsPage, botão DEV da PartyPage) o acesso ao
				// Dev Mode é sempre gated por "gc.dev". Quem tivesse "gc.dev" mas não fosse OP de
				// verdade conseguia abrir o Dev Mode inteiro só não conseguia usar "Limpar Skins"
				// dentro dele, silenciosamente, por checar um permission node que não existe/nunca
				// foi documentado nem concedido por ninguém.
				if (!isRealOperator(player) && !checkPermission(player, "gc.dev")) {
					player.sendMessage(LangConfig.chat("messages.devstudio.no_perm_clear_skins", context.server().getRegistryManager()), false);
					return;
				}

				com.cobblemon.mod.common.api.storage.party.PlayerPartyStore party = com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player);
				boolean changed = false;

				if (payload.slot() == -1) {
					for (int i = 0; i < party.size(); i++) {
						com.cobblemon.mod.common.pokemon.Pokemon p = party.get(i);
						if (p != null && removeAllSkinAspects(p)) {
							changed = true;
						}
					}
				} else {
					com.cobblemon.mod.common.pokemon.Pokemon p = party.get(payload.slot());
					if (p != null && removeAllSkinAspects(p)) {
						changed = true;
					}
				}

				if (changed) {
					playCustomSound(player, "equip_item");
					player.sendMessage(LangConfig.chat("messages.skin.removed_success", context.server().getRegistryManager()), false);
				} else {
					player.sendMessage(LangConfig.chat("messages.skin.none_detected", context.server().getRegistryManager()), false);
				}
			});
		});

		// ==========================================
		// EQUIPAR SKIN NO POKEMON DA PARTY
		// ==========================================
		ServerPlayNetworking.registerGlobalReceiver(EquipPokemonSkinPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayerEntity player = context.player();
				if (licenseBlocked(player)) return;

				debugLog("EquipPokemonSkinPayload from " + player.getName().getString() + ": skinId='" + payload.skinId() + "' slot=" + payload.slot());

				PokemonSkin skin = SkinConfigManager.getSkin(payload.skinId());
				if (skin == null) {
					debugLog("EquipPokemonSkinPayload: skin '" + payload.skinId() + "' not found in the catalog.");
					return;
				}

				com.cobblemon.mod.common.api.storage.party.PlayerPartyStore party = com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player);
				com.cobblemon.mod.common.pokemon.Pokemon targetPokemon = party.get(payload.slot());
				if (targetPokemon == null) {
					debugLog("EquipPokemonSkinPayload: slot " + payload.slot() + " empty in the party of " + player.getName().getString() + ".");
					return;
				}

				boolean isOp = isRealOperator(player);

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
				debugLog("EquipPokemonSkinPayload: skin '" + skin.getId() + "' applied to the Pokémon in slot " + payload.slot() + " of " + player.getName().getString() + ".");

				playCustomSound(player, "equip_item");
				player.sendMessage(LangConfig.chat("messages.skin.applied", context.server().getRegistryManager()), true);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(ClearAllCosmeticsPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				var player = context.player();
				if (licenseBlocked(player)) return;

				java.util.List<String> equipped = new java.util.ArrayList<>(com.f4xizzz.greatcosmetics.database.DatabaseManager.getPlayerEquippedCosmetics(player.getUuid()));

				for (String id : equipped) {
					com.f4xizzz.greatcosmetics.database.DatabaseManager.unequipCosmetic(player.getUuid(), id);
				}

				com.f4xizzz.greatcosmetics.database.DatabaseManager.broadcastPlayerCosmetics(player);
				debugLog("ClearAllCosmeticsPayload: " + equipped.size() + " cosmetics removed from " + player.getName().getString() + ": " + equipped);
				player.sendMessage(LangConfig.chat("messages.cosmetic.all_removed", context.server().getRegistryManager()), true);
				playCustomSound(player, "equip_item");
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.ToggleVisibilityPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				if (licenseBlocked(context.player())) return;
				com.f4xizzz.greatcosmetics.database.DatabaseManager.updatePlayerSetting(context.player().getUuid(), payload.setting(), payload.state());
				com.f4xizzz.greatcosmetics.database.DatabaseManager.broadcastPlayerCosmetics(context.player());
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.ToggleCosmeticVisibilityPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				if (licenseBlocked(context.player())) return;
				com.f4xizzz.greatcosmetics.database.DatabaseManager.setCosmeticHidden(context.player().getUuid(), payload.cosmeticId(), payload.hidden());
				com.f4xizzz.greatcosmetics.database.DatabaseManager.broadcastPlayerCosmetics(context.player());
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(CloseWardrobePayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				com.f4xizzz.greatcosmetics.util.WardrobeManager.closeWardrobeAndReturn(context.player());
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.OpenBackpackKeybindPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				if (licenseBlocked(context.player())) return;
				BackpackManager.handleOpenRequest(context.player());
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.OpenSpecificBackpackPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				if (licenseBlocked(context.player())) return;
				BackpackManager.openSpecificBackpack(context.player(), payload.cosmeticId());
			});
		});

		// Botão PC da PartyPage: abre o PC direto pela API do Cobblemon (igual clicar num bloco
		// de PC de verdade), sem passar pelo comando "/pc" — assim nenhum plugin de permissão de
		// comando (ex: VanillaPermissions) consegue bloquear isso sem querer.
		ServerPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.OpenPcFromWardrobePayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayerEntity player = context.player();
				if (licenseBlocked(player)) return;
				try {
					if (com.cobblemon.mod.common.util.PlayerExtensionsKt.isInBattle(player)) return;

					com.cobblemon.mod.common.api.storage.pc.PCStore pcStore = com.cobblemon.mod.common.util.PlayerExtensionsKt.pc(player);

					// PCLink puro (sem PermissiblePcLink) de propósito — o wrapper "Permissible"
					// checa a permissão real do Cobblemon (CobblemonPermissions.getPC()) a cada
					// ação dentro do PC, e em servidores onde o grupo default não tem essa
					// permissão liberada, o PC abria mas as ações dentro dele ficavam bloqueadas.
					// O PC do wardrobe deve se comportar como se fosse um PC físico colocado na
					// frente do player e aberto direto — sem depender de permissão nenhuma.
					com.cobblemon.mod.common.api.storage.pc.link.PCLinkManager.INSTANCE.addLink(
							new com.cobblemon.mod.common.api.storage.pc.link.PCLink(pcStore, player.getUuid())
					);
					new com.cobblemon.mod.common.net.messages.client.storage.pc.OpenPCPacket(pcStore, null).sendToPlayer(player);
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
		});

		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			com.f4xizzz.greatcosmetics.database.DatabaseManager.close();
			com.f4xizzz.greatcosmetics.config.NpcCosmeticsConfig.flush();
		});

		// ==========================================
		// CARREGAMENTO E SINCRONIZAÇÃO NO JOIN
		// ==========================================
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			server.execute(() -> {
				// ==========================================
				// CHECAGEM DO MOD DO CLIENTE
				// SyncCosmeticsPayload é registrado como receiver tanto no jar completo (dev) quanto
				// no jar do jogador (client-only) — se o client não respondeu que sabe receber esse
				// canal, ele não tem o SaSCosmetics instalado (ou está numa versão desatualizada que
				// ainda não registrava esse payload). Sem essa checagem, o jogador só descobria isso
				// quando o servidor mandava um payload que o client não reconhecia, derrubando ele com
				// uma mensagem genérica de erro de pacote em vez de um aviso claro pra atualizar.
				// ==========================================
				debugLog("JOIN: " + handler.player.getName().getString() + " connecting — starting sync.");

				if (!ServerPlayNetworking.canSend(handler.player, SyncCosmeticsPayload.ID)) {
					debugLog("JOIN: " + handler.player.getName().getString() + " REJECTED — client didn't register the SyncCosmeticsPayload channel (mod missing/outdated).");
					handler.disconnect(LangConfig.chat("messages.join.mod_missing", server.getRegistryManager()));
					return;
				}

				// Informativo primeiro (payload informativo antes do pacote vanilla que age): o
				// client precisa saber SE o cache dele pra esse servidor já bate ANTES do pacote
				// de resource pack chegar, senão não dá tempo de
				// decidir suprimir a SplashOverlay a tempo (ver ClientJoinReloadState).
				sendCatalogStateSnapshot(handler.player, true);
				sendForcedResourcePack(handler.player);

				// 1. Sincroniza Acessórios (Antigo)
				ServerPlayNetworking.send(handler.player, new SyncCosmeticsPayload(CosmeticsConfig.cosmeticsMap, true));
				ServerPlayNetworking.send(handler.player, new com.f4xizzz.greatcosmetics.network.DebugModePayload(isDebugMode));

				// PERMISSÕES DE DEV: calculadas aqui (única fonte confiável) e sincronizadas —
				// ver SyncDevPermissionsPayload pro motivo de nunca calcular isso no client.
				ServerPlayNetworking.send(handler.player, new com.f4xizzz.greatcosmetics.network.SyncDevPermissionsPayload(
						isRealOperator(handler.player),
						checkPermission(handler.player, "gc.dev"),
						checkPermission(handler.player, MainConfig.config.devModePermission)
				));
				com.f4xizzz.greatcosmetics.database.DatabaseManager.broadcastPlayerCosmetics(handler.player);

				// broadcastPlayerCosmetics() só manda os cosméticos do PRÓPRIO jogador que entrou
				// pra todo mundo — sem isso aqui, quem JÁ estava online e tinha algo equipado
				// nunca era resincronizado pro jogador NOVO (ClientCosmeticCache só aprende sobre
				// o UUID de outro player quando recebe um SyncPlayerCosmeticsPayload pra ele, e
				// isso só acontecia de novo se aquele outro player equipasse/desequipasse ALGO
				// depois do novo já estar online). O cosmético do outro player simplesmente não
				// renderizava pro jogador recém-entrado até alguém mexer em algo ou ele relogar.
				for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
					if (online != handler.player) {
						com.f4xizzz.greatcosmetics.database.DatabaseManager.broadcastPlayerCosmetics(online);
					}
				}

				com.f4xizzz.greatcosmetics.command.CosmeticsCommand.syncPlayerSkins(handler.player);
				com.f4xizzz.greatcosmetics.config.NpcCosmeticsConfig.syncAllTo(handler.player);

				// PROTEÇÃO CONTRA DESCONEXÃO DENTRO DO WARDROBE: se por qualquer motivo o
				// jogador ficou "preso" no studio (o hook de DISCONNECT normalmente já resolve
				// isso, essa é a rede de segurança pra quedas abruptas, tipo o servidor inteiro
				// crashando antes do DISCONNECT disparar), devolve ele pra posição de antes.
				WardrobeManager.forceReturnIfPending(handler.player);

				// MIGRAÇÃO AUTOMÁTICA DE COSMÉTICOS ANTIGOS (carved_pumpkin com CMD antigo -> acessório novo)
				com.f4xizzz.greatcosmetics.util.LegacyCosmeticMigrator.migratePlayer(handler.player);

				// SISTEMA DE TAGS: catálogo completo + posse/tag equipada do jogador que entrou
				validateEquippedGroupTag(handler.player);
				autoEquipCurrentGroupTag(handler.player);
				ServerPlayNetworking.send(handler.player, new SyncTagsPayload(new HashMap<>(TagsConfig.tagsMap)));
				syncPlayerTags(handler.player);

				// SISTEMA DE EFEITOS: catálogo completo pro jogador que entrou (ver SaveEffectPayload)
				ServerPlayNetworking.send(handler.player, new com.f4xizzz.greatcosmetics.network.SyncEffectsPayload(new com.google.gson.Gson().toJson(EffectConfig.effectsMap)));

				// NAMETAG (prefix/suffix do LuckPerms — ver SyncNameTagPayload): antes só era
				// mandado quando o wardrobe abria, então ClientNameTagCache ficava sem valor
				// nenhum (prefix/suffix vazios) até o player abrir o wardrobe pela primeira vez
				// depois de logar. Mandar aqui também deixa ele pronto desde o join.
				String[] joinPrefixSuffix = com.f4xizzz.greatcosmetics.util.LuckPermsTagManager.getPrefixSuffix(handler.player);
				ServerPlayNetworking.send(handler.player, new com.f4xizzz.greatcosmetics.network.SyncNameTagPayload(joinPrefixSuffix[0], joinPrefixSuffix[1]));

				// Segunda passada da validação de tags de grupo 3s depois — ver o comentário em
				// pendingJoinTagSync pro motivo (LuckPerms pode não ter terminado de carregar os
				// dados do usuário ainda nesse ponto do join).
				pendingJoinTagSync.put(handler.player.getUuid(), server.getTicks() + 60);

				// ARMADURAS CONVERTIDAS EM COSMÉTICO: catálogo pro cliente montar o CosmeticData sintético
				ServerPlayNetworking.send(handler.player, new com.f4xizzz.greatcosmetics.network.SyncArmorCosmeticsPayload(
						new HashMap<>(com.f4xizzz.greatcosmetics.config.ArmorCosmeticsConfig.armorCosmetics), true));

				// MAINCONFIG (slots/types/config geral): ver SyncMainConfigPayload — sem isso o
				// Dev Studio e o EquippedSlotsWidget nunca sabiam os limites/permissões de
				// verdade configurados no servidor numa conexão remota.
				ServerPlayNetworking.send(handler.player, new com.f4xizzz.greatcosmetics.network.SyncMainConfigPayload(
						new com.google.gson.Gson().toJson(com.f4xizzz.greatcosmetics.config.MainConfig.config)));

				if (isRealOperator(handler.player)) {
					int count = CosmeticsConfig.cosmeticsMap.size();
					handler.player.sendMessage(LangConfig.chat("messages.join.synced", server.getRegistryManager(), "count", count), false);
				}

				// ARMADURA VIRADA COSMÉTICO: se o jogador já entrar com o item real no inventário
				// (deu pra ele antes de converter, trouxe de outro servidor, etc), converte na hora.
				checkAndConvertArmorInventory(handler.player);
				validateEquippedCosmeticOwnership(handler.player);
				debugLog("JOIN: sync for " + handler.player.getName().getString() + " completed.");
			});
		});

		// ==========================================
		// PROTEÇÃO CONTRA DESCONEXÃO DENTRO DO WARDROBE
		// Se o jogador desconectar (kick, crash do cliente, fechar o jogo) enquanto ainda
		// estiver dentro do wardrobe/studio, devolve ele pra posição de antes ANTES da posição
		// atual (dentro do studio) ser salva no NBT dele — sem isso, ele reaparece dentro do
		// studio no próximo login.
		// ==========================================
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			debugLog("DISCONNECT: " + handler.player.getName().getString() + " disconnecting — clearing in-memory state.");
			WardrobeManager.forceReturnIfPending(handler.player);
			activeCosmeticStatusEffects.remove(handler.player.getUuid());
			activeFlyPlayers.remove(handler.player.getUuid());
			activeFlySpeedBoostPlayers.remove(handler.player.getUuid());
			com.f4xizzz.greatcosmetics.database.DatabaseManager.clearPlayerCache(handler.player.getUuid());
			com.f4xizzz.greatcosmetics.util.BackpackManager.handlePlayerDisconnect(handler.player.getUuid());
		});

		ServerPlayNetworking.registerGlobalReceiver(EquipCosmeticPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				var player = context.player();
				if (licenseBlocked(player)) return;
				String cosmeticId = payload.cosmeticId();
				boolean requestedDevMode = payload.isDevMode();

				debugLog("EquipCosmeticPayload from " + player.getName().getString() + ": cosmeticId='" + cosmeticId + "' devMode=" + requestedDevMode);

				CosmeticData data = getCosmeticById(cosmeticId);
				if (data == null) {
					debugLog("EquipCosmeticPayload: cosmetic '" + cosmeticId + "' not found — ignored.");
					return;
				}

				java.util.List<String> equipped = com.f4xizzz.greatcosmetics.database.DatabaseManager.getPlayerEquippedCosmetics(player.getUuid());
				if (equipped.contains(cosmeticId)) {
					debugLog("EquipCosmeticPayload: '" + cosmeticId + "' was already equipped — unequipping.");
					com.f4xizzz.greatcosmetics.database.DatabaseManager.unequipCosmetic(player.getUuid(), cosmeticId);
					player.sendMessage(LangConfig.chat("messages.cosmetic.unequipped", context.server().getRegistryManager()), true);
					com.f4xizzz.greatcosmetics.database.DatabaseManager.broadcastPlayerCosmetics(player);
					return;
				}

				String slotVirtual = data.slot.name();
				String type = data.type;

				boolean canUseDevMode = isRealOperator(player) || checkPermission(player, MainConfig.config.devModePermission);
				boolean isBypassing = requestedDevMode && canUseDevMode;

				// O client só mostra/permite clicar em cosméticos não desbloqueados quando o
				// player tem hasAllUnlocked (OP de verdade) ou o toggle de Dev Mode ligado — mas
				// sem checar posse aqui, um payload equipava QUALQUER cosmético direto no banco
				// (player_equipped_cosmetics) mesmo sem o player nunca ter desbloqueado ele de
				// verdade, e ficava "grudado" pra sempre depois que ele perdia o OP/permissão
				// (ver validateEquippedCosmeticOwnership, que só limpa DEPOIS do fato).
				if (!canUseDevMode && !com.f4xizzz.greatcosmetics.database.DatabaseManager.hasCosmetic(player.getUuid(), cosmeticId)) {
					debugLog("EquipCosmeticPayload: " + player.getName().getString() + " does NOT own '" + cosmeticId + "' and has no bypass — blocked.");
					player.sendMessage(LangConfig.chat("messages.cosmetic.not_owned", context.server().getRegistryManager()), true);
					playCustomSound(player, "error_action");
					return;
				}

				int slotLimit = isBypassing ? 99 : 1;

				if (!isBypassing && MainConfig.config.slots.containsKey(slotVirtual)) {
					var slotConfig = MainConfig.config.slots.get(slotVirtual);
					slotLimit = slotConfig.defaultLimit;

					String basePerm = slotConfig.permission;
					if (basePerm != null && !basePerm.isEmpty()) {
						if (checkPermission(player, basePerm + ".bypass")) {
							slotLimit = 99;
						} else {
							for (int i = 20; i > slotLimit; i--) {
								if (checkPermission(player, basePerm + "." + i)) {
									slotLimit = i;
									break;
								}
							}
						}
					}
				}

				// gc.extraslot.<slot>.<N> / gc.extraslot.all.<N>: bônus ADITIVO (soma em cima do
				// limite normal do slot), diferente do "gc.slot.<slot>.<N>" acima (que troca o
				// limite pro valor absoluto do tier mais alto concedido). Não se aplica quando
				// slotLimit já virou 99 (bypass) — não faz diferença nenhuma nesse caso.
				if (!isBypassing && slotLimit < 99) {
					slotLimit += getExtraSlotBonus(player, slotVirtual);
				}

				int currentInSlot = com.f4xizzz.greatcosmetics.database.DatabaseManager.getEquippedCountBySlot(player.getUuid(), slotVirtual);
				if (currentInSlot >= slotLimit) {
					debugLog("EquipCosmeticPayload: slot '" + slotVirtual + "' limit reached for " + player.getName().getString() + " (" + currentInSlot + "/" + slotLimit + ").");
					player.sendMessage(LangConfig.chat("messages.cosmetic.slot_limit", context.server().getRegistryManager(), "slot", slotVirtual, "limit", slotLimit), true);
					playCustomSound(player, "error_action");
					return;
				}

				if (MainConfig.config.types.containsKey(type)) {
					var typeConfig = MainConfig.config.types.get(type);
					int typeLimit = isBypassing ? 99 : typeConfig.limitPerPlayer;

					if (!isBypassing) {
						String basePerm = typeConfig.permission;
						if (basePerm != null && !basePerm.isEmpty()) {
							if (checkPermission(player, basePerm + ".bypass")) {
								typeLimit = 99;
							} else {
								for (int i = 20; i > typeLimit; i--) {
									if (checkPermission(player, basePerm + "." + i)) {
										typeLimit = i;
										break;
									}
								}
							}
						}
					}

					int currentInType = com.f4xizzz.greatcosmetics.database.DatabaseManager.getEquippedCountByType(player.getUuid(), type);
					if (currentInType >= typeLimit) {
						debugLog("EquipCosmeticPayload: type '" + type + "' limit reached for " + player.getName().getString() + " (" + currentInType + "/" + typeLimit + ").");
						player.sendMessage(LangConfig.chat("messages.cosmetic.type_limit", context.server().getRegistryManager(), "type", type, "limit", typeLimit), true);
						playCustomSound(player, "error_action");
						return;
					}
				}

				com.f4xizzz.greatcosmetics.database.DatabaseManager.equipCosmetic(player.getUuid(), cosmeticId, slotVirtual, type);
				debugLog("EquipCosmeticPayload: '" + cosmeticId + "' equipped successfully on " + player.getName().getString() + " (slot=" + slotVirtual + " type=" + type + " bypass=" + isBypassing + ").");
				player.sendMessage(LangConfig.chat("messages.cosmetic.equipped", context.server().getRegistryManager()), true);
				playCustomSound(player, "equip_item");

				com.f4xizzz.greatcosmetics.database.DatabaseManager.broadcastPlayerCosmetics(player);
			});
		});

		// ==========================================
		// SISTEMA DE TAGS: equipar/desequipar (toggle, igual EquipCosmeticPayload)
		// ==========================================
		ServerPlayNetworking.registerGlobalReceiver(EquipTagPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayerEntity player = context.player();
				if (licenseBlocked(player)) return;
				String tagId = payload.tagId();

				TagData data = TagsConfig.getById(tagId);
				if (data == null) return;

				// Posse real (sem bypass de dev): tag de grupo = pertencer ao grupo no LuckPerms;
				// tag normal = linha no banco.
				boolean realOwns = data.isGroupTag
						? com.f4xizzz.greatcosmetics.util.LuckPermsTagManager.isInGroup(player, data.id)
						: com.f4xizzz.greatcosmetics.database.DatabaseManager.hasTag(player.getUuid(), tagId);

				// A "chavinha de Dev" é só PREVIEW VISUAL do lado do cliente (TagsPage já trata
				// isso localmente sem chamar esse payload pra tags não possuídas) — o servidor
				// NUNCA persiste equip nem aplica prefix/permissions reais pra quem não possui
				// de verdade a tag, mesmo com devMode=true. Sem essa checagem, o antigo bypass
				// criava uma linha real em player_tags (equipTag() cai pro INSERT quando não
				// existe linha ainda), fazendo a tag "vazar" como desbloqueada pra sempre depois
				// do preview, mesmo o player nunca tendo tido ela de verdade.
				debugLog("EquipTagPayload from " + player.getName().getString() + ": tagId='" + tagId + "' devMode=" + payload.devMode() + " realOwns=" + realOwns);

				if (!realOwns) {
					if (!payload.devMode()) {
						player.sendMessage(LangConfig.chat("messages.tag.not_owned", context.server().getRegistryManager()), true);
						playCustomSound(player, "error_action");
					}
					return;
				}

				String currentlyEquipped = com.f4xizzz.greatcosmetics.database.DatabaseManager.getEquippedTagId(player.getUuid());

				if (tagId.equals(currentlyEquipped)) {
					// Nunca deixa o player sem NENHUMA tag — só permite trocar, nunca "zerar". Se
					// ele tem uma tag de grupo correspondente ao cargo atual (diferente da que tá
					// sendo removida), cai pra ela. Só fica sem tag mesmo se não pertencer a
					// nenhum grupo com tag correspondente (ex: só o grupo "default", que nem
					// entra no catálogo — ver MainConfig.tagGroupBlacklist).
					com.f4xizzz.greatcosmetics.util.LuckPermsTagManager.removeTag(player, data);
					TagData fallback = findBestGroupTag(player);

					if (fallback != null && !fallback.id.equals(tagId)) {
						com.f4xizzz.greatcosmetics.database.DatabaseManager.equipTag(player.getUuid(), fallback.id);
						com.f4xizzz.greatcosmetics.util.LuckPermsTagManager.applyTag(player, fallback);
						player.sendMessage(LangConfig.chat("messages.tag.removed_role_fallback", context.server().getRegistryManager()), true);
					} else if (fallback != null) {
						// A tag que ele tentou remover JÁ É a tag do cargo dele — não tem pra onde
						// cair, então mantém equipada (bloqueia o "desequipar" de verdade).
						com.f4xizzz.greatcosmetics.util.LuckPermsTagManager.applyTag(player, fallback);
						player.sendMessage(LangConfig.chat("messages.tag.cannot_remove_role", context.server().getRegistryManager()), true);
					} else {
						com.f4xizzz.greatcosmetics.database.DatabaseManager.unequipAllForPlayer(player.getUuid());
						player.sendMessage(LangConfig.chat("messages.tag.removed", context.server().getRegistryManager()), true);
					}
				} else {
					if (currentlyEquipped != null) {
						TagData previous = TagsConfig.getById(currentlyEquipped);
						if (previous != null) com.f4xizzz.greatcosmetics.util.LuckPermsTagManager.removeTag(player, previous);
					}

					com.f4xizzz.greatcosmetics.database.DatabaseManager.equipTag(player.getUuid(), tagId);
					com.f4xizzz.greatcosmetics.util.LuckPermsTagManager.applyTag(player, data);
					player.sendMessage(LangConfig.chat("messages.tag.equipped", context.server().getRegistryManager()), true);
				}

				playCustomSound(player, "equip_item");
				syncPlayerTags(player);

				// Sem isso, o nametag da aba Tags (ver SyncNameTagPayload) só ficava sabendo do
				// prefix/suffix novo na PRÓXIMA vez que o wardrobe abrisse — trocar de tag com o
				// wardrobe já aberto não atualizava o nome em cima da cabeça na hora.
				String[] prefixSuffix = com.f4xizzz.greatcosmetics.util.LuckPermsTagManager.getPrefixSuffix(player);
				ServerPlayNetworking.send(player, new com.f4xizzz.greatcosmetics.network.SyncNameTagPayload(prefixSuffix[0], prefixSuffix[1]));
			});
		});

		// ==========================================
		// SISTEMA DE TAGS: dev cria/edita uma tag (aba Dev do TagsPage)
		// ==========================================
		ServerPlayNetworking.registerGlobalReceiver(SaveTagPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayerEntity player = context.player();
				if (licenseBlocked(player)) return;
				if (!(isRealOperator(player) || checkPermission(player, "gc.dev"))) {
					player.sendMessage(LangConfig.chat("messages.tag.no_perm_edit", context.server().getRegistryManager()), true);
					return;
				}

				String id = payload.id() != null ? payload.id().trim().toLowerCase() : "";
				if (id.isEmpty()) {
					player.sendMessage(LangConfig.chat("messages.tag.id_empty", context.server().getRegistryManager()), true);
					return;
				}

				TagData existing = TagsConfig.getById(id);
				TagData data = existing != null ? existing : new TagData();

				// isGroupTag/weight nunca mudam pelo editor — só o dono (LuckPerms/import) define isso.
				data.id = id;
				data.displayName = payload.displayName();
				data.description = payload.description();
				data.tag = payload.tag();
				data.permissions = new java.util.ArrayList<>(payload.permissions());
				data.minecraftTag = payload.minecraftTag();

				TagsConfig.tagsMap.put(id, data);
				TagsConfig.save();
				broadcastTagsCatalog(context.server());
				debugLog("SaveTagPayload: tag '" + id + "' saved by " + player.getName().getString() + ".");

				player.sendMessage(LangConfig.chat("messages.tag.saved", context.server().getRegistryManager(), "id", id), true);
			});
		});

		// ==========================================
		// SISTEMA DE TAGS: dev apaga uma tag (tags de grupo nunca podem ser apagadas)
		// ==========================================
		ServerPlayNetworking.registerGlobalReceiver(DeleteTagPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayerEntity player = context.player();
				if (licenseBlocked(player)) return;
				if (!(isRealOperator(player) || checkPermission(player, "gc.dev"))) {
					player.sendMessage(LangConfig.chat("messages.tag.no_perm_delete", context.server().getRegistryManager()), true);
					return;
				}

				TagData data = TagsConfig.getById(payload.id());
				if (data == null) return;

				if (data.isGroupTag) {
					player.sendMessage(LangConfig.chat("messages.tag.group_cannot_delete", context.server().getRegistryManager()), true);
					return;
				}

				TagsConfig.tagsMap.remove(data.id);
				TagsConfig.save();
				com.f4xizzz.greatcosmetics.database.DatabaseManager.removeAllOwnershipOfTag(data.id);
				broadcastTagsCatalog(context.server());
				debugLog("DeleteTagPayload: tag '" + data.id + "' deleted by " + player.getName().getString() + ".");

				player.sendMessage(LangConfig.chat("messages.tag.deleted", context.server().getRegistryManager(), "id", data.id), true);
			});
		});

		// ==========================================
		// SISTEMA DE EFEITOS: dev cria/edita um efeito de partícula (aba Dev Studio > Effects)
		// ==========================================
		ServerPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.SaveEffectPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayerEntity player = context.player();
				if (licenseBlocked(player)) return;
				if (!(isRealOperator(player) || checkPermission(player, "gc.dev"))) {
					player.sendMessage(LangConfig.chat("messages.effect.no_perm_edit", context.server().getRegistryManager()), true);
					return;
				}

				String newId = payload.newId() != null ? payload.newId().trim() : "";
				if (newId.isEmpty()) {
					player.sendMessage(LangConfig.chat("messages.effect.id_empty", context.server().getRegistryManager()), true);
					return;
				}

				EffectData data = new com.google.gson.Gson().fromJson(payload.jsonData(), EffectData.class);
				if (data == null) return;

				String oldId = payload.oldId() != null ? payload.oldId().trim() : "";
				if (!oldId.isEmpty() && !oldId.equals(newId)) {
					EffectConfig.effectsMap.remove(oldId);
				}

				EffectConfig.effectsMap.put(newId, data);
				EffectConfig.saveEffects();
				broadcastEffectsCatalog(context.server());
				debugLog("SaveEffectPayload: effect '" + oldId + "' -> '" + newId + "' saved by " + player.getName().getString() + ".");

				player.sendMessage(LangConfig.chat("messages.effect.saved", context.server().getRegistryManager(), "id", newId), true);
			});
		});

		// ==========================================
		// SISTEMA DE EFEITOS: dev apaga um efeito de partícula
		// ==========================================
		ServerPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.DeleteEffectPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayerEntity player = context.player();
				if (licenseBlocked(player)) return;
				if (!(isRealOperator(player) || checkPermission(player, "gc.dev"))) {
					player.sendMessage(LangConfig.chat("messages.effect.no_perm_delete", context.server().getRegistryManager()), true);
					return;
				}

				if (EffectConfig.effectsMap.remove(payload.id()) == null) return;
				EffectConfig.saveEffects();
				broadcastEffectsCatalog(context.server());
				debugLog("DeleteEffectPayload: effect '" + payload.id() + "' deleted by " + player.getName().getString() + ".");

				player.sendMessage(LangConfig.chat("messages.effect.deleted", context.server().getRegistryManager(), "id", payload.id()), true);
			});
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			BackpackManager.checkClosedBackpacks(server);

			// Revalida a licença a cada 4h + checa expiração (ver security.ActivationManager).
			com.f4xizzz.greatcosmetics.security.ActivationManager.tickPeriodicCheck(server);

			if (pendingStartupCommandsTick != null && server.getTicks() >= pendingStartupCommandsTick) {
				pendingStartupCommandsTick = null;
				for (String cmd : MainConfig.config.startupCommands) {
					if (cmd == null || cmd.isBlank()) continue;
					String clean = cmd.startsWith("/") ? cmd.substring(1) : cmd;
					server.getCommandManager().executeWithPrefix(server.getCommandSource(), clean);
				}
			}

			if (!pendingJoinTagSync.isEmpty()) {
				java.util.Iterator<Map.Entry<UUID, Integer>> it = pendingJoinTagSync.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry<UUID, Integer> entry = it.next();
					if (server.getTicks() < entry.getValue()) continue;
					it.remove();

					ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
					if (player == null) continue; // já desconectou antes da segunda passada

					validateEquippedGroupTag(player);
					autoEquipCurrentGroupTag(player);
					syncPlayerTags(player);

					String[] prefixSuffix = com.f4xizzz.greatcosmetics.util.LuckPermsTagManager.getPrefixSuffix(player);
					ServerPlayNetworking.send(player, new com.f4xizzz.greatcosmetics.network.SyncNameTagPayload(prefixSuffix[0], prefixSuffix[1]));
				}
			}

			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				boolean isMajorTick = (server.getTicks() % 20 == 0);
				boolean isSoundTick = (server.getTicks() % 8 == 0);
				boolean isArmorCheckTick = (server.getTicks() % 100 == 0);
				boolean shouldFly = false;

				// ARMADURA VIRADA COSMÉTICO: varre o inventário a cada 5s (em vez de só no pickup/
				// join) pra pegar qualquer forma do jogador ter conseguido o item (drop, troca com
				// outro player, plugin externo, etc).
				if (isArmorCheckTick) {
					checkAndConvertArmorInventory(player);
					validateEquippedCosmeticOwnership(player);
					// LegacyCosmeticMigrator.migratePlayer() só rodava no JOIN (ver o comentário da
					// própria classe) — um player que ganhava a abóbora antiga DEPOIS de logar (troca,
					// drop, /give, plugin externo) nunca era migrado até relogar. Mesmo tick de 5s do
					// checkAndConvertArmorInventory, mesmo motivo: idempotente (o item velho já não
					// existe mais depois da primeira vez), então rodar de novo pra quem já foi migrado
					// não tem custo real.
					com.f4xizzz.greatcosmetics.util.LegacyCosmeticMigrator.migratePlayer(player);
				}

				// TAGS DE GRUPO: rede de segurança — o listener de UserDataRecalculateEvent
				// (LuckPermsTagManager.registerRankChangeListener) já deveria resincronizar na
				// hora quando um grupo é adicionado/removido via /lp, mas sem like confirmar ao
				// vivo se esse evento sempre dispara pra TODA operação do LuckPerms (ex: /lp user
				// X parent remove Y direto no console), essa revalidação periódica garante que
				// uma tag de grupo perdida some do client em no máximo 5s mesmo que o evento
				// falhe silenciosamente por qualquer motivo.
				if (isArmorCheckTick) {
					validateEquippedGroupTag(player);
					autoEquipCurrentGroupTag(player);
					syncPlayerTags(player);
				}

				net.minecraft.util.math.Vec3d currentPos = player.getPos();
				net.minecraft.util.math.Vec3d lastPos = lastPositions.getOrDefault(player.getUuid(), currentPos);
				boolean isMoving = currentPos.squaredDistanceTo(lastPos) > 0.0001;
				lastPositions.put(player.getUuid(), currentPos);

				boolean isSneaking = player.isSneaking();
				boolean justSneaked = isSneaking && !wasSneaking.contains(player.getUuid());
				if (isSneaking) wasSneaking.add(player.getUuid());
				else wasSneaking.remove(player.getUuid());

				boolean isFlying = player.getAbilities().flying;

				double flySpeedMult = 1.0, groundSpeedMult = 1.0, swimSpeedMult = 1.0;
				Map<Identifier, Integer> desiredStatusEffects = isMajorTick ? new HashMap<>() : null;

				java.util.List<String> equippedIds = com.f4xizzz.greatcosmetics.database.DatabaseManager.getPlayerEquippedCosmetics(player.getUuid());
				// Cosmético "escondido" (botão de olho na Wardrobe — ver ToggleCosmeticVisibilityPayload)
				// só parava de DESENHAR o item (ArmorFeatureRendererMixin), mas as partículas dele
				// (effectVisual/flyParticle) continuavam spawnando igual — o cosmético sumia mas o
				// rastro de partícula ficava, entregando visualmente que ele ainda tava equipado.
				java.util.Set<String> hiddenIds = com.f4xizzz.greatcosmetics.database.DatabaseManager.getHiddenCosmeticIds(player.getUuid());
				for (String id : equippedIds) {
					CosmeticData data = getCosmeticById(id);
					if (data != null) {
						if (data.permission != null && !data.permission.isEmpty() && !checkPermission(player, data.permission)) continue;

						if (data.sounds != null) {
							if (isSoundTick) {
								if (isFlying && data.sounds.flySound != null && !data.sounds.flySound.isEmpty()) {
									playCosmeticSound(player, data.sounds.flySound, (float) data.sounds.flyVolume, (float) data.sounds.flyPitch);
								} else if (isMoving && !isFlying && data.sounds.walkSound != null && !data.sounds.walkSound.isEmpty()) {
									playCosmeticSound(player, data.sounds.walkSound, (float) data.sounds.walkVolume, (float) data.sounds.walkPitch);
								}
							}
							if (justSneaked && data.sounds.shiftSound != null && !data.sounds.shiftSound.isEmpty()) {
								playCosmeticSound(player, data.sounds.shiftSound, (float) data.sounds.shiftVolume, (float) data.sounds.shiftPitch);
							}
						}

						if (!hiddenIds.contains(id)) {
							for (String effectId : data.effectVisual) {
								EffectData effect = EffectConfig.effectsMap.get(effectId);
								if (effect != null && server.getTicks() % effect.tickInterval == 0) spawnCosmeticParticle(player, effect);
							}

							if (isFlying) {
								for (String flyId : data.flyParticle) {
									EffectData effect = EffectConfig.effectsMap.get(flyId);
									if (effect != null && server.getTicks() % effect.tickInterval == 0) spawnCosmeticParticle(player, effect);
								}
							}
						}

						if (isMajorTick) {
							if (data.EnableFly) shouldFly = true;
							collectPotionEffects(data, desiredStatusEffects);
							// Math.max (não mais *=) — dois cosméticos de +50% de velocidade
							// multiplicando entre si virava +125% (1.5*1.5), empilhando bônus que
							// cada um deveria valer sozinho. Igual collectPotionEffects já faz pros
							// efeitos de status: entre todos os equipados, só o MAIOR multiplicador
							// vale, não a soma/produto de todos.
							flySpeedMult = Math.max(flySpeedMult, data.flySpeedMultiplier);
							groundSpeedMult = Math.max(groundSpeedMult, data.groundSpeedMultiplier);
							swimSpeedMult = Math.max(swimSpeedMult, data.swimSpeedMultiplier);
						}
					}
				}
				if (isMajorTick) {
					handleFlyLogic(player, shouldFly);
					handleSpeedLogic(player, flySpeedMult, groundSpeedMult, swimSpeedMult);
					syncCosmeticStatusEffects(player, desiredStatusEffects);
				}
			}
		});

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (player.isSpectator()) return ActionResult.PASS;
			ItemStack stack = player.getStackInHand(hand);
			if (stack.isOf(Items.CARVED_PUMPKIN) && stack.contains(DataComponentTypes.CUSTOM_MODEL_DATA)) {
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});
	}

	private void spawnCosmeticParticle(ServerPlayerEntity player, EffectData effect) {
		ServerWorld world = (ServerWorld) player.getWorld();
		Identifier partId = Identifier.tryParse(effect.particleId);
		if (partId != null) {
			ParticleType<?> type = Registries.PARTICLE_TYPE.get(partId);
			if (type instanceof ParticleEffect particleEffect) {
				// BUG: isso usava spreadX/spreadZ (o ESPALHAMENTO aleatório) pra calcular a posição
				// rotacionada, e nunca lia offsetX/offsetZ (o OFFSET de posição de verdade, ajustado
				// pelo gizmo 3D no Dev Studio) — por isso a partícula nunca ficava onde configurado.
				// offsetX/offsetZ são "Lados"/"Frente-Trás" (ver labels em DevEffectsSubPage), ou
				// seja, relativos ao corpo do jogador — por isso rotacionam com bodyYaw. spreadX/Y/Z
				// agora vão pro lugar de verdade: os parâmetros deltaX/deltaY/deltaZ de
				// spawnParticles(), que é o espalhamento aleatório por partícula (igual o preview
				// client-side já fazia em DevEffectsSubPage#spawnPreviewParticles).
				// Convenção de yaw do Minecraft (yaw=0 olhando pro +Z/sul): forward = (-sin, cos),
				// right = forward × up = (-cos, -sin). offsetZ anda ao longo de "forward" (frente/
				// trás), offsetX ao longo de "right" (lados) — a versão anterior somava offsetX
				// com o sinal invertido (tratava como se fosse um "left" em vez de "right"), então
				// o lado pra onde a partícula desviava ficava espelhado conforme o jogador girava.
				double yawRad = Math.toRadians(player.bodyYaw);
				double finalX = player.getX() - (effect.offsetX * Math.cos(yawRad)) - (effect.offsetZ * Math.sin(yawRad));
				double finalZ = player.getZ() - (effect.offsetX * Math.sin(yawRad)) + (effect.offsetZ * Math.cos(yawRad));
				world.spawnParticles(particleEffect, finalX, player.getY() + effect.offsetY, finalZ, effect.count, effect.spreadX, effect.spreadY, effect.spreadZ, effect.speed);
			}
		}
	}

	public static boolean removeAllSkinAspects(com.cobblemon.mod.common.pokemon.Pokemon pokemon) {
		boolean changed = false;
		String currentSpecies = pokemon.getSpecies().getName().toLowerCase();
		java.util.Set<String> forcedAspects = new java.util.HashSet<>(pokemon.getForcedAspects());

		for (com.f4xizzz.greatcosmetics.config.PokemonSkin skin : com.f4xizzz.greatcosmetics.config.SkinConfigManager.getAllSkins()) {
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

	/** Junta (sem aplicar ainda) os efeitos de status que este cosmético concede, dentro do mapa
	 *  agregado de TODOS os cosméticos equipados nesse tick — quando duas peças dão o mesmo efeito,
	 *  fica o nível mais forte. Ver syncCosmeticStatusEffects() pra aplicação de verdade. */
	private void collectPotionEffects(CosmeticData data, Map<Identifier, Integer> desired) {
		if (data.effects == null || data.effects.isEmpty()) return;
		for (String effectString : data.effects) {
			String[] parts = effectString.split(":");
			if (parts.length < 2) continue;
			String effectName = parts[0] + ":" + parts[1];
			int level = (parts.length > 2) ? Integer.parseInt(parts[2]) - 1 : 0;
			Identifier id = Identifier.tryParse(effectName);
			if (id != null) {
				desired.merge(id, Math.max(0, level), Math::max);
			}
		}
	}

	// Duração bem grande (~1 dia) só pra nunca cair na faixa em que o ícone do HUD começa a
	// "piscar avisando que tá acabando" — na prática o efeito é reaplicado todo isMajorTick (1s)
	// enquanto o cosmético estiver equipado, então nunca chega a esgotar de verdade.
	private static final int PERMANENT_EFFECT_DURATION_TICKS = 20 * 60 * 60 * 24;

	/** Aplica de verdade os efeitos de status agregados de todos os cosméticos equipados AGORA, e
	 *  remove qualquer efeito que o player tinha por causa de um cosmético que não está mais na
	 *  lista (ex: acabou de desequipar) — antes disso, os efeitos eram aplicados com duração curta
	 *  (60 ticks) reaplicada a cada segundo, o que nunca deixava a duração passar de poucos
	 *  segundos e mantinha o ícone do HUD sempre no estado "piscando/desaparecendo", e nada
	 *  removia o efeito explicitamente ao desequipar (só a curta duração ia embora sozinha, com
	 *  atraso). */
	private void syncCosmeticStatusEffects(ServerPlayerEntity player, Map<Identifier, Integer> desired) {
		java.util.Set<Identifier> previous = activeCosmeticStatusEffects.computeIfAbsent(player.getUuid(), k -> new java.util.HashSet<>());

		for (Identifier id : new java.util.ArrayList<>(previous)) {
			if (!desired.containsKey(id)) {
				Registries.STATUS_EFFECT.getEntry(id).ifPresent(player::removeStatusEffect);
				previous.remove(id);
			}
		}

		for (Map.Entry<Identifier, Integer> entry : desired.entrySet()) {
			var effectEntry = Registries.STATUS_EFFECT.getEntry(entry.getKey());
			if (effectEntry.isPresent()) {
				player.addStatusEffect(new StatusEffectInstance(effectEntry.get(), PERMANENT_EFFECT_DURATION_TICKS, entry.getValue(), true, false, true));
				previous.add(entry.getKey());
			}
		}
	}

	private void handleFlyLogic(ServerPlayerEntity player, boolean shouldFly) {
		boolean isModFlyActive = activeFlyPlayers.contains(player.getUuid());
		RegistryWrapper.WrapperLookup regs = player.getWorld().getRegistryManager();

		if (shouldFly) {
			if (!player.getAbilities().allowFlying) {
				player.getAbilities().allowFlying = true;
				player.sendAbilitiesUpdate();
				sendOpMessage(player, LangConfig.chat("messages.fly.enabled", regs), true);
			}
			activeFlyPlayers.add(player.getUuid());
		} else if (isModFlyActive) {
			activeFlyPlayers.remove(player.getUuid());
			if (!player.isCreative() && !player.isSpectator()) {
				player.getAbilities().allowFlying = false;
				player.getAbilities().flying = false;
				player.sendAbilitiesUpdate();
				sendOpMessage(player, LangConfig.chat("messages.fly.disabled", regs), true);
			}
		}
	}

	private static final net.minecraft.util.Identifier SWIM_SPEED_MODIFIER_ID =
			net.minecraft.util.Identifier.of("greatcosmetics", "swim_speed_boost");
	private static final net.minecraft.util.Identifier GROUND_SPEED_MODIFIER_ID =
			net.minecraft.util.Identifier.of("greatcosmetics", "ground_speed_boost");

	/** Aplica os multiplicadores de velocidade (fly/ground/swim) combinados de todos os
	 *  cosméticos equipados. Fly usa o campo nativo PlayerAbilities#flySpeed (mesmo mecanismo do
	 *  /gamerule ou do creative fly — esse campo é de fato lido pelo movimento em modo voo). Ground
	 *  e swim NÃO têm campo nativo equivalente pra movimento normal — PlayerAbilities#walkSpeed é
	 *  um campo vestigial que o jogo nunca lê pra velocidade de andar/correr de verdade (setWalkSpeed
	 *  aqui só mandava um PlayerAbilitiesS2CPacket que não fazia NADA pra velocidade real, e ainda
	 *  interferia no cálculo de FOV do client — por isso o multiplicador "não aumentava a
	 *  velocidade" e ainda "diminuía o FOV"). O jeito certo é um modificador no atributo de
	 *  movimento de verdade (GENERIC_MOVEMENT_SPEED), igual já era feito pro swim — só que ground
	 *  fica ativo o tempo todo (não só dentro d'água).
	 *
	 *  BUG (2026-09): flyMult == 1.0 (sem cosmético de fly speed equipado, o caso comum) chamava
	 *  setFlySpeed(0.05) + sendAbilitiesUpdate() do MESMO JEITO, todo segundo, pra TODO jogador —
	 *  não só quem tem cosmético. Isso forçava o flySpeed de volta pro padrão vanilla (0.05) a cada
	 *  isMajorTick, brigando com QUALQUER outra coisa que tivesse ajustado essa velocidade por fora
	 *  (outro plugin/comando, edição de NBT, etc.) — o jogador via a própria velocidade "travar no
	 *  normal" mesmo sem nenhum cosmético do GreatCosmetics equipado. Agora só mexemos em
	 *  abilities.flySpeed quando: (a) tem boost ativo (flyMult != 1.0), ou (b) acabou de perder um
	 *  boost que A GENTE tinha aplicado (activeFlySpeedBoostPlayers) — nesse caso devolve pro
	 *  padrão UMA vez só e para de tocar no campo. Sem cosmético e sem boost anterior nosso = o
	 *  campo fica intocado, livre pra qualquer outro sistema controlar. */
	private void handleSpeedLogic(ServerPlayerEntity player, double flyMult, double groundMult, double swimMult) {
		boolean hasFlySpeedBoost = flyMult != 1.0;
		boolean hadFlySpeedBoost = activeFlySpeedBoostPlayers.contains(player.getUuid());
		if (hasFlySpeedBoost) {
			player.getAbilities().setFlySpeed((float) (0.05 * flyMult));
			player.sendAbilitiesUpdate();
			activeFlySpeedBoostPlayers.add(player.getUuid());
		} else if (hadFlySpeedBoost) {
			player.getAbilities().setFlySpeed(0.05F);
			player.sendAbilitiesUpdate();
			activeFlySpeedBoostPlayers.remove(player.getUuid());
		}

		net.minecraft.entity.attribute.EntityAttributeInstance speedAttr =
				player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speedAttr != null) {
			speedAttr.removeModifier(GROUND_SPEED_MODIFIER_ID);
			if (groundMult != 1.0) {
				speedAttr.addTemporaryModifier(new net.minecraft.entity.attribute.EntityAttributeModifier(
						GROUND_SPEED_MODIFIER_ID, groundMult - 1.0,
						net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}

			speedAttr.removeModifier(SWIM_SPEED_MODIFIER_ID);
			if (swimMult != 1.0 && player.isTouchingWater()) {
				speedAttr.addTemporaryModifier(new net.minecraft.entity.attribute.EntityAttributeModifier(
						SWIM_SPEED_MODIFIER_ID, swimMult - 1.0,
						net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
	}

	/**
	 * IMPORTANTE: usa a sobrecarga de Permissions.check(entity, permission, boolean) — a que
	 * recebe um fallback BOOLEANO fixo — em vez da versão com int (nível de OP). A versão com
	 * int cai pra entity.hasPermissionLevel(nivel) quando nenhum provider responde de forma
	 * definitiva pro node, e em servidores com o mod "Vanilla Permissions" instalado isso
	 * resolvia hasPermissionLevel(2) como true pra QUALQUER jogador (mesmo sem OP e sem
	 * permissão nenhuma) pra nodes que ele não reconhece, tipo os nossos "gc.*" — vazando dev
	 * mode/GUIs de admin pra todo mundo. A versão booleana nunca toca em hasPermissionLevel().
	 */
	public static boolean checkPermission(ServerPlayerEntity player, String permission) {
		if (permission == null || permission.isEmpty()) return false;
		try {
			Class<?> permsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
			java.lang.reflect.Method checkMethod = permsClass.getMethod("check", net.minecraft.entity.Entity.class, String.class, boolean.class);
			return (boolean) checkMethod.invoke(null, player, permission, false);
		} catch (Exception e) {
			return false;
		}
	}

	public static CosmeticData getCosmeticData(int cmdToFind) {
		for (CosmeticData data : CosmeticsConfig.cosmeticsMap.values()) {
			if (data.cmd == cmdToFind) {
				return data;
			}
		}
		return null;
	}

	// Índice case-insensitive de CosmeticsConfig.cosmeticsMap, reconstruído só quando a REFERÊNCIA
	// do map muda de verdade (reload/save/sync sempre trocam a referência inteira, nunca mutam o
	// map existente — ver CosmeticsConfig#loadConfig e o receiver de SyncCosmeticsPayload). Sem
	// isso, getCosmeticById fazia uma varredura linear com equalsIgnoreCase em TODOS os cosméticos
	// pra cada chamada — e é chamado uma vez POR COSMÉTICO EQUIPADO, POR JOGADOR, TODO TICK (além
	// de toda vez que LivingEntity#getArmor() roda) — com muitos jogadores e cosméticos configurados
	// isso vira uma varredura O(cosméticos) desnecessária centenas de vezes por segundo.
	private static Map<String, CosmeticData> cosmeticsByIdLower = java.util.Collections.emptyMap();
	private static Map<String, CosmeticData> cosmeticsByIdLowerSource = null;

	/** CosmeticsConfig.cosmeticsMap.put()/remove() diretos (edição individual pelo Dev Studio, sem
	 *  passar por um reload completo) MUTAM o map existente em vez de trocar a referência — a
	 *  detecção "trocou a referência" sozinha em getCosmeticById() nunca pegaria isso, deixando o
	 *  índice desatualizado até o próximo /gc reload. Chamar isso logo depois de qualquer put()/
	 *  remove() direto no cosmeticsMap força o rebuild na próxima chamada. */
	public static void invalidateCosmeticIndex() {
		cosmeticsByIdLowerSource = null;
	}

	public static CosmeticData getCosmeticById(String idProcurado) {
		if (idProcurado == null) return null;
		Map<String, CosmeticData> currentMap = CosmeticsConfig.cosmeticsMap;
		if (currentMap != cosmeticsByIdLowerSource) {
			Map<String, CosmeticData> rebuilt = new HashMap<>();
			// Indexa pela CHAVE DE VERDADE do mapa (entry.getKey()), não por data.id — data.id é só
			// um campo transient que o loadConfig()/save reatribui pra bater com a chave, mas se ELE
			// alguma vez ficar fora de sincronia com a chave real (ex: um bug futuro em algum fluxo
			// de salvar/duplicar cosmético que reaproveite a mesma instância de CosmeticData pra dois
			// ids diferentes), indexar por data.id faz esse índice silenciosamente MESCLAR duas
			// mochilas/cosméticos DIFERENTES num só — getCosmeticById(idA) e getCosmeticById(idB)
			// passam a devolver o MESMO objeto, cada edição/leitura de um "vaza" pro outro. Indexar
			// pela chave real do map é imune a esse tipo de bug em qualquer outro lugar do código.
			for (Map.Entry<String, CosmeticData> entry : currentMap.entrySet()) {
				if (entry.getKey() != null) rebuilt.put(entry.getKey().toLowerCase(), entry.getValue());
			}
			cosmeticsByIdLower = rebuilt;
			cosmeticsByIdLowerSource = currentMap;
		}
		CosmeticData found = cosmeticsByIdLower.get(idProcurado.toLowerCase());
		if (found != null) return found;
		// Armadura convertida em cosmético (ver ArmorCosmeticsConfig) — nunca fica no
		// cosmeticsMap de verdade, é sintetizada sob demanda a partir do armor_cosmetics.json.
		// Esse método é chamado tanto de código SERVER-side (comandos, LivingEntityMixin) quanto
		// CLIENT-side (ArmorFeatureRendererMixin, EquippedSlotsWidget) — só UM dos dois mapas
		// abaixo está de fato populado em cada lado (ArmorCosmeticsConfig.load() só roda no
		// servidor; ClientArmorCosmeticsCache só é preenchido via SyncArmorCosmeticsPayload no
		// client). Sem tentar os dois aqui, TODO código client-side que resolvia uma armadura-
		// cosmético por id (renderização no corpo, EquippedSlotsWidget) sempre recebia null numa
		// conexão remota de verdade — a armadura "equipava" no banco mas nunca aparecia em lugar
		// nenhum no client.
		//
		// ORDEM IMPORTA no client: em singleplayer/hospedando, o servidor integrado E o client
		// rodam na mesma JVM, então ArmorCosmeticsConfig (servidor) E ClientArmorCosmeticsCache
		// (client) ficam OS DOIS populados ao mesmo tempo — como objetos DIFERENTES (o client
		// sempre recebe o catálogo por rede, mesmo hospedando local, nunca lê o do servidor
		// direto). Tentar o do servidor primeiro fazia esse método devolver, do lado do client,
		// um CosmeticData/CosmeticPart que NUNCA era o mesmo objeto que o Dev Studio estava
		// editando — o Gizmo 3D (GizmoManager, que compara "é essa a part ativa?" por referência
		// de objeto) nunca reconhecia a part como ativa e nunca desenhava/ficava clicável, mesmo
		// com tudo certo na tela de edição. No client, sempre prioriza o cache client-side.
		// FabricLoader.getEnvironmentType(), NUNCA MinecraftClient.getInstance() aqui — esse método
		// roda no tick do SERVIDOR também (equipar cosmético, etc), e net.minecraft.client.
		// MinecraftClient nem existe no classpath de um servidor dedicado de verdade. Só a
		// REFERÊNCIA à classe (mesmo dentro de um "!= null") já derruba o server tick loop inteiro
		// com NoClassDefFoundError — aconteceu de verdade ao tentar equipar um cosmético.
		// FabricLoader/EnvType são API comum, sempre presentes nos dois lados, seguros de chamar
		// daqui sem carregar nada client-only.
		if (net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT) {
			CosmeticData clientArmorData = com.f4xizzz.greatcosmetics.client.ClientArmorCosmeticsCache.getSyntheticCosmetic(idProcurado);
			if (clientArmorData != null) return clientArmorData;
		}
		CosmeticData serverArmorData = com.f4xizzz.greatcosmetics.config.ArmorCosmeticsConfig.getSyntheticCosmetic(idProcurado);
		if (serverArmorData != null) return serverArmorData;
		return com.f4xizzz.greatcosmetics.client.ClientArmorCosmeticsCache.getSyntheticCosmetic(idProcurado);
	}

	/** Baixa o arquivo de MainConfig#textureUrl e calcula o SHA1 real do conteúdo, atualizando
	 *  MainConfig#textureSha1 (e salvando) se o hash mudou. Bloqueia a thread que chamar — em
	 *  /gc reload isso é aceitável (ação manual do admin). Retorna true só se o hash mudou.
	 *
	 *  O motivo de existir: sem um sha1 correto e ESTÁVEL entre sessões, o client nunca consegue
	 *  reconhecer "já tenho esse resource pack exato" (o cache nativo dele em
	 *  .minecraft/server-resource-packs/<sha1> é indexado pelo hash) — então o pacote forçado deste
	 *  mod parecia uma segunda "tela de loading" toda vez que o player entrava, mesmo sem nenhum
	 *  cosmético novo. Calculando o hash de verdade a partir do arquivo (em vez de depender do admin
	 *  digitar certo no Dev Studio), o client passa a reaproveitar o pack já baixado em sessões
	 *  anteriores e só baixa de novo quando o CONTEÚDO do arquivo em textureUrl realmente mudar. */
	private static boolean refreshTextureHashBlocking() throws Exception {
		String url = MainConfig.config.textureUrl;
		java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
				.followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
				.connectTimeout(java.time.Duration.ofSeconds(15))
				.build();
		java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
				.timeout(java.time.Duration.ofSeconds(30))
				.GET()
				.build();
		java.net.http.HttpResponse<byte[]> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
		if (response.statusCode() != 200) {
			throw new java.io.IOException("HTTP " + response.statusCode() + " ao baixar " + url);
		}

		java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
		byte[] hashBytes = digest.digest(response.body());
		StringBuilder sb = new StringBuilder(hashBytes.length * 2);
		for (byte b : hashBytes) sb.append(String.format("%02x", b));
		String newHash = sb.toString();

		String oldHash = MainConfig.config.textureSha1 != null ? MainConfig.config.textureSha1 : "";
		if (newHash.equalsIgnoreCase(oldHash)) return false;

		MainConfig.config.textureSha1 = newHash;
		MainConfig.saveConfig();
		debugLog("Texture hash (forced resource pack) automatically updated to " + newHash + ".");
		return true;
	}

	/** Versão em background do refresh acima — usada no boot do servidor pra não travar a thread
	 *  principal esperando o download (SERVER_STARTED roda ANTES do loop de tick começar; travar
	 *  ali de verdade atrasa o servidor inteiro ficar pronto se a URL estiver lenta/fora do ar).
	 *  Se o hash mudou (ex: primeiro boot depois de configurar a URL), reenvia o pack pra quem já
	 *  estiver online — normal não ter ninguém ainda nesse ponto, mas é seguro de qualquer forma. */
	public static void refreshTextureHashAsync(net.minecraft.server.MinecraftServer server) {
		if (!MainConfig.config.forceTexture) return;
		if (MainConfig.config.textureUrl == null || MainConfig.config.textureUrl.isBlank()) return;

		new Thread(() -> {
			try {
				boolean changed = refreshTextureHashBlocking();
				if (changed) {
					server.execute(() -> {
						for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
							sendForcedResourcePack(p);
						}
					});
				}
			} catch (Exception e) {
				System.err.println("[GreatCosmetics] Failed to compute SHA1 of the forced resource pack ("
						+ MainConfig.config.textureUrl + "): " + e.getMessage());
			}
		}, "GreatCosmetics-TextureHash").start();
	}

	/** Versão síncrona pro /gc reload — CosmeticsCommand#executeReload chama isso ANTES de reenviar
	 *  o pack pros players já online, pra garantir que o resend usa o hash já atualizado (evita
	 *  mandar o pacote duas vezes: uma com hash velho, outra logo depois com o novo). Retorna uma
	 *  mensagem de erro pro feedback do comando, ou null se deu certo (ou nem tentou por estar
	 *  desabilitado/sem URL). */
	public static String refreshTextureHashForReload() {
		if (!MainConfig.config.forceTexture) return null;
		if (MainConfig.config.textureUrl == null || MainConfig.config.textureUrl.isBlank()) return null;

		try {
			refreshTextureHashBlocking();
			return null;
		} catch (Exception e) {
			return e.getMessage();
		}
	}

	/** Manda o resource pack configurado em MainConfig#forceTexture direto pro client via pacote —
	 *  alternativa ao resource-pack/resource-pack-sha1 do server.properties, que exige reiniciar o
	 *  servidor pra qualquer troca de URL/hash pegar. Chamado no join E em /gc reload (assim uma
	 *  textura nova só precisa de upload + /gc reload, sem restart nenhum). textureId não precisa
	 *  ser um UUID de verdade — qualquer texto vira um UUID estável via nameUUIDFromBytes. */
	public static void sendForcedResourcePack(ServerPlayerEntity player) {
		if (!MainConfig.config.forceTexture) return;
		if (MainConfig.config.textureUrl == null || MainConfig.config.textureUrl.isBlank()) return;

		UUID packId = resolveForcedTexturePackId();
		String sha1 = MainConfig.config.textureSha1 != null ? MainConfig.config.textureSha1 : "";

		// required=true: o "forçar" do nome do botão — o client mostra um prompt sem opção de
		// recusar sem se desconectar (igual o resource-pack do server.properties quando obrigatório).
		player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket(
				packId, MainConfig.config.textureUrl, sha1, true, java.util.Optional.empty()
		));
	}

	/** textureId não precisa ser um UUID de verdade — qualquer texto vira um UUID estável via
	 *  nameUUIDFromBytes. Extraído de sendForcedResourcePack() pra sendCatalogStateSnapshot()
	 *  mandar o MESMO id (byte a byte) — o client precisa reconhecer, quando o pacote de resource
	 *  pack de verdade chegar, que é ESSE pack específico (ver ServerResourcePackLoaderMixin). */
	private static UUID resolveForcedTexturePackId() {
		try {
			return UUID.fromString(MainConfig.config.textureId);
		} catch (Exception e) {
			return UUID.nameUUIDFromBytes(MainConfig.config.textureId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
	}

	/** SHA-256 (hex) estável do catálogo inteiro de cosméticos+armaduras convertidas — usado pelo
	 *  client (ver SyncCatalogStatePayload) pra decidir sozinho se o que já tem em cache pra ESSE
	 *  servidor ainda bate com o catálogo atual, sem precisar de nenhum flag manual (ver Fase 3 do
	 *  plano). TreeMap (não o HashMap/ConcurrentHashMap "cru" dos dois catálogos) é obrigatório
	 *  aqui — sem isso, um restart do servidor com ZERO mudança real de config podia inverter a
	 *  ordem de iteração e mudar o hash sozinho, forçando um recarregamento desnecessário em todo
	 *  mundo que reentrasse. Calculado sob demanda (não cacheado) — só roda no join e no
	 *  /gc reload, nunca por tick, então o custo de recalcular não compensa o risco de esquecer um
	 *  ponto de invalidação e deixar o hash "mentindo" que nada mudou. */
	public static String computeCosmeticsCatalogHash() {
		try {
			java.util.TreeMap<String, CosmeticData> sortedCosmetics = new java.util.TreeMap<>(CosmeticsConfig.cosmeticsMap);
			java.util.TreeMap<String, CosmeticData> sortedArmor = new java.util.TreeMap<>(com.f4xizzz.greatcosmetics.config.ArmorCosmeticsConfig.armorCosmetics);
			com.google.gson.Gson gson = new com.google.gson.Gson();
			String combined = "cosmetics:" + gson.toJson(sortedCosmetics) + "|armor:" + gson.toJson(sortedArmor);

			java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(combined.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(digest.length * 2);
			for (byte b : digest) sb.append(String.format("%02x", b));
			return sb.toString();
		} catch (Exception e) {
			// Nunca deveria acontecer (SHA-256 sempre existe) — mas se acontecer, um hash vazio só
			// faz o client sempre tratar como "mudou", nunca o contrário (fail-safe: no pior caso
			// recarrega à toa, nunca deixa de recarregar quando devia).
			return "";
		}
	}

	/** Mandado ANTES de sendForcedResourcePack()/dos syncs de cosmético (join) ou antes deles no
	 *  loop de /gc reload — ver SyncCatalogStatePayload pro porquê da ordem importar.
	 *
	 *  {@code joinSync} = true só no handler de JOIN (entrada no servidor); false no /gc reload —
	 *  é o que o client usa pra saber, sem heurística de tempo, se pode suprimir a tela / pular o
	 *  recarregamento (só quando true). Ver ClientJoinReloadState. */
	public static void sendCatalogStateSnapshot(ServerPlayerEntity player, boolean joinSync) {
		String textureSha1 = MainConfig.config.textureSha1 != null ? MainConfig.config.textureSha1 : "";
		UUID texturePackId = MainConfig.config.forceTexture ? resolveForcedTexturePackId() : new UUID(0L, 0L);
		ServerPlayNetworking.send(player, new com.f4xizzz.greatcosmetics.network.SyncCatalogStatePayload(
				computeCosmeticsCatalogHash(), MainConfig.config.forceTexture, textureSha1, texturePackId, joinSync));
	}

	/** Reenvia o catálogo de cosméticos NORMAIS pra todo mundo já online (ver SyncCosmeticsPayload).
	 *  {@code allowResourceReload} só deve ser true no /gc reload — o Dev Studio salvando uma
	 *  edição manda os dados certos pro client sem disparar reloadResources() (recarga pesada de
	 *  textura/model, perceptível na tela), que só deve acontecer quando o admin pede de propósito. */
	public static void broadcastCosmeticsCatalog(net.minecraft.server.MinecraftServer server, boolean allowResourceReload) {
		debugLog("broadcastCosmeticsCatalog: " + CosmeticsConfig.cosmeticsMap.size() + " cosmetics to " + server.getPlayerManager().getCurrentPlayerCount() + " players (allowResourceReload=" + allowResourceReload + ").");
		SyncCosmeticsPayload payload = new SyncCosmeticsPayload(CosmeticsConfig.cosmeticsMap, allowResourceReload);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	/** Idem, pra armaduras convertidas em cosmético (ver SyncArmorCosmeticsPayload). */
	public static void broadcastArmorCosmeticsCatalog(net.minecraft.server.MinecraftServer server, boolean allowResourceReload) {
		debugLog("broadcastArmorCosmeticsCatalog: " + com.f4xizzz.greatcosmetics.config.ArmorCosmeticsConfig.armorCosmetics.size() + " armor pieces to " + server.getPlayerManager().getCurrentPlayerCount() + " players (allowResourceReload=" + allowResourceReload + ").");
		com.f4xizzz.greatcosmetics.network.SyncArmorCosmeticsPayload payload = new com.f4xizzz.greatcosmetics.network.SyncArmorCosmeticsPayload(
				new HashMap<>(com.f4xizzz.greatcosmetics.config.ArmorCosmeticsConfig.armorCosmetics), allowResourceReload);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	/** Idem, pro mainconfig.conf inteiro (slots/types/config geral — ver SyncMainConfigPayload). */
	public static void broadcastMainConfig(net.minecraft.server.MinecraftServer server) {
		debugLog("broadcastMainConfig: syncing MainConfig to " + server.getPlayerManager().getCurrentPlayerCount() + " players.");
		com.google.gson.Gson gson = new com.google.gson.Gson();
		com.f4xizzz.greatcosmetics.network.SyncMainConfigPayload payload = new com.f4xizzz.greatcosmetics.network.SyncMainConfigPayload(
				gson.toJson(com.f4xizzz.greatcosmetics.config.MainConfig.config));
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	/**
	 * Varre TODO o inventário do jogador (mochila, hotbar, armadura vestida e offhand) atrás de
	 * itens reais que viraram cosmético (ver ArmorCosmeticsConfig) — se achar, remove o item e
	 * libera o cosmético equivalente no guarda-roupa dele, avisando no chat. Ignora jogadores no
	 * criativo (lá o item não passa de um bloco de construção, não faz sentido "confiscar").
	 * Chamado no join e periodicamente (ver ServerTickEvents.END_SERVER_TICK), pra pegar o item
	 * não importa como ele foi parar no inventário (drop, troca, comando externo, etc).
	 */
	public static void checkAndConvertArmorInventory(ServerPlayerEntity player) {
		if (player.getAbilities().creativeMode) return;
		if (com.f4xizzz.greatcosmetics.config.ArmorCosmeticsConfig.armorCosmetics.isEmpty()) return;

		net.minecraft.entity.player.PlayerInventory inv = player.getInventory();
		net.minecraft.registry.RegistryWrapper.WrapperLookup regs = player.getServerWorld().getRegistryManager();

		for (int i = 0; i < inv.size(); i++) {
			net.minecraft.item.ItemStack stack = inv.getStack(i);
			if (stack.isEmpty()) continue;

			String cosmeticId = com.f4xizzz.greatcosmetics.config.ArmorCosmeticsConfig.getCosmeticIdForItem(stack.getItem());
			if (cosmeticId == null) continue;

			CosmeticData data = getCosmeticById(cosmeticId);
			String displayName = (data != null && data.DisplayName != null && !data.DisplayName.isBlank()) ? data.getChatSafeDisplayName() : cosmeticId;

			inv.removeStack(i);
			debugLog("checkAndConvertArmorInventory: real item converted to cosmetic '" + cosmeticId + "' for " + player.getName().getString() + " (inventory slot " + i + ").");

			boolean unlockedNow = com.f4xizzz.greatcosmetics.database.DatabaseManager.unlockCosmetic(player.getUuid(), cosmeticId);

			// Sem isso, ClientUnlockedCosmetics só ficava sabendo desse unlock na PRÓXIMA vez que
			// o wardrobe abrisse — até lá o cosmético sumia do inventário E continuava invisível
			// na aba Acessórios (nem posse física nem "concedido" pro client), como se nunca
			// tivesse sido liberado de verdade.
			ServerPlayNetworking.send(player, new com.f4xizzz.greatcosmetics.network.GrantCosmeticPayload(cosmeticId));

			if (unlockedNow) {
				player.sendMessage(LangConfig.chat("messages.cosmetic.converted_wardrobe", regs, "name", displayName), false);
			} else {
				player.sendMessage(LangConfig.chat("messages.cosmetic.converted_already", regs, "name", displayName), false);
			}
		}
	}

	// ==========================================
	// SISTEMA DE TAGS: helpers de sincronização
	// ==========================================
	/** Bônus ADITIVO de slots extras via permissão (ex: "gc.extraslot.hands.2" = +2 slots pro slot
	 *  HANDS, "gc.extraslot.all.4" = +4 slots pra TODO slot) — diferente do sistema existente
	 *  "gc.slot.&lt;slot&gt;.&lt;N&gt;" (que define o limite ABSOLUTO, não soma). Pega o MAIOR N
	 *  concedido em cada uma das duas categorias (específica do slot e "all") separadamente — não
	 *  soma múltiplos tiers da MESMA categoria (senão um jogador com permissões de vários cargos
	 *  empilhados acumularia bônus indefinidamente) — e depois SOMA as duas categorias entre si
	 *  (um bônus geral + um bônus específico do slot são coisas diferentes, então se somam). */
	private int getExtraSlotBonus(ServerPlayerEntity player, String slotVirtual) {
		String slotLower = slotVirtual.toLowerCase();
		int bonus = 0;

		for (int i = 100; i > 0; i--) {
			if (checkPermission(player, "gc.extraslot." + slotLower + "." + i)) {
				bonus += i;
				break;
			}
		}

		for (int i = 100; i > 0; i--) {
			if (checkPermission(player, "gc.extraslot.all." + i)) {
				bonus += i;
				break;
			}
		}

		return bonus;
	}

	public static void broadcastTagsCatalog(net.minecraft.server.MinecraftServer server) {
		SyncTagsPayload payload = new SyncTagsPayload(new HashMap<>(TagsConfig.tagsMap));
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	public static void broadcastEffectsCatalog(net.minecraft.server.MinecraftServer server) {
		String json = new com.google.gson.Gson().toJson(EffectConfig.effectsMap);
		com.f4xizzz.greatcosmetics.network.SyncEffectsPayload payload = new com.f4xizzz.greatcosmetics.network.SyncEffectsPayload(json);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	public static void syncPlayerTags(ServerPlayerEntity player) {
		java.util.List<String> owned = new java.util.ArrayList<>();

		// player_tags é usada tanto pra tags NORMAIS (posse de verdade, ganha via /gc tags give
		// etc) quanto pra tags de GRUPO — mas pra tags de grupo, a linha só existe porque
		// equipTag() precisa de UMA linha pra marcar o campo "equipped" (ver comentário lá), não
		// porque o player É dono de verdade. Sem filtrar isso aqui, uma tag de grupo equipada uma
		// vez só (ex: enquanto o player tinha o cargo "dono") ficava marcada como "possuída" pra
		// SEMPRE na GUI — mesmo depois do player perder o cargo — porque essa linha nunca era
		// apagada, e essa função confiava cegamente nela. O EquipTagPayload (clique real) já fazia
		// a coisa certa (sempre recalcula isInGroup na hora pra tag de grupo, nunca confia na
		// linha) — essa função só não seguia a mesma regra, causando o desync visual: a tag
		// aparecia liberada/verde na lista, mas clicar nela não fazia nada (o servidor barrava
		// certinho, só a exibição que tava errada).
		for (String tagId : com.f4xizzz.greatcosmetics.database.DatabaseManager.getPlayerOwnedTags(player.getUuid())) {
			TagData rowData = TagsConfig.getById(tagId);
			if (rowData != null && rowData.isGroupTag) {
				if (com.f4xizzz.greatcosmetics.util.LuckPermsTagManager.isInGroup(player, rowData.id)) owned.add(tagId);
			} else {
				owned.add(tagId);
			}
		}

		// Cobre o caso de uma tag de grupo que o player pertence AGORA mas ainda nunca teve
		// nenhuma linha criada em player_tags (nunca foi equipada por ele antes).
		for (TagData data : TagsConfig.tagsMap.values()) {
			if (data.isGroupTag && com.f4xizzz.greatcosmetics.util.LuckPermsTagManager.isInGroup(player, data.id) && !owned.contains(data.id)) {
				owned.add(data.id);
			}
		}

		String equipped = com.f4xizzz.greatcosmetics.database.DatabaseManager.getEquippedTagId(player.getUuid());
		ServerPlayNetworking.send(player, new SyncPlayerTagsPayload(player.getUuid(), owned, equipped));
	}

	/**
	 * Rede de segurança: se a tag equipada é uma tag de grupo do LuckPerms e o player não
	 * pertence mais àquele grupo (ex: foi despromovido), desequipa automaticamente no join.
	 */
	public static void validateEquippedGroupTag(ServerPlayerEntity player) {
		String equippedId = com.f4xizzz.greatcosmetics.database.DatabaseManager.getEquippedTagId(player.getUuid());
		if (equippedId == null) return;

		TagData data = TagsConfig.getById(equippedId);
		if (data == null || !data.isGroupTag) return;

		if (!com.f4xizzz.greatcosmetics.util.LuckPermsTagManager.isInGroup(player, data.id)) {
			com.f4xizzz.greatcosmetics.database.DatabaseManager.unequipAllForPlayer(player.getUuid());
			// Remove a linha fantasma de player_tags também — só desmarcar "equipped" não bastava,
			// já que syncPlayerTags() (antes desse fix) confiava em QUALQUER linha existente pra
			// mostrar a tag como "possuída" na GUI pra sempre, mesmo sem o player pertencer mais
			// ao grupo. Ver comentário em syncPlayerTags() pra mais detalhes.
			com.f4xizzz.greatcosmetics.database.DatabaseManager.removeTagOwnership(player.getUuid(), data.id);
			com.f4xizzz.greatcosmetics.util.LuckPermsTagManager.removeTag(player, data);
			debugLog("Group tag '" + equippedId + "' automatically unequipped from " + player.getName().getString() + " (no longer in the group).");

			// Avisa o PLAYER também (antes só ficava no log do console/debug) — ele precisa saber
			// que perdeu a tag por ter saído do grupo, não só ver ela sumir sem explicação.
			// data.displayName é texto em formato MiniMessage (ex: "<red>VIP") — a chave de lang
			// concatena {name} cru, então o parse resolve as tags dele junto.
			net.minecraft.registry.RegistryWrapper.WrapperLookup regs = player.getServerWorld().getRegistryManager();
			player.sendMessage(LangConfig.chat("messages.tag.group_lost", regs, "name", data.displayName, "id", data.id), false);
		}
	}

	/**
	 * Rede de segurança pro Dev Mode/OP de cosméticos: tanto ser OP de verdade (hasAllUnlocked,
	 * ver WardrobeManager) quanto o toggle de Dev Mode deixam o client mandar EquipCosmeticPayload
	 * pra QUALQUER cosmético — mesmo um que o player nunca desbloqueou de verdade (nunca teve o
	 * item real convertido) — e o handler do payload confia nisso, gravando direto em
	 * player_equipped_cosmetics sem checar hasCosmetic(). Sem essa validação, um cosmético
	 * equipado assim ficava "grudado" no personagem pra sempre depois que o player perdia o OP/
	 * permissão de dev mode, e nem dava pra tirar via /gc remove — esse comando só mexe em
	 * player_unlocked_cosmetics (posse), não em player_equipped_cosmetics (equipado), então
	 * reclamava que o player "não possuía" um cosmético que ainda estava visivelmente equipado.
	 * Chamado no join e periodicamente (mesmo tick de 5s do checkAndConvertArmorInventory).
	 */
	public static void validateEquippedCosmeticOwnership(ServerPlayerEntity player) {
		boolean canBypassOwnership = isRealOperator(player) || checkPermission(player, MainConfig.config.devModePermission);
		if (canBypassOwnership) return;

		java.util.List<String> equippedIds = com.f4xizzz.greatcosmetics.database.DatabaseManager.getPlayerEquippedCosmetics(player.getUuid());
		boolean changed = false;
		for (String id : equippedIds) {
			if (!com.f4xizzz.greatcosmetics.database.DatabaseManager.hasCosmetic(player.getUuid(), id)) {
				com.f4xizzz.greatcosmetics.database.DatabaseManager.unequipCosmetic(player.getUuid(), id);
				changed = true;
				debugLog("Cosmetic '" + id + "' automatically unequipped from " + player.getName().getString() + " (equipped via OP/Dev Mode bypass, no real ownership, and the bypass is no longer active).");
			}
		}

		if (changed) {
			com.f4xizzz.greatcosmetics.database.DatabaseManager.broadcastPlayerCosmetics(player);
			net.minecraft.registry.RegistryWrapper.WrapperLookup regs = player.getServerWorld().getRegistryManager();
			player.sendMessage(LangConfig.chat("messages.cosmetic.test_removed", regs), false);
		}
	}

	/** A tag de GRUPO de maior peso (mesma prioridade que o LuckPerms usa) entre as que o player
	 *  pertence de verdade — a "tag padrão" dele no momento, usada tanto pro auto-equip no
	 *  join/troca de cargo quanto pra decidir pra onde cair quando ele tenta desequipar. */
	private static TagData findBestGroupTag(ServerPlayerEntity player) {
		TagData best = null;
		for (TagData data : TagsConfig.tagsMap.values()) {
			if (!data.isGroupTag) continue;
			if (!com.f4xizzz.greatcosmetics.util.LuckPermsTagManager.isInGroup(player, data.id)) continue;
			if (best == null || data.weight > best.weight) best = data;
		}
		return best;
	}

	/**
	 * Auto-equipa a tag de GRUPO do cargo atual do LuckPerms quando o player não tem NENHUMA tag
	 * equipada — chamado no join e toda vez que o cargo muda (ver LuckPermsTagManager.
	 * registerRankChangeListener). Só age quando não tem nada equipado de propósito: aplicar/
	 * remover uma tag via LuckPermsTagManager dispara um UserDataRecalculateEvent (que chama esse
	 * mesmo método de novo através do listener de troca de cargo) — se ele tentasse "corrigir"
	 * pra tag de MAIOR peso toda vez, um clique manual pra equipar uma tag de grupo diferente da
	 * de maior peso seria revertido na hora pelo próprio recalculate que o equip dispara, e o
	 * clique parecia não fazer nada.
	 */
	public static void autoEquipCurrentGroupTag(ServerPlayerEntity player) {
		String currentEquippedId = com.f4xizzz.greatcosmetics.database.DatabaseManager.getEquippedTagId(player.getUuid());
		if (currentEquippedId != null) return;

		TagData best = findBestGroupTag(player);
		if (best == null) return;

		com.f4xizzz.greatcosmetics.database.DatabaseManager.equipTag(player.getUuid(), best.id);
		com.f4xizzz.greatcosmetics.util.LuckPermsTagManager.applyTag(player, best);
		debugLog("Tag group '" + best.id + "' auto-equipada em " + player.getName().getString() + " (cargo atual do LuckPerms).");
	}
}