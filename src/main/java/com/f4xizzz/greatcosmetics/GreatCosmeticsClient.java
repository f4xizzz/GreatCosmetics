package com.f4xizzz.greatcosmetics;

import com.f4xizzz.greatcosmetics.client.ClientSkinCache;
import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import com.f4xizzz.greatcosmetics.config.CosmeticData;
import com.f4xizzz.greatcosmetics.config.CosmeticsConfig;
import com.f4xizzz.greatcosmetics.config.MainConfig;
import com.f4xizzz.greatcosmetics.config.SkinConfigManager;
import com.f4xizzz.greatcosmetics.network.OpenWardrobePayload;
import com.f4xizzz.greatcosmetics.network.SyncCosmeticsPayload;
import com.f4xizzz.greatcosmetics.util.AutoCMDManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.resource.ResourceManager;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class GreatCosmeticsClient implements ClientModInitializer {

	private static final Logger LOGGER = LoggerFactory.getLogger("GreatCosmetics-Client");

	// Espelha GreatCosmetics.isDebugMode/debugLog() do lado servidor — sincronizado via
	// DebugModePayload (ligado/desligado por /gc debug). Cada lado imprime no PRÓPRIO console
	// (server nunca vê os logs client automaticamente, exceto os que o próprio client decide
	// encaminhar via DebugLogPayload — ver debugLog() abaixo).
	public static boolean isDebugMode = false;

	private static int idleTickCounter = 0;

	/** Loga no console do CLIENT (sempre, se isDebugMode client estiver ligado) e também
	 *  encaminha a mesma linha pro servidor via DebugLogPayload (C2S) — o servidor só imprime
	 *  se o SEU PRÓPRIO isDebugMode também estiver ligado (ver receiver em GreatCosmetics#onInitialize),
	 *  então um admin acompanhando só o console do server continua vendo o que acontece no
	 *  client de qualquer jogador com debug ativo. */
	public static void debugLog(String message) {
		if (!isDebugMode) return;
		LOGGER.info("[GreatCosmetics DEBUG] " + message);

		if (MinecraftClient.getInstance().getNetworkHandler() != null
				&& ClientPlayNetworking.canSend(com.f4xizzz.greatcosmetics.network.DebugLogPayload.ID)) {
			ClientPlayNetworking.send(new com.f4xizzz.greatcosmetics.network.DebugLogPayload(message));
		}
	}

	@Override
	public void onInitializeClient() {

		com.f4xizzz.greatcosmetics.client.ClientFavoriteCosmetics.load();

		// Limpa qualquer arquivo de textura decifrado que tenha sobrado de uma sessão anterior
		// encerrada sem sair limpo (crash, "kill" do processo) — ver ClientForcedTextureCache.
		com.f4xizzz.greatcosmetics.client.ClientForcedTextureCache.cleanupTempDir();

		// ClientJoinReloadState nunca deve sobreviver de uma conexão pra outra — ver a classe pro
		// porquê. "É entrada no servidor?" NÃO é mais heurística de tempo no client: o
		// SyncCatalogStatePayload carrega um joinSync que o servidor seta (true no handler de
		// JOIN, false no /gc reload), e o receiver desse payload arma/limpa o estado. Aqui só
		// garantimos que nada vaza de uma conexão pra outra.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> com.f4xizzz.greatcosmetics.client.ClientJoinReloadState.resetAll());

		// HUD dos bônus de Lure — coluna à direita da hotbar quando o player usa cosmético que dá
		// Lure ativo (ver LureHudOverlay). Ligado por mainconfig.conf -> lureHud (default on,
		// sincronizado do servidor via SyncMainConfigPayload).
		HudRenderCallback.EVENT.register((drawContext, tickCounter) ->
				com.f4xizzz.greatcosmetics.client.gui.LureHudOverlay.render(drawContext));

		MainConfig.loadConfig();
		com.f4xizzz.greatcosmetics.config.LangConfig.load();
		CosmeticsConfig.loadConfig();

		// Pré-carrega o AutoCMDManager com os CMDs do ÚLTIMO servidor usado (persistido em disco) —
		// TEM que ser depois de CosmeticsConfig.loadConfig() (que populou com o config local) e
		// ANTES do primeiro bake de models (ModelLoadingPlugin roda no 1º reload de recursos, que
		// acontece depois do onInitializeClient). Sem isso, o boot bakeia com CMDs locais/vazios e
		// a 1ª entrada em QUALQUER servidor sempre precisa de um reloadResources() de 30s. Ver
		// ClientBakeState.
		lastBakedCatalogHash = com.f4xizzz.greatcosmetics.client.ClientBakeState.loadAtBoot();

		SkinConfigManager.load();
		com.f4xizzz.greatcosmetics.config.SkinGroupConfigManager.load();
		com.f4xizzz.greatcosmetics.client.GizmoDevConfig.load();

		// ==========================================
		// REGISTRO DE KEYBINDS E PACOTES DA MOCHILA
		// ==========================================
		com.f4xizzz.greatcosmetics.client.KeybindManager.registerKeybinds();
		ClientSkinCache.registerNetworkReceivers();

		ClientPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.ShowBackpackSelectorPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				debugLog("ShowBackpackSelectorPayload received: " + payload.backpackIds());
				context.client().setScreen(new com.f4xizzz.greatcosmetics.client.gui.BackpackSelectorScreen(payload.backpackIds()));
			});
		});

		// ==========================================
		// RECEBE O ESTADO DO MODO DEBUG (ligado/desligado via /gc debug no servidor)
		// ==========================================
		ClientPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.DebugModePayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				isDebugMode = payload.enabled();
				LOGGER.info("[GreatCosmetics] Debug mode (client) " + (isDebugMode ? "ENABLED" : "DESENABLED") + ".");
			});
		});

		// ==========================================
		// RECEBE O CATÁLOGO DE SKINS DE POKÉMON (join + /gc reload) — ver SyncSkinCatalogPayload
		// ==========================================
		ClientPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.SyncSkinCatalogPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				debugLog("SyncSkinCatalogPayload received: " + payload.skins().size() + " skins, " + payload.groupColors().size() + " group colors.");
				com.f4xizzz.greatcosmetics.config.SkinConfigManager.setSkinsFromServer(payload.skins());
				com.f4xizzz.greatcosmetics.config.SkinGroupConfigManager.setColorsFromServer(payload.groupColors());
				com.f4xizzz.greatcosmetics.client.gui.pages.PartyPage.refreshSkinsIfOpen();
			});
		});

		// ==========================================
		// REGISTRO PARA ABRIR A UI DO WARDROBE
		// ==========================================
		ClientPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.SyncDevPermissionsPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				debugLog("SyncDevPermissionsPayload received: isOperator=" + payload.isOperator() + " hasGcDev=" + payload.hasGcDev() + " hasGcPermDevmode=" + payload.hasGcPermDevmode());
				com.f4xizzz.greatcosmetics.client.ClientPermissionCache.isOperator = payload.isOperator();
				com.f4xizzz.greatcosmetics.client.ClientPermissionCache.hasGcDev = payload.hasGcDev();
				com.f4xizzz.greatcosmetics.client.ClientPermissionCache.hasGcPermDevmode = payload.hasGcPermDevmode();
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.GrantCosmeticPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				debugLog("GrantCosmeticPayload received: " + payload.cosmeticId());
				com.f4xizzz.greatcosmetics.client.ClientUnlockedCosmetics.unlockedIds.add(payload.cosmeticId());
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.SyncMainConfigPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				debugLog("SyncMainConfigPayload received (" + payload.configJson().length() + " chars).");
				com.f4xizzz.greatcosmetics.client.ClientMainConfigCache.setConfig(payload.configJson());
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.SyncNameTagPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				debugLog("SyncNameTagPayload received: prefix='" + payload.prefix() + "' suffix='" + payload.suffix() + "'");
				com.f4xizzz.greatcosmetics.client.ClientNameTagCache.prefix = payload.prefix();
				com.f4xizzz.greatcosmetics.client.ClientNameTagCache.suffix = payload.suffix();
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(OpenWardrobePayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				debugLog("OpenWardrobePayload received: hasAllUnlocked=" + payload.hasAllUnlocked() + " unlockedIds=" + payload.unlockedIds().size() + " hasBackground=" + payload.hasBackground());
				com.f4xizzz.greatcosmetics.client.ClientUnlockedCosmetics.clear();
				com.f4xizzz.greatcosmetics.client.ClientUnlockedCosmetics.hasAllUnlocked = payload.hasAllUnlocked();
				com.f4xizzz.greatcosmetics.client.ClientUnlockedCosmetics.unlockedIds.addAll(payload.unlockedIds());
				context.client().setScreen(new com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen(payload.hasBackground()));
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.SyncPlayerCosmeticsPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				debugLog("SyncPlayerCosmeticsPayload received: player=" + payload.playerUuid() + " equipados=" + payload.equippedCosmetics());
				com.f4xizzz.greatcosmetics.client.ClientCosmeticCache.setEquipped(payload.playerUuid(), payload.equippedCosmetics());
				com.f4xizzz.greatcosmetics.client.ClientCosmeticCache.setSettings(
						payload.playerUuid(),
						payload.hideHelmet(), payload.hideChestplate(), payload.hideLeggings(), payload.hideBoots(),
						new java.util.HashSet<>(payload.hiddenCosmeticIds())
				);
			});
		});

		// ==========================================
		// SISTEMA DE TAGS: CATÁLOGO + POSSE/EQUIPADA DO JOGADOR LOCAL
		// ==========================================
		ClientPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.SyncTagsPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				debugLog("SyncTagsPayload received: " + payload.tagsMap().size() + " tags in the catalog.");
				com.f4xizzz.greatcosmetics.client.ClientTagsCache.setAllTags(payload.tagsMap());
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.SyncPlayerTagsPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				debugLog("SyncPlayerTagsPayload received: owned=" + payload.ownedTagIds() + " equipada=" + payload.equippedTagId());
				com.f4xizzz.greatcosmetics.client.ClientTagsCache.setPlayerTags(payload.ownedTagIds(), payload.equippedTagId());
			});
		});

		// ==========================================
		// SISTEMA DE EFEITOS: catálogo completo (ver SaveEffectPayload/DeleteEffectPayload) —
		// sem isso, criar/editar um efeito no Dev Studio só gravava no client de quem editou.
		// ==========================================
		ClientPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.SyncEffectsPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<java.util.Map<String, com.f4xizzz.greatcosmetics.config.EffectData>>(){}.getType();
				java.util.Map<String, com.f4xizzz.greatcosmetics.config.EffectData> map = new com.google.gson.Gson().fromJson(payload.jsonData(), type);
				com.f4xizzz.greatcosmetics.config.EffectConfig.effectsMap = map != null ? map : new java.util.HashMap<>();
				debugLog("SyncEffectsPayload received: " + com.f4xizzz.greatcosmetics.config.EffectConfig.effectsMap.size() + " effects in the catalog.");
			});
		});

		// ==========================================
		// ESTADO DO CATÁLOGO/TEXTURA FORÇADA (ver Fase 3 do plano — cache criptografado por
		// servidor + supressão da SplashOverlay quando nada mudou). Mandado ANTES do resource pack
		// forçado e antes dos dois payloads de catálogo abaixo (ver GreatCosmetics#
		// sendCatalogStateSnapshot) — só decide/arma coisas, nunca aplica dado nenhum sozinho.
		// ==========================================
		ClientPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.SyncCatalogStatePayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				long now = System.currentTimeMillis();
				com.f4xizzz.greatcosmetics.client.ClientJoinReloadState.stash(
						payload.catalogHash(), payload.forceTextureEnabled(), payload.textureSha1(), payload.texturePackId());

				String serverKey = com.f4xizzz.greatcosmetics.client.ClientServerIdentity.currentServerKey();
				boolean catalogUnchanged = payload.catalogHash() != null && payload.catalogHash().equals(lastBakedCatalogHash);
				debugLog("SyncCatalogStatePayload received: catalogHash=" + payload.catalogHash() + " forceTextureEnabled=" + payload.forceTextureEnabled()
						+ " textureSha1=" + payload.textureSha1() + " serverKey=" + serverKey + " joinSync=" + payload.joinSync()
						+ " catalogUnchanged=" + catalogUnchanged + " (lastBaked=" + lastBakedCatalogHash + ")");

				// joinSync = o servidor diz DEFINITIVAMENTE se este sync é uma ENTRADA no servidor
				// (pode suprimir/pular) ou um /gc reload ao vivo (nunca suprime — o admin pediu o
				// recarregamento pra todo mundo online de propósito). Sem heurística de tempo no
				// client (a tentativa anterior fechava a "janela de entrada" antes dos payloads
				// sequer serem processados num modpack pesado).
				if (!payload.joinSync()) {
					com.f4xizzz.greatcosmetics.client.ClientJoinReloadState.clearJoinSync();
					debugLog("SyncCatalogStatePayload: joinSync=false (/gc reload ao vivo) — nada suprimido/pulado, recarregamento normal.");
					return;
				}
				com.f4xizzz.greatcosmetics.client.ClientJoinReloadState.markJoinSync(now);
				if (serverKey == null) {
					debugLog("SyncCatalogStatePayload: serverKey nulo (singleplayer/LAN) — sem otimização.");
					return;
				}

				boolean textureMatches;
				if (!payload.forceTextureEnabled()) {
					textureMatches = true;
				} else {
					com.f4xizzz.greatcosmetics.client.ClientForcedTextureCache.Entry entry =
							com.f4xizzz.greatcosmetics.client.ClientForcedTextureCache.getEntry(serverKey);
					textureMatches = entry != null
							&& payload.textureSha1().equalsIgnoreCase(entry.declaredTextureSha1)
							&& com.f4xizzz.greatcosmetics.client.ClientForcedTextureCache.hasEncryptedFile(entry);
				}

				if (catalogUnchanged && !payload.forceTextureEnabled()) {
					// Caminho mais comum: catálogo idêntico ao já bakeado e sem textura forçada —
					// NENHUM reloadResources() precisa acontecer. Os receivers de cosmético/armadura
					// só reconstroem o GeoModelRegistry direto. Sem reload => sem SplashOverlay,
					// nem precisa suprimir nada.
					com.f4xizzz.greatcosmetics.client.ClientJoinReloadState.armSkipReload();
					debugLog("SyncCatalogStatePayload: catálogo igual + sem textura forçada — entrada silenciosa (pulando reloadResources() nesta entrada).");
				} else if (catalogUnchanged && payload.forceTextureEnabled() && textureMatches) {
					// O vanilla vai recarregar pro pacote forçado de qualquer jeito (mesmo vindo do
					// cache local) — não dá pra pular esse reload, mas dá pra esconder a tela dele.
					// O rebuild do GeoModelRegistry encadeia depois desse reload (não é pulado).
					com.f4xizzz.greatcosmetics.client.ClientJoinReloadState.armSuppress(now);
					debugLog("SyncCatalogStatePayload: catálogo igual + textura forçada em cache — armando supressão da SplashOverlay.");
				} else {
					debugLog("SyncCatalogStatePayload: catalogUnchanged=" + catalogUnchanged + " forceTextureEnabled=" + payload.forceTextureEnabled()
							+ " textureMatches=" + textureMatches + " — entrada normal (reload + tela).");
				}
			});
		});

		// ==========================================
		// ARMADURAS CONVERTIDAS EM COSMÉTICO (ver ArmorCosmeticsConfig)
		// ==========================================
		ClientPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.SyncPokemonIvsPayload.ID, (payload, context) -> {
			context.client().execute(() -> com.f4xizzz.greatcosmetics.client.ClientPokemonIvCache.put(payload.ivsByEntityId()));
		});

		ClientPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.SyncArmorCosmeticsPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				debugLog("SyncArmorCosmeticsPayload received: " + payload.armorCosmetics().size() + " converted armor pieces, allowResourceReload=" + payload.allowResourceReload());
				com.f4xizzz.greatcosmetics.client.ClientArmorCosmeticsCache.setArmorCosmetics(payload.armorCosmetics());

				// Mesmo tratamento do receiver de SyncCosmeticsPayload abaixo (mesmos dois bugs já
				// documentados lá): sem o clear() aqui, um CMD podia ser reaproveitado por uma
				// armadura-cosmético DIFERENTE e herdar um modelo GeckoLib desatualizado; e sem
				// esperar o reloadResources() terminar antes de reconstruir, registerGeoModels lia o
				// ResourceManager ainda com os arquivos ANTIGOS (o /gc reload é justamente pra pegar
				// os novos), caindo pro ícone chapado 2D até o próximo rejoin. payload.
				// allowResourceReload() existe desde que esse payload foi criado, mas nunca era lido
				// aqui — só o receiver de cosméticos normais tinha recebido essa correção.
				//
				// rebuildAllGeoModels() (não só registerGeoModels(payload.armorCosmetics())) —
				// GeoModelRegistry é uma tabela ÚNICA compartilhada entre cosméticos normais E
				// armadura convertida. Limpar ela aqui e reconstruir só com armorCosmetics apagava
				// (e nunca devolvia) os .geo dos cosméticos NORMAIS — bastava salvar uma armadura
				// pra derrubar todo cosmético .geo normal, e vice-versa salvando um normal derrubava
				// as armaduras .geo (ver o outro receiver abaixo, mesmo bug espelhado).
				String armorCatalogHash = com.f4xizzz.greatcosmetics.client.ClientJoinReloadState.peekCatalogHash();
				if (canSkipJoinReload(armorCatalogHash)) {
					debugLog("SyncArmorCosmeticsPayload: catálogo IGUAL ao já bakeado + entrada silenciosa — pulando reloadResources(), só rebuild direto.");
					rebuildAllGeoModels();
				} else if (payload.allowResourceReload() && MinecraftClient.getInstance().world != null) {
					debugLog("SyncArmorCosmeticsPayload: scheduling GeoModel rebuild AFTER reloadResources().");
					runAfterResourceReload(GreatCosmeticsClient::rebuildAllGeoModels, armorCatalogHash);
				} else {
					debugLog("SyncArmorCosmeticsPayload: GeoModel rebuild directly (no reloadResources).");
					rebuildAllGeoModels();
				}
			});
		});

		// ==========================================
		// SINCRONIZAÇÃO DE COSMÉTICOS E MODELS
		// ==========================================
		ClientPlayNetworking.registerGlobalReceiver(SyncCosmeticsPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				debugLog("SyncCosmeticsPayload received: " + payload.configMap().size() + " cosmetics, allowResourceReload=" + payload.allowResourceReload());
				CosmeticsConfig.cosmeticsMap = payload.configMap();

				AutoCMDManager.clear();

				ResourceManager rm = MinecraftClient.getInstance().getResourceManager();

				Map<String, String> modelMap = new HashMap<>();
				for (Identifier resId : rm.findResources("models", id -> id.getNamespace().equals("greatcosmetics") && id.getPath().endsWith(".json")).keySet()) {
					String fullPath = resId.getPath().substring(7, resId.getPath().length() - 5);
					String shortName = fullPath.substring(fullPath.lastIndexOf('/') + 1);
					modelMap.put(shortName, fullPath);
				}

				// Chaveado pelo caminho relativo COMPLETO (ex: "custom/teste"), não só o nome do
				// arquivo — antes usava só o basename (shortName), então digitar "custom/teste" no
				// campo "Nome do Ícone" nunca batia com nada (o mapa só conhecia "teste" sem pasta)
				// e, pior, dois ícones de mesmo nome em pastas diferentes colidiam (o último
				// escaneado vencia silenciosamente). Agora "teste" busca textures/icons/teste.png e
				// "custom/teste" busca textures/icons/custom/teste.png, sem ambiguidade.
				Map<String, String> iconMap = new HashMap<>();
				for (Identifier resId : rm.findResources("textures/icons", id -> id.getNamespace().equals("greatcosmetics") && id.getPath().endsWith(".png")).keySet()) {
					String fullPath = resId.getPath().substring(15, resId.getPath().length() - 4);
					iconMap.put(fullPath, fullPath);
				}

				for (Map.Entry<String, CosmeticData> entry : payload.configMap().entrySet()) {
					String id = entry.getKey();
					CosmeticData data = entry.getValue();

					String fullModelPath = modelMap.getOrDefault(id, id);
					String iconKey = (data.iconId != null && !data.iconId.isBlank()) ? data.iconId : id;
					String fullIconPath = iconMap.get(iconKey);

					// data.cmd é o CMD "canônico" que TODA a UI usa pra desenhar o ícone chapado do
					// cosmético (Acessórios, seletor de mochila, Dev Studio, /gc give, e o corpo quando
					// a Part não tem geoModelId — ver AcessoriesPage/BackpackSelectorScreen/
					// CosmeticsCommand#executeGive/ArmorFeatureRendererMixin). O SERVIDOR não tem como
					// saber se existe de verdade um models/<id>.json no resourcepack do CLIENT (só o
					// client escaneia isso) — sem esse fallback pro PNG de ícone, todo cosmético que só
					// tem textures/icons/<id ou iconId>.png (sem NENHUM models/<id>.json próprio, o caso
					// mais comum) virava um override apontando pra um model INEXISTENTE. O Minecraft não
					// consegue bakear esse override, e o item cai pro mesmo visual genérico errado em
					// TODOS os cosméticos nessa situação — exatamente o "reload deixou tudo com a mesma
					// textura", inclusive persistindo até no join (nunca dependeu do reload de verdade,
					// só do resourcepack não ter um models/<id>.json pra esse id).
					// PRIORIDADE: ícone explícito (iconId, ou textures/icons/<id>.png) sempre vence o
					// model — antes era o contrário (if modelMap.containsKey(id) primeiro), então
					// TODO cosmético que já tem um model 3D (a imensa maioria) IGNORAVA por completo
					// o "Nome do Ícone" configurado no Dev Studio, mesmo com o arquivo certinho
					// presente em textures/icons/ — o campo simplesmente não tinha como fazer efeito
					// nenhum nesses casos. iconId existe justamente PRA SUBSTITUIR o ícone padrão por
					// outro arquivo; só cai pro model como fallback quando não existe ícone nenhum.
					if (data.cmd > 0) {
						if (fullIconPath != null) {
							AutoCMDManager.registeredIcons.put(fullIconPath, data.cmd);
						} else if (modelMap.containsKey(id)) {
							AutoCMDManager.registeredModels.put(fullModelPath, data.cmd);
						} else {
							System.err.println("[GreatCosmetics] WARNING: cosmetic '" + id + "' — iconKey '" + iconKey
									+ "' did not find textures/icons/" + iconKey + ".png (namespace 'greatcosmetics') nor a "
									+ "models/" + id + ".json. Icons found in the resourcepack: " + iconMap.keySet()
									+ ". This cosmetic will look broken/generic until one of the two exists.");
							debugLog("Cosmetic '" + id + "' with NO resolved icon/model (cmd=" + data.cmd + ") — fell through to fallback.");
							AutoCMDManager.registeredModels.put(fullModelPath + "_fallback", data.cmd);
						}
					}

					// NÃO registra data.resolvedIconCmd aqui — esse número é DIFERENTE de data.cmd
					// (contadores separados no AutoCMDManager: getOrCreateCmd vs getOrCreateIconCmd) e
					// NADA renderiza usando resolvedIconCmd (ItemRendererMixin/AcessoriesPage/
					// BackpackSelectorScreen/EquippedSlotsWidget/DevCosmeticsSubPage/CosmeticsCommand
					// só usam data.cmd pro CustomModelDataComponent de verdade — ver comentário
					// "REMOVIDO" em ItemRendererMixin). Registrar os dois pro MESMO fullIconPath só
					// sobrescrevia a entrada certa (data.cmd) com um número que nunca aparece em
					// nenhum item de verdade — exatamente por isso o ícone nunca resolvia: o mapa
					// ficava com {teste=resolvedIconCmd} em vez de {teste=data.cmd}.

					java.util.List<CosmeticData.CosmeticPart> allParts = new java.util.ArrayList<>(data.parts);
					if (data.variants != null) for (CosmeticData.CosmeticVariant vv : data.variants) if (vv != null && vv.parts != null) allParts.addAll(vv.parts);
					for (CosmeticData.CosmeticPart part : allParts) {
						// !isEmpty() (não só != null): Part que SÓ tem geoModelId (customModelData_or_ID
						// vazio) mesmo assim recebe part.resolvedCmd > 0 do servidor (getOrCreateCmd
						// "geo:<id>", ver CosmeticsConfig#resolveModelIds) — mas essa Part é 100%
						// GeckoLib (GeoModelRegistry, via rebuildAllGeoModels abaixo), NUNCA um override
						// de model chapado. Sem esse isEmpty(), a chave virava "" e o override do
						// carved_pumpkin ficava {resolvedCmd -> "greatcosmetics:"} (path vazio,
						// inbakeável) — o que fazia o Minecraft descartar o modelo inteiro e TODO
						// cosmético (inclusive os que só tinham iconId, tipo o examplehat) cair pro
						// visual genérico de abóbora.
						if (part.customModelData_or_ID != null && !part.customModelData_or_ID.isEmpty() && part.resolvedCmd > 0) {
							// useExactPath precisa virar o MESMO prefixo "exact:" que o SERVIDOR usa pra
							// gerar a chave (ver CosmeticsConfig#resolveModelIds) — sem isso, o ramo
							// "exact:" em modifyModelOnLoad (que busca em QUALQUER namespace pelo caminho
							// completo) nunca era acionado pelo client, e a Part caía sempre no ramo
							// genérico (só nome do arquivo, restrito ao namespace "greatcosmetics").
							// Quando o arquivo de verdade tava numa pasta/namespace diferente, isso virava
							// um override apontando pra um modelo INEXISTENTE — e, exatamente como no bug
							// do ícone chapado (data.cmd), TODA Part nessa situação caía pro mesmo modelo
							// genérico errado, não só o ícone: o corpo inteiro do cosmético.
							String key = part.useExactPath ? "exact:" + part.customModelData_or_ID : part.customModelData_or_ID;
							AutoCMDManager.registeredModels.put(key, part.resolvedCmd);
						}
					}
				}

				// GeoModelRegistry é uma tabela PARALELA (cmd -> geo/textura/animação) que nunca era
				// limpa antes — só AutoCMDManager.clear() reiniciava a contagem de CMD do zero. Depois
				// de qualquer mudança na config (cosmético removido/reordenado, modelo trocado de
				// GeckoLib pra ícone chapado etc), o MESMO número de CMD podia acabar sendo
				// reaproveitado por um cosmético DIFERENTE — e como o registro antigo (geo/textura de
				// alguma parte .geo que já teve esse número um dia) nunca sumia, o cosmético novo
				// (vanilla) herdava um modelo GeckoLib aleatório e desatualizado no lugar do ícone de
				// abóbora certo.
				//
				// rebuildAllGeoModels() em vez de só registerGeoModels(payload.configMap()) — mesmo
				// motivo do comentário espelhado no receiver de SyncArmorCosmeticsPayload acima:
				// GeoModelRegistry é uma tabela ÚNICA compartilhada, limpar+reconstruir só com os
				// cosméticos NORMAIS apagava (e nunca devolvia) os .geo das armaduras convertidas —
				// exatamente o bug reportado ("salvei um cosmético normal, os acessórios .geo viraram
				// 2D").
				Runnable rebuildGeoModels = GreatCosmeticsClient::rebuildAllGeoModels;

				// allowResourceReload só vem true no /gc reload — o Dev Studio salvando uma
				// edição individual já manda os dados certos (cosmeticsMap acima, registeredModels/
				// registeredIcons pra construção de ícone), mas sem forçar o reloadResources()
				// pesado até o admin pedir de propósito. Como só vem true nesse comando explícito
				// (nunca em join/save), não precisa de nenhuma checagem de "mudou mesmo" antes de
				// decidir recarregar — o comando em si já É o pedido explícito. Uma checagem assim
				// existia antes comparando só AutoCMDManager.registeredModels/registeredIcons, que
				// NUNCA rastreiam Parts GeckoLib (só customModelData_or_ID, ver loop acima) — um
				// /gc reload que só mexeu em .geo sempre achava "nada mudou", pulava o reload de
				// verdade, e o registro do modelo GeckoLib escaneava o resourcepack ANTIGO — não
				// achava os arquivos novos, caindo pro ícone chapado 2D.
				if (payload.allowResourceReload() && MinecraftClient.getInstance().world != null) {
					// registerGeoModels lê o ResourceManager AO VIVO (arquivo geo/textura/animação no
					// resourcepack) — rodar ele ANTES do reloadResources() terminar escaneava o estado
					// AINDA VELHO dos recursos (sem os arquivos novos que o /gc reload é justamente
					// pra pegar), registrando "não achei" pra parte cujo arquivo só ficaria disponível
					// segundos depois. Combinado com o clear() acima (que agora apaga o registro
					// antigo, ainda válido, antes de reconstruir), isso deixava cosméticos .geo
					// funcionando ANTES ficarem quebrados (textura preto/roxa, ou caindo pro ícone
					// chapado) até o próximo rejoin — mesmo o arquivo certo estando lá. Só reconstrói
					// DEPOIS do reload terminar de verdade. E usa runAfterResourceReload() (não
					// reloadResources() direto) porque o /gc reload manda ESSE payload junto com
					// SyncArmorCosmeticsPayload — os dois chamando reloadResources() cada um por si
					// (dois reloads concorrentes de verdade) fazia um atropelar o outro e corromper o
					// registro de models GeckoLib pros DOIS lados, deixando .geo em ícone 2D até
					// alguém salvar uma edição manual (que não passa por reloadResources() de novo).
					String cosmeticsCatalogHash = com.f4xizzz.greatcosmetics.client.ClientJoinReloadState.peekCatalogHash();
					if (canSkipJoinReload(cosmeticsCatalogHash)) {
						debugLog("SyncCosmeticsPayload: catálogo IGUAL ao já bakeado + entrada silenciosa — pulando reloadResources(), só rebuild direto.");
						rebuildGeoModels.run();
					} else {
						debugLog("SyncCosmeticsPayload: scheduling GeoModel rebuild AFTER reloadResources().");
						runAfterResourceReload(rebuildGeoModels, cosmeticsCatalogHash);
					}
				} else {
					debugLog("SyncCosmeticsPayload: GeoModel rebuild directly (no reloadResources).");
					rebuildGeoModels.run();
				}
			});
		});

		// ==========================================
		// SISTEMA DE IDLE SOUND (SOM PARADO)
		// ==========================================
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.world == null) return;

			boolean isIdle = client.player.forwardSpeed == 0 && client.player.sidewaysSpeed == 0 && client.player.getVelocity().horizontalLengthSquared() < 0.001;

			if (isIdle) {
				idleTickCounter++;
				if (idleTickCounter > 40) {
					if (client.world.random.nextInt(50) == 0) {
						java.util.Set<String> equipped = com.f4xizzz.greatcosmetics.client.ClientCosmeticCache.getEquipped(client.player.getUuid());

						if (equipped != null && !equipped.isEmpty()) {
							for (String id : equipped) {
								CosmeticData data = GreatCosmetics.getCosmeticById(id);
								if (data != null && data.sounds != null && data.sounds.idleSound != null && !data.sounds.idleSound.isEmpty()) {
									try {
										Identifier soundId = Identifier.of(data.sounds.idleSound);
										SoundEvent soundEvent = SoundEvent.of(soundId);
										client.player.playSound(soundEvent, (float) data.sounds.idleVolume, (float) data.sounds.idlePitch);
									} catch (Exception ignored) {}
								}
							}
						}
						idleTickCounter = 0;
					}
				}
			} else {
				idleTickCounter = 0;
			}
		});

		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			if (stack.isOf(net.minecraft.item.Items.CARVED_PUMPKIN)) {
				var cmdComp = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
				if (cmdComp != null) {
					int currentCmd = (int) cmdComp.value();
					lines.add(com.f4xizzz.greatcosmetics.config.LangConfig.text("items.scanner.divider"));
					lines.add(com.f4xizzz.greatcosmetics.config.LangConfig.text("items.scanner.line", "cmd", currentCmd));
					lines.add(com.f4xizzz.greatcosmetics.config.LangConfig.text("items.scanner.divider"));
				}
			}
		});

		// ==========================================
		// SISTEMA DE GERAÇÃO DE OVERRIDES (MODELS)
		// ==========================================
		ModelLoadingPlugin.register(pluginContext -> {
			pluginContext.resolveModel().register(context -> {
				if (!MainConfig.config.autoDetectModels) return null;

				Identifier id = context.id();
				if (id == null) return null;

				if (id.getNamespace().equals("greatcosmetics")) {

					if (id.getPath().startsWith("icon_")) {
						String iconName = id.getPath().replace("icon_", "");
						if (AutoCMDManager.registeredIcons.containsKey(iconName)) {
							String json = String.format("{ \"parent\": \"minecraft:item/generated\", \"textures\": { \"layer0\": \"greatcosmetics:icons/%s\" } }", iconName);
							return JsonUnbakedModel.deserialize(json);
						}
					}

					try {
						Identifier fileId = Identifier.of("greatcosmetics", "models/" + id.getPath() + ".json");
						ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
						var resourceOpt = rm.getResource(fileId);

						if (resourceOpt.isPresent()) {
							try (java.io.Reader reader = resourceOpt.get().getReader()) {
								com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();

								if (json.has("textures")) {
									com.google.gson.JsonObject textures = json.getAsJsonObject("textures");
									for (Map.Entry<String, com.google.gson.JsonElement> entry : textures.entrySet()) {
										String texPath = entry.getValue().getAsString();

										if (!texPath.startsWith("#")) {
											String cleanPath = texPath.replace("greatcosmetics:", "");

											if (!cleanPath.startsWith("item/") && !cleanPath.startsWith("block/")) {
												textures.addProperty(entry.getKey(), "greatcosmetics:item/" + cleanPath);
											} else {
												textures.addProperty(entry.getKey(), "greatcosmetics:" + cleanPath);
											}
										}
									}
								}
								return JsonUnbakedModel.deserialize(json.toString());
							}
						}
					} catch (Exception e) {
						debugLog("resolveModel: failed reading models/" + id.getPath() + ".json — " + e);
					}
				}
				return null;
			});

			pluginContext.modifyModelOnLoad().register((unbakedModel, context) -> {
				if (!MainConfig.config.autoDetectModels) return unbakedModel;

				Identifier id = context.resourceId();
				if (id == null) return unbakedModel;

				if (id.getNamespace().equals("minecraft") && id.getPath().equals("item/carved_pumpkin")) {

					ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
					Map<String, String> modelMap = new HashMap<>();
					for (Identifier resId : rm.findResources("models", res -> res.getNamespace().equals("greatcosmetics") && res.getPath().endsWith(".json")).keySet()) {
						String fullPath = resId.getPath().substring(7, resId.getPath().length() - 5);
						String shortName = fullPath.substring(fullPath.lastIndexOf('/') + 1);
						modelMap.put(shortName, fullPath);
					}

					if (unbakedModel instanceof JsonUnbakedModel jsonModel) {
						StringBuilder overridesJson = new StringBuilder();
						overridesJson.append("{ \"parent\": \"minecraft:item/generated\", \"overrides\": [ ");

						TreeMap<Integer, String> sortedOverrides = new TreeMap<>();

						for (Map.Entry<String, Integer> entry : AutoCMDManager.registeredModels.entrySet()) {
							String rawId = entry.getKey();

							if (rawId.startsWith("exact:")) {
								// Modo "Caminho Exato" (CosmeticPart#useExactPath): busca o caminho
								// LITERAL digitado em QUALQUER namespace carregado, em vez do modo
								// antigo (só o nome do arquivo, restrito ao namespace greatcosmetics).
								String exactPath = rawId.substring("exact:".length());
								Identifier found = findExactResource(rm, "models", "models/" + exactPath + ".json");
								String modelRef = found != null ? (found.getNamespace() + ":" + exactPath) : ("greatcosmetics:" + exactPath);
								sortedOverrides.put(entry.getValue(), modelRef);
								continue;
							}

							if (rawId.endsWith("_fallback")) {
								rawId = rawId.replace("_fallback", "");
							}
							String realModelPath = modelMap.getOrDefault(rawId, rawId);
							sortedOverrides.put(entry.getValue(), "greatcosmetics:" + realModelPath);
						}

						for (Map.Entry<String, Integer> entry : AutoCMDManager.registeredIcons.entrySet()) {
							sortedOverrides.put(entry.getValue(), "greatcosmetics:icon_" + entry.getKey());
						}

						boolean first = true;
						for (Map.Entry<Integer, String> entry : sortedOverrides.entrySet()) {
							if (!first) overridesJson.append(", ");
							overridesJson.append(String.format("{ \"predicate\": {\"custom_model_data\": %d}, \"model\": \"%s\" }", entry.getKey(), entry.getValue()));
							first = false;
						}

						overridesJson.append(" ] }");

						debugLog("modifyModelOnLoad(carved_pumpkin): generating " + sortedOverrides.size() + " overrides ("
								+ AutoCMDManager.registeredModels.size() + " models + " + AutoCMDManager.registeredIcons.size() + " icons).");

						try {
							JsonUnbakedModel dummyModel = JsonUnbakedModel.deserialize(overridesJson.toString());
							jsonModel.getOverrides().addAll(dummyModel.getOverrides());
						} catch (Exception e) {
							debugLog("modifyModelOnLoad(carved_pumpkin): FAILED to apply overrides — " + e);
							e.printStackTrace();
						}
					}
				}
				return unbakedModel;
			});
		});
	}

	/**
	 * Acha, no resourcepack (qualquer namespace — igual o resto do sistema de models), o
	 * geo/textura/animação de cada parte com geoModelId preenchido, e registra em GeoModelRegistry
	 * pelo resolvedCmd (número que o SERVIDOR já calculou e sincronizou — o client nunca recalcula
	 * esse número sozinho, só usa geoModelId pra achar o ARQUIVO certo). Convenção: pra um
	 * geoModelId "xyz", espera geo/item/xyz.geo.json + textures/item/xyz.png (animations/item/
	 * xyz.animation.json é opcional) em QUALQUER namespace carregado — mesmo fluxo de sempre pra
	 * adicionar um modelo novo: solta os arquivos no resourcepack, digita "xyz" no Dev Studio,
	 * sem precisar recompilar o mod.
	 */
	/** Acha, em QUALQUER namespace carregado, um recurso cujo caminho bate EXATO com
	 *  {@code exactRelativePath} (ex: "models/sas/cigarro.json") — usado pelo modo "Caminho Exato"
	 *  (ver CosmeticPart#useExactPath) tanto pro modelo vanilla quanto pro GeckoLib, em vez da busca
	 *  antiga por só o nome do arquivo (ambígua com pastas, e no caso do modelo vanilla, restrita ao
	 *  namespace "greatcosmetics"). {@code topFolder} é só a otimização de raiz que findResources
	 *  pede (ex: "models", "geo", "textures", "animations").
	 */
	private static Identifier findExactResource(ResourceManager rm, String topFolder, String exactRelativePath) {
		for (Identifier resId : rm.findResources(topFolder, res -> res.getPath().equals(exactRelativePath)).keySet()) {
			return resId;
		}
		return null;
	}

	// /gc reload manda SyncArmorCosmeticsPayload e SyncCosmeticsPayload praticamente juntos, e os
	// dois receivers precisam de um reloadResources() (recarga pesada de texturas/models) quando
	// allowResourceReload() vem true. Chamar MinecraftClient#reloadResources() duas vezes seguidas
	// (uma por payload) dispara dois reloads CONCORRENTES de verdade, que se atropelam e corrompem
	// o registro de models GeckoLib pros dois lados — sintoma: cosméticos .geo caem pro ícone 2D
	// depois do /gc reload, e só voltam ao normal quando alguém salva uma edição manual (que nunca
	// chama reloadResources() de novo, só reconstrói em cima do resourcepack já estável). Os dois
	// receivers chamam runAfterResourceReload() em vez de reloadResources() direto — o primeiro que
	// chegar dispara o único reload de verdade, o outro só encadeia no MESMO future.
	private static java.util.concurrent.CompletableFuture<Void> pendingResourceReload = null;

	/** Hash do catálogo (cosméticos+armaduras) contra o qual os models estão bakeados AGORA —
	 *  em memória, começa null a cada boot do jogo (no boot os models são bakeados com o
	 *  AutoCMDManager VAZIO, sem os CMDs do servidor; a 1ª entrada em qualquer servidor sempre
	 *  precisa de um reload pra re-bakear com os CMDs certos, ver ModelLoadingPlugin/
	 *  modifyModelOnLoad). Atualizado só quando um reloadResources() de verdade termina. Se numa
	 *  entrada o catálogo recebido == esse valor, os models já estão bakeados certo e o reload
	 *  (e a tela vermelha) é puro desperdício — pulamos. */
	private static volatile String lastBakedCatalogHash = null;

	/** true = essa entrada no servidor NÃO precisa de reloadResources() (nem da tela vermelha):
	 *  o receiver de SyncCatalogStatePayload já confirmou (joinSync do servidor + catálogo idêntico
	 *  ao já bakeado + sem textura forçada) e armou o skip em ClientJoinReloadState — aqui só
	 *  reconfirmamos que o hash que vamos deixar de re-bakear realmente já é o {@code lastBakedCatalogHash}.
	 *  Num /gc reload ao vivo (joinSync=false) o skip nunca é armado, então sempre devolve false. */
	private static boolean canSkipJoinReload(String catalogHash) {
		if (!com.f4xizzz.greatcosmetics.client.ClientJoinReloadState.shouldSkipJoinReload(System.currentTimeMillis())) return false;
		return catalogHash != null && catalogHash.equals(lastBakedCatalogHash);
	}

	private static void runAfterResourceReload(Runnable afterReload, String catalogHashToRecord) {
		if (pendingResourceReload == null) {
			debugLog("runAfterResourceReload: triggering a real reloadResources().");
			pendingResourceReload = MinecraftClient.getInstance().reloadResources()
					.whenComplete((v, ex) -> {
						pendingResourceReload = null;
						if (ex != null) {
							debugLog("runAfterResourceReload: reloadResources() finished with error — " + ex);
						} else {
							debugLog("runAfterResourceReload: reloadResources() completed.");
							if (catalogHashToRecord != null) {
								lastBakedCatalogHash = catalogHashToRecord;
								// Persiste em disco (por servidor) — sem isso, o lastBakedCatalogHash
								// zera a cada reabertura do launcher e a 1ª entrada SEMPRE recarrega.
								// Roda no whenComplete (ainda conectado), então currentServerKey() é válido.
								com.f4xizzz.greatcosmetics.client.ClientBakeState.record(
										com.f4xizzz.greatcosmetics.client.ClientServerIdentity.currentServerKey(),
										catalogHashToRecord);
							}
						}
					});
		} else {
			debugLog("runAfterResourceReload: chaining onto the reload already in progress.");
		}
		pendingResourceReload.thenRun(() -> MinecraftClient.getInstance().execute(afterReload));
	}

	/** Limpa e reconstrói o GeoModelRegistry INTEIRO (cosméticos normais + armadura convertida
	 *  juntos) — GeoModelRegistry é uma tabela ÚNICA compartilhada pelas duas categorias, então
	 *  qualquer clear()+rebuild precisa cobrir as DUAS de uma vez, senão a categoria que não foi
	 *  passada pro rebuild fica sem entrada nenhuma (limpa mas nunca reconstruída) até a PRÓXIMA
	 *  vez que ALGUÉM editar algo dela especificamente. Usa o estado ATUAL de
	 *  CosmeticsConfig.cosmeticsMap/ClientArmorCosmeticsCache.armorCosmetics — que já foram
	 *  atualizados pelo receiver ANTES de chamar isso, então sempre reflete os dados certos
	 *  independente de qual dos dois payloads (normal ou armadura) disparou o rebuild. */
	// Público (não mais private) — DevCosmeticsSubPage#discardChanges() também precisa chamar
	// isso, ver o comentário lá pro motivo (Descartar nunca passava pelo round-trip de rede que
	// normalmente aciona isso pra Salvar).
	public static void rebuildAllGeoModels() {
		debugLog("rebuildAllGeoModels: clearing GeoModelRegistry and rebuilding (" + CosmeticsConfig.cosmeticsMap.size()
				+ " normal cosmetics + " + com.f4xizzz.greatcosmetics.client.ClientArmorCosmeticsCache.armorCosmetics.size() + " armor pieces).");
		com.f4xizzz.greatcosmetics.geckolib.GeoModelRegistry.clear();
		registerGeoModels(CosmeticsConfig.cosmeticsMap);
		registerGeoModels(com.f4xizzz.greatcosmetics.client.ClientArmorCosmeticsCache.armorCosmetics);
	}

	private static void registerGeoModels(Map<String, CosmeticData> configMap) {
		ResourceManager rm = MinecraftClient.getInstance().getResourceManager();

		Map<String, Identifier> geoMap = new HashMap<>();
		for (Identifier resId : rm.findResources("geo", id -> id.getPath().endsWith(".geo.json")).keySet()) {
			String fileName = resId.getPath().substring(resId.getPath().lastIndexOf('/') + 1);
			geoMap.put(fileName.substring(0, fileName.length() - ".geo.json".length()), resId);
		}

		// "textures" inteiro, não só "textures/item" — mods organizam a textura de modelo GeckoLib
		// em pastas diferentes (ex: Cobblemon Armory usa "textures/armor/...", não "textures/item/...").
		// Só o nome do arquivo importa pra bater com geoModelId, então procurar em qualquer subpasta
		// não tem custo — só um pouco mais de arquivo escaneado.
		Map<String, Identifier> textureMap = new HashMap<>();
		for (Identifier resId : rm.findResources("textures", id -> id.getPath().endsWith(".png")).keySet()) {
			String fileName = resId.getPath().substring(resId.getPath().lastIndexOf('/') + 1);
			textureMap.put(fileName.substring(0, fileName.length() - ".png".length()), resId);
		}

		Map<String, Identifier> animMap = new HashMap<>();
		for (Identifier resId : rm.findResources("animations", id -> id.getPath().endsWith(".animation.json")).keySet()) {
			String fileName = resId.getPath().substring(resId.getPath().lastIndexOf('/') + 1);
			animMap.put(fileName.substring(0, fileName.length() - ".animation.json".length()), resId);
		}

		for (CosmeticData data : configMap.values()) {
			if (data.parts == null) continue;
			java.util.List<CosmeticData.CosmeticPart> geoParts = new java.util.ArrayList<>(data.parts);
			if (data.variants != null) for (CosmeticData.CosmeticVariant vv : data.variants) if (vv != null && vv.parts != null) geoParts.addAll(vv.parts);
			for (CosmeticData.CosmeticPart part : geoParts) {
				if (part.geoModelId == null || part.geoModelId.isBlank() || part.resolvedCmd <= 0) continue;

				String key = part.geoModelId;
				String baseName = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;

				Identifier geoId;
				Identifier texId = null;
				Identifier animId;

				if (part.useExactPath) {
					// Modo "Caminho Exato": key é o caminho relativo COMPLETO (ex: "sas/cigarro"),
					// buscado por igualdade exata em qualquer namespace — nada de casar só pelo nome.
					geoId = findExactResource(rm, "geo", "geo/" + key + ".geo.json");
					if (geoId != null) {
						String mirroredPath = geoId.getPath().replaceFirst("^geo/", "textures/").replaceFirst("\\.geo\\.json$", ".png");
						Identifier mirrored = Identifier.of(geoId.getNamespace(), mirroredPath);
						if (rm.getResource(mirrored).isPresent()) texId = mirrored;
					}
					if (texId == null) texId = findExactResource(rm, "textures", "textures/" + key + ".png");
					animId = findExactResource(rm, "animations", "animations/" + key + ".animation.json");
				} else {
					// Aceita tanto só o nome do arquivo ("brendans_hat") quanto o admin ter digitado
					// junto a pasta que viu dentro do jar ("armors/brendans_hat") — só o nome depois da
					// última "/" importa pra achar o arquivo, então tenta os dois antes de desistir.
					geoId = geoMap.containsKey(key) ? geoMap.get(key) : geoMap.get(baseName);

					// Textura: alguns mods têm DOIS arquivos com o MESMO nome — um ícone chapado de
					// inventário (ex: textures/item/brendans_hat.png) e a textura de verdade do modelo
					// 3D, numa subpasta diferente (ex: textures/item/armor/brendans_hat.png). Casar só
					// pelo nome do arquivo é ambíguo nesse caso e pode pegar a errada (dá exatamente
					// esse visual "quebrado", UV toda errada). Por isso tenta PRIMEIRO o caminho que
					// espelha a MESMA subpasta do geo (troca "geo/" por "textures/" e ".geo.json" por
					// ".png") — só cai pro nome solto se esse espelho não existir de verdade.
					if (geoId != null) {
						String mirroredPath = geoId.getPath().replaceFirst("^geo/", "textures/").replaceFirst("\\.geo\\.json$", ".png");
						Identifier mirrored = Identifier.of(geoId.getNamespace(), mirroredPath);
						if (rm.getResource(mirrored).isPresent()) texId = mirrored;
					}
					if (texId == null) {
						texId = textureMap.containsKey(key) ? textureMap.get(key) : textureMap.get(baseName);
					}
					animId = animMap.containsKey(key) ? animMap.get(key) : animMap.get(baseName);
				}

				if (geoId == null || texId == null) {
					System.err.println("[GreatCosmetics] WARNING: geoModelId '" + part.geoModelId + "' did not find geo/texture in the resourcepack "
							+ (part.useExactPath
									? "(caminho exato esperado: geo/" + key + ".geo.json + textures/" + key + ".png em algum namespace). "
									: "(esperado: geo/item/" + baseName + ".geo.json + textures/item/" + baseName + ".png em algum namespace). ")
							+ "This part will be invisible until the files exist.");
					debugLog("registerGeoModels: MISSING geo/texture for geoModelId='" + part.geoModelId + "' (resolvedCmd=" + part.resolvedCmd + ").");
					continue;
				}
				debugLog("registerGeoModels: registered geoModelId='" + part.geoModelId + "' -> geo=" + geoId + " tex=" + texId + " anim=" + animId + " cmd=" + part.resolvedCmd);
				com.f4xizzz.greatcosmetics.geckolib.GeoModelRegistry.register(part.resolvedCmd, geoId, texId, animId);
			}
		}
	}
}