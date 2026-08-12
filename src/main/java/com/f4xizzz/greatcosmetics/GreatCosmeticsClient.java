package com.f4xizzz.greatcosmetics;

import com.f4xizzz.greatcosmetics.client.ClientSkinCache;
import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import com.f4xizzz.greatcosmetics.config.CosmeticData;
import com.f4xizzz.greatcosmetics.config.CosmeticsConfig;
import com.f4xizzz.greatcosmetics.config.MainConfig;
import com.f4xizzz.greatcosmetics.config.SkinConfigManager;
import com.f4xizzz.greatcosmetics.manager.ThemeManager;
import com.f4xizzz.greatcosmetics.network.OpenWardrobePayload;
import com.f4xizzz.greatcosmetics.network.SyncCosmeticsPayload;
import com.f4xizzz.greatcosmetics.util.AutoCMDManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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

	private static int idleTickCounter = 0;

	@Override
	public void onInitializeClient() {

		// Carrega a preferência salva do jogador
		ThemeManager.load();
		com.f4xizzz.greatcosmetics.client.ClientFavoriteCosmetics.load();

		// Registra os Comandos Client-Side
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommandManager.literal("lightmode").executes(context -> {
				if (!ThemeManager.isLightMode) {
					ThemeManager.isLightMode = true;
					ThemeManager.save();
					context.getSource().sendFeedback(Text.literal("§e[Tema] Modo Light ativado! Recarregando texturas..."));
					MinecraftClient.getInstance().reloadResources();
				} else {
					context.getSource().sendFeedback(Text.literal("§cO Modo Light já está ativado."));
				}
				return 1;
			}));

			dispatcher.register(ClientCommandManager.literal("darkmode").executes(context -> {
				if (ThemeManager.isLightMode) {
					ThemeManager.isLightMode = false;
					ThemeManager.save();
					context.getSource().sendFeedback(Text.literal("§8[Tema] Modo Dark ativado! Recarregando texturas..."));
					MinecraftClient.getInstance().reloadResources();
				} else {
					context.getSource().sendFeedback(Text.literal("§cO Modo Dark já está ativado."));
				}
				return 1;
			}));
		});

		MainConfig.loadConfig();
		CosmeticsConfig.loadConfig();
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
				context.client().setScreen(new com.f4xizzz.greatcosmetics.client.gui.BackpackSelectorScreen(payload.backpackIds()));
			});
		});

		// ==========================================
		// RECEBE O ESTADO DO MODO DEBUG (ligado/desligado via /gc debug no servidor)
		// ==========================================
		ClientPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.DebugModePayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				// no-op no core — DEBUG_MODE de animação de NPC vive só no EasyNPCobblemonIntegration
			});
		});

		// ==========================================
		// RECEBE O CATÁLOGO DE SKINS DE POKÉMON (join + /gc reload) — ver SyncSkinCatalogPayload
		// ==========================================
		ClientPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.SyncSkinCatalogPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
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
				com.f4xizzz.greatcosmetics.client.ClientPermissionCache.isOperator = payload.isOperator();
				com.f4xizzz.greatcosmetics.client.ClientPermissionCache.hasGcDev = payload.hasGcDev();
				com.f4xizzz.greatcosmetics.client.ClientPermissionCache.hasGcPermDevmode = payload.hasGcPermDevmode();
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.GrantCosmeticPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				com.f4xizzz.greatcosmetics.client.ClientUnlockedCosmetics.unlockedIds.add(payload.cosmeticId());
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.SyncMainConfigPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				com.f4xizzz.greatcosmetics.client.ClientMainConfigCache.setConfig(payload.configJson());
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.SyncNameTagPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				com.f4xizzz.greatcosmetics.client.ClientNameTagCache.prefix = payload.prefix();
				com.f4xizzz.greatcosmetics.client.ClientNameTagCache.suffix = payload.suffix();
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(OpenWardrobePayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				com.f4xizzz.greatcosmetics.client.ClientUnlockedCosmetics.clear();
				com.f4xizzz.greatcosmetics.client.ClientUnlockedCosmetics.hasAllUnlocked = payload.hasAllUnlocked();
				com.f4xizzz.greatcosmetics.client.ClientUnlockedCosmetics.unlockedIds.addAll(payload.unlockedIds());
				context.client().setScreen(new com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen(payload.hasBackground()));
			});
		});

		// Mochila paginada (ver CosmeticData#backpackPages/BackpackManager#openSpecificBackpackPage)
		// — servidor manda ISSO antes de abrir/trocar a tela do baú, nunca depois (ver comentário em
		// ClientBackpackState#pendingCosmeticId pro motivo da ordem importar).
		ClientPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.BackpackPageInfoPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				com.f4xizzz.greatcosmetics.client.ClientBackpackState.pendingCosmeticId = payload.cosmeticId();
				com.f4xizzz.greatcosmetics.client.ClientBackpackState.pendingPage = payload.page();
				com.f4xizzz.greatcosmetics.client.ClientBackpackState.pendingTotalPages = payload.totalPages();
				com.f4xizzz.greatcosmetics.client.ClientBackpackState.pendingSetAtMillis = System.currentTimeMillis();

				// Atualiza current* JÁ AQUI também (não só via AFTER_INIT) — trocar de página não
				// fecha/reabre mais a tela (ver BackpackManager#openSpecificBackpackPage), então pra
				// esse caso NENHUMA tela nova vai abrir e o AFTER_INIT abaixo nunca vai rodar de novo
				// pra consumir o pending; o overlay (que já está registrado na tela que continua
				// aberta) precisa enxergar a página nova direto daqui. Pra abertura de verdade (tela
				// nova), AFTER_INIT ainda roda depois e só re-seta os MESMOS valores — redundante,
				// mas inofensivo.
				if (payload.cosmeticId().equals(com.f4xizzz.greatcosmetics.client.ClientBackpackState.currentCosmeticId)) {
					com.f4xizzz.greatcosmetics.client.ClientBackpackState.currentPage = payload.page();
					com.f4xizzz.greatcosmetics.client.ClientBackpackState.totalPages = payload.totalPages();
				}
			});
		});

		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof net.minecraft.client.gui.screen.ingame.GenericContainerScreen containerScreen)) return;
			if (com.f4xizzz.greatcosmetics.client.ClientBackpackState.pendingCosmeticId == null) return;

			// QUALQUER GenericContainerScreen conta pra esse "instanceof" — inclusive baús de
			// verdade e telas de OUTROS mods/plugins que usam o mesmo handler genérico (ex: um menu
			// tipo /flan, relatado acontecendo de verdade). Sem essa checagem, essa tela sem nenhuma
			// relação com a mochila herdava as setas/texto de página por engano. O servidor sempre
			// manda BackpackPageInfoPayload IMEDIATAMENTE antes de abrir a tela (mesmo tick) — se já
			// se passou mais que uma folga generosa de rede, esse pending é de uma tentativa antiga
			// que nunca foi consumida pela mochila de verdade.
			//
			// (Uma checagem extra por contagem de slots — rows*9+36 — chegou a existir aqui também,
			// mas ela dependia de resolver o CosmeticData pelo id no client bem nesse instante do
			// AFTER_INIT, e acabava rejeitando mochilas paginadas DE VERDADE (o botão/texto de página
			// simplesmente não aparecia), então foi removida — a checagem de idade sozinha já cobre
			// o caso real relatado do /flan.)
			long pendingAgeMs = System.currentTimeMillis() - com.f4xizzz.greatcosmetics.client.ClientBackpackState.pendingSetAtMillis;
			if (pendingAgeMs > 3000) {
				com.f4xizzz.greatcosmetics.client.ClientBackpackState.pendingCosmeticId = null;
				return;
			}

			com.f4xizzz.greatcosmetics.client.ClientBackpackState.currentCosmeticId = com.f4xizzz.greatcosmetics.client.ClientBackpackState.pendingCosmeticId;
			com.f4xizzz.greatcosmetics.client.ClientBackpackState.currentPage = com.f4xizzz.greatcosmetics.client.ClientBackpackState.pendingPage;
			com.f4xizzz.greatcosmetics.client.ClientBackpackState.totalPages = com.f4xizzz.greatcosmetics.client.ClientBackpackState.pendingTotalPages;
			// Consome — sem isso, o PRÓXIMO baú de verdade (sem relação nenhuma com mochila) que o
			// jogador abrisse depois herdaria esse cosmeticId por engano (ver
			// ClientBackpackState#pendingCosmeticId).
			com.f4xizzz.greatcosmetics.client.ClientBackpackState.pendingCosmeticId = null;

			if (com.f4xizzz.greatcosmetics.client.ClientBackpackState.totalPages > 1) {
				net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((s, drawContext, mouseX, mouseY, tickDelta) ->
						com.f4xizzz.greatcosmetics.client.gui.BackpackPageOverlay.render(drawContext, containerScreen));
				net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen).register((s, mouseX, mouseY, button) ->
						com.f4xizzz.greatcosmetics.client.gui.BackpackPageOverlay.handleClick(mouseX, mouseY, button));
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.SyncPlayerCosmeticsPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
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
				com.f4xizzz.greatcosmetics.client.ClientTagsCache.setAllTags(payload.tagsMap());
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.SyncPlayerTagsPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
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
			});
		});

		// ==========================================
		// ARMADURAS CONVERTIDAS EM COSMÉTICO (ver ArmorCosmeticsConfig)
		// ==========================================
		ClientPlayNetworking.registerGlobalReceiver(com.f4xizzz.greatcosmetics.network.SyncArmorCosmeticsPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
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
				if (payload.allowResourceReload() && MinecraftClient.getInstance().world != null) {
					runAfterResourceReload(GreatCosmeticsClient::rebuildAllGeoModels);
				} else {
					rebuildAllGeoModels();
				}
			});
		});

		// ==========================================
		// SINCRONIZAÇÃO DE COSMÉTICOS E MODELS
		// ==========================================
		ClientPlayNetworking.registerGlobalReceiver(SyncCosmeticsPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
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
							System.err.println("[GreatCosmetics] AVISO: cosmético '" + id + "' — iconKey '" + iconKey
									+ "' não achou textures/icons/" + iconKey + ".png (namespace 'greatcosmetics') nem um "
									+ "models/" + id + ".json. Ícones encontrados no resourcepack: " + iconMap.keySet()
									+ ". Esse cosmético vai ficar com um visual quebrado/genérico até um dos dois existir.");
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

					for (CosmeticData.CosmeticPart part : data.parts) {
						if (part.customModelData_or_ID != null && part.resolvedCmd > 0) {
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
					runAfterResourceReload(rebuildGeoModels);
				} else {
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
								CosmeticData data = CosmeticsConfig.cosmeticsMap.get(id);
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
					lines.add(Text.literal("§8§m                                     "));
					lines.add(Text.literal("§e[Scanner] §fID Atual: §c" + currentCmd));
					lines.add(Text.literal("§8§m                                     "));
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

						try {
							JsonUnbakedModel dummyModel = JsonUnbakedModel.deserialize(overridesJson.toString());
							jsonModel.getOverrides().addAll(dummyModel.getOverrides());
						} catch (Exception e) {
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

	private static void runAfterResourceReload(Runnable afterReload) {
		if (pendingResourceReload == null) {
			pendingResourceReload = MinecraftClient.getInstance().reloadResources()
					.whenComplete((v, ex) -> pendingResourceReload = null);
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
	private static void rebuildAllGeoModels() {
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
			for (CosmeticData.CosmeticPart part : data.parts) {
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
					System.err.println("[GreatCosmetics] AVISO: geoModelId '" + part.geoModelId + "' não achou geo/textura no resourcepack "
							+ (part.useExactPath
									? "(caminho exato esperado: geo/" + key + ".geo.json + textures/" + key + ".png em algum namespace). "
									: "(esperado: geo/item/" + baseName + ".geo.json + textures/item/" + baseName + ".png em algum namespace). ")
							+ "Essa parte vai ficar invisível até os arquivos existirem.");
					continue;
				}
				com.f4xizzz.greatcosmetics.geckolib.GeoModelRegistry.register(part.resolvedCmd, geoId, texId, animId);
			}
		}
	}
}