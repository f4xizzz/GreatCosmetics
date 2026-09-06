package com.f4xizzz.greatcosmetics.client.gui.pages;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.storage.ClientParty;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget;
import com.f4xizzz.greatcosmetics.client.ClientSkinCache;
import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import com.f4xizzz.greatcosmetics.config.PokemonSkin;
import com.f4xizzz.greatcosmetics.config.SkinConfigManager;
import com.f4xizzz.greatcosmetics.network.ClearPokemonSkinsPayload;
import com.f4xizzz.greatcosmetics.network.EquipPokemonSkinPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class PartyPage extends WardrobePage {

    public static PartyPage INSTANCE;

    // Variáveis de controle de área para os cliques
    private int lastListX, lastListY, lastListW, lastListH;
    private int pX, pY, pW, pH;

    // NOVO: Temporizador de atualização da página
    private long pendingRefreshTime = 0;

    public static boolean waitingForPcToOpen = false;
    public static boolean reopenAfterPC = false;

    private final Pokemon[] partyCache = new Pokemon[6];
    private final ModelWidget[] slotWidgets = new ModelWidget[6];
    private final PokemonEntity[] entityCache = new PokemonEntity[6];

    private int selectedSlot = 0;
    private boolean isLoaded = false;
    private boolean checkedDevState = false;
    private float lastScale = -1f;

    // --- PREVIEW DE SKIN NÃO DESBLOQUEADA ---
    // Enquanto ativo, o Pokémon da party some do lugar do player e a espécie+skin sendo
    // pré-visualizada toma o lugar dele — 100% client-side, não desbloqueia nada de verdade.
    private PokemonSkin previewingSkin = null;
    private PokemonEntity previewEntity = null;
    private int previewFormIndex = 0;
    private boolean previewShiny = false;
    private int previewPoseIndex = 0;

    // Poses "genéricas" que fazem sentido tentar pra qualquer espécie — as que dependem de
    // habilidade específica (voar, nadar) simplesmente não mudam nada visualmente se o modelo da
    // espécie não tiver aquela animação definida, sem quebrar nada. "Batalha" não é um PoseType
    // (o Cobblemon não tem um valor pra isso) — é a MESMA pose STAND, só que com isBattling()
    // fingindo true (ver applyPreviewPose), que é o que o modelo usa pra escolher a idle de
    // batalha em vez da idle normal.
    private static final PoseType[] PREVIEW_POSES = {
            PoseType.STAND, PoseType.WALK, PoseType.SLEEP, PoseType.PROFILE, PoseType.PORTRAIT,
            PoseType.HOVER, PoseType.FLY, PoseType.SWIM, PoseType.FLOAT, PoseType.GLIDE
    };
    private static final int PREVIEW_BATTLE_INDEX = PREVIEW_POSES.length; // "posição extra" no ciclo
    private static final java.util.UUID PREVIEW_BATTLE_ID = java.util.UUID.randomUUID();

    // --- VARIÁVEIS DO SISTEMA DE SKINS (GUI) ---
    private final List<PokemonSkin> currentAvailableSkins = new ArrayList<>();
    private final Map<String, ModelWidget> skinWidgets = new HashMap<>();
    private double scrollY = 0;
    private double skinListMaxScrollY = 0;
    private boolean isDraggingSkinScrollbar = false;

    private static final int SKIN_ITEM_HEIGHT = 45;
    private static final int SKIN_SPACING_Y = 5;
    private static final int SKIN_HEADER_HEIGHT = 18;

    // Espaço reservado SÓ pra barra de scroll, fora da largura de conteúdo das linhas (ícone/nome/
    // status/cadeado) — antes a barra desenhava em cima dos últimos 4px da própria linha (mesma
    // largura do conteúdo), entrando visualmente por cima do ícone 3D do Pokémon/cadeado quando a
    // lista era mais estreita. preferredPanelSize() abaixo pede um painel um pouco mais largo só
    // nessa aba pra compensar esse espaço, sem espremer o conteúdo.
    private static final int SCROLLBAR_RESERVED = 10;

    /** Linha da lista de skins — ou um cabeçalho de grupo, ou uma skin de verdade. A lista
     *  original (currentAvailableSkins) é flat e assume altura uniforme; essa aqui já vem com o
     *  yOffset acumulado calculado (headers e skins têm alturas diferentes), pra render/clique/
     *  scroll não precisarem recalcular posição nenhum na mão. */
    private static class SkinDisplayRow {
        final boolean isHeader;
        final String headerLabel;
        final PokemonSkin skin;
        int yOffset;
        int height;

        SkinDisplayRow(String headerLabel) {
            this.isHeader = true;
            this.headerLabel = headerLabel;
            this.skin = null;
            this.height = SKIN_HEADER_HEIGHT;
        }

        SkinDisplayRow(PokemonSkin skin) {
            this.isHeader = false;
            this.headerLabel = null;
            this.skin = skin;
            this.height = SKIN_ITEM_HEIGHT;
        }
    }

    private final List<SkinDisplayRow> skinDisplayRows = new ArrayList<>();

    // Posições dos Botões de Remoção
    private int btnClearCurX, btnClearCurY, btnClearW, btnClearH = 20;
    private int btnClearAllX;

    public PartyPage(Wardrobe3DScreen parent) {
        super(parent);
        INSTANCE = this;
    }

    @Override
    public int[] preferredPanelSize() {
        // +SCROLLBAR_RESERVED de largura só pra sobrar espaço dedicado pra barra de scroll da
        // lista de skins (ver SCROLLBAR_RESERVED) sem espremer ícone/texto das linhas. Altura igual
        // ao padrão das outras abas (260) — só a largura precisa do ajuste aqui.
        return new int[]{ 180 + SCROLLBAR_RESERVED, 260 };
    }

    public static PokemonEntity getCurrentPokemonEntity() {
        if (INSTANCE != null && INSTANCE.previewingSkin != null && INSTANCE.previewEntity != null) {
            return INSTANCE.previewEntity;
        }
        if (INSTANCE != null && INSTANCE.isLoaded) {
            return INSTANCE.entityCache[INSTANCE.selectedSlot];
        }
        return null;
    }

    private void startSkinPreview(PokemonSkin skin) {
        this.previewingSkin = skin;
        this.previewFormIndex = 0; // 0 = forma base da skin, sem nenhum altForm
        this.previewShiny = false;
        this.previewPoseIndex = 0;
        rebuildPreviewEntity();
    }

    public void cyclePreviewPose() {
        if (previewingSkin == null) return;
        previewPoseIndex = (previewPoseIndex + 1) % (PREVIEW_POSES.length + 1); // +1 = "Batalha"
        applyPreviewPose();
    }

    private String currentPreviewPoseLabel() {
        if (previewPoseIndex == PREVIEW_BATTLE_INDEX) return com.f4xizzz.greatcosmetics.config.LangConfig.legacy("party.preview.pose_battle");
        String name = PREVIEW_POSES[previewPoseIndex].name();
        return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
    }

    /** Trava a entidade do preview numa pose específica — sem desligar
     *  enablePoseTypeRecalculation, o Cobblemon recalcula a pose sozinho a cada tick baseado no
     *  comportamento "de verdade" da entidade (parada = STAND, se movendo = WALK...), sobrescrevendo
     *  a pose escolhida no frame seguinte. "Batalha" é a pose STAND com isBattling() fingido true
     *  (via BATTLE_ID no DataTracker) — não existe uma UUID de batalha de verdade por trás, é só
     *  o suficiente pro modelo escolher a animação idle de batalha em vez da idle parada normal. */
    private void applyPreviewPose() {
        if (previewEntity == null) return;
        try {
            previewEntity.setEnablePoseTypeRecalculation(false);
            boolean isBattlePose = previewPoseIndex == PREVIEW_BATTLE_INDEX;
            PoseType pose = isBattlePose ? PoseType.STAND : PREVIEW_POSES[previewPoseIndex];
            previewEntity.getEntityData().set(PokemonEntity.getPOSE_TYPE(), pose);
            previewEntity.getEntityData().set(PokemonEntity.getBATTLE_ID(),
                    isBattlePose ? java.util.Optional.of(PREVIEW_BATTLE_ID) : java.util.Optional.empty());
        } catch (Exception ignored) {}
    }

    private void stopSkinPreview() {
        if (this.previewingSkin == null) return;
        this.previewingSkin = null;
        this.previewEntity = null;
        updateCameraToHitbox();
    }

    /** Cicla: base (0) -> altForms[0] -> altForms[1] -> ... -> volta pra base. Só existe alguma
     *  coisa pra ciclar se a skin tiver altForms configurados no pokeskins.json. */
    public void cyclePreviewForm() {
        if (previewingSkin == null || previewingSkin.getAltForms().isEmpty()) return;
        previewFormIndex = (previewFormIndex + 1) % (previewingSkin.getAltForms().size() + 1);
        rebuildPreviewEntity();
    }

    public void togglePreviewShiny() {
        if (previewingSkin == null) return;
        previewShiny = !previewShiny;
        rebuildPreviewEntity();
    }

    /** Nome pra mostrar no botão de forma: "Padrão" na posição 0, ou o próprio aspect configurado
     *  (capitalizado) nas posições seguintes. */
    private String currentPreviewFormLabel() {
        if (previewFormIndex == 0) return com.f4xizzz.greatcosmetics.config.LangConfig.legacy("party.form.default");
        int altIndex = previewFormIndex - 1;
        List<String> altForms = previewingSkin.getAltForms();
        if (altIndex < 0 || altIndex >= altForms.size()) return com.f4xizzz.greatcosmetics.config.LangConfig.legacy("party.form.default");
        String raw = altForms.get(altIndex);
        if (raw.isEmpty()) return com.f4xizzz.greatcosmetics.config.LangConfig.legacy("party.form.default");

        // "battle_bond=ash" / "meteorite_form=attack" (feature composta, ver appendAspectTokens)
        // vira só "Ash" / "Attack" no botão — o admin não precisa ver a chave interna da feature,
        // só o nome da forma de verdade. Aspect simples ("mega", "alola") continua igual.
        String label = raw;
        int eq = raw.indexOf('=');
        if (eq < 0) eq = raw.indexOf(':');
        if (eq >= 0 && eq < raw.length() - 1) label = raw.substring(eq + 1);

        return label.isEmpty() ? com.f4xizzz.greatcosmetics.config.LangConfig.legacy("party.form.default") : label.substring(0, 1).toUpperCase() + label.substring(1);
    }

    /** Parseia uma string de aspect (mesmo formato usado em pokeskins.json: "shiny", "f=alola",
     *  "mega gmax", "female", etc). Tokens com prefixo "f="/"form=" (ou chave composta tipo
     *  "wushu_style=rapid_strike") viram propriedade de verdade no StringBuilder do Cobblemon;
     *  "male"/"female"/"genderless" viram "gender=X" (chave própria do Cobblemon, é a única forma
     *  de fazer PokemonProperties#create() setar o Gender de verdade — ver comentário abaixo).
     *  Qualquer outro token solto entra em extraAspects, pra ser aplicado DEPOIS via
     *  Pokemon#setForcedAspects.
     *
     *  DESCOBERTA (lendo o bytecode decompilado do Cobblemon): "aspect=X" no DSL do
     *  PokemonProperties NUNCA é lido dentro de PokemonProperties#apply()/create() — só existe
     *  pra PokemonProperties#asRenderablePokemon() (usado noutro contexto, não o nosso). Escrever
     *  "aspect=X" na string de propriedades e chamar .create() era, na prática, um no-op — o
     *  aspect nunca chegava no Pokemon criado. É por isso que o preview (Indeedee virava
     *  Substitute, Scizor sem a skin) sempre esteve quebrado enquanto o equip de verdade (servidor,
     *  ver GreatCosmetics#EquipPokemonSkinPayload) sempre funcionou — lá NUNCA se usou esse token,
     *  sempre foi Pokemon#setForcedAspects() direto. */
    private void appendAspectTokens(StringBuilder propsBuilder, java.util.Set<String> extraAspects, String rawAspectStr) {
        if (rawAspectStr == null) return;
        String rawAspect = rawAspectStr.toLowerCase().trim();
        if (rawAspect.isEmpty()) return;

        if (rawAspect.startsWith("f=") || rawAspect.startsWith("form=") || rawAspect.startsWith("f:") || rawAspect.startsWith("form:")) {
            propsBuilder.append(" ").append(rawAspect.replace(":", "="));
        } else {
            for (String part : rawAspect.split(" ")) {
                String cleanPart = part.trim();
                if (cleanPart.isEmpty()) continue;

                if (cleanPart.contains("=") || cleanPart.contains(":")) {
                    // Propriedade composta própria do Cobblemon (species feature — ex:
                    // "wushu_style=rapid_strike" pro Urshifu, "song_forme=pirouette" pra Meloetta):
                    // passa DIRETO como a própria propriedade, só trocando ":" por "=", igual já
                    // era feito pra "f="/"form=" acima.
                    String normalized = cleanPart.replace(":", "=");
                    propsBuilder.append(" ").append(normalized);

                    // MESMO PROBLEMA do gender/shiny abaixo: PokemonProperties#create() seta a
                    // FEATURE de verdade no dummy (battle_bond, meteorite_forme, song_forme...), mas
                    // Pokemon#updateAspects() client-side nunca roda o AspectProvider que traduziria
                    // essa feature pro aspect correspondente — sem isso, dummy.getAspects() nunca
                    // reflete a forma, e o preview (e SÓ o preview; o equip de verdade no servidor
                    // roda o AspectProvider normal e sempre funcionou) sempre cai no modelo base,
                    // mesmo com a feature certa configurada.
                    //
                    // O NOME do aspect NEM SEMPRE é só o valor puro — depende de como o addon (ex:
                    // Mega Showdown) escreveu o resolver daquela espécie (ver bedrock/pokemon/
                    // resolvers/*.json no jar): Greninja (feature "battle_bond") só precisa do
                    // aspect "ash" puro, mas Deoxys ("meteorite_forme") e Meloetta ("song_forme")
                    // exigem "attack-forme"/"pirouette-forme" — sufixo "-forme" no aspect sempre que
                    // a CHAVE da feature termina em "_form"/"_forme". Injeta os dois candidatos
                    // (puro E com sufixo) pra cobrir ambas convenções sem precisar adivinhar qual
                    // addon fez a espécie — um aspect extra que nenhum resolver pede é inofensivo.
                    int eq = normalized.indexOf('=');
                    if (eq >= 0 && eq < normalized.length() - 1) {
                        String featureKey = normalized.substring(0, eq);
                        String value = normalized.substring(eq + 1);
                        extraAspects.add(value);
                        if (featureKey.endsWith("_forme") || featureKey.endsWith("_form")) {
                            extraAspects.add(value + "-forme");
                        }
                    }
                } else if (cleanPart.equals("male") || cleanPart.equals("female") || cleanPart.equals("genderless")) {
                    // "gender=X" é uma chave DEDICADA do PokemonProperties (setGender de verdade),
                    // ao contrário de "aspect=X" (nunca aplicado). Sem isso, um Pokémon com aspecto
                    // "female" configurado nascia com gênero ALEATÓRIO — se saísse macho por sorte,
                    // o resolvedor de pose via ver "male" (do gênero de verdade) E "female" (se
                    // "aspect=X" fosse aplicado) ao mesmo tempo, conflito que o Cobblemon não sabe
                    // resolver (cai no fallback Substitute) — pra espécies como Indeedee, que têm
                    // modelo 3D diferente por gênero, isso é sempre fatal.
                    propsBuilder.append(" gender=").append(cleanPart);
                } else if (cleanPart.equals("shiny")) {
                    propsBuilder.append(" shiny=true");
                } else {
                    extraAspects.add(cleanPart);
                }
            }
        }
    }

    /** Monta (ou remonta, ao trocar forma/shiny) a entidade "fake" (não é um Pokémon de verdade
     *  do player) usada pro preview — mesma técnica dos ícones 3D da lista de skins (dummy via
     *  PokemonProperties), só que como entidade completa pra render no mundo, igual entityCache[]
     *  faz com Pokémon reais. Forma/shiny do preview são só aspects INJETADOS em cima do aspect
     *  base da skin — nunca mudam a skin/config de verdade, só o que é mostrado no preview. */
    private void rebuildPreviewEntity() {
        if (previewingSkin == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        try {
            StringBuilder propsBuilder = new StringBuilder("species=" + previewingSkin.getSpecies());
            java.util.Set<String> extraAspects = new HashSet<>();
            appendAspectTokens(propsBuilder, extraAspects, previewingSkin.getAspect());

            if (previewFormIndex > 0) {
                int altIndex = previewFormIndex - 1;
                List<String> altForms = previewingSkin.getAltForms();
                if (altIndex < altForms.size()) appendAspectTokens(propsBuilder, extraAspects, altForms.get(altIndex));
            }

            if (previewShiny) {
                propsBuilder.append(" shiny=true");
                // Mesmo motivo do genderAspect abaixo: "shiny=true" na string de propriedades seta
                // o boolean Pokemon#shiny, mas o dummy client-side nunca roda o AspectProvider que
                // traduziria isso pra string "shiny" dentro de getAspects() — sem forçar aqui, o
                // model renderiza normal (sem brilho/partícula de shiny) mesmo com o boolean true.
                extraAspects.add("shiny");
            }

            Pokemon dummy = PokemonProperties.Companion.parse(propsBuilder.toString()).create(null);
            // Um Pokémon de verdade (já capturado/spawnado) sempre tem um gênero VÁLIDO — é assim
            // que o equip real funciona mesmo pra skins sem "male"/"female" no aspect (ex: Indeedee
            // com só "sxf" configurado). Esse dummy é criado do zero, então pode nascer "genderless"
            // (species como Indeedee, com modelo 3D diferente por gênero, nunca acha variação
            // nenhuma nesse estado — sempre cai no fallback Substitute). checkGender() é o mesmo
            // método público que o Cobblemon usa pra validar/corrigir isso sozinho — idempotente,
            // não faz nada se o gênero já for válido.
            dummy.checkGender();
            // Pokemon#updateAspects() no lado CLIENT (isClient=true, que é o caso desse dummy) só
            // usa forcedAspects DIRETO — ele NUNCA roda o sistema de AspectProvider (que é quem,
            // normalmente, traduziria o Gender pra uma aspect string "male"/"female"). Ou seja,
            // checkGender() sozinho AJUSTA o Gender (enum) mas isso nunca vira a STRING "male"/
            // "female" dentro de getAspects() — precisa adicionar isso na mão.
            String genderAspect = switch (dummy.getGender().name()) {
                case "MALE" -> "male";
                case "FEMALE" -> "female";
                default -> null;
            };
            if (genderAspect != null) extraAspects.add(genderAspect);

            if (!extraAspects.isEmpty()) {
                // "aspect=X" na string de propriedades não pega (ver appendAspectTokens) — só
                // Pokemon#setForcedAspects aplica de verdade, mesma técnica do equip real.
                java.util.Set<String> forced = new HashSet<>(dummy.getForcedAspects());
                forced.addAll(extraAspects);
                dummy.setForcedAspects(forced);
                dummy.updateAspects();
            }

            PokemonEntity entity = (PokemonEntity) CobblemonEntities.POKEMON.create(client.level);
            if (entity == null) return;

            entity.setPokemon(dummy);
            entity.getEntityData().set(PokemonEntity.getSPECIES(), dummy.getSpecies().getResourceIdentifier().toString());
            entity.getEntityData().set(PokemonEntity.getASPECTS(), new HashSet<>(dummy.getAspects()));
            entity.getEntityData().set(PokemonEntity.getSCALE_MODIFIER(), dummy.getScaleModifier());
            com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("PartyPage preview: species=" + previewingSkin.getSpecies()
                    + " skinAspectRaw=\"" + previewingSkin.getAspect() + "\""
                    + " gender=" + dummy.getGender()
                    + " forcedAspects=" + dummy.getForcedAspects()
                    + " finalAspects=" + dummy.getAspects()
                    + " form=" + dummy.getForm().getName());
            // ASPECTS precisa disparar ANTES de SPECIES: é o onTrackedDataSet(SPECIES) que resolve o
            // poser/model de verdade (PokemonClientDelegate.getPoser(), internamente do Cobblemon),
            // usando os aspects JÁ aplicados no state naquele momento — não os que o dataTracker.set()
            // acima só guardou no campo bruto. Na ordem antiga (SPECIES primeiro), o poser era
            // escolhido com o conjunto de aspects ainda vazio, e só DEPOIS os aspects de verdade
            // entravam (sem re-resolver nada) — para espécies que exigem aspect pra achar uma
            // variação válida (ex: Indeedee precisa de "male"/"female" sempre), isso não achava
            // variação nenhuma e caía no fallback "Substitute" do Cobblemon.
            entity.onSyncedDataUpdated(PokemonEntity.getASPECTS());
            entity.onSyncedDataUpdated(PokemonEntity.getSPECIES());
            entity.onSyncedDataUpdated(PokemonEntity.getSCALE_MODIFIER());
            entity.setPosRaw(client.player.getX(), client.player.getY(), client.player.getZ());
            entity.setNoGravity(true);
            entity.refreshDimensions();

            this.previewEntity = entity;
            applyPreviewPose();
            updateCameraToHitbox();
        } catch (Exception ignored) {}
    }

    // Lê do ClientPermissionCache (calculado no servidor) em vez de hasPermissionLevel()/
    // Permissions.check() direto no client — ver SyncDevPermissionsPayload.
    private boolean hasDevPermission(net.minecraft.client.player.LocalPlayer player) {
        return com.f4xizzz.greatcosmetics.client.ClientPermissionCache.isOperator
                || com.f4xizzz.greatcosmetics.client.ClientPermissionCache.hasGcPermDevmode;
    }

    @Override
    public void onOpen() {
        Minecraft client = Minecraft.getInstance();
        if (!checkedDevState && client.player != null) {
            // Ativa o Dev Mode automaticamente pra OPs e para quem tem a permissão!
            if (hasDevPermission(client.player)) {
                com.f4xizzz.greatcosmetics.client.ClientCosmeticCache.isDevModeActive = true;
            }
            checkedDevState = true;
        }

        scrollY = 0;
        previewingSkin = null;
        previewEntity = null;
        loadParty();
        updateCameraToHitbox();
        updateAvailableSkins();
    }

    /** Chamado pelo receiver do SyncSkinCatalogPayload — se o wardrobe já estiver aberto na
     *  PartyPage quando o catálogo chegar (ex: durante um /gc reload), recarrega a lista na hora
     *  em vez de só refletir da próxima vez que a página for reaberta. */
    public static void refreshSkinsIfOpen() {
        if (INSTANCE != null && INSTANCE.isLoaded) {
            INSTANCE.updateAvailableSkins();
        }
    }

    private void updateAvailableSkins() {
        currentAvailableSkins.clear();
        skinWidgets.clear();
        skinDisplayRows.clear();
        // scrollY NÃO reseta aqui de propósito — esse método é chamado toda vez que troca de
        // Pokémon selecionado (seta/slot), e resetar o scroll junto fazia a lista de skins voltar
        // pro topo sozinha a cada troca. Só reseta em onOpen() (wardrobe abrindo do zero).

        // MESMA correção de escala usada nos slots da party (scaleMultiplier) — sem multiplicar
        // o "scale" do ModelWidget por isso, o modelo 3D renderiza no tamanho físico ERRADO
        // dependendo da resolução/GUI Scale do jogador (podia ficar gigante e vazar pra fora do
        // recorte da lista, já que o scissor é calculado em outro espaço de coordenadas).
        Minecraft client = Minecraft.getInstance();
        float skinsScale = 0.8f;
        if (client.getWindow() != null) {
            float frameScale = client.getWindow().getHeight() / 450f;
            skinsScale = 0.8f * (frameScale / (float) client.getWindow().getGuiScale());
        }

        // Skins não desbloqueadas continuam aparecendo na lista (bloqueadas, com cadeado) em vez
        // de sumir completamente — igual o padrão já usado na TagsPage.
        for (PokemonSkin skin : SkinConfigManager.getAllSkins()) {
            currentAvailableSkins.add(skin);
            try {
                // Mesmo parsing usado no preview (appendAspectTokens) — antes essa lógica estava
                // DUPLICADA aqui com uma cópia ligeiramente diferente, o que já era por si só uma
                // fonte de bugs (ícone e preview podendo divergir pro mesmo aspect configurado).
                StringBuilder propsBuilder = new StringBuilder("species=" + skin.getSpecies());
                java.util.Set<String> extraAspects = new HashSet<>();
                appendAspectTokens(propsBuilder, extraAspects, skin.getAspect());

                Pokemon dummy = PokemonProperties.Companion.parse(propsBuilder.toString()).create(null);
                dummy.checkGender();
                // Mesma correção do preview grande (ver rebuildPreviewEntity) — updateAspects() no
                // client nunca traduz Gender pra aspect string sozinho.
                String genderAspect = switch (dummy.getGender().name()) {
                    case "MALE" -> "male";
                    case "FEMALE" -> "female";
                    default -> null;
                };
                if (genderAspect != null) extraAspects.add(genderAspect);
                if (!extraAspects.isEmpty()) {
                    java.util.Set<String> forced = new HashSet<>(dummy.getForcedAspects());
                    forced.addAll(extraAspects);
                    dummy.setForcedAspects(forced);
                    dummy.updateAspects();
                }
                ModelWidget widget = new ModelWidget(0, 0, 40, 40, dummy.asRenderablePokemon(), skinsScale, 0f, 0.0, false, false, 13);
                skinWidgets.put(skin.getId(), widget);
            } catch (Exception ignored) {}
        }

        // Agrupa por PokemonSkin.getGroup() — skins sem grupo (campo vazio) aparecem primeiro,
        // soltas, na ordem do catálogo; skins agrupadas ficam depois, sob um cabeçalho por grupo,
        // ordenadas alfabeticamente pelo nome do grupo. O yOffset é acumulado aqui uma vez só,
        // pra render/clique/scroll não terem que recalcular a altura variável (header != skin) toda hora.
        Map<String, List<PokemonSkin>> grouped = new java.util.LinkedHashMap<>();
        List<PokemonSkin> ungrouped = new ArrayList<>();
        for (PokemonSkin skin : currentAvailableSkins) {
            String g = skin.getGroup().trim();
            if (g.isEmpty()) {
                ungrouped.add(skin);
            } else {
                grouped.computeIfAbsent(g, k -> new ArrayList<>()).add(skin);
            }
        }

        List<String> groupNames = new ArrayList<>(grouped.keySet());
        groupNames.sort(String.CASE_INSENSITIVE_ORDER);

        int y = 0;
        for (PokemonSkin skin : ungrouped) {
            SkinDisplayRow row = new SkinDisplayRow(skin);
            row.yOffset = y;
            skinDisplayRows.add(row);
            y += row.height + SKIN_SPACING_Y;
        }
        for (String groupName : groupNames) {
            SkinDisplayRow header = new SkinDisplayRow(groupName);
            header.yOffset = y;
            skinDisplayRows.add(header);
            y += header.height + SKIN_SPACING_Y;

            for (PokemonSkin skin : grouped.get(groupName)) {
                SkinDisplayRow row = new SkinDisplayRow(skin);
                row.yOffset = y;
                skinDisplayRows.add(row);
                y += row.height + SKIN_SPACING_Y;
            }
        }
    }

    /** "#RRGGBB" -> ARGB opaco (0xFFRRGGBB) — cai no dourado padrão se o texto configurado for
     *  inválido (typo no skin_groups.json), em vez de quebrar o desenho do cabeçalho. */
    private int parseGroupColor(String hex) {
        try {
            String clean = hex.startsWith("#") ? hex.substring(1) : hex;
            return 0xFF000000 | (Integer.parseInt(clean, 16) & 0xFFFFFF);
        } catch (Exception e) {
            return 0xFFFFAA00;
        }
    }

    private boolean isSkinUnlocked(PokemonSkin skin) {
        return ClientSkinCache.isUnlocked(skin.getId()) || com.f4xizzz.greatcosmetics.client.ClientCosmeticCache.isDevModeActive;
    }

    private void updateCameraToHitbox() {
        PokemonEntity entity = getCurrentPokemonEntity();
        if (entity != null) {
            float maxHeight = 1.2f;
            float actualHeight = entity.getBbHeight();

            float displayHeight = Math.min(actualHeight, maxHeight);

            Wardrobe3DScreen.targetFocusY = displayHeight / 2.0f;
            Wardrobe3DScreen.targetDistance = 3.5;
            Wardrobe3DScreen.targetPan = 0.0f;
        } else {
            Wardrobe3DScreen.targetFocusY = 1.0f;
            Wardrobe3DScreen.targetDistance = 3.5;
            Wardrobe3DScreen.targetPan = 0.0f;
        }
    }

    private void loadParty() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        for (int i = 0; i < 6; i++) {
            partyCache[i] = null;
            slotWidgets[i] = null;
            entityCache[i] = null;
        }

        try {
            ClientParty party = CobblemonClient.INSTANCE.getStorage().getParty();
            int partySize = party.getSlots().size();

            for (int i = 0; i < partySize && i < 6; i++) {
                Pokemon pokemon = party.get(i);
                if (pokemon != null) {
                    partyCache[i] = pokemon;

                    PokemonEntity entity = (PokemonEntity) CobblemonEntities.POKEMON.create(client.level);
                    if (entity != null) {
                        entity.setPokemon(pokemon);

                        entity.getEntityData().set(PokemonEntity.getSPECIES(), pokemon.getSpecies().getResourceIdentifier().toString());
                        entity.getEntityData().set(PokemonEntity.getASPECTS(), new HashSet<>(pokemon.getAspects()));
                        entity.getEntityData().set(PokemonEntity.getSCALE_MODIFIER(), pokemon.getScaleModifier());

                        // Mesma correção de ordem de rebuildPreviewEntity() acima — ver comentário lá.
                        entity.onSyncedDataUpdated(PokemonEntity.getASPECTS());
                        entity.onSyncedDataUpdated(PokemonEntity.getSPECIES());
                        entity.onSyncedDataUpdated(PokemonEntity.getSCALE_MODIFIER());

                        entity.setPosRaw(client.player.getX(), client.player.getY(), client.player.getZ());
                        entity.setNoGravity(true);
                        entity.refreshDimensions();
                    }
                    entityCache[i] = entity;
                }
            }
            this.isLoaded = true;
            updateAvailableSkins();
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Error loading a Party do Cobblemon: " + e.getMessage());
        }
    }

    @Override
    public void render(GuiGraphics c, int mouseX, int mouseY, float delta, int x, int y, int width, int height) {
        if (!isLoaded) loadParty();

        // Solta o arrasto da barra de scroll quando o botão do mouse é liberado — mesmo padrão dos
        // subpages do Dev Studio (não tem callback de mouseReleased aqui, então poll a cada frame).
        if (org.lwjgl.glfw.GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) != org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            isDraggingSkinScrollbar = false;
        }

        // NOVO: Dá 600ms pro servidor processar a skin e força a tela a se recarregar sozinha!
        if (pendingRefreshTime > 0 && Util.getMillis() >= pendingRefreshTime) {
            pendingRefreshTime = 0;
            loadParty();
            updateCameraToHitbox();
        }

        this.pX = x; this.pY = y; this.pW = width; this.pH = height;

        Minecraft client = Minecraft.getInstance();
        PokemonEntity renderEntity = getCurrentPokemonEntity();
        // Espécie do Pokémon REAL selecionado na party — de propósito NUNCA a do preview (ver
        // getCurrentPokemonEntity(), que troca pro previewEntity enquanto previewingSkin != null).
        // Usar o preview aqui fazia toda skin da MESMA espécie sendo pré-visualizada (ex: outras
        // skins de Ceruledge) perder a borda vermelha de "incompatível" na lista inteira, mesmo o
        // player não tendo nenhuma delas e o Pokémon de verdade selecionado sendo outra espécie.
        PokemonEntity realSelectedEntity = isLoaded ? entityCache[selectedSlot] : null;
        String currentSpecies = realSelectedEntity != null ? realSelectedEntity.getPokemon().getSpecies().getName().toLowerCase() : "";
        net.minecraft.core.HolderLookup.Provider regs = client.level != null ? client.level.registryAccess() : null;

        // =========================================================
        // 1. RENDERIZAÇÃO DO BOTÃO DEV (só desenha se tiver permissão — antes aparecia pra
        // qualquer player, só o clique é que era bloqueado)
        // =========================================================
        if (hasDevPermission(client.player)) {
            int devBtnW = 40;
            int devBtnX = (x + width - 12) - devBtnW;
            int devBtnY = y + 32;
            boolean devHover = over(mouseX, mouseY, devBtnX, devBtnY, devBtnW, 14);
            int devColor = com.f4xizzz.greatcosmetics.client.ClientCosmeticCache.isDevModeActive ? (devHover ? 0xFF00FF00 : 0xFF00AA00) : (devHover ? 0xFFFF5555 : 0xFFAA0000);

            c.fill(devBtnX, devBtnY, devBtnX + devBtnW, devBtnY + 14, 0x44000000);
            c.renderOutline(devBtnX, devBtnY, devBtnW, 14, devColor);
            c.drawCenteredString(parent.getTextRenderer(), "DEV", devBtnX + (devBtnW / 2), devBtnY + 3, devColor);
        }

        c.drawString(parent.getTextRenderer(), com.f4xizzz.greatcosmetics.config.LangConfig.legacy("party.skins.header"), x + 12, y + 35, 0xFFFFFF);
        c.fill(x + 12, y + 48, x + width - 12, y + 49, 0xFF444444);

        // =========================================================
        // 2. RENDERIZAÇÃO DA LISTA DE SKINS E BOTÕES EXTRAS
        // =========================================================
        lastListX = x + 10;
        lastListY = y + 55;
        lastListW = width - 20 - SCROLLBAR_RESERVED;

        // Se o DEV mode tá ativo, espreme a lista pra caber os botões de limpar
        if (com.f4xizzz.greatcosmetics.client.ClientCosmeticCache.isDevModeActive) {
            lastListH = height - 90;

            btnClearW = (lastListW / 2) - 2;
            btnClearCurX = lastListX;
            btnClearCurY = lastListY + lastListH + 5;
            btnClearAllX = lastListX + btnClearW + 4;

            boolean hovCur = over(mouseX, mouseY, btnClearCurX, btnClearCurY, btnClearW, btnClearH);
            c.fill(btnClearCurX, btnClearCurY, btnClearCurX + btnClearW, btnClearCurY + btnClearH, hovCur ? 0x66FF5555 : 0x44AA0000);
            c.renderOutline(btnClearCurX, btnClearCurY, btnClearW, btnClearH, hovCur ? 0xFFFF5555 : 0xFFAA0000);
            c.drawCenteredString(parent.getTextRenderer(), com.f4xizzz.greatcosmetics.config.LangConfig.legacy("party.skins.clear_current"), btnClearCurX + (btnClearW / 2), btnClearCurY + 6, 0xFFFFFF);

            boolean hovAll = over(mouseX, mouseY, btnClearAllX, btnClearCurY, btnClearW, btnClearH);
            c.fill(btnClearAllX, btnClearCurY, btnClearAllX + btnClearW, btnClearCurY + btnClearH, hovAll ? 0x66FF5555 : 0x44AA0000);
            c.renderOutline(btnClearAllX, btnClearCurY, btnClearW, btnClearH, hovAll ? 0xFFFF5555 : 0xFFAA0000);
            c.drawCenteredString(parent.getTextRenderer(), com.f4xizzz.greatcosmetics.config.LangConfig.legacy("party.skins.clear_party"), btnClearAllX + (btnClearW / 2), btnClearCurY + 6, 0xFFFFFF);
        } else {
            lastListH = height - 65;
        }

        // Mesmo cálculo do mouseScrolled — precisa estar disponível aqui (campo, não local) pra
        // desenhar e clicar na barra de scroll de verdade (não só a roda do mouse).
        int skinTotalContentHeight = skinDisplayRows.isEmpty() ? 0
                : skinDisplayRows.get(skinDisplayRows.size() - 1).yOffset + skinDisplayRows.get(skinDisplayRows.size() - 1).height;
        skinListMaxScrollY = Math.max(0, skinTotalContentHeight - lastListH);

        if (currentAvailableSkins.isEmpty()) {
            c.drawString(parent.getTextRenderer(), com.f4xizzz.greatcosmetics.config.LangConfig.legacy("party.skins.none"), x + 12, y + 60, 0xFFFFFF);
        } else {
            // parent.enableScissorStacked() (não c.enableScissor() puro, nem RenderSystem cru) —
            // empilha o recorte (quando o ModelWidget do Cobblemon chama context.enableScissor()/
            // disableScissor() por dentro do renderPKM, pra recortar o modelo 3D na caixinha
            // PRÓPRIA dele, intersecta com esse recorte da lista em vez de substituir) E compensa
            // o canvas virtual escalado desse wardrobe (c.enableScissor() puro NUNCA passa x/y
            // pela matrix ativa, só multiplica pelo GUI Scale — fica desalinhado do conteúdo de
            // verdade nessa tela, e o desalinho muda com a resolução/GUI Scale).
            parent.enableScissorStacked(c, lastListX, lastListY, lastListW, lastListH);

            for (SkinDisplayRow row : skinDisplayRows) {
                int itemY = (int) (lastListY + row.yOffset - scrollY);
                int itemHeight = row.height;

                if (itemY + itemHeight < lastListY || itemY > lastListY + lastListH) continue;

                if (row.isHeader) {
                    int groupColor = parseGroupColor(com.f4xizzz.greatcosmetics.config.SkinGroupConfigManager.getColor(row.headerLabel));
                    c.fill(lastListX, itemY, lastListX + lastListW, itemY + itemHeight, 0x66000000);
                    c.fill(lastListX, itemY, lastListX + 3, itemY + itemHeight, groupColor);
                    c.drawString(parent.getTextRenderer(), "§l" + row.headerLabel, lastListX + 6, itemY + (itemHeight - 8) / 2, groupColor);
                    continue;
                }

                PokemonSkin skin = row.skin;
                boolean isHovered = over(mouseX, mouseY, lastListX, itemY, lastListW, itemHeight);
                boolean isUnlocked = isSkinUnlocked(skin);
                // Só considera "compatível" (some a borda vermelha) quando o player REALMENTE tem
                // a skin E o Pokémon de verdade selecionado é da espécie dela — ver comentário em
                // currentSpecies acima sobre por que não pode vir do preview.
                boolean isCompatible = isUnlocked && skin.getSpecies().toLowerCase().equals(currentSpecies);
                long cdLeft = ClientSkinCache.getCooldownLeftMs(skin.getId(), skin.getCooldownMinutes());
                boolean onCooldown = cdLeft > 0 && !com.f4xizzz.greatcosmetics.client.ClientCosmeticCache.isDevModeActive;

                int bgColor = !isCompatible ? 0x66440000 : (onCooldown ? 0x66FF5555 : (isHovered ? 0x66FFFFFF : 0x44000000));
                int borderColor = !isCompatible ? 0xFF880000 : (onCooldown ? 0xFFFF5555 : (isHovered ? 0xFFFFFFFF : 0xFF555555));

                c.fill(lastListX, itemY, lastListX + lastListW, itemY + itemHeight, bgColor);
                c.renderOutline(lastListX, itemY, lastListW, itemHeight, borderColor);

                ModelWidget widget = skinWidgets.get(skin.getId());
                if (widget != null) {
                    c.pose().pushPose();
                    float frameScale = client.getWindow().getHeight() / 450f;
                    float sm = frameScale / (float) client.getWindow().getGuiScale();

                    c.pose().scale(1.0f / sm, 1.0f / sm, 1.0f);
                    widget.setX((int)((lastListX + 2) * sm));
                    widget.setY((int)((itemY + 2) * sm));
                    widget.setWidth((int)(40 * sm));
                    widget.setHeight((int)(40 * sm));
                    // Algumas combinações de species+aspect fazem o Cobblemon lançar durante a
                    // resolução de pose/model em vez de cair no fallback "Substitute" — captura pra
                    // não derrubar a tela inteira (nem o resto da lista) por causa de UM ícone ruim.
                    try {
                        widget.render(c, (int)(mouseX * sm), (int)(mouseY * sm), delta);
                    } catch (Exception ignored) {}
                    c.pose().popPose();
                }

                // Nome configurado pelo admin — precisa passar pelo MiniMessage (igual todo texto
                // configurável do resto do mod), senão tags tipo <gradient>/<red> aparecem como
                // texto literal em vez de formatadas.
                net.minecraft.network.chat.Component displayName = regs != null
                        ? com.f4xizzz.greatcosmetics.util.BackpackManager.parseMiniMessage("§f" + skin.getDisplayName(), regs)
                        : net.minecraft.network.chat.Component.literal("§f" + skin.getDisplayName());
                int maxTextWidth = lastListW - 55;
                // Nas linhas trancadas o botão de preview (👁) fica no canto embaixo do cadeado,
                // bem em cima de onde o texto de status normalmente vai — encurta o texto pra não
                // passar por baixo do botão.
                int maxTextWidthLocked = maxTextWidth - 20;
                int textX = lastListX + 46;
                int textY = itemY + 8;
                drawMarqueeText(c, displayName, textX, textY, maxTextWidth, lastListY, lastListH);

                String statusText;
                if (!isUnlocked) {
                    statusText = com.f4xizzz.greatcosmetics.config.LangConfig.legacy("party.skins.status.not_owned");
                } else if (!isCompatible) {
                    statusText = com.f4xizzz.greatcosmetics.config.LangConfig.legacy("party.skins.status.incompatible");
                } else if (onCooldown) {
                    long totalSecs = cdLeft / 1000;
                    String timeStr = totalSecs > 3600 ? String.format("%02d:%02d:%02d", totalSecs / 3600, (totalSecs % 3600) / 60, totalSecs % 60) : String.format("%02d:%02d", totalSecs / 60, totalSecs % 60);
                    statusText = com.f4xizzz.greatcosmetics.config.LangConfig.legacy("party.skins.status.cooldown", "time", timeStr);
                } else {
                    statusText = com.f4xizzz.greatcosmetics.config.LangConfig.legacy(isHovered ? "party.skins.status.click_apply" : "party.skins.status.ready");
                }
                drawMarqueeText(c, net.minecraft.network.chat.Component.literal(statusText), lastListX + 46, itemY + 22, isUnlocked ? maxTextWidth : maxTextWidthLocked, lastListY, lastListH);

                if (!isUnlocked) {
                    c.fill(lastListX, itemY, lastListX + lastListW, itemY + itemHeight, 0x99000000);
                    c.drawString(parent.getTextRenderer(), "🔒", lastListX + lastListW - 16, itemY + 4, 0xFFCCCCCC);

                    // Botão de preview embaixo do cadeado — só pra skins não desbloqueadas.
                    int previewBtnX = lastListX + lastListW - 20;
                    int previewBtnY = itemY + 18;
                    int previewBtnSize = 14;
                    boolean hovPreviewBtn = over(mouseX, mouseY, previewBtnX, previewBtnY, previewBtnSize, previewBtnSize);
                    boolean isPreviewingThis = previewingSkin != null && previewingSkin.getId().equals(skin.getId());

                    c.fill(previewBtnX, previewBtnY, previewBtnX + previewBtnSize, previewBtnY + previewBtnSize,
                            isPreviewingThis ? 0xFF22AA22 : (hovPreviewBtn ? 0xFF666666 : 0xFF333333));
                    c.renderOutline(previewBtnX, previewBtnY, previewBtnSize, previewBtnSize, isPreviewingThis ? 0xFF55FF55 : 0xFFAAAAAA);
                    c.drawString(parent.getTextRenderer(), "👁", previewBtnX + 3, previewBtnY + 3, 0xFFFFFFFF);
                }
            }

            c.disableScissor();

            // Barra de scroll clicável/arrastável (mesmo padrão dos subpages do Dev Studio, ex:
            // DevTypesSubPage#drawScrollbar) — antes só dava pra rolar com a roda do mouse.
            if (skinListMaxScrollY > 0) {
                int sbX = lastListX + lastListW + 4;
                c.fill(sbX, lastListY, sbX + 4, lastListY + lastListH, 0x44000000);
                int handleHeight = Math.max(10, (int) (lastListH * ((float) lastListH / (lastListH + skinListMaxScrollY))));
                int handleY = lastListY + (int) ((lastListH - handleHeight) * (scrollY / skinListMaxScrollY));
                c.fill(sbX, handleY, sbX + 4, handleY + handleHeight, 0xFFAAAAAA);
            }
        }

        // =========================================================
        // 3. RENDERIZAÇÃO DA INTERFACE DE BAIXO (SLOTS DA PARTY)
        // =========================================================
        float targetScale = client.getWindow().getHeight() / 450f;
        float scaleMultiplier = targetScale / (float) client.getWindow().getGuiScale();

        int vWidth = (int) (client.getWindow().getWidth() / targetScale);
        int vHeight = 450;

        int slotSize = 32;
        int spacing = 6;
        int pcBtnW = 32;

        int totalWidth = (6 * slotSize) + (5 * spacing) + 10 + pcBtnW;
        int startX = (vWidth - totalWidth) / 2;
        int startY = vHeight - 50;

        if (previewingSkin != null) {
            net.minecraft.network.chat.Component skinName = regs != null
                    ? com.f4xizzz.greatcosmetics.util.BackpackManager.parseMiniMessage("§f" + previewingSkin.getDisplayName(), regs)
                    : net.minecraft.network.chat.Component.literal(previewingSkin.getDisplayName());

            c.drawCenteredString(parent.getTextRenderer(), com.f4xizzz.greatcosmetics.config.LangConfig.legacy("party.preview.title"), vWidth / 2, startY - 28, 0xFFFFFF);
            c.drawCenteredString(parent.getTextRenderer(), skinName, vWidth / 2, startY - 15, 0xFFFFFF);

            // --- CONTROLES DO PREVIEW: POSE + FORMA + SHINY, canto inferior esquerdo da tela ---
            int ctrlBtnW = 110, ctrlBtnH = 20, ctrlSpacing = 6;
            int ctrlX = 12;
            int shinyBtnY = vHeight - ctrlBtnH - 12;
            int formBtnY = shinyBtnY - ctrlBtnH - ctrlSpacing;
            int poseBtnY = formBtnY - ctrlBtnH - ctrlSpacing;

            boolean hovPose = over(mouseX, mouseY, ctrlX, poseBtnY, ctrlBtnW, ctrlBtnH);
            c.fill(ctrlX, poseBtnY, ctrlX + ctrlBtnW, poseBtnY + ctrlBtnH, hovPose ? 0x66222222 : 0x44000000);
            c.renderOutline(ctrlX, poseBtnY, ctrlBtnW, ctrlBtnH, hovPose ? 0xFFFFAA00 : 0xFFAAAAAA);
            c.drawCenteredString(parent.getTextRenderer(), com.f4xizzz.greatcosmetics.config.LangConfig.legacy("party.preview.pose", "label", currentPreviewPoseLabel()), ctrlX + (ctrlBtnW / 2), poseBtnY + 6, 0xFFFFFF);

            if (!previewingSkin.getAltForms().isEmpty()) {
                boolean hovForm = over(mouseX, mouseY, ctrlX, formBtnY, ctrlBtnW, ctrlBtnH);
                c.fill(ctrlX, formBtnY, ctrlX + ctrlBtnW, formBtnY + ctrlBtnH, hovForm ? 0x66222222 : 0x44000000);
                c.renderOutline(ctrlX, formBtnY, ctrlBtnW, ctrlBtnH, hovForm ? 0xFFFFAA00 : 0xFFAAAAAA);
                c.drawCenteredString(parent.getTextRenderer(), com.f4xizzz.greatcosmetics.config.LangConfig.legacy("party.preview.form", "label", currentPreviewFormLabel()), ctrlX + (ctrlBtnW / 2), formBtnY + 6, 0xFFFFFF);
            }

            boolean hovShiny = over(mouseX, mouseY, ctrlX, shinyBtnY, ctrlBtnW, ctrlBtnH);
            c.fill(ctrlX, shinyBtnY, ctrlX + ctrlBtnW, shinyBtnY + ctrlBtnH, previewShiny ? 0xFF886600 : (hovShiny ? 0x66222222 : 0x44000000));
            c.renderOutline(ctrlX, shinyBtnY, ctrlBtnW, ctrlBtnH, previewShiny ? 0xFFFFD700 : (hovShiny ? 0xFFFFAA00 : 0xFFAAAAAA));
            c.drawCenteredString(parent.getTextRenderer(), com.f4xizzz.greatcosmetics.config.LangConfig.legacy(previewShiny ? "party.preview.shiny_on" : "party.preview.shiny_off"), ctrlX + (ctrlBtnW / 2), shinyBtnY + 6, 0xFFFFFF);
        } else if (renderEntity != null) {
            String pokeName = renderEntity.getPokemon().getSpecies().getName().toUpperCase();
            c.drawCenteredString(parent.getTextRenderer(), "§e§l" + pokeName, vWidth / 2, startY - 15, 0xFFFFFF);
        } else {
            c.drawCenteredString(parent.getTextRenderer(), com.f4xizzz.greatcosmetics.config.LangConfig.legacy("party.slot.empty"), vWidth / 2, startY - 15, 0xFFFFFF);
        }

        if (previewingSkin == null && getValidPokemonCount() > 1) {
            int arrowW = 30;
            int arrowH = 120;
            int leftArrowX = (vWidth / 2) - 180;
            int rightArrowX = (vWidth / 2) + 150;
            int arrowY = (vHeight / 2) - 60;

            boolean hovLeft = over(mouseX, mouseY, leftArrowX, arrowY, arrowW, arrowH);
            boolean hovRight = over(mouseX, mouseY, rightArrowX, arrowY, arrowW, arrowH);

            c.fill(leftArrowX, arrowY, leftArrowX + arrowW, arrowY + arrowH, hovLeft ? 0x66222222 : 0x330A0A0A);
            c.renderOutline(leftArrowX, arrowY, arrowW, arrowH, hovLeft ? 0xFFFFAA00 : 0x22FFFFFF);
            c.drawCenteredString(parent.getTextRenderer(), "<", leftArrowX + (arrowW / 2), arrowY + (arrowH / 2) - 4, hovLeft ? 0xFFFFAA00 : 0xFFAAAAAA);

            c.fill(rightArrowX, arrowY, rightArrowX + arrowW, arrowY + arrowH, hovRight ? 0x66222222 : 0x330A0A0A);
            c.renderOutline(rightArrowX, arrowY, arrowW, arrowH, hovRight ? 0xFFFFAA00 : 0x22FFFFFF);
            c.drawCenteredString(parent.getTextRenderer(), ">", rightArrowX + (arrowW / 2), arrowY + (arrowH / 2) - 4, hovRight ? 0xFFFFAA00 : 0xFFAAAAAA);
        }

        for (int i = 0; i < 6; i++) {
            int slotX = startX + (i * (slotSize + spacing));
            boolean isSelected = (i == selectedSlot);
            boolean isHovered = over(mouseX, mouseY, slotX, startY, slotSize, slotSize);

            int bgColor = isSelected ? 0x88FFAA00 : (isHovered ? 0x88FFFFFF : 0x44000000);
            int borderColor = isSelected ? 0xFFFFAA00 : 0xFF444444;

            c.fill(slotX, startY, slotX + slotSize, startY + slotSize, bgColor);
            c.renderOutline(slotX, startY, slotSize, slotSize, borderColor);

            if (partyCache[i] != null) {
                // parent.enableScissorStacked() precisa ser chamado AQUI FORA, com slotX/startY —
                // ele passa as coordenadas pela matrix ATIVA no momento da chamada, e logo abaixo
                // empurramos um scale INVERSO (1/scaleMultiplier) só pro widget (pra ele trabalhar
                // com realX/realY/realSize, coordenadas de pixel "de verdade"). Chamar o scissor
                // DEPOIS desse push (como eu tinha feito) aplicava esse scale inverso TAMBÉM em
                // cima de slotX/startY — que já são a coordenada final, nunca deveriam passar por
                // ele — resultando num recorte praticamente do tamanho de um pixel (ou fora da
                // tela), sumindo com o Pokémon inteiro. O recorte fica empilhado (não depende do
                // matrix stack) então continua valendo normalmente depois do push/pop abaixo.
                parent.enableScissorStacked(c, slotX, startY, slotSize, slotSize);

                c.pose().pushPose();
                c.pose().scale(1.0f / scaleMultiplier, 1.0f / scaleMultiplier, 1.0f);

                int realX = (int)(slotX * scaleMultiplier);
                int realY = (int)(startY * scaleMultiplier);
                int realSize = (int)(slotSize * scaleMultiplier);

                if (slotWidgets[i] == null || lastScale != scaleMultiplier) {
                    slotWidgets[i] = new ModelWidget(realX, realY, realSize, realSize, partyCache[i].asRenderablePokemon(), 0.8f * scaleMultiplier, 0f, 0.0, false, false, 13);
                }

                ModelWidget widget = slotWidgets[i];
                widget.setX(realX);
                widget.setY(realY);
                widget.setWidth(realSize);
                widget.setHeight(realSize);

                // Mesma proteção do ícone da lista acima — um Pokémon de verdade na party com uma
                // combinação de aspect ruim não pode derrubar o client só por estar num dos 6 slots.
                try {
                    widget.render(c, (int)(mouseX * scaleMultiplier), (int)(mouseY * scaleMultiplier), delta);
                } catch (Exception ignored) {}

                c.pose().popPose();
                c.disableScissor();
            }
        }

        lastScale = scaleMultiplier;

        int pcBtnX = startX + (6 * slotSize) + (5 * spacing) + 10;
        boolean pcHovered = over(mouseX, mouseY, pcBtnX, startY, pcBtnW, slotSize);
        c.fill(pcBtnX, startY, pcBtnX + pcBtnW, startY + slotSize, pcHovered ? 0x6600AAFF : 0x330055AA);
        c.renderOutline(pcBtnX, startY, pcBtnW, slotSize, pcHovered ? 0xFF00AAFF : 0xFF0055AA);
        c.drawCenteredString(parent.getTextRenderer(), com.f4xizzz.greatcosmetics.config.LangConfig.legacy("party.pc_button"), pcBtnX + (pcBtnW / 2), startY + (slotSize / 2) - 4, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;

        // --- CLIQUE NA BARRA DE SCROLL DA LISTA DE SKINS ---
        if (skinListMaxScrollY > 0) {
            int sbX = lastListX + lastListW + 4;
            if (mx >= sbX && mx <= sbX + 4 && my >= lastListY && my <= lastListY + lastListH) {
                isDraggingSkinScrollbar = true;
                return true;
            }
        }

        // --- CHECA CLIQUE NO BOTAO DEV (só existe hitbox se tiver permissão de verdade) ---
        if (hasDevPermission(Minecraft.getInstance().player)) {
            int devBtnW = 40;
            int devBtnX = (this.pX + this.pW - 12) - devBtnW;
            int devBtnY = this.pY + 32;
            if (over(mx, my, devBtnX, devBtnY, devBtnW, 14)) {
                playClick();
                com.f4xizzz.greatcosmetics.client.ClientCosmeticCache.isDevModeActive = !com.f4xizzz.greatcosmetics.client.ClientCosmeticCache.isDevModeActive;
                updateAvailableSkins();
                return true;
            }
        }

        // --- CHECA CLIQUES NOS BOTOES DE REMOVER (DEV) ---
        if (com.f4xizzz.greatcosmetics.client.ClientCosmeticCache.isDevModeActive) {
            if (over(mx, my, btnClearCurX, btnClearCurY, btnClearW, btnClearH)) {
                playClick();
                com.f4xizzz.greatcosmetics.platform.GcNet.toServer(new ClearPokemonSkinsPayload(this.selectedSlot));
                pendingRefreshTime = Util.getMillis() + 600; // Agenda a atualização
                return true;
            }
            if (over(mx, my, btnClearAllX, btnClearCurY, btnClearW, btnClearH)) {
                playClick();
                com.f4xizzz.greatcosmetics.platform.GcNet.toServer(new ClearPokemonSkinsPayload(-1));
                pendingRefreshTime = Util.getMillis() + 600; // Agenda a atualização
                return true;
            }
        }

        // --- CHECA CLIQUES NA LISTA DE SKINS ---
        if (!currentAvailableSkins.isEmpty() && over(mx, my, lastListX, lastListY, lastListW, lastListH)) {
            for (SkinDisplayRow row : skinDisplayRows) {
                if (row.isHeader) continue;
                int itemY = (int) (lastListY + row.yOffset - scrollY);
                int itemHeight = row.height;
                if (over(mx, my, lastListX, itemY, lastListW, itemHeight)) {
                    PokemonSkin skin = row.skin;

                    if (!isSkinUnlocked(skin)) {
                        int previewBtnX = lastListX + lastListW - 20;
                        int previewBtnY = itemY + 18;
                        int previewBtnSize = 14;
                        if (over(mx, my, previewBtnX, previewBtnY, previewBtnSize, previewBtnSize)) {
                            playClick();
                            if (previewingSkin != null && previewingSkin.getId().equals(skin.getId())) {
                                stopSkinPreview();
                            } else {
                                startSkinPreview(skin);
                            }
                            return true;
                        }

                        try { Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.VILLAGER_NO, 1.0F)); } catch (Exception ignored) {}
                        return true;
                    }

                    // Equipar skin é uma ação "de verdade" — sai do preview antes, senão o check
                    // de compatibilidade abaixo compararia contra a espécie do PREVIEW em vez do
                    // Pokémon real selecionado.
                    stopSkinPreview();

                    PokemonEntity renderEntity = getCurrentPokemonEntity();
                    boolean isCompatible = renderEntity != null && skin.getSpecies().toLowerCase().equals(renderEntity.getPokemon().getSpecies().getName().toLowerCase());

                    if (!isCompatible) {
                        try { Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), 1.0F)); } catch (Exception ignored) {}
                        return true;
                    }

                    long cdLeft = ClientSkinCache.getCooldownLeftMs(skin.getId(), skin.getCooldownMinutes());
                    if (cdLeft > 0 && !com.f4xizzz.greatcosmetics.client.ClientCosmeticCache.isDevModeActive) {
                        try { Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), 1.0F)); } catch (Exception ignored) {}
                        return true;
                    }

                    playClick();
                    com.f4xizzz.greatcosmetics.platform.GcNet.toServer(new EquipPokemonSkinPayload(this.selectedSlot, skin.getId()));
                    pendingRefreshTime = Util.getMillis() + 600; // Agenda a atualização
                    return true;
                }
            }
        }

        // --- CHECA CLIQUES NOS SLOTS DA PARTY ---
        Minecraft client = Minecraft.getInstance();
        float targetScale = client.getWindow().getHeight() / 450f;
        int vWidth = (int) (client.getWindow().getWidth() / targetScale);
        int vHeight = 450;

        if (previewingSkin != null) {
            int ctrlBtnW = 110, ctrlBtnH = 20, ctrlSpacing = 6;
            int ctrlX = 12;
            int shinyBtnY = vHeight - ctrlBtnH - 12;
            int formBtnY = shinyBtnY - ctrlBtnH - ctrlSpacing;
            int poseBtnY = formBtnY - ctrlBtnH - ctrlSpacing;

            if (over(mx, my, ctrlX, poseBtnY, ctrlBtnW, ctrlBtnH)) {
                playClick();
                cyclePreviewPose();
                return true;
            }

            if (!previewingSkin.getAltForms().isEmpty() && over(mx, my, ctrlX, formBtnY, ctrlBtnW, ctrlBtnH)) {
                playClick();
                cyclePreviewForm();
                return true;
            }

            if (over(mx, my, ctrlX, shinyBtnY, ctrlBtnW, ctrlBtnH)) {
                playClick();
                togglePreviewShiny();
                return true;
            }
        }

        if (previewingSkin == null && getValidPokemonCount() > 1) {
            int arrowW = 30;
            int arrowH = 120;
            int leftArrowX = (vWidth / 2) - 180;
            int rightArrowX = (vWidth / 2) + 150;
            int arrowY = (vHeight / 2) - 60;

            if (over(mx, my, leftArrowX, arrowY, arrowW, arrowH)) {
                playClick();
                do {
                    selectedSlot = (selectedSlot - 1 + 6) % 6;
                } while (partyCache[selectedSlot] == null);
                updateCameraToHitbox();
                updateAvailableSkins();
                return true;
            }

            if (over(mx, my, rightArrowX, arrowY, arrowW, arrowH)) {
                playClick();
                do {
                    selectedSlot = (selectedSlot + 1) % 6;
                } while (partyCache[selectedSlot] == null);
                updateCameraToHitbox();
                updateAvailableSkins();
                return true;
            }
        }

        int slotSize = 32;
        int spacing = 6;
        int pcBtnW = 32;
        int totalWidth = (6 * slotSize) + (5 * spacing) + 10 + pcBtnW;
        int startX = (vWidth - totalWidth) / 2;
        int startY = vHeight - 50;

        for (int i = 0; i < 6; i++) {
            int slotX = startX + (i * (slotSize + spacing));
            if (over(mx, my, slotX, startY, slotSize, slotSize)) {
                if (partyCache[i] != null) {
                    playClick();
                    stopSkinPreview();
                    this.selectedSlot = i;
                    updateCameraToHitbox();
                    updateAvailableSkins();
                }
                return true;
            }
        }

        int pcBtnX = startX + (6 * slotSize) + (5 * spacing) + 10;
        if (over(mx, my, pcBtnX, startY, pcBtnW, slotSize)) {
            playClick();
            if (client.player != null) {
                waitingForPcToOpen = true;
                client.setScreen(null);
                com.f4xizzz.greatcosmetics.platform.GcNet.toServer(new com.f4xizzz.greatcosmetics.network.OpenPcFromWardrobePayload());
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double verticalAmount) {
        if (!currentAvailableSkins.isEmpty() && over(mx, my, lastListX, lastListY, lastListW, lastListH)) {
            scrollY -= verticalAmount * 15;

            if (scrollY < 0) scrollY = 0;
            if (scrollY > skinListMaxScrollY) scrollY = skinListMaxScrollY;

            return true;
        }
        return super.mouseScrolled(mx, my, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double deltaX, double deltaY) {
        if (isDraggingSkinScrollbar && skinListMaxScrollY > 0) {
            double proportion = (my - lastListY) / lastListH;
            scrollY = proportion * skinListMaxScrollY;
            if (scrollY < 0) scrollY = 0;
            if (scrollY > skinListMaxScrollY) scrollY = skinListMaxScrollY;
            return true;
        }
        return super.mouseDragged(mx, my, button, deltaX, deltaY);
    }

    private int getValidPokemonCount() {
        int count = 0;
        for (Pokemon p : partyCache) {
            if (p != null) count++;
        }
        return count;
    }

    private boolean over(double mx, double my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    /** Corta o texto que passa de maxWidth com scissor e faz ele "andar" de ida e volta (marquee)
     * pra dar pra ler tudo, em vez de vazar pra fora da caixa. Usa parent.enableScissorStacked()/
     * c.disableScissor() em vez de RenderSystem.enableScissor() puro OU c.enableScissor() puro:
     * essa função sempre é chamada de DENTRO de um recorte externo (a lista inteira) já ativo, e
     * precisa tanto EMPILHAR/intersectar com ele (senão desmancha o recorte externo ao restaurar)
     * quanto passar as coordenadas pela matrix ATIVA (esse wardrobe renderiza um canvas virtual
     * escalado à parte — DrawContext.enableScissor() cru nunca faz essa conta, só o
     * enableScissorStacked do Wardrobe3DScreen faz). Sem a parte da matrix, o recorte ficava
     * desalinhado do texto de verdade, cortando ele errado dependendo da resolução/GUI Scale.
     * disableScissor() aqui só desempilha de volta pro recorte externo, sem precisar reconstruir
     * ele na mão — por isso não precisa mais receber clipX/clipW. */
    private void drawMarqueeText(GuiGraphics c, net.minecraft.network.chat.Component text, int x, int y, int maxWidth, int clipY, int clipH) {
        int textWidth = parent.getTextRenderer().width(text);

        if (textWidth <= maxWidth) {
            c.drawString(parent.getTextRenderer(), text, x, y, 0xFFFFFF);
            return;
        }

        long time = Util.getMillis();
        double speed = 1.5;
        int overflow = textWidth - maxWidth;

        double wave = (Math.sin(time / 1000.0 * speed) + 1.0) / 2.0;
        int offset = (int) (wave * overflow);

        int clipY1 = Math.max(y, clipY);
        int clipY2 = Math.min(y + 12, clipY + clipH);

        if (clipY1 < clipY2) {
            parent.enableScissorStacked(c, x, clipY1, maxWidth, clipY2 - clipY1);
            c.drawString(parent.getTextRenderer(), text, x - offset, y, 0xFFFFFF);
            c.disableScissor();
        }
    }

    private void playClick() {
        try { Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F)); } catch (Exception ignored) {}
    }
}