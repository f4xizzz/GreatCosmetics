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
| **0** | Esqueleto Architectury + stubs + provar que os 3 subprojetos buildam e as deps NeoForge resolvem | **EM ANDAMENTO** |
| 1 | Mover código sem-loader de `src/` → `common/` (remap Yarn→Mojmap). Abstrair paths de config. `fabric/` volta a compilar. | pendente |
| 2 | Cola de loader: networking (`PayloadTypeRegistry`→`RegisterPayloadHandlersEvent`), eventos (`ClientTickEvents` etc → `NeoForge.EVENT_BUS`), entrypoints, keybinds, comandos — atrás das APIs do Architectury em `common/` com impls por plataforma | pendente |
| 3 | Os 21 mixins → `common/` (Mojmap). Refmap. AccessWidener→AT no lado NeoForge. | pendente |
| 4 | `ModelLoadingPlugin` (injeção de CustomModelData no `carved_pumpkin`) → `ModelEvent.ModifyBakingResult` por plataforma | pendente |
| 5 | ProGuard + StrV + manifesto de integridade + assinatura por plataforma | pendente |
| 6 | BattleHUB, mesmo playbook | pendente |

## Notas Yarn→Mojmap (referência rápida)

`DrawContext`→`GuiGraphics`, `drawGuiTexture`→`GuiGraphics#blitSprite`, `PlayerEntity`→`Player`,
`ServerPlayerEntity`→`ServerPlayer`, `ItemStack` igual, `Identifier`→`ResourceLocation`,
`Text`→`Component`, `NbtCompound`→`CompoundTag`, `SimpleInventory`→`SimpleContainer`,
`getScaledWindowWidth()`→`guiWidth()`, `MinecraftClient`→`Minecraft`, `World`→`Level`,
`SplashOverlay`→`LoadingOverlay`, `InGameHud`→`Gui`, `renderHealthBar` etc — nomes Mojmap
diferentes, conferir com javap no jar `neoforge` (`.gradle/loom-cache/.../minecraft-...-neoforge-*.jar`).
