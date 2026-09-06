package com.f4xizzz.greatcosmetics.client.gui.pages;

import com.f4xizzz.greatcosmetics.client.ClientCosmeticCache;
import com.f4xizzz.greatcosmetics.client.ClientUnlockedCosmetics;
import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import com.f4xizzz.greatcosmetics.config.CosmeticData;
import com.f4xizzz.greatcosmetics.config.CosmeticsConfig;
import com.f4xizzz.greatcosmetics.config.LangConfig;
import com.f4xizzz.greatcosmetics.network.ClearAllCosmeticsPayload;
import com.f4xizzz.greatcosmetics.network.EquipCosmeticPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AcessoriesPage extends WardrobePage {

    private float scrollY = 0f;
    private float maxScrollY = 0f;

    private boolean isDropdownOpen = false;
    private int currentCatIndex = 0;

    // Última posição/tamanho REAL com que render() foi chamado — panelX/panelY/width no
    // Wardrobe3DScreen são animados (currentPanelW/H fazem lerp ao trocar de aba, ex: vindo da
    // aba Dev que é bem maior), então usar números fixos aqui em mouseClicked/mouseScrolled
    // desalinhava clique/scroll do que estava desenhado de verdade sempre que o painel não
    // estivesse parado exatamente no tamanho de repouso (180x260).
    private int lastX, lastY, lastWidth, lastHeight;

    private final String[] categoryIds = {"ALL", "HEAD", "FACE", "NECK", "CHEST", "BACK", "WAIST", "LEGS", "FEET", "HAND"};

    /** Nome exibido da categoria — resolvido do Lang (config/GreatCosmetics/lang/accessories.json). */
    private static String catName(String id) {
        return LangConfig.legacy("accessories.category." + id.toLowerCase());
    }

    private final EditBox searchField;

    public AcessoriesPage(Wardrobe3DScreen parent) {
        super(parent);

        // MinecraftClient.getInstance().textRenderer (não parent.getTextRenderer()) — esse
        // construtor roda dentro do construtor do Wardrobe3DScreen (Wardrobe3DScreen#pages.put),
        // ou seja, ANTES do Screen#init() rodar, que é quando o Screen.textRenderer (o que
        // parent.getTextRenderer() devolve) é setado. Passar null pro TextFieldWidget aqui não
        // dava erro na hora, mas guardava esse null pra sempre e crashava com NPE assim que a
        // Acessórios tentava desenhar o campo. O client global já está pronto bem antes de
        // qualquer Screen existir, então esse aqui nunca é null.
        this.searchField = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 16, Component.literal(""));
        this.searchField.setMaxLength(64);
        this.searchField.setHint(LangConfig.text("accessories.search_placeholder"));
        // Reseta o scroll toda vez que a busca muda — sem isso, pesquisar um termo que resulta em
        // MENOS linhas que a posição de scroll atual deixava o grid "vazio" até o jogador rolar pra
        // cima de novo manualmente.
        this.searchField.setResponder(text -> this.scrollY = 0);
    }

    /** Formata uma entrada de data.effects ("namespace:effect_id:nivel", ex:
     *  "minecraft:water_breathing:5") pro tooltip — resolve o Text traduzido/localizado de verdade
     *  do StatusEffect em vez de só mostrar a string crua ("MINECRAFT:WATER_BREATHING:5"). O
     *  "nível" salvo aqui já é o nível humano (1 = sem amplificador) — mesma convenção usada em
     *  GreatCosmetics#collectPotionEffects (que subtrai 1 pra virar amplifier de verdade). */
    private Component formatEffectForTooltip(String eff) {
        if (eff == null || eff.isEmpty()) return Component.literal("");
        String[] parts = eff.split(":");
        if (parts.length < 2) return Component.literal(eff.toUpperCase());

        String effectId = parts[0] + ":" + parts[1];
        String level = parts.length > 2 ? parts[2] : "1";

        ResourceLocation id = ResourceLocation.tryParse(effectId);
        Component name = Component.literal(eff.toUpperCase());
        if (id != null) {
            var entry = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getHolder(id);
            if (entry.isPresent()) name = entry.get().value().getDisplayName();
        }
        return Component.literal("").append(name).append(Component.literal(" " + level));
    }

    /** Números de LureStats vêm de campos double configurados livremente no Dev Studio — sem
     *  arredondar, valores tipo "1.5" às vezes rendiam artefato de ponto flutuante feio no tooltip
     *  (ex: "1.5000000000000002"). Mostra inteiro puro quando o valor é redondo (2.0 -> "2"), senão
     *  no máximo 2 casas decimais sem zero à toa (1.50 -> "1.5"). */
    private static String formatLureNumber(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) return String.valueOf((long) value);
        return java.math.BigDecimal.valueOf(value)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    /** Campos de "chance" da LureStats guardam probabilidade 0.0-1.0 (comparados direto contra
     *  Random#nextDouble() no CobblemonIntegration) — aqui é só a exibição em %. */
    private static String formatLurePercent(double chance01) {
        return formatLureNumber(chance01 * 100.0) + "%";
    }

    // --- CACHE DE getFilteredItems() ---
    // render() chama getFilteredItems() TODO FRAME, e o filtro fazia getFormattedName() (parse de
    // MiniMessage completo, nada barato) por item tanto no removeIf() da busca quanto — pior ainda —
    // dentro do Comparator do sort() (chamado O(n log n) vezes, não O(n)). Com poucos itens (uma
    // categoria específica) isso passava batido, mas em "Todos" (bem mais itens) isso sozinho já
    // derrubava o FPS, recalculando a mesma lista 60x/segundo mesmo com o mouse parado. Só
    // recalcula de verdade quando algo que pode MUDAR o resultado realmente mudou desde o frame
    // anterior; senão devolve a lista já pronta.
    private List<CosmeticData> cachedFilteredItems = null;
    private int cachedCatIndex = -1;
    private String cachedQuery = null;
    private int cachedMapSize = -1;
    private int cachedUnlockedSize = -1;
    private int cachedArmorSize = -1;
    private boolean cachedHasAllUnlocked = false;
    private boolean cachedDevMode = false;
    private int cachedFavoriteVersion = -1;

    private List<CosmeticData> getFilteredItems() {
        String query = searchField.getValue().trim().toLowerCase();
        boolean cacheValid = cachedFilteredItems != null
                && cachedCatIndex == currentCatIndex
                && query.equals(cachedQuery)
                && cachedMapSize == CosmeticsConfig.cosmeticsMap.size()
                && cachedUnlockedSize == ClientUnlockedCosmetics.unlockedIds.size()
                && cachedArmorSize == com.f4xizzz.greatcosmetics.client.ClientArmorCosmeticsCache.armorCosmetics.size()
                && cachedHasAllUnlocked == ClientUnlockedCosmetics.hasAllUnlocked
                && cachedDevMode == ClientCosmeticCache.isDevModeActive
                && cachedFavoriteVersion == com.f4xizzz.greatcosmetics.client.ClientFavoriteCosmetics.version;
        if (cacheValid) return cachedFilteredItems;

        List<CosmeticData> filtered = computeFilteredItems(query);

        cachedFilteredItems = filtered;
        cachedCatIndex = currentCatIndex;
        cachedQuery = query;
        cachedMapSize = CosmeticsConfig.cosmeticsMap.size();
        cachedUnlockedSize = ClientUnlockedCosmetics.unlockedIds.size();
        cachedArmorSize = com.f4xizzz.greatcosmetics.client.ClientArmorCosmeticsCache.armorCosmetics.size();
        cachedHasAllUnlocked = ClientUnlockedCosmetics.hasAllUnlocked;
        cachedDevMode = ClientCosmeticCache.isDevModeActive;
        cachedFavoriteVersion = com.f4xizzz.greatcosmetics.client.ClientFavoriteCosmetics.version;
        return filtered;
    }

    private List<CosmeticData> computeFilteredItems(String query) {
        List<CosmeticData> filtered = new ArrayList<>();
        String currentCat = categoryIds[currentCatIndex];

        for (CosmeticData data : CosmeticsConfig.cosmeticsMap.values()) {
            boolean isUnlocked = ClientUnlockedCosmetics.hasAllUnlocked || ClientUnlockedCosmetics.unlockedIds.contains(data.id) || ClientCosmeticCache.isDevModeActive;

            if (!isUnlocked) continue;

            if (currentCat.equals("ALL") || (data.slot != null && data.slot.name().equals(currentCat))) {
                filtered.add(data);
            }
        }

        // Armaduras convertidas em cosmético (ver ArmorCosmeticsConfig) aparecem se o player TEM o
        // item de verdade em algum lugar do inventário (igual nunca dava pra vestir uma armadura
        // que você não possui) OU se o id dela foi concedido direto via /gc give (mesma flag
        // ClientUnlockedCosmetics/hasAllUnlocked que os cosméticos normais já usam) — sem esse OR,
        // "/gc give <armor_cosmetic_id>" desbloqueava no banco mas nunca tinha efeito nenhum
        // visível na GUI, já que só o item físico era checado. Dev Mode ignora as duas exigências.
        for (com.f4xizzz.greatcosmetics.config.CosmeticData data : com.f4xizzz.greatcosmetics.client.ClientArmorCosmeticsCache.getAllSynthetic()) {
            boolean isGranted = ClientUnlockedCosmetics.hasAllUnlocked || ClientUnlockedCosmetics.unlockedIds.contains(data.id);
            if (!ClientCosmeticCache.isDevModeActive && !isGranted && !hasRealItem(data.realItemId)) continue;
            if (currentCat.equals("ALL") || (data.slot != null && data.slot.name().equals(currentCat))) {
                filtered.add(data);
            }
        }

        // Busca por nome exibido (mesma limpeza de códigos § usada na ordenação abaixo) — case
        // insensitive e por substring, então "chap" já acha "Chapéu de Festa".
        if (!query.isEmpty()) {
            filtered.removeIf(d -> !d.getFormattedName().replaceAll("§.", "").toLowerCase().contains(query));
        }

        // Equipados primeiro (antes até dos favoritos), depois favoritos (ver
        // ClientFavoriteCosmetics), depois ordem alfabética pelo nome EXIBIDO (não o id cru nem a
        // String com tags de cor do DisplayName na frente — senão "<light_purple>Zeta" ordenaria
        // antes de "Alfa" só por causa da tag). getFormattedName() já resolve DisplayName -> id
        // capitalizado -> "Cosmético", só falta descartar os códigos § que ele devolve antes de
        // comparar.
        java.util.Set<String> equippedIds = ClientCosmeticCache.getEquipped(Minecraft.getInstance().player.getUUID());
        filtered.sort(java.util.Comparator
                .comparing((CosmeticData d) -> !equippedIds.contains(d.id))
                .thenComparing(d -> !com.f4xizzz.greatcosmetics.client.ClientFavoriteCosmetics.isFavorite(d.id))
                .thenComparing(d -> d.getFormattedName().replaceAll("§.", ""), String.CASE_INSENSITIVE_ORDER)
        );

        return filtered;
    }

    private boolean hasRealItem(String realItemId) {
        if (realItemId == null) return false;
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(realItemId);
        if (id == null) return false;
        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
        if (item == net.minecraft.world.item.Items.AIR) return false;

        net.minecraft.world.entity.player.Player player = Minecraft.getInstance().player;
        if (player == null) return false;

        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) return true;
        }
        for (ItemStack stack : player.getInventory().armor) {
            if (stack.is(item)) return true;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(item)) return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics c, int mouseX, int mouseY, float delta, int x, int y, int width, int height) {
        this.lastX = x; this.lastY = y; this.lastWidth = width; this.lastHeight = height;

        // Barra de pesquisa logo abaixo do título da aba — abre um espaço próprio (22px) ANTES do
        // filtro de categoria, empurrando ele (e o grid) pra baixo, em vez de disputar espaço com o
        // filtro na mesma linha.
        int searchY = y + 32;
        int filterY = searchY + 22;
        int gridStartY = filterY + 22;

        searchField.setX(x + 12);
        searchField.setY(searchY);
        searchField.setWidth(width - 24);
        searchField.visible = true;
        searchField.active = true;
        searchField.render(c, mouseX, mouseY, delta);

        var world = Minecraft.getInstance().level;
        net.minecraft.core.HolderLookup.Provider regs = world != null ? world.registryAccess() : null;

        List<Component> activeTooltip = null;
        List<CosmeticData> items = getFilteredItems();

        Set<String> equippedIds = new java.util.HashSet<>();
        if (Minecraft.getInstance().player != null) {
            equippedIds = ClientCosmeticCache.getEquipped(Minecraft.getInstance().player.getUUID());
        }

        // ==========================================
        // RENDER DO BOTÃO CLEAR ALL
        // ==========================================
        int btnClearW = 20;
        int btnClearH = 16;
        int btnClearX = x + width - 12 - btnClearW;
        boolean hoverClear = over(mouseX, mouseY, btnClearX, filterY, btnClearW, btnClearH);

        c.fill(btnClearX, filterY, btnClearX + btnClearW, filterY + btnClearH, hoverClear ? 0xFFFF4444 : 0xFFAA0000);
        c.renderOutline(btnClearX, filterY, btnClearW, btnClearH, hoverClear ? 0xFFFFAA00 : 0xFF222222);
        c.drawCenteredString(getTextRenderer(), "X", btnClearX + (btnClearW/2), filterY + 4, 0xFFFFFF);

        if (hoverClear && activeTooltip == null) {
            activeTooltip = new ArrayList<>();
            activeTooltip.add(LangConfig.text("accessories.clear_all.tooltip_title"));
            activeTooltip.add(LangConfig.text("accessories.clear_all.tooltip_1"));
            activeTooltip.add(LangConfig.text("accessories.clear_all.tooltip_2"));
        }

        // ==========================================
        // RENDER DO FILTRO CATEGORIA
        // ==========================================
        int filterBoxW = (width - 24) - btnClearW - 4; // Diminui o filtro pra caber o botão vermelho do lado
        boolean hoverHeader = over(mouseX, mouseY, x + 12, filterY, filterBoxW, 16);
        c.fill(x + 12, filterY, x + 12 + filterBoxW, filterY + 16, hoverHeader ? 0xFF333333 : 0xFF222222);

        c.fill(x + 12, filterY, x + 12 + filterBoxW, filterY + 1, 0xFF555555);
        c.fill(x + 12, filterY + 15, x + 12 + filterBoxW, filterY + 16, 0xFF111111);

        c.drawString(getTextRenderer(), catName(categoryIds[currentCatIndex]), x + 16, filterY + 4, 0xFFFFAA);
        c.drawString(getTextRenderer(), isDropdownOpen ? "▲" : "▼", x + 12 + filterBoxW - 12, filterY + 4, 0xFFAAAAAA);

        if (isDropdownOpen) {
            c.pose().pushPose();
            c.pose().translate(0, 0, 300);

            int listHeight = categoryIds.length * 14;
            c.fill(x + 12, filterY + 16, x + 12 + filterBoxW, filterY + 16 + listHeight, 0xFA111111);

            for (int i = 0; i < categoryIds.length; i++) {
                int itemY = filterY + 16 + (i * 14);
                boolean hoverList = over(mouseX, mouseY, x + 12, itemY, filterBoxW, 14);

                if (hoverList) {
                    c.fill(x + 13, itemY, x + 12 + filterBoxW - 1, itemY + 14, 0xFF444444);
                }
                c.drawString(getTextRenderer(), catName(categoryIds[i]), x + 16, itemY + 3, hoverList ? 0xFFFFFF : 0xFFAAAAAA);
            }
            c.pose().popPose();
        }

        // ==========================================
        // RENDER DO GRID DE ITENS
        // ==========================================
        int columns = 4;
        int iconRealSize = 32;
        int spacing = 38;
        int startX = x + 16;
        int startY = gridStartY;

        int totalRows = (int) Math.ceil((float) items.size() / columns);
        // "- 102" (não mais "- 80") — a barra de pesquisa empurrou gridStartY 22px pra baixo, então
        // a área visível do grid encolheu na mesma medida; sem ajustar aqui, a última fileira de
        // itens ficava inalcançável mesmo scrollando até o fim.
        this.maxScrollY = Math.max(0, (totalRows * spacing) - (height - 102));

        // Sem recorte aqui, item parcialmente dentro/fora da área ao scrollar renderizava sem
        // corte nenhum (o check de itemY só decide mostrar OU esconder o item inteiro, não
        // recorta o pixel exato da borda) — ícone "vazando" por cima do filtro ou por baixo do
        // painel, exatamente a renderização esquisita reportada.
        parent.enablePerfectScissor(c, x, gridStartY - 2, width, (y + height - 32) - (gridStartY - 2));

        int index = 0;
        for (CosmeticData data : items) {
            int col = index % columns;
            int row = index / columns;

            int itemX = startX + (col * spacing);
            int itemY = startY + (row * spacing) - (int)scrollY;

            if (itemY < filterY + 16 || itemY > y + height - 32) {
                index++;
                continue;
            }

            boolean isEquipped = equippedIds.contains(data.id);

            if (isEquipped) {
                c.fill(itemX, itemY, itemX + iconRealSize, itemY + iconRealSize, 0x4400FF00);
                c.renderOutline(itemX, itemY, iconRealSize, iconRealSize, 0xFF00FF00);
            }

            ItemStack renderStack;
            if (data.realItemId != null) {
                // Armadura convertida: ícone é o item de verdade, não o ghost carved_pumpkin.
                net.minecraft.resources.ResourceLocation realId = net.minecraft.resources.ResourceLocation.tryParse(data.realItemId);
                net.minecraft.world.item.Item realItem = realId != null ? net.minecraft.core.registries.BuiltInRegistries.ITEM.get(realId) : Items.CARVED_PUMPKIN;
                renderStack = new ItemStack(realItem);
            } else {
                renderStack = new ItemStack(Items.CARVED_PUMPKIN);
                renderStack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(data.cmd));
            }

            c.pose().pushPose();
            c.pose().translate(itemX, itemY, 0);
            c.pose().scale(2.0f, 2.0f, 1.0f);
            c.renderItem(renderStack, 0, 0);
            c.pose().popPose();

            boolean hoveringThisItem = !isDropdownOpen && over(mouseX, mouseY, itemX, itemY, iconRealSize, iconRealSize);

            // ==========================================
            // ESTRELA DE FAVORITO (canto superior direito do ícone)
            // ==========================================
            boolean isFavorite = com.f4xizzz.greatcosmetics.client.ClientFavoriteCosmetics.isFavorite(data.id);
            // Estrela cheia sempre visível se já é favorito (pra bater o olho e ver quais são);
            // vazia só aparece no hover, como convite pra favoritar quem ainda não é.
            if (isFavorite || hoveringThisItem) {
                int starSize = 9;
                int starX = itemX + iconRealSize - starSize;
                int starY = itemY - 1;
                boolean hoveringStar = mouseX >= starX && mouseX < starX + starSize && mouseY >= starY && mouseY < starY + starSize;

                // translate em Z — c.drawItem() logo acima é render de item 3D DE VERDADE (escreve
                // no depth buffer), então um fill/drawTextWithShadow comum no mesmo plano podia
                // ficar ATRÁS do ícone no teste de profundidade mesmo desenhado depois no código.
                // Empurrar a estrela pra frente garante que ela sempre vence o ícone.
                c.pose().pushPose();
                c.pose().translate(0, 0, 200);
                c.drawString(getTextRenderer(), isFavorite ? "★" : "☆", starX, starY,
                        isFavorite ? (hoveringStar ? 0xFFFFEE88 : 0xFFFFD700) : (hoveringStar ? 0xFFFFFFFF : 0xFF888888));
                c.pose().popPose();
            }

            if (hoveringThisItem) {
                c.fill(itemX, itemY, itemX + iconRealSize, itemY + iconRealSize, 0x44FFFFFF);

                List<Component> safeNameLines = data.getFormattedNameLines(regs);
                // Essa label fica perto do rodapé do painel, fora da faixa do grid que o scissor
                // acima recorta — sem isso ela sumia sempre que o mouse passava sobre um ícone.
                // getFormattedNameLines() (não getFormattedNameText()) — deixa usar "\n" no Display
                // Name também aqui; MultilineMarqueeLabel ancora a ÚLTIMA linha em y+height-20 (onde
                // o nome de 1 linha só sempre ficou) e empilha linhas extras PRA CIMA.
                RenderSystem.disableScissor();
                com.f4xizzz.greatcosmetics.client.gui.MultilineMarqueeLabel.draw(
                        c, parent, getTextRenderer(), safeNameLines,
                        x + 12, x + 12, false, y + height - 20, width - 24, 10, 0xFFFFAA);
                parent.enablePerfectScissor(c, x, gridStartY - 2, width, (y + height - 32) - (gridStartY - 2));

                activeTooltip = new ArrayList<>();
                // safeNameLines (não safeName) — drawTooltip trata cada elemento da lista como UMA
                // linha e não quebra "\n" sozinho, então um DisplayName com "\n" aparecia com o "\n"
                // literal em vez de quebrar a tooltip em várias linhas.
                activeTooltip.addAll(safeNameLines); // Título principal

                if (data.armor > 0 || data.toughness > 0) {
                    activeTooltip.add(Component.literal(" "));
                    activeTooltip.add(LangConfig.text("accessories.tooltip.attributes"));
                    if (data.armor > 0) activeTooltip.add(LangConfig.text("accessories.tooltip.armor", "value", data.armor));
                    if (data.toughness > 0) activeTooltip.add(LangConfig.text("accessories.tooltip.toughness", "value", data.toughness));
                }

                if (data.EnableFly || data.isBackpack || data.AutoFeed) {
                    activeTooltip.add(Component.literal(" "));
                    activeTooltip.add(LangConfig.text("accessories.tooltip.abilities"));
                    if (data.EnableFly) activeTooltip.add(LangConfig.text("accessories.tooltip.fly"));
                    if (data.isBackpack) {
                        String backpackInfo = LangConfig.legacy("accessories.tooltip.backpack_rows", "rows", data.backpackRows);
                        activeTooltip.add(LangConfig.text("accessories.tooltip.backpack", "info", backpackInfo));
                    }
                    if (data.AutoFeed) activeTooltip.add(LangConfig.text("accessories.tooltip.autofeed"));
                }

                if (data.effects != null && !data.effects.isEmpty()) {
                    activeTooltip.add(Component.literal(" "));
                    activeTooltip.add(LangConfig.text("accessories.tooltip.passive_effects"));
                    for (String eff : data.effects) {
                        activeTooltip.add(LangConfig.text("accessories.tooltip.effect_line").append(formatEffectForTooltip(eff)));
                    }
                }

                // TODAS as estatísticas de LureStats (ver CosmeticData.LureStats/CobblemonIntegration)
                // — a versão antiga só mostrava 5 das 17 opções, então cosméticos configurados com
                // qualquer um dos outros bônus (amizade, EV, captura, chance de IV, pesca...) pareciam
                // não ter esse bônus nenhum pra quem só olhava o tooltip da Acessórios.
                if (data.lure != null && data.lure.enabled) {
                    activeTooltip.add(Component.literal(" "));
                    activeTooltip.add(LangConfig.text("accessories.tooltip.lure_header"));

                    if (data.lure.lureTYPE != null && !data.lure.lureTYPE.isEmpty())
                        activeTooltip.add(LangConfig.text("accessories.tooltip.lure_type", "value", data.lure.lureTYPE.toUpperCase()));
                    if (data.lure.lureShinyMultiplier > 0)
                        activeTooltip.add(LangConfig.text("accessories.tooltip.lure_shiny", "value", formatLureNumber(data.lure.lureShinyMultiplier)));
                    if (data.lure.lureUltraRAREMultiplier > 0)
                        activeTooltip.add(LangConfig.text("accessories.tooltip.lure_ultrarare", "value", formatLureNumber(data.lure.lureUltraRAREMultiplier)));
                    if (data.lure.lureHiddenAbilityMultiplier > 0)
                        activeTooltip.add(LangConfig.text("accessories.tooltip.lure_hidden_ability", "value", formatLureNumber(data.lure.lureHiddenAbilityMultiplier)));
                    if (data.lure.lureIV > 0)
                        activeTooltip.add(LangConfig.text("accessories.tooltip.lure_iv", "value", data.lure.lureIV));
                    if (data.lure.lureChanceIV > 0)
                        activeTooltip.add(LangConfig.text("accessories.tooltip.lure_iv_chance", "value", formatLurePercent(data.lure.lureChanceIV)));
                    if (data.lure.lureExpAllMultiplier > 0)
                        activeTooltip.add(LangConfig.text("accessories.tooltip.lure_expall", "value", formatLureNumber(data.lure.lureExpAllMultiplier)));
                    if (data.lure.lureEXP > 0)
                        activeTooltip.add(LangConfig.text("accessories.tooltip.lure_exp", "value", formatLureNumber(data.lure.lureEXP)));
                    if (data.lure.lureEV > 0)
                        activeTooltip.add(LangConfig.text("accessories.tooltip.lure_ev", "value", formatLureNumber(data.lure.lureEV)));
                    if (data.lure.lureAmizadeMultiplier > 0)
                        activeTooltip.add(LangConfig.text("accessories.tooltip.lure_friendship", "value", formatLureNumber(data.lure.lureAmizadeMultiplier)));
                    if (data.lure.lureChanceDeCaptura > 0)
                        activeTooltip.add(LangConfig.text("accessories.tooltip.lure_capture", "value", formatLureNumber(data.lure.lureChanceDeCaptura)));

                    boolean hasFishingBonus = data.lure.lurePescaShiny > 0 || data.lure.lurePescaUltraRare > 0
                            || data.lure.lurePescaIvChance > 0 || data.lure.lurePescaIv > 0
                            || data.lure.lurePescaVelocidade > 0 || data.lure.lureDePesca > 0;
                    if (hasFishingBonus) {
                        activeTooltip.add(LangConfig.text("accessories.tooltip.lure_fishing_header"));
                        if (data.lure.lurePescaShiny > 0)
                            activeTooltip.add(LangConfig.text("accessories.tooltip.lure_fishing_shiny", "value", formatLureNumber(data.lure.lurePescaShiny)));
                        if (data.lure.lurePescaUltraRare > 0)
                            activeTooltip.add(LangConfig.text("accessories.tooltip.lure_fishing_ultrarare", "value", formatLureNumber(data.lure.lurePescaUltraRare)));
                        if (data.lure.lurePescaIv > 0)
                            activeTooltip.add(LangConfig.text("accessories.tooltip.lure_fishing_iv", "value", data.lure.lurePescaIv));
                        if (data.lure.lurePescaIvChance > 0)
                            activeTooltip.add(LangConfig.text("accessories.tooltip.lure_fishing_iv_chance", "value", formatLurePercent(data.lure.lurePescaIvChance)));
                        if (data.lure.lurePescaVelocidade > 0)
                            activeTooltip.add(LangConfig.text("accessories.tooltip.lure_fishing_speed", "value", formatLurePercent(data.lure.lurePescaVelocidade)));
                        if (data.lure.lureDePesca > 0)
                            activeTooltip.add(LangConfig.text("accessories.tooltip.lure_fishing_power", "value", formatLureNumber(data.lure.lureDePesca)));
                    }
                }
            }
            index++;
        }
        RenderSystem.disableScissor();

        if (activeTooltip != null) {
            c.renderComponentTooltip(getTextRenderer(), activeTooltip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        int panelX = lastX;
        int panelY = lastY;
        int searchY = panelY + 32;
        int filterY = searchY + 22;
        int width = lastWidth;
        int height = lastHeight;
        int btnClearW = 20;
        int btnClearH = 16;
        int btnClearX = panelX + width - 12 - btnClearW;
        int filterBoxW = (width - 24) - btnClearW - 4;

        // Clica na barra de pesquisa — checa ANTES de tudo, senão um clique nela também acabaria
        // caindo num dos outros cases abaixo (dropdown/categoria/grid) por causa dos "return true"
        // deles nunca serem alcançados primeiro.
        if (over(mouseX, mouseY, searchField.getX(), searchField.getY(), searchField.getWidth(), 16)) {
            searchField.setFocused(true);
            searchField.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        searchField.setFocused(false);

        // Clica no Clear All
        if (over(mouseX, mouseY, btnClearX, filterY, btnClearW, btnClearH)) {
            playClick();
            com.f4xizzz.greatcosmetics.platform.GcNet.toServer(new ClearAllCosmeticsPayload());
            return true;
        }

        if (isDropdownOpen) {
            for (int i = 0; i < categoryIds.length; i++) {
                int itemY = filterY + 16 + (i * 14);
                if (over(mouseX, mouseY, panelX + 12, itemY, filterBoxW, 14)) {
                    currentCatIndex = i;
                    isDropdownOpen = false;
                    this.scrollY = 0;
                    playClick();

                    if (Minecraft.getInstance().player != null) {
                        float eyeY = Minecraft.getInstance().player.getEyeHeight();
                        String cat = categoryIds[i];

                        switch (cat) {
                            case "ALL" -> {
                                Wardrobe3DScreen.targetFocusY = eyeY - 0.5f;
                                Wardrobe3DScreen.targetDistance = 3.5;
                                Wardrobe3DScreen.isZoomLocked = false;
                            }
                            case "HEAD", "FACE" -> {
                                Wardrobe3DScreen.targetFocusY = eyeY - 0.1f;
                                Wardrobe3DScreen.targetDistance = 2.0;
                                Wardrobe3DScreen.isZoomLocked = true;
                            }
                            case "CHEST", "BACK", "NECK", "HAND" -> {
                                Wardrobe3DScreen.targetFocusY = eyeY - 0.4f;
                                Wardrobe3DScreen.targetDistance = 1.5;
                                Wardrobe3DScreen.isZoomLocked = true;
                            }
                            case "WAIST", "LEGS" -> {
                                Wardrobe3DScreen.targetFocusY = eyeY - 0.9f;
                                Wardrobe3DScreen.targetDistance = 1.5;
                                Wardrobe3DScreen.isZoomLocked = true;
                            }
                            case "FEET" -> {
                                Wardrobe3DScreen.targetFocusY = 0.2f;
                                Wardrobe3DScreen.targetDistance = 1.2;
                                Wardrobe3DScreen.isZoomLocked = true;
                            }
                        }
                    }
                    return true;
                }
            }
            isDropdownOpen = false;
            playClick();
            return true;
        }

        if (over(mouseX, mouseY, panelX + 12, filterY, filterBoxW, 16)) {
            isDropdownOpen = true;
            playClick();
            return true;
        }

        List<CosmeticData> items = getFilteredItems();
        int columns = 4;
        int iconRealSize = 32;
        int spacing = 38;
        int startX = panelX + 16;
        int startY = filterY + 22;

        int index = 0;
        for (CosmeticData data : items) {
            int col = index % columns;
            int row = index / columns;

            int itemX = startX + (col * spacing);
            int itemY = startY + (row * spacing) - (int)scrollY;

            if (itemY < filterY + 16 || itemY > panelY + height - 32) {
                index++;
                continue;
            }

            // Estrela de favorito — checa ANTES do ícone inteiro, senão o clique nela também
            // equiparia o cosmético (a estrela fica sobreposta no canto do ícone, ver render()).
            int starSize = 9;
            int starX = itemX + iconRealSize - starSize;
            int starY = itemY - 1;
            if (over(mouseX, mouseY, starX, starY, starSize, starSize)) {
                playClick();
                com.f4xizzz.greatcosmetics.client.ClientFavoriteCosmetics.toggle(data.id);
                return true;
            }

            if (over(mouseX, mouseY, itemX, itemY, iconRealSize, iconRealSize)) {
                com.f4xizzz.greatcosmetics.platform.GcNet.toServer(new EquipCosmeticPayload(data.id, ClientCosmeticCache.isDevModeActive));
                playEquipSound();
                return true;
            }
            index++;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isDropdownOpen) return true;

        // mouseX já chega em coordenada "virtual" (Wardrobe3DScreen#mouseScrolled já fez essa
        // conversão antes de chamar aqui, igual mouseClicked nesta mesma classe já assume) —
        // dividir de novo por scaleMultiplier encolhia o valor errado, deslocando toda a área
        // onde o scroll funcionava pra bem longe de onde a lista realmente está desenhada.
        // O limite direito também não pode ser um "220" fixo: lastWidth acompanha a animação de
        // painel (currentPanelW faz lerp ao trocar de/para a aba Dev, que é bem mais larga), e um
        // número fixo ficava desalinhado do painel de verdade sempre que ele não estivesse
        // exatamente parado no tamanho de repouso — exatamente o "hitbox do scroll desalinhado"
        // reportado.
        if (mouseX >= lastX && mouseX <= lastX + lastWidth) {
            this.scrollY -= verticalAmount * 15f;
            if (this.scrollY < 0) this.scrollY = 0;
            if (this.scrollY > this.maxScrollY) this.scrollY = this.maxScrollY;
            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return searchField.isFocused() && searchField.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return searchField.isFocused() && searchField.charTyped(chr, modifiers);
    }

    private boolean over(double mx, double my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    private void playClick() {
        if (Minecraft.getInstance() != null) {
            try {
                ResourceLocation soundId = ResourceLocation.fromNamespaceAndPath("cobblemon", "gui_click");
                SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(soundId);
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(soundEvent, 1.0F));
            } catch (Exception ignored) {}
        }
    }

    private void playEquipSound() {
        if (Minecraft.getInstance() != null) {
            try {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F));
            } catch (Exception ignored) {}
        }
    }
}