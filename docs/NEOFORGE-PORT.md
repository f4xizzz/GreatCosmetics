# GreatCosmetics — port pra NeoForge (multiloader Architectury)

> Branch: `neoforge-multiloader`. O `master` continua sendo o mod Fabric 1.21.1 shippável
> (`build.gradle.legacy` + `src/` raiz). Nada aqui é testável ao vivo neste ambiente — cada fase
> entrega "compila + builda", o teste é numa instância NeoForge real.

## Arquitetura (decidida com o usuário 2026-09-05)

- **Multiloader Architectury**: `common/` (código sem loader, mappings Mojmap) + `fabric/` + `neoforge/`.
- **GreatCosmetics primeiro**, BattleHUB depois (mesmo playbook).
- Feasibility gate PASSOU: `com.cobblemon:neoforge:1.7.3+1.21.1` existe (HTTP 200 no maven do Cobblemon).

## Dependências (o que sobrevive vs sai)

| Dep | Fabric hoje | NeoForge | Nota |
|---|---|---|---|
| Cobblemon | `com.cobblemon:fabric:1.7.3+1.21.1` | `com.cobblemon:neoforge:1.7.3+1.21.1` | ✅ existe |
| Architectury API | `architectury-fabric:13.0.8` | `architectury-neoforge:13.0.8` + `architectury:13.0.8` (common) | |
| GeckoLib | jar local `geckolib-fabric-1.21.1-4.8.4` | `software.bernie.geckolib:geckolib-neoforge-1.21.1:4.8.4` (maven cloudsmith) | |
| Kotlin runtime | `fabric-language-kotlin` | `thedarkcolour:kotlinforforge-neoforge` | Cobblemon é Kotlin |
| LuckPerms | `net.luckperms:api:5.5` (compileOnly) | igual | loader-agnóstico |
| Adventure | `adventure-text-minimessage` + `adventure-platform-fabric` | só `minimessage` + serializers gson/legacy, **shaded** | `adventure-platform-fabric` NÃO é usado em código |
| GooeyLibs / api-repack | `include` no build | ❌ sai | **zero referência em código** |
| Impactor economy | `implementation` | ❌ sai | zero referência em código |
| mega_showdown | dev-only | ❌ sai | zero import |
| SQLite / MySQL JDBC | `include` (JiJ) | JiJ via `jarJar` do NeoForge | |

## Toolchain

- Gradle 8.12.1 (wrapper), Architectury Loom `1.9-SNAPSHOT` (Gradle 8.11+), Architectury Plugin `3.4-SNAPSHOT`, `com.gradleup.shadow` 8.3.0.
- Mappings: `loom.officialMojangMappings()` (Mojmap) nos 3 subprojetos.
- `loom.platform` fica no `gradle.properties` de CADA subprojeto (`fabric/`, `neoforge/`) — sem ele o loom não registra a config `neoForge`. `common/` NÃO tem `loom.platform` (o valor `common` não é enum válido).
- NeoForge `21.1.133` (chute — ajustar pro que o Cobblemon-neoforge foi buildado contra).

## Fases

| # | Escopo | Estado |
|---|---|---|
| **0** | Esqueleto Architectury + stubs + provar que os 3 subprojetos buildam e as deps NeoForge resolvem | ✅ FEITO |
| **1** | Mod inteiro Yarn→Mojmap, compilando + buildando no `fabric/` (NÃO split ainda) | ✅ FEITO |
| **2** | Split `fabric/` → `common/`: TODO o mod (config, security, database, util, geckolib, client/GUI, os 21 mixins, network, manager, assets, AW, mixins.json) mora em `common/`. Só `GreatCosmetics`/`GreatCosmeticsClient` (entrypoints), `command/CosmeticsCommand` e `fabric/KeybindManager` ficaram no `fabric/`. Os 3 subprojetos compilam + buildam. | ✅ FEITO |
| **3** | Cola de loader — networking via `dev.architectury.networking.NetworkManager`, eventos via `dev.architectury.event.events.*`, `DeferredRegister`, `KeyMappingRegistry`, `CommandRegistrationEvent`. Entrypoints Fabric viraram stubs; lógica toda em `common/` (`GreatCosmeticsServer`/`GreatCosmeticsClientInit`). NeoForge `@Mod` → `GreatCosmeticsServer.init()` + guard client. Os 3 subprojetos buildam. | ✅ FEITO (commit `00691c1`) |
| **4** | `ModelLoadingPlugin` → **mixin comum** `ModelManagerMixin` (`@Inject` no RETURN de `ModelManager#loadBlockModels`, pós-processa o `Map<ResourceLocation, BlockModel>` via `GcModelOverrides.injectInto`). Funciona igual nos 2 loaders; o `ModelLoadingPlugin` do Fabric foi REMOVIDO. | ✅ FEITO |
| 5 | ProGuard + StrV + manifesto de integridade + assinatura por plataforma + JiJ das deps no NeoForge (`jarJar` — adventure, sqlite/mysql) | **PRÓXIMA** |
| 6 | BattleHUB, mesmo playbook | pendente |

## Phase 2 — o que foi feito (2026-09-06)

**Ponte de código comum → entrypoint** (evita `common` referenciar o entrypoint Fabric):
- `platform/GcNet` (common) — `bindServer`/`bindClient` (ligados no topo de `onInitialize`/
  `onInitializeClient`) + `toPlayer`/`toServer`. Todo `ServerPlayNetworking.send`/
  `ClientPlayNetworking.send` do código leaf virou `GcNet.toPlayer`/`toServer`. **REGISTRO de
  payload + RECEIVERS continuam 100% no entrypoint Fabric** — Phase 3 migra.
- `GreatCosmeticsCommon.debugMode`/`debugLog` — era `GreatCosmetics.isDebugMode`/`debugLog`.
- `GreatCosmeticsCommon.clientGeoRebuild` (Runnable) — hook pro `GreatCosmeticsClient::rebuildAllGeoModels`.
- `GcServer` (common) — `isRealOperator`, `sendOpMessage`, `licenseBlocked`, `checkPermission`,
  `playCustomSound`, `playCosmeticSound`. `GreatCosmetics` mantém delegadores (mesma assinatura)
  pros ~40 receivers dele.
- `CosmeticsConfig.getCosmeticById`/`getCosmeticData`/`invalidateCosmeticIndex` — eram de `GreatCosmetics`.
  `FabricLoader.getEnvironmentType()` → `dev.architectury.platform.Platform.getEnv()`.
- `LuckPermsTagManager.onGroupDataRecalculated` (Consumer<ServerPlayer>) — hook; o entrypoint liga
  `validateEquippedGroupTag`+`autoEquipCurrentGroupTag`+`syncPlayerTags` (essas 3 continuam no
  entrypoint, Phase 3).
- `ClientSkinCache` — dados ficam em common; `registerNetworkReceivers()` virou um receiver inline
  em `GreatCosmeticsClient` + `ClientSkinCache.apply(...)`.
- `FabricLoader.getConfigDir()` → `Platform.getConfigFolder()` (21 arquivos, já feito antes).

**Build (common/build.gradle):** `modCompileOnly com.cobblemon:mod` + `geckolib-common-1.21.1`
(variantes COMUNS — existem), `compileOnly` kotlin-stdlib 2.0.21 (o POM do `com.cobblemon:mod` é
vazio, não traz transitivo), `compileOnly` adventure/luckperms/jdbc. `loom.accessWidenerPath` no
common; fabric e neoforge apontam pra `project(':common').loom.accessWidenerPath`.

**NeoForge:** `neoforge.mods.toml` ganhou `[[mixins]]` + `[[accessTransformers]]`.
`META-INF/accesstransformer.cfg` escrito **à mão** (7 linhas, espelha o AW) — o architectury-loom
1.9 NÃO converte AW→AT sozinho no lado NeoForge. Qualquer linha nova no AW tem que ser espelhada lá.

## Phase 3 — o que foi feito (2026-09-06, commit `00691c1`)

- **`GreatCosmeticsServer`** (common, ex `GreatCosmetics.java`): `PayloadTypeRegistry`/
  `ServerPlayNetworking` → `NetworkManager` (helper `s2c()` registra o tipo S2C **só no servidor
  dedicado** — no client o `registerReceiver(Side.S2C)` já registra, e o `PayloadTypeRegistry` do
  Fabric reclama de tipo duplicado). `ServerLifecycleEvents`→`LifecycleEvent`,
  `ServerTickEvents.END_SERVER_TICK`→`TickEvent.SERVER_POST`, `ServerPlayConnectionEvents.JOIN/
  DISCONNECT`→`PlayerEvent.PLAYER_JOIN/PLAYER_QUIT` (só dá `ServerPlayer` — `handler.player`→o
  param, `handler.disconnect`→`player.connection.disconnect`), `UseBlockCallback`→
  `InteractionEvent.RIGHT_CLICK_BLOCK` (`InteractionResult.FAIL`→`EventResult.interruptFalse()`).
  `context.player()`→`(ServerPlayer) context.getPlayer()` (helper `sp()`), `context.server()`→
  `sp(context).getServer()`, `context.server().execute`→`context.queue`.
- **`GreatCosmeticsClientInit`** (common, ex `GreatCosmeticsClient.java`): idem no lado client —
  `NetworkManager.registerReceiver(Side.S2C, ...)`, `ClientTickEvent.CLIENT_POST`,
  `ClientTooltipEvent.ITEM` (ordem dos params muda: `(stack, lines, ctx, flag)`),
  `ClientPlayerEvent.CLIENT_PLAYER_QUIT`, `ClientCommandRegistrationEvent`
  (`.sendFeedback(x)`→`.arch$sendSuccess(() -> x, false)`).
- **`CosmeticsCommand`** → common. `CommandRegistrationCallback`→`CommandRegistrationEvent`
  (mesma aridade de params).
- **`KeybindManager`** → common. `KeyBindingHelper.registerKeyBinding(km)` → `new KeyMapping(...)`
  + `KeyMappingRegistry.register(km)`; `ClientTickEvents`→`ClientTickEvent.CLIENT_POST`.
- **`GreatCosmeticsItems`** → Architectury `DeferredRegister<Item>` (NeoForge não aceita
  `Registry.register` direto). `GEO_DISPLAY` virou `RegistrySupplier<Item>` → usos = `.get()`.
- **`GcModelOverrides`** (common): lógica de override de model (carved_pumpkin + `icon_*` +
  normalização de textura) extraída do `ModelLoadingPlugin`. Fabric pluga via `ModelLoadingPlugin`
  no `fabric/GreatCosmeticsClient`; NeoForge = Phase 4.
- **`GcNet`**: delega direto pro `NetworkManager` (sem `bind`).
- `Platform.getEnv()`/`net.fabricmc.api.EnvType` → `Platform.getEnvironment()`/
  `dev.architectury.utils.Env` — `EnvType` **não existe no runtime NeoForge** (não vem em
  nenhuma dep). Consertado no `GreatCosmeticsServer.s2c()` e no `CosmeticsConfig.getCosmeticById`
  (esse era um bug latente da Phase 2).

**Entrypoints:** `fabric/` só tem `GreatCosmetics` + `GreatCosmeticsClient` (finos, delegam).
`neoforge/` tem `GreatCosmeticsNeoForge` (`@Mod` → `GreatCosmeticsServer.init()` + guard
`FMLEnvironment.dist == CLIENT` → `GreatCosmeticsNeoForgeClient.init()`) — tudo no construtor do
mod; o Architectury bufferiza os `registerReceiver`/`DeferredRegister`/`KeyMappingRegistry` e
reproduz nos eventos de registro certos.

**Riscos não testados (Phase 3):** timing/threading de `PLAYER_JOIN` vs `ServerPlayConnectionEvents.JOIN`;
`registerReceiver(Side.S2C)` no client depois do `s2c()` no servidor dedicado (a lógica de guard
por `Env` evita o duplicado, mas não deu pra testar ao vivo); ordem dos eventos Architectury vs
Fabric. Testar nos DOIS loaders.

## Phase 4 — feito (2026-09-06)

`GcModelOverrides.injectInto(Map<ResourceLocation, BlockModel>, ResourceManager)` (common): (1)
adiciona models sintéticos `greatcosmetics:icon_<name>` (`item/generated` + layer0 =
`greatcosmetics:icons/<name>`), (2) pega o `BlockModel` do `minecraft:item/carved_pumpkin` e
`getOverrides().addAll(...)` os overrides de CMD gerados (mesma lógica string→`BlockModel.fromString`
de antes). `getOverrides()` devolve um `ArrayList` mutável no 1.21.1 (deserializer usa
`Lists.newArrayList`), então o `addAll` funciona.

`ModelManagerMixin` (common, `client` na mixins.json): `@Inject(method="loadBlockModels",
at=@At("RETURN"), cancellable=true)` — `cir.setReturnValue(cir.getReturnValue().thenApply(map ->
{ copia mutável + injectInto; return }))`. `loadBlockModels` = `class_1092;method_45881` no
intermediary (refmap gerado OK). `@At("RETURN")` casa 1x só (método tem 1 `areturn`).

O `ModelLoadingPlugin.register(...)` foi **removido** do `fabric/GreatCosmeticsClient` (o mixin
cobre os dois). `fabric/GreatCosmeticsClient` e `neoforge/GreatCosmeticsNeoForgeClient` agora só
chamam `GreatCosmeticsClientInit.initClient()`.

**Risco não testado:** o `thenApply` roda numa thread de worker (igual o `modifyModelOnLoad` do
Fabric já rodava); `injectInto` lê o `ResourceManager` off-thread (o código do Fabric já fazia
isso). Perda vs Fabric: a normalização de textura do ramo `resolveModel` pra models
`greatcosmetics:<path>` com caminho de textura "solto" (raro — o arquivo do admin normalmente já
tem o caminho certo). Testar ícone chapado + model 3D + `/gc reload` nos 2 loaders.

**Ainda NÃO roda em runtime no NeoForge** sem a Phase 5 (sem JiJ de adventure/jdbc, qualquer
classe de config quebra por `NoClassDefFoundError` se fosse
tocada. O jar Fabric (`greatcosmetics-fabric-1.1.0.jar`, 17 MB, 10 JiJ) é o único shippável.

## Yarn→Mojmap — FEITO via `./gradlew migrateMappings`

O loom (`net.fabricmc.fabric-loom-remap` 1.17.17 no master) tem a task `migrateMappings`. Rodada
num worktree do master:

```
./gradlew migrateMappings --mappings "net.minecraft:mappings:1.21.1" --output <dir>
./gradlew migrateClassTweakerMappings --mappings "net.minecraft:mappings:1.21.1"   # o accesswidener
```

Converteu os 128 arquivos + o accesswidener MECANICAMENTE, sem NENHUM warning de "não consegui
remapear". Inclusive remapeou os nomes dos métodos-alvo nos `@Inject`/`@At` dos 21 mixins
(`renderHealthBar`→`renderHearts`, descriptors, etc.). O `:fabric:build` completo passou de
primeira (compile + Mixin AP + refmap + accesswidener válido + remapJar).

**Se precisar rodar de novo** (ex: pegar mudança nova do master): worktree do master, mesmos
comandos. O prefixo `net.minecraft:mappings:` é o que faz o loom entender "Mojang mappings".

Nomes comuns: `DrawContext`→`GuiGraphics`, `PlayerEntity`→`Player`, `Identifier`→`ResourceLocation`,
`Text`→`Component`, `MathHelper`→`Mth`, `MinecraftClient`→`Minecraft`, `InGameHud`→`Gui`,
`SplashOverlay`→`LoadingOverlay`, `World`→`Level`, `NbtCompound`→`CompoundTag`,
`SimpleInventory`→`SimpleContainer`, `RenderLayer`→`RenderType`, `RenderPhase`→`RenderStateShard`.
