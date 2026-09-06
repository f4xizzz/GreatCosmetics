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
| 3 | Cola de loader: networking (`PayloadTypeRegistry`→`RegisterPayloadHandlersEvent`, ou `dev.architectury.networking.NetworkManager`), eventos (`ClientTickEvents` etc → `dev.architectury.event.events.*`), entrypoints, keybinds, comandos — atrás das APIs do Architectury em `common/` com impls por plataforma | pendente |
| 4 | `ModelLoadingPlugin` (injeção de CustomModelData no `carved_pumpkin`) → `ModelEvent.ModifyBakingResult` por plataforma | pendente |
| 5 | ProGuard + StrV + manifesto de integridade + assinatura por plataforma + JiJ das deps no NeoForge (`jarJar` — adventure, sqlite/mysql) | pendente |
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

**Ainda NÃO funciona em runtime no NeoForge** (esperado — é Phase 3): o `@Mod` só chama
`GreatCosmeticsCommon.init()` (loga). Sem registro de payload/receiver/eventos, e sem JiJ das deps
(adventure/jdbc) — então qualquer classe de config quebraria por `NoClassDefFoundError` se fosse
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
