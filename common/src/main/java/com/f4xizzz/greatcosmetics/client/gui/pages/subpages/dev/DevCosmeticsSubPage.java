package com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev;

import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager;
import com.f4xizzz.greatcosmetics.config.CosmeticData;
import com.f4xizzz.greatcosmetics.config.CosmeticsConfig;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

public class DevCosmeticsSubPage extends DevSubPage {

    public static DevCosmeticsSubPage INSTANCE;

    private enum State { LIST, EDITOR }
    private State currentState = State.LIST;

    private float scrollY = 0;
    private float maxScrollY = 0;

    // Guarda o scroll da grade (State.LIST) de onde o admin entrou pra editar um cosmético — scrollY
    // é reaproveitado pelas duas telas (grade da lista E outliner/properties do editor), então
    // entrar num cosmético zera scrollY pro editor (loadEditor) sem isso perder de vez a posição de
    // onde a lista estava. Ao voltar (botão de voltar ou depois de apagar), restaura daqui em vez de
    // zerar — sem isso, editar um cosmético no MEIO/FIM de uma lista grande sempre te devolvia lá no
    // topo, obrigando a rolar tudo de novo pra achar onde você estava.
    private float listScrollY = 0;

    // --- AUTOCOMPLETE (ver EditorRow#suggestions/addStringField/renderAutocompleteDropdown) ---
    // Recalculado a cada frame dentro do loop de render das rows (só quando o campo focado tem
    // suggestions != null) — nunca precisa ser "fechado" na mão: se o campo perde o foco, o próximo
    // frame simplesmente não seta essas variáveis de novo, e o dropdown some sozinho.
    private EditBox openDropdownField = null;
    private List<String> openDropdownFiltered = List.of();
    private int openDropdownX, openDropdownY, openDropdownW;
    private static final int AUTOCOMPLETE_ITEM_H = 12;
    private static final int AUTOCOMPLETE_MAX_ITEMS = 6;

    private String editingId = null;
    private String tempId = null;
    private CosmeticData editingData = null;
    private boolean editingIsArmorCosmetic = false;
    private String backupJson = null;
    private String backupRealItemId = null;
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    private FloatingPopup activePopup = null;

    // Popup de confirmação genérico (usado tanto pra apagar o cosmético inteiro quanto pra
    // remover uma Part avulsa) — mesma UI, só muda título/mensagem/ação de quem chamou.
    private boolean showConfirmPopup = false;
    private String confirmPopupTitle = "";
    private String confirmPopupMessage = "";
    private Runnable confirmPopupAction = null;

    private void openConfirmPopup(String title, String message, Runnable onConfirm) {
        this.confirmPopupTitle = title;
        this.confirmPopupMessage = message;
        this.confirmPopupAction = onConfirm;
        this.showConfirmPopup = true;
    }

    private boolean isDraggingPopup = false;
    private boolean isDraggingScrollbar = false;
    private boolean isDraggingPopupScrollbar = false;
    private int dragOffsetX = 0, dragOffsetY = 0;

    // Capturado no render() (que recebe x/y/width/height de verdade) pra mouseClicked/mouseDragged/
    // mouseScrolled (que não recebem bounds) poderem testar clique contra o MESMO retângulo que foi
    // desenhado nesse frame — sem isso, o painel Dev (bem mais largo que os 180px de sempre desde o
    // rebuild do HUD) desenhava largo mas testava clique contra o retângulo antigo, pequeno.
    private int lastX = 30, lastY = 50, lastWidth = 180, lastHeight = 260;

    private enum RowType { STRING, INT, DOUBLE, TOGGLE, BUTTON, GIZMO_MODE, GIZMO_AXIS, DIVIDER, EFFECT }
    private static class EditorRow {
        String label; RowType type; EditBox textField;
        boolean toggleValue; java.util.function.Consumer<Boolean> onToggle; Runnable onButtonClick;
        String effectRegistryId; int effectLevel; java.util.function.BiConsumer<String, Integer> onEffectLevelChange;

        // Identidade ESTÁVEL da row (independente do idioma) — toda checagem de lógica
        // (syncGizmoToFields, cor de botão, etc) compara isto, nunca o `label` (que agora é
        // texto traduzível vindo do Lang). Por padrão vale o próprio label; rows cujo label
        // foi traduzido e que ainda são chave de lógica setam um id fixo em inglês via withId().
        String id;

        // Explicação que aparece como tooltip ao passar o mouse em cima do LABEL da row (ver
        // renderRowTooltip) — null = sem tooltip. Existe pra tirar as informações técnicas que
        // antes ficavam direto no nome do campo (ex: "Main ID (File Name)") e explicar de verdade
        // o que o campo faz, sem obrigar quem tá vendo a lista toda a ler um texto longo o tempo
        // inteiro — só aparece quando o jogador passa o mouse em cima, pedido explícito do usuário
        // pra deixar o Dev Studio mais fácil de entender pra quem tá começando.
        String tooltip;

        // Bloqueia o clique de um BUTTON (visual acinzentado + onButtonClick não roda) — usado
        // pelo "Config Part" quando a Part é de uma armadura-cosmético real (sem GeckoLib): nesse
        // caso o render usa o modelo 3D de verdade da armadura (ver ArmorFeatureRendererMixin#
        // greatcosmetics$renderRealArmor), que já encaixa sozinho e IGNORA offset/rotação/escala —
        // deixar o botão clicável sugeria que esses campos fariam alguma diferença, quando não
        // fazem nada nesse caso. O "tooltip" explica o motivo pro jogador que passar o mouse.
        boolean disabled;

        // Fonte de sugestões de autocomplete pra esse campo (null = sem autocomplete). Supplier (não
        // uma List pronta) porque algumas fontes (scan do resourcepack, ids já configurados no mod)
        // podem mudar depois que a row foi criada — reavaliar só quando o campo tem foco de verdade
        // evita escanear resourcepack toda hora sem necessidade.
        java.util.function.Supplier<java.util.List<String>> suggestions;

        EditorRow(String label, RowType type, EditBox field) { this.label = label; this.id = label; this.type = type; this.textField = field; }
        EditorRow(String label, boolean startVal, java.util.function.Consumer<Boolean> onToggle) { this.label = label; this.id = label; this.type = RowType.TOGGLE; this.toggleValue = startVal; this.onToggle = onToggle; }
        EditorRow(String label, Runnable onButtonClick) { this.label = label; this.id = label; this.type = RowType.BUTTON; this.onButtonClick = onButtonClick; }
        EditorRow(String label, String effectRegistryId, int startLevel, java.util.function.BiConsumer<String, Integer> onEffectLevelChange) {
            this.label = label; this.id = label; this.type = RowType.EFFECT;
            this.effectRegistryId = effectRegistryId; this.effectLevel = startLevel; this.onEffectLevelChange = onEffectLevelChange;
        }

        EditorRow withId(String id) { this.id = id; return this; }
        EditorRow withTooltip(String tooltip) { this.tooltip = tooltip; return this; }
        EditorRow withDisabled(boolean disabled) { this.disabled = disabled; return this; }
    }

    // Preenchido no render() de cada frame (ver renderRowTooltip) com a row cujo LABEL o mouse
    // está em cima agora — desenhado por ÚLTIMO (depois de toda a lista, e depois até do popup),
    // senão outra row/o próprio popup desenharia por cima da caixinha do tooltip.
    private EditorRow hoveredTooltipRow = null;

    private final List<EditorRow> rows = new ArrayList<>();

    /** Atalho pro Lang (config/GreatCosmetics/lang/devstudio.json). */
    private static String L(String key, Object... ph) {
        return com.f4xizzz.greatcosmetics.config.LangConfig.legacy(key, ph);
    }

    // Itens selecionáveis do outliner pra cada Part (ver "MODELOS 3D" em loadEditor) — clicar
    // ativa o gizmo NA HORA (GizmoManager.activePart), sem precisar abrir o popup ">> Config Part".
    private final List<CosmeticData.CosmeticPart> outlinerParts = new ArrayList<>();
    private final List<Integer> outlinerPartRowIndex = new ArrayList<>();

    // --- BUSCA + FILTRO DE TYPE (State.LIST) ---
    // A busca é por nome exibido/id (ver matchesSearch); o filtro de type usa scanConfiguredTypes()
    // porque "type" é texto livre do Dev Studio (sem enum fixo, ao contrário do slot da Acessórios),
    // então a lista de opções precisa ser reconstruída a cada abertura do dropdown a partir do que
    // já está configurado em algum cosmético.
    private final EditBox searchField;
    private boolean isTypeDropdownOpen = false;
    private String typeFilter = "ALL";
    private List<String> typeFilterOptions = List.of("ALL");

    public DevCosmeticsSubPage(Wardrobe3DScreen parent, Runnable onBack) {
        super(parent, onBack);
        this.scrollY = 0;
        INSTANCE = this;

        this.searchField = new EditBox(parent.getTextRenderer(), 0, 0, 100, 16, Component.literal(""));
        this.searchField.setMaxLength(64);
        this.searchField.setHint(com.f4xizzz.greatcosmetics.config.LangConfig.text("devstudio.cosmetic.search_placeholder"));
        this.searchField.setResponder(text -> this.scrollY = 0);
    }

    /** true se o cosmético passa nos dois filtros da grade (busca por texto + type selecionado) —
     *  usado tanto no render() quanto no mouseClicked(), que precisam enxergar EXATAMENTE a mesma
     *  lista filtrada pro clique cair no item certo. */
    private boolean matchesListFilters(String id, CosmeticData data) {
        if (!"ALL".equals(typeFilter)) {
            String type = data != null && data.type != null ? data.type.trim() : "";
            if (!type.equalsIgnoreCase(typeFilter)) return false;
        }
        String query = searchField.getValue().trim().toLowerCase();
        if (query.isEmpty()) return true;
        String name = data != null ? data.getFormattedName().replaceAll("§.", "").toLowerCase() : "";
        return name.contains(query) || id.toLowerCase().contains(query);
    }

    /** Ids da grade (normais + armadura-cosmético, nessa ordem) já filtrados por matchesListFilters
     *  — {@code regularCountOut[0]} recebe quantos dos ids retornados são normais (o resto é
     *  armadura), já que render()/mouseClicked() precisam dessa contagem pra saber de qual mapa
     *  buscar cada CosmeticData. */
    private List<String> filteredListIds(int[] regularCountOut) {
        List<String> out = new ArrayList<>();
        for (String id : CosmeticsConfig.cosmeticsMap.keySet()) {
            if (matchesListFilters(id, CosmeticsConfig.cosmeticsMap.get(id))) out.add(id);
        }
        if (regularCountOut != null) regularCountOut[0] = out.size();
        for (String id : com.f4xizzz.greatcosmetics.client.ClientArmorCosmeticsCache.armorCosmetics.keySet()) {
            if (matchesListFilters(id, com.f4xizzz.greatcosmetics.client.ClientArmorCosmeticsCache.getSyntheticCosmetic(id))) out.add(id);
        }
        return out;
    }

    public boolean hasPendingChanges() {
        return this.hasUnsavedChanges;
    }

    /** Usado pelo Wardrobe3DScreen pra esconder o painel principal (abas + outliner/properties)
     *  enquanto o popup "Config Part" está aberto — activePopup é privado, sem isso não dava pra
     *  saber de fora se tem um popup ativo. */
    public boolean isPartPopupOpen() {
        return activePopup != null;
    }

    /** Chamado pela barrinha lateral esquerda (Wardrobe3DScreen#renderGizmoSidebar) ao apertar o
     *  botão de Sneak — activePopup é privado, então precisa desse método público pra sincronizar
     *  os campos de texto do popup (se estiver aberto) com os valores da pose que acabou de ficar
     *  ativa, sem esperar o próximo arraste do gizmo pra atualizar. */
    public void syncGizmoPopupIfOpen() {
        if (activePopup != null) activePopup.syncGizmoToFields();
    }

    public void attemptTabSwitch(Runnable onConfirm) {
        tryExit(onConfirm);
    }

    private EditorRow addDivider(String title) {
        EditorRow row = new EditorRow(title, RowType.DIVIDER, null);
        this.rows.add(row);
        return row;
    }

    private class FloatingPopup {
        String title; int x, y, width, height;
        List<EditorRow> rows = new ArrayList<>();
        float pScrollY = 0, pMaxScrollY = 0;

        FloatingPopup(String title, int x, int y, int width, int height) {
            this.title = title; this.x = x; this.y = y; this.width = width; this.height = height;
        }

        void addDivider(String label) {
            this.rows.add(new EditorRow(label, RowType.DIVIDER, null));
        }

        void addGizmoControls() {
            this.rows.add(new EditorRow("Modo Gizmo", RowType.GIZMO_MODE, null));
        }

        void syncGizmoToFields() {
            if (GizmoManager.activePart == null) return;
            // BUG (2026-09): TextFieldWidget#setText() dispara o changedListener SEMPRE, mesmo
            // reescrevendo o MESMO valor que já tava lá (confirmado no bytecode vanilla — setText
            // chama onChanged incondicionalmente, sem checar se o texto realmente mudou). Esse
            // método só ESPELHA o valor atual nos campos (chamado a cada frame de arraste E toda
            // vez que o botão "S" alterna qual conjunto de campos mostrar) — nunca deveria, sozinho,
            // marcar "alteração não salva". Snapshot/restore do flag (mesmo truque que
            // openPartPopup/openLurePopup já usam) cancela esses disparos espúrios sem precisar
            // desligar o listener; quem chamou isso por causa de uma mudança DE VERDADE (arraste
            // com delta != 0) já setou hasUnsavedChanges=true ANTES de chamar, então o valor
            // restaurado continua correto nesse caso.
            boolean wasUnsavedBeforeSync = hasUnsavedChanges;
            boolean isS = Wardrobe3DScreen.isPreviewSneaking;
            for (EditorRow r : rows) {
                if (r.textField == null) continue;
                try {
                    if (!isS) {
                        if (r.id.equals("Offset X")) r.textField.setValue(String.valueOf(Math.round(GizmoManager.activePart.offsetX * 1000.0) / 1000.0));
                        if (r.id.equals("Offset Y")) r.textField.setValue(String.valueOf(Math.round(GizmoManager.activePart.offsetY * 1000.0) / 1000.0));
                        if (r.id.equals("Offset Z")) r.textField.setValue(String.valueOf(Math.round(GizmoManager.activePart.offsetZ * 1000.0) / 1000.0));
                        if (r.id.equals("Rotation X")) r.textField.setValue(String.valueOf(Math.round(GizmoManager.activePart.rotationX * 10.0) / 10.0));
                        if (r.id.equals("Rotation Y")) r.textField.setValue(String.valueOf(Math.round(GizmoManager.activePart.rotationY * 10.0) / 10.0));
                        if (r.id.equals("Rotation Z")) r.textField.setValue(String.valueOf(Math.round(GizmoManager.activePart.rotationZ * 10.0) / 10.0));
                    } else {
                        if (r.id.equals("Shift Offset X")) r.textField.setValue(String.valueOf(Math.round(GizmoManager.activePart.shiftOffsetX * 1000.0) / 1000.0));
                        if (r.id.equals("Shift Offset Y")) r.textField.setValue(String.valueOf(Math.round(GizmoManager.activePart.shiftOffsetY * 1000.0) / 1000.0));
                        if (r.id.equals("Shift Offset Z")) r.textField.setValue(String.valueOf(Math.round(GizmoManager.activePart.shiftOffsetZ * 1000.0) / 1000.0));
                        if (r.id.equals("Shift Rotation X")) r.textField.setValue(String.valueOf(Math.round(GizmoManager.activePart.shiftRotationX * 10.0) / 10.0));
                        if (r.id.equals("Shift Rotation Y")) r.textField.setValue(String.valueOf(Math.round(GizmoManager.activePart.shiftRotationY * 10.0) / 10.0));
                        if (r.id.equals("Shift Rotation Z")) r.textField.setValue(String.valueOf(Math.round(GizmoManager.activePart.shiftRotationZ * 10.0) / 10.0));
                    }
                    if (r.id.equals("Scale X")) r.textField.setValue(String.valueOf(Math.round(GizmoManager.activePart.scaleX * 100.0) / 100.0));
                    if (r.id.equals("Scale Y")) r.textField.setValue(String.valueOf(Math.round(GizmoManager.activePart.scaleY * 100.0) / 100.0));
                    if (r.id.equals("Scale Z")) r.textField.setValue(String.valueOf(Math.round(GizmoManager.activePart.scaleZ * 100.0) / 100.0));
                } catch (Exception ignored) {}
            }
            hasUnsavedChanges = wasUnsavedBeforeSync;
        }

        void addFloat(String label, float startVal, java.util.function.Consumer<Float> action) {
            EditBox field = new EditBox(parent.getTextRenderer(), 0, 0, width - 30, 16, Component.literal(""));
            field.setMaxLength(15); field.setValue(String.valueOf(startVal));
            field.setResponder(text -> {
                hasUnsavedChanges = true;
                try { if (!text.isEmpty() && !text.equals("-") && !text.equals(".")) action.accept(Float.parseFloat(text)); } catch (Exception ignored) {}
            });
            this.rows.add(new EditorRow(label, RowType.DOUBLE, field));
        }

        void addDouble(String label, double startVal, java.util.function.Consumer<Double> action) {
            EditBox field = new EditBox(parent.getTextRenderer(), 0, 0, width - 30, 16, Component.literal(""));
            field.setMaxLength(20); field.setValue(String.valueOf(startVal));
            field.setResponder(text -> {
                hasUnsavedChanges = true;
                try { if (!text.isEmpty() && !text.equals("-") && !text.equals(".")) action.accept(Double.parseDouble(text)); } catch (Exception ignored) {}
            });
            this.rows.add(new EditorRow(label, RowType.DOUBLE, field));
        }

        void addInt(String label, int startVal, java.util.function.Consumer<Integer> action) {
            EditBox field = new EditBox(parent.getTextRenderer(), 0, 0, width - 30, 16, Component.literal(""));
            field.setMaxLength(10); field.setValue(String.valueOf(startVal));
            field.setResponder(text -> {
                hasUnsavedChanges = true;
                try { if (!text.isEmpty() && !text.equals("-")) action.accept(Integer.parseInt(text)); } catch (Exception ignored) {}
            });
            this.rows.add(new EditorRow(label, RowType.INT, field));
        }

        void addString(String label, String startVal, java.util.function.Consumer<String> action) {
            addString(label, startVal, null, action);
        }

        /** Igual addString, com autocomplete (ver EditorRow#suggestions/renderAutocompleteDropdown
         *  — o dropdown é compartilhado com o editor de fora, essa classe só precisa alimentar as
         *  mesmas variáveis openDropdown* quando UMA DAS SUAS PRÓPRIAS rows está focada). */
        void addString(String label, String startVal, java.util.function.Supplier<java.util.List<String>> suggestions, java.util.function.Consumer<String> action) {
            EditBox field = new EditBox(parent.getTextRenderer(), 0, 0, width - 30, 16, Component.literal(""));
            field.setMaxLength(128); field.setValue(startVal != null ? startVal : "");
            field.setResponder(text -> {
                hasUnsavedChanges = true;
                action.accept(text);
            });
            EditorRow row = new EditorRow(label, RowType.STRING, field);
            row.suggestions = suggestions;
            this.rows.add(row);
        }

        void addToggle(String label, boolean startVal, java.util.function.Consumer<Boolean> action) {
            this.rows.add(new EditorRow(label, startVal, action));
        }

        void addEffect(String label, String effectRegistryId, int startLevel, java.util.function.BiConsumer<String, Integer> onChange) {
            this.rows.add(new EditorRow(label, effectRegistryId, startLevel, onChange));
        }

        void drawBtn(GuiGraphics c, String label, int bx, int by, int bw, int bh, int mx, int my, int activeColor) {
            boolean hov = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
            c.fill(bx, by, bx + bw, by + bh, hov ? 0xFFFFAA00 : activeColor);
            c.drawCenteredString(parent.getTextRenderer(), label, bx + (bw/2), by + 3, 0xFFFFFF);
        }

        boolean checkClick(double mx, double my, int bx, int by, int bw, int bh) {
            return mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
        }

        void render(GuiGraphics c, int mx, int my, float delta) {
            c.pose().pushPose();
            c.pose().translate(0, 0, 100);

            c.fill(x, y, x + width, y + height, 0xF2111111);
            c.renderOutline(x, y, width, height, 0xFFFFAA00);

            c.fill(x, y, x + width, y + 16, 0xFFFFAA00);
            c.drawString(parent.getTextRenderer(), "§l" + title, x + 5, y + 4, 0xFFFFFF);

            boolean hovClose = mx >= x + width - 16 && mx <= x + width && my >= y && my <= y + 16;
            c.fill(x + width - 16, y, x + width, y + 16, hovClose ? 0xFFFF5555 : 0x00000000);
            c.drawCenteredString(parent.getTextRenderer(), "X", x + width - 8, y + 4, 0xFFFFFF);

            int listY = y + 20;
            int rowHeight = 35;
            this.pMaxScrollY = Math.max(0, (rows.size() * rowHeight) - (height - 25));

            // enableScissorStacked (não enablePerfectScissor) — os rows abaixo renderizam
            // TextFieldWidget, que mexe no scissor do PRÓPRIO DrawContext internamente pra recortar
            // o texto digitado. Um RenderSystem.enableScissor() cru (o que enablePerfectScissor usa)
            // fica CEGO pra esse scissor aninhado — quando o campo de texto desliga o dele, desliga
            // o nosso junto, e todo row desenhado DEPOIS na mesma lista (mais pra baixo, exposto ao
            // rolar) passava a desenhar sem recorte nenhum, vazando pra fora do popup.
            parent.enableScissorStacked(c, x, listY, width, height - 25);
            for (int i = 0; i < rows.size(); i++) {
                EditorRow row = rows.get(i);
                int rowY = listY + (i * rowHeight) - (int)pScrollY;

                if (rowY < listY - rowHeight || rowY > y + height) {
                    if (row.textField != null) { row.textField.visible = false; row.textField.active = false; }
                    continue;
                }

                // Mesmo hover-check do editor principal (ver render() da classe de fora) — o campo
                // hoveredTooltipRow é compartilhado, sem conflito: quando o popup está aberto o loop
                // de rows de fora nem roda (ver "activePopup == null" no render() de fora).
                if (row.tooltip != null && mx >= x + 5 && mx <= x + width - 5 && my >= rowY - 2 && my <= rowY + 30) {
                    hoveredTooltipRow = row;
                }

                if (row.type == RowType.DIVIDER) {
                    c.fill(x + 5, rowY + 4, x + width - 5, rowY + 20, 0x88FFAA00);
                    c.drawCenteredString(parent.getTextRenderer(), row.label, x + (width/2), rowY + 8, 0xFFFFFF);
                    continue;
                }

                if (row.type == RowType.GIZMO_MODE) {
                    c.drawString(parent.getTextRenderer(), L("devstudio.part.gizmo_hint"), x + 10, rowY - 2, 0xFFFFFF);
                    int bw = (width - 30) / 3;
                    drawBtn(c, L("devstudio.part.gizmo_move"), x + 10, rowY + 12, bw, 14, mx, my, GizmoManager.currentMode == GizmoManager.Mode.TRANSLATE ? 0xFF22AA22 : 0xFF444444);
                    drawBtn(c, L("devstudio.part.gizmo_rotate"), x + 10 + bw + 2, rowY + 12, bw, 14, mx, my, GizmoManager.currentMode == GizmoManager.Mode.ROTATE ? 0xFF22AA22 : 0xFF444444);
                    drawBtn(c, L("devstudio.part.gizmo_scale"), x + 10 + bw*2 + 4, rowY + 12, bw, 14, mx, my, GizmoManager.currentMode == GizmoManager.Mode.SCALE ? 0xFF22AA22 : 0xFF444444);
                } else {
                    c.drawString(parent.getTextRenderer(), "§f" + row.label, x + 10, rowY, 0xFFFFFF);

                    if (row.type == RowType.STRING || row.type == RowType.INT || row.type == RowType.DOUBLE) {
                        row.textField.visible = true; row.textField.active = true;
                        row.textField.setX(x + 10); row.textField.setY(rowY + 12);
                        row.textField.render(c, mx, my, delta);

                        if (row.suggestions != null && row.textField.isFocused()) {
                            openDropdownField = row.textField;
                            openDropdownX = x + 10;
                            openDropdownY = rowY + 12 + 16;
                            openDropdownW = width - 30;
                            openDropdownFiltered = filterSuggestions(row.suggestions.get(), row.textField.getValue());
                        }
                    } else if (row.type == RowType.TOGGLE) {
                        boolean hovTog = mx >= x + 10 && mx <= x + width - 20 && my >= rowY + 12 && my <= rowY + 28;
                        boolean isSneak = row.id.contains("SNEAK");
                        int colorOn = isSneak ? 0xFFBB00FF : 0xFF00FF00;
                        int colorBgOn = isSneak ? 0x66BB00FF : 0x66FFFFFF;

                        c.fill(x + 10, rowY + 12, x + width - 20, rowY + 28, hovTog ? colorBgOn : 0x44000000);
                        c.renderOutline(x + 10, rowY + 12, width - 30, 16, row.toggleValue ? colorOn : 0xFFFF0000);
                        c.drawCenteredString(parent.getTextRenderer(), L(row.toggleValue ? "devstudio.common.on" : "devstudio.common.off"), x + (width/2) - 5, rowY + 16, 0xFFFFFF);
                    } else if (row.type == RowType.EFFECT) {
                        boolean active = row.effectLevel > 0;
                        boolean hovEff = mx >= x + 10 && mx <= x + width - 20 && my >= rowY + 12 && my <= rowY + 28;
                        c.fill(x + 10, rowY + 12, x + width - 20, rowY + 28, hovEff ? 0x66FFFFFF : 0x44000000);
                        c.renderOutline(x + 10, rowY + 12, width - 30, 16, active ? 0xFF00FF00 : 0xFF444444);
                        String levelLabel = active ? L("devstudio.effects.level", "n", row.effectLevel) : L("devstudio.effects.off");
                        c.drawCenteredString(parent.getTextRenderer(), levelLabel, x + (width/2) - 5, rowY + 16, active ? 0xFFFFFF : 0xAAAAAA);
                    }
                }
            }
            c.disableScissor();

            if (pMaxScrollY > 0) {
                int scrollX = x + width - 6;
                c.fill(scrollX, listY, scrollX + 4, listY + height - 25, 0x44000000);
                int h = Math.max(10, (int) ((height - 25) * ((float) (height - 25) / (height - 25 + pMaxScrollY))));
                int hY = listY + (int) (((height - 25) - h) * (pScrollY / pMaxScrollY));
                c.fill(scrollX, hY, scrollX + 4, hY + h, 0xFFAAAAAA);
            }
            c.pose().popPose();
        }

        boolean mouseClicked(double mx, double my, int button) {
            if (mx >= x && mx <= x + width && my >= y && my <= y + height) {
                if (mx >= x && mx <= x + width - 16 && my >= y && my <= y + 16) {
                    isDraggingPopup = true; dragOffsetX = (int) mx - x; dragOffsetY = (int) my - y; return true;
                }
                if (mx >= x + width - 16 && mx <= x + width && my >= y && my <= y + 16) {
                    playClick(); GizmoManager.activePart = null; GizmoManager.currentAxis = GizmoManager.Axis.NONE; activePopup = null; return true;
                }
                if (pMaxScrollY > 0) {
                    int scrollX = x + width - 6; int listY = y + 20; int viewHeight = height - 25;
                    if (mx >= scrollX && mx <= scrollX + 4 && my >= listY && my <= listY + viewHeight) {
                        isDraggingPopupScrollbar = true; return true;
                    }
                }

                boolean clickedAny = false;
                int listY = y + 20; int rowHeight = 35;
                for (EditorRow row : rows) {
                    int rowY = listY + (rows.indexOf(row) * rowHeight) - (int)pScrollY;
                    if (rowY >= listY - rowHeight && rowY <= y + height) {
                        if (row.type == RowType.DIVIDER) continue;

                        if (row.type == RowType.GIZMO_MODE) {
                            int bw = (width - 30) / 3;
                            if (checkClick(mx, my, x + 10, rowY + 12, bw, 14)) { playClick(); GizmoManager.currentMode = GizmoManager.Mode.TRANSLATE; clickedAny = true; }
                            if (checkClick(mx, my, x + 10 + bw + 2, rowY + 12, bw, 14)) { playClick(); GizmoManager.currentMode = GizmoManager.Mode.ROTATE; clickedAny = true; }
                            if (checkClick(mx, my, x + 10 + bw*2 + 4, rowY + 12, bw, 14)) { playClick(); GizmoManager.currentMode = GizmoManager.Mode.SCALE; clickedAny = true; }
                        }

                        if (row.textField != null) {
                            row.textField.setX(x + 10); row.textField.setY(rowY + 12);
                            if (mx >= x + 10 && mx <= x + width - 20 && my >= rowY + 12 && my <= rowY + 28) {
                                row.textField.setFocused(true); row.textField.mouseClicked(mx, my, button); clickedAny = true;
                                for (EditorRow r : rows) if (r != row && r.textField != null) r.textField.setFocused(false);
                                return true;
                            }
                        } else if (row.type == RowType.TOGGLE) {
                            if (mx >= x + 10 && mx <= x + width - 20 && my >= rowY + 12 && my <= rowY + 28) {
                                playClick();
                                if (!row.id.contains("SNEAK")) { hasUnsavedChanges = true; }
                                row.toggleValue = !row.toggleValue; row.onToggle.accept(row.toggleValue);
                                return true;
                            }
                        } else if (row.type == RowType.EFFECT) {
                            if (mx >= x + 10 && mx <= x + width - 20 && my >= rowY + 12 && my <= rowY + 28) {
                                playClick();
                                hasUnsavedChanges = true;
                                // Cicla: OFF -> I -> II -> III -> IV -> V -> OFF
                                row.effectLevel = (row.effectLevel + 1) % 6;
                                row.onEffectLevelChange.accept(row.effectRegistryId, row.effectLevel);
                                return true;
                            }
                        }
                    }
                }
                if (!clickedAny) { for (EditorRow r : rows) if (r.textField != null) r.textField.setFocused(false); }
                // clickedAny (não "true" cru): título/fechar/scrollbar já retornaram true mais
                // acima, cada um na hora — chegar até aqui só acontece clicando num espaço VAZIO
                // do popup (padding entre rows, etc). Antes isso sempre "consumia" o clique mesmo
                // sem fazer nada, e como o popup é bem grande e cobre boa parte da tela (inclusive
                // onde o personagem/gizmo aparecem), o clique nunca chegava no GizmoManager.tryGrab
                // — o gizmo ficava visível mas 100% inclicável sempre que o popup estava aberto.
                return clickedAny;
            }
            return false;
        }
    }

    private void loadEditor(String id) {
        this.editingId = id;
        this.tempId = id;
        this.editingIsArmorCosmetic = com.f4xizzz.greatcosmetics.client.ClientArmorCosmeticsCache.armorCosmetics.containsKey(id);
        this.editingData = this.editingIsArmorCosmetic
                ? com.f4xizzz.greatcosmetics.client.ClientArmorCosmeticsCache.armorCosmetics.get(id)
                : CosmeticsConfig.cosmeticsMap.get(id);
        this.backupJson = this.editingData != null ? GSON.toJson(this.editingData) : null;
        this.backupRealItemId = this.editingData != null ? this.editingData.realItemId : null;

        Wardrobe3DScreen.previewCosmeticId = id;

        if (this.currentState == State.LIST) this.listScrollY = this.scrollY;
        this.currentState = State.EDITOR;
        this.scrollY = 0;
        this.rows.clear();
        this.outlinerParts.clear();
        this.outlinerPartRowIndex.clear();
        this.activePopup = null;
        GizmoManager.activePart = null;
        GizmoManager.currentAxis = GizmoManager.Axis.NONE;

        if (this.editingData == null) return;

        if (this.editingData.lure == null) this.editingData.lure = new CosmeticData.LureStats();
        if (this.editingData.parts == null) this.editingData.parts = new ArrayList<>();
        if (this.editingData.parts.isEmpty()) this.editingData.parts.add(new CosmeticData.CosmeticPart(CosmeticData.Anchor.HEAD));

        // --- PREVIEW ---
        // Preview "SNEAK" virou a barrinha lateral esquerda (só visível com uma Part ativa — ver
        // Wardrobe3DScreen#renderGizmoSidebar), não é mais uma linha aqui na lista.

        // --- IDENTIFICAÇÃO BÁSICA ---
        // Armadura-cosmético: o "ID Principal" não é editável aqui (renomear trocaria a chave do
        // mapa sem mover o resto dos dados) — mostra o id de cosmético (usado em /gc give,
        // /gc giveitem, /gc cosmetics equip, tags...) direto no cabeçalho, já que ele some do resto
        // do formulário, e o campo do item real que ela representa logo abaixo.
        if (this.editingIsArmorCosmetic) {
            addDivider(L("devstudio.cosmetic.divider.identification_armor", "id", this.editingId));
            addStringField(L("devstudio.cosmetic.field.real_item"), this.editingData.realItemId, text -> this.editingData.realItemId = text.toLowerCase().trim())
                    .withTooltip(L("devstudio.cosmetic.tooltip.real_item"));
        } else {
            addDivider(L("devstudio.cosmetic.divider.identification"));
            addStringField(L("devstudio.cosmetic.field.main_id"), this.tempId, text -> this.tempId = text)
                    .withTooltip(L("devstudio.cosmetic.tooltip.main_id"));
            // Nome do arquivo de ícone (textures/icons/<nome>.png) — vazio usa o ID acima, igual
            // sempre foi. Só existe pra reaproveitar o MESMO arquivo de ícone entre cosméticos
            // diferentes (ex: variações de cor) sem duplicar a textura com nomes repetidos.
            addStringField(L("devstudio.cosmetic.field.icon_name"), this.editingData.iconId,
                    L("devstudio.cosmetic.hint.icon_name", "id", id),
                    () -> scanResourceShortNames("textures/icons", rid -> rid.getNamespace().equals("greatcosmetics") && rid.getPath().endsWith(".png"), ".png"),
                    text -> this.editingData.iconId = text.trim())
                    .withTooltip(L("devstudio.cosmetic.tooltip.icon_name"));
        }

        String safeNameToLoad = (this.editingData.DisplayName != null && !this.editingData.DisplayName.trim().isEmpty())
                ? this.editingData.DisplayName
                : "&d" + id.substring(0, 1).toUpperCase() + id.substring(1);
        addStringField(L("devstudio.cosmetic.field.display_name"), safeNameToLoad, text -> { this.editingData.DisplayName = text; })
                .withTooltip(L("devstudio.cosmetic.tooltip.display_name"));

        addStringField(L("devstudio.cosmetic.field.slot"), this.editingData.slot != null ? this.editingData.slot.name() : "HEAD", null,
                () -> java.util.Arrays.stream(CosmeticData.VirtualSlot.values()).map(Enum::name).collect(java.util.stream.Collectors.toList()),
                text -> {
            try { this.editingData.slot = CosmeticData.VirtualSlot.valueOf(text.toUpperCase()); } catch (Exception ignored) {}
        }).withTooltip(L("devstudio.cosmetic.tooltip.slot"));
        addStringField(L("devstudio.cosmetic.field.type"), this.editingData.type, null, DevCosmeticsSubPage::scanConfiguredTypes, text -> this.editingData.type = text)
                .withTooltip(L("devstudio.cosmetic.tooltip.type"));
        addStringField(L("devstudio.cosmetic.field.permission"), this.editingData.permission, text -> this.editingData.permission = text)
                .withTooltip(L("devstudio.cosmetic.tooltip.permission"));

        // --- PARTES 3D: offset/rotação/escala por parte, igual cosmético normal.
        // "GeckoLib Model ID" tem PRIORIDADE sobre tudo (inclusive sobre o item real de armadura-
        // cosmético) — espera geo/item/<id>.geo.json + textures/item/<id>.png no resourcepack
        // (qualquer namespace), ver GreatCosmeticsClient#registerGeoModels. Vazio = comportamento
        // de sempre (ícone chapado pra cosmético normal, item real dobrado pra armadura-cosmético).
        addDivider(L("devstudio.cosmetic.divider.models3d"));
        for (int i = 0; i < this.editingData.parts.size(); i++) {
            CosmeticData.CosmeticPart part = this.editingData.parts.get(i);
            int partIndex = i;
            // Sem autocomplete (suggestions == null) nesses dois campos — scanResourceShortNames()
            // varre o ResourceManager inteiro (rm.findResources) e era refeito TODO FRAME enquanto o
            // campo estivesse focado (ver render()/EditorRow#suggestions), o que derrubava o FPS
            // brutalmente ao clicar numa Part. O campo continua editável igual, só perde a sugestão.
            if (!this.editingIsArmorCosmetic) {
                addStringField(L("devstudio.cosmetic.field.part_model", "i", i), part.customModelData_or_ID,
                        L("devstudio.cosmetic.hint.part_model"),
                        t -> part.customModelData_or_ID = t)
                        .withTooltip(L("devstudio.cosmetic.tooltip.part_model"));
            }
            addStringField(L("devstudio.cosmetic.field.part_geo", "i", i), part.geoModelId,
                    L("devstudio.cosmetic.hint.part_geo"),
                    t -> part.geoModelId = t)
                    .withTooltip(L("devstudio.cosmetic.tooltip.part_geo"));
            // Alterna entre "nome solto" (busca por qualquer pasta, só pelo nome do arquivo, e no
            // caso do Model/ID restrita ao namespace greatcosmetics) e "caminho exato" (o texto
            // acima vira o caminho relativo completo, ex: "sas/cigarro", buscado em qualquer
            // namespace) — ver CosmeticPart#useExactPath.
            this.rows.add(new EditorRow(L("devstudio.cosmetic.field.part_exact_path", "i", i), part.useExactPath, v -> part.useExactPath = v)
                    .withTooltip(L("devstudio.cosmetic.tooltip.part_exact_path")));
            outlinerParts.add(part);
            outlinerPartRowIndex.add(this.rows.size());
            boolean usesRealArmorModel = partUsesRealArmorRender(part);
            this.rows.add(new EditorRow(L("devstudio.cosmetic.btn.config_part", "i", i), () -> openPartPopup(part, partIndex))
                    .withId("btn_config_part_" + i)
                    .withDisabled(usesRealArmorModel)
                    .withTooltip(usesRealArmorModel ? L("devstudio.cosmetic.tooltip.config_part_disabled_armor") : L("devstudio.cosmetic.tooltip.config_part")));
            this.rows.add(new EditorRow(L("devstudio.cosmetic.btn.remove_part", "i", i), () -> {
                openConfirmPopup(L("devstudio.cosmetic.confirm.remove_part_title"), L("devstudio.cosmetic.confirm.remove_part_body", "i", partIndex), () -> {
                    hasUnsavedChanges = true;
                    this.editingData.parts.remove(part);
                    if (GizmoManager.activePart == part) GizmoManager.activePart = null;
                    loadEditor(this.editingId);
                });
            }).withId("btn_remove_part_" + i));
        }
        this.rows.add(new EditorRow(L("devstudio.cosmetic.btn.add_part"), () -> {
            hasUnsavedChanges = true;
            this.editingData.parts.add(new CosmeticData.CosmeticPart(CosmeticData.Anchor.HEAD));
            loadEditor(this.editingId);
        }).withId("btn_add_part").withTooltip(L("devstudio.cosmetic.tooltip.add_part")));

        // --- STATUS DE COMBATE ---
        addDivider(L("devstudio.cosmetic.divider.status_combat"));
        addIntField(L("devstudio.cosmetic.field.armor"), this.editingData.armor, val -> this.editingData.armor = val)
                .withTooltip(L("devstudio.cosmetic.tooltip.armor"));
        addDoubleField(L("devstudio.cosmetic.field.toughness"), this.editingData.toughness, val -> this.editingData.toughness = val)
                .withTooltip(L("devstudio.cosmetic.tooltip.toughness"));
        addIntField(L("devstudio.cosmetic.field.max_durability"), this.editingData.maxDurability, val -> this.editingData.maxDurability = val)
                .withTooltip(L("devstudio.cosmetic.tooltip.max_durability"));
        this.rows.add(new EditorRow(L("devstudio.cosmetic.field.auto_feed"), this.editingData.AutoFeed, val -> this.editingData.AutoFeed = val)
                .withTooltip(L("devstudio.cosmetic.tooltip.auto_feed")));

        // --- MOCHILA ---
        addDivider(L("devstudio.cosmetic.divider.backpack"));
        this.rows.add(new EditorRow(L("devstudio.cosmetic.field.is_backpack"), this.editingData.isBackpack, val -> this.editingData.isBackpack = val)
                .withTooltip(L("devstudio.cosmetic.tooltip.is_backpack")));
        addIntField(L("devstudio.cosmetic.field.backpack_rows"), this.editingData.backpackRows, val -> this.editingData.backpackRows = val)
                .withTooltip(L("devstudio.cosmetic.tooltip.backpack_rows"));
        addStringField(L("devstudio.cosmetic.field.backpack_name"), this.editingData.backpackDisplayName, text -> this.editingData.backpackDisplayName = text)
                .withTooltip(L("devstudio.cosmetic.tooltip.backpack_name"));

        // --- EFEITOS ESPECIAIS ---
        addDivider(L("devstudio.cosmetic.divider.special_effects"));
        this.rows.add(new EditorRow(L("devstudio.cosmetic.field.enable_fly"), this.editingData.EnableFly, val -> this.editingData.EnableFly = val)
                .withTooltip(L("devstudio.cosmetic.tooltip.enable_fly")));
        addDoubleField(L("devstudio.cosmetic.field.fly_speed"), this.editingData.flySpeedMultiplier, val -> this.editingData.flySpeedMultiplier = val)
                .withTooltip(L("devstudio.cosmetic.tooltip.fly_speed"));
        addDoubleField(L("devstudio.cosmetic.field.ground_speed"), this.editingData.groundSpeedMultiplier, val -> this.editingData.groundSpeedMultiplier = val)
                .withTooltip(L("devstudio.cosmetic.tooltip.ground_speed"));
        addDoubleField(L("devstudio.cosmetic.field.swim_speed"), this.editingData.swimSpeedMultiplier, val -> this.editingData.swimSpeedMultiplier = val)
                .withTooltip(L("devstudio.cosmetic.tooltip.swim_speed"));
        this.rows.add(new EditorRow(L("devstudio.cosmetic.btn.select_effects", "count", (this.editingData.effects != null ? this.editingData.effects.size() : 0)), this::openEffectsPopup)
                .withId("btn_select_effects").withTooltip(L("devstudio.cosmetic.tooltip.select_effects")));
        addStringField(L("devstudio.cosmetic.field.effect_visual"), String.join(", ", this.editingData.effectVisual), text -> this.editingData.effectVisual = parseList(text))
                .withTooltip(L("devstudio.cosmetic.tooltip.effect_visual"));
        addStringField(L("devstudio.cosmetic.field.fly_particle"), String.join(", ", this.editingData.flyParticle), text -> this.editingData.flyParticle = parseList(text))
                .withTooltip(L("devstudio.cosmetic.tooltip.fly_particle"));

        // --- LURE ---
        addDivider(L("devstudio.cosmetic.divider.lure"));
        this.rows.add(new EditorRow(L("devstudio.cosmetic.btn.config_lure"), this::openLurePopup).withId("btn_config_lure"));

        this.hasUnsavedChanges = false;

        // --- SONS PERSONALIZADOS ---
        addDivider(L("devstudio.cosmetic.divider.sounds"));
        if (this.editingData.sounds == null) this.editingData.sounds = new CosmeticData.CosmeticSounds();

        addStringField("Idle Sound", this.editingData.sounds.idleSound, text -> this.editingData.sounds.idleSound = text);
        addDoubleField("Idle Volume", this.editingData.sounds.idleVolume, val -> this.editingData.sounds.idleVolume = val);
        addDoubleField("Idle Pitch", this.editingData.sounds.idlePitch, val -> this.editingData.sounds.idlePitch = val);

        addStringField("Equip Sound", this.editingData.sounds.equipSound, text -> this.editingData.sounds.equipSound = text);
        addStringField("Unequip Sound", this.editingData.sounds.unequipSound, text -> this.editingData.sounds.unequipSound = text);

        addStringField("Walk Sound", this.editingData.sounds.walkSound, text -> this.editingData.sounds.walkSound = text);
        addDoubleField("Walk Volume", this.editingData.sounds.walkVolume, val -> this.editingData.sounds.walkVolume = val);
        addDoubleField("Walk Pitch", this.editingData.sounds.walkPitch, val -> this.editingData.sounds.walkPitch = val);

        addStringField("Fly Sound", this.editingData.sounds.flySound, text -> this.editingData.sounds.flySound = text);
        addDoubleField("Fly Volume", this.editingData.sounds.flyVolume, val -> this.editingData.sounds.flyVolume = val);
        addDoubleField("Fly Pitch", this.editingData.sounds.flyPitch, val -> this.editingData.sounds.flyPitch = val);

        addStringField("Shift Sound", this.editingData.sounds.shiftSound, text -> this.editingData.sounds.shiftSound = text);
        addDoubleField("Shift Volume", this.editingData.sounds.shiftVolume, val -> this.editingData.sounds.shiftVolume = val);
        addDoubleField("Shift Pitch", this.editingData.sounds.shiftPitch, val -> this.editingData.sounds.shiftPitch = val);

        addStringField("Backpack Sound", this.editingData.sounds.backpackSound, text -> this.editingData.sounds.backpackSound = text);
        addDoubleField("Backpack Volume", this.editingData.sounds.backpackVolume, val -> this.editingData.sounds.backpackVolume = val);
        addDoubleField("Backpack Pitch", this.editingData.sounds.backpackPitch, val -> this.editingData.sounds.backpackPitch = val);
    }



    /** Largura da coluna do outliner (esquerda) — proporcional ao painel, com um mínimo pra não
     *  esmagar o texto e um máximo pra sobrar espaço de verdade pras properties na direita. */
    private int outlinerWidth(int panelWidth) {
        return Math.min(120, Math.max(80, panelWidth / 3));
    }

    /** Quantas colunas cabem na grade de cosméticos do estado LIST — o painel Dev ficou bem mais
     *  largo que os 180px de sempre, então trava um item-alvo de ~52px em vez de sempre 3 colunas
     *  fixas, senão o painel novo desenhava só 3 ícones gigantes espalhados no meio do vazio. */
    private int gridColumns(int panelWidth, int spacing) {
        int targetItemSize = 52;
        return Math.max(3, (panelWidth - 20 + spacing) / (targetItemSize + spacing));
    }

    /** Qual divisor (=== SEÇÃO ===) está "no topo" da área visível agora — usado só pra destacar
     *  no outliner qual seção a coluna de properties está mostrando no momento. */
    private int activeDividerIndex(int rowHeight) {
        int topRow = (int) (scrollY / rowHeight);
        int result = -1;
        for (int i = 0; i < this.rows.size(); i++) {
            if (this.rows.get(i).type != RowType.DIVIDER) continue;
            if (i <= topRow) result = i; else break;
        }
        return result == -1 ? firstDividerIndex() : result;
    }

    private int firstDividerIndex() {
        for (int i = 0; i < this.rows.size(); i++) if (this.rows.get(i).type == RowType.DIVIDER) return i;
        return -1;
    }

    /** "=== IDENTIFICAÇÃO (ID: xyz) ===" -> "IDENTIFICAÇÃO" — o outliner só precisa do nome curto
     *  da seção, o parêntese com detalhes fica só no divisor de verdade lá nas properties. */
    private String shortSectionLabel(String dividerLabel) {
        String s = dividerLabel.replace("=", "").trim();
        int paren = s.indexOf('(');
        if (paren > 0) s = s.substring(0, paren).trim();
        return s;
    }

    /** Registries do mundo atual — precisa pra montar Text de verdade a partir de MiniMessage (ver
     *  CosmeticData.getFormattedNameText). Sempre disponível aqui: essa tela só abre com o player
     *  dentro de um mundo carregado. */
    private net.minecraft.core.HolderLookup.Provider registries() {
        var world = Minecraft.getInstance().level;
        return world != null ? world.registryAccess() : null;
    }

    private List<String> parseList(String input) {
        List<String> list = new ArrayList<>();
        for (String s : input.split(",")) { if (!s.trim().isEmpty()) list.add(s.trim()); }
        return list;
    }

    /** true quando essa Part vai renderizar pelo modelo 3D de armadura de VERDADE (ver
     *  ArmorFeatureRendererMixin#greatcosmetics$renderRealArmor) em vez do ícone chapado
     *  configurável — mesma condição usada lá, replicada aqui só pra decidir a UI (desabilitar o
     *  botão "Config Part", já que offset/rotação/escala não têm efeito nenhum nesse caso). Só
     *  vale pra armadura-cosmético (item real) sem GeckoLib e com anchor HEAD apontando pra um
     *  ArmorItem de slot HEAD de verdade — qualquer outra combinação (GeckoLib setado, anchor
     *  diferente, item não-armadura) continua usando os campos normalmente. */
    private boolean partUsesRealArmorRender(CosmeticData.CosmeticPart part) {
        if (!this.editingIsArmorCosmetic || this.editingData == null) return false;
        if (part.geoModelId != null && !part.geoModelId.isBlank()) return false;
        if (part.anchor != CosmeticData.Anchor.HEAD) return false;

        String realItemId = this.editingData.realItemId;
        if (realItemId == null || realItemId.isBlank()) return false;
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(realItemId);
        net.minecraft.world.item.Item item = id != null ? net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id) : null;
        return item instanceof net.minecraft.world.item.ArmorItem armorItem
                && armorItem.getEquipmentSlot() == net.minecraft.world.entity.EquipmentSlot.HEAD;
    }

    private void openPartPopup(CosmeticData.CosmeticPart part, int index) {
        boolean wasUnsaved = this.hasUnsavedChanges;

        String pName = part.customModelData_or_ID != null && !part.customModelData_or_ID.isEmpty() ? part.customModelData_or_ID : L("devstudio.part.new");
        activePopup = new FloatingPopup(L("devstudio.part.popup_title", "i", index, "name", pName), 80, 40, 260, 320);

        // Atrela o Gizmo
        GizmoManager.activePart = part;
        GizmoManager.onUpdate = () -> {
            this.hasUnsavedChanges = true;
            if (activePopup != null) activePopup.syncGizmoToFields();
        };

        activePopup.addDivider(L("devstudio.part.divider.gizmo_tool"));
        activePopup.addGizmoControls();

        activePopup.addDivider(L("devstudio.part.divider.general"));
        activePopup.addString("Anchor", part.anchor != null ? part.anchor.name() : "HEAD",
                () -> java.util.Arrays.stream(CosmeticData.Anchor.values()).map(Enum::name).collect(java.util.stream.Collectors.toList()),
                text -> {
            try { part.anchor = CosmeticData.Anchor.valueOf(text.toUpperCase().trim()); } catch (Exception ignored) {}
        });

        activePopup.addDivider(L("devstudio.part.divider.scale"));
        activePopup.addFloat("Scale X", part.scaleX, v -> part.scaleX = v);
        activePopup.addFloat("Scale Y", part.scaleY, v -> part.scaleY = v);
        activePopup.addFloat("Scale Z", part.scaleZ, v -> part.scaleZ = v);

        activePopup.addDivider(L("devstudio.part.divider.normal_values"));
        activePopup.addFloat("Offset X", part.offsetX, v -> part.offsetX = v);
        activePopup.addFloat("Offset Y", part.offsetY, v -> part.offsetY = v);
        activePopup.addFloat("Offset Z", part.offsetZ, v -> part.offsetZ = v);
        activePopup.addFloat("Rotation X", part.rotationX, v -> part.rotationX = v);
        activePopup.addFloat("Rotation Y", part.rotationY, v -> part.rotationY = v);
        activePopup.addFloat("Rotation Z", part.rotationZ, v -> part.rotationZ = v);

        activePopup.addDivider(L("devstudio.part.divider.sneak_values"));
        activePopup.addFloat("Shift Offset X", part.shiftOffsetX, v -> part.shiftOffsetX = v);
        activePopup.addFloat("Shift Offset Y", part.shiftOffsetY, v -> part.shiftOffsetY = v);
        activePopup.addFloat("Shift Offset Z", part.shiftOffsetZ, v -> part.shiftOffsetZ = v);
        activePopup.addFloat("Shift Rotation X", part.shiftRotationX, v -> part.shiftRotationX = v);
        activePopup.addFloat("Shift Rotation Y", part.shiftRotationY, v -> part.shiftRotationY = v);
        activePopup.addFloat("Shift Rotation Z", part.shiftRotationZ, v -> part.shiftRotationZ = v);

        this.hasUnsavedChanges = wasUnsaved;
    }

    private void openLurePopup() {
        boolean wasUnsaved = this.hasUnsavedChanges;

        activePopup = new FloatingPopup(L("devstudio.lure.title"), 50, 50, 220, 220);
        activePopup.addToggle(L("devstudio.lure.field.enabled"), editingData.lure.enabled, v -> editingData.lure.enabled = v);
        activePopup.addString(L("devstudio.lure.field.type"), editingData.lure.lureTYPE, t -> editingData.lure.lureTYPE = t);
        activePopup.addDouble(L("devstudio.lure.field.shiny_mult"), editingData.lure.lureShinyMultiplier, v -> editingData.lure.lureShinyMultiplier = v);
        activePopup.addDouble(L("devstudio.lure.field.ultrarare_mult"), editingData.lure.lureUltraRAREMultiplier, v -> editingData.lure.lureUltraRAREMultiplier = v);
        activePopup.addDouble(L("devstudio.lure.field.hidden_ability_mult"), editingData.lure.lureHiddenAbilityMultiplier, v -> editingData.lure.lureHiddenAbilityMultiplier = v);
        activePopup.addDouble(L("devstudio.lure.field.expall_mult"), editingData.lure.lureExpAllMultiplier, v -> editingData.lure.lureExpAllMultiplier = v);
        activePopup.addDouble(L("devstudio.lure.field.friendship_mult"), editingData.lure.lureAmizadeMultiplier, v -> editingData.lure.lureAmizadeMultiplier = v);
        activePopup.addInt(L("devstudio.lure.field.iv"), editingData.lure.lureIV, v -> editingData.lure.lureIV = v);
        activePopup.addDouble(L("devstudio.lure.field.iv_chance"), editingData.lure.lureChanceIV, v -> editingData.lure.lureChanceIV = v);
        activePopup.addDouble(L("devstudio.lure.field.fishing_shiny"), editingData.lure.lurePescaShiny, v -> editingData.lure.lurePescaShiny = v);
        activePopup.addDouble(L("devstudio.lure.field.fishing_ultrarare"), editingData.lure.lurePescaUltraRare, v -> editingData.lure.lurePescaUltraRare = v);
        activePopup.addDouble(L("devstudio.lure.field.fishing_iv_chance"), editingData.lure.lurePescaIvChance, v -> editingData.lure.lurePescaIvChance = v);
        activePopup.addInt(L("devstudio.lure.field.fishing_iv"), editingData.lure.lurePescaIv, v -> editingData.lure.lurePescaIv = v);
        activePopup.addDouble(L("devstudio.lure.field.fishing_speed"), editingData.lure.lurePescaVelocidade, v -> editingData.lure.lurePescaVelocidade = v);
        activePopup.addDouble(L("devstudio.lure.field.exp_mult"), editingData.lure.lureEXP, v -> editingData.lure.lureEXP = v);
        activePopup.addDouble(L("devstudio.lure.field.ev_mult"), editingData.lure.lureEV, v -> editingData.lure.lureEV = v);
        activePopup.addDouble(L("devstudio.lure.field.capture_chance"), editingData.lure.lureChanceDeCaptura, v -> editingData.lure.lureChanceDeCaptura = v);
        activePopup.addDouble(L("devstudio.lure.field.fishing_lure"), editingData.lure.lureDePesca, v -> editingData.lure.lureDePesca = v);

        this.hasUnsavedChanges = wasUnsaved;
    }

    /** GUI clicável com TODOS os efeitos de status do Minecraft — clicar num efeito cicla o
     *  nível dele (OFF -> I -> II -> III -> IV -> V -> OFF), sem precisar decorar/digitar o id
     *  namespace:effect:nível na mão. Mantém o mesmo formato de string usado por
     *  applyPotionEffects() (ver GreatCosmetics.java), só troca a forma de editar. */
    private void openEffectsPopup() {
        boolean wasUnsaved = this.hasUnsavedChanges;

        activePopup = new FloatingPopup(L("devstudio.effects.title"), 50, 20, 240, 360);
        activePopup.addDivider(L("devstudio.effects.divider"));

        List<net.minecraft.resources.ResourceLocation> ids = new ArrayList<>(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.keySet());
        ids.sort(java.util.Comparator.comparing(net.minecraft.resources.ResourceLocation::getPath));

        for (net.minecraft.resources.ResourceLocation id : ids) {
            String fullId = id.toString();
            String displayName = id.getPath().replace('_', ' ');
            displayName = displayName.substring(0, 1).toUpperCase() + displayName.substring(1);
            activePopup.addEffect(displayName, fullId, getEffectLevel(fullId), this::setEffectLevel);
        }

        this.hasUnsavedChanges = wasUnsaved;
    }

    private int getEffectLevel(String fullId) {
        if (this.editingData.effects == null) return 0;
        for (String s : this.editingData.effects) {
            String[] parts = s.split(":");
            if (parts.length >= 2 && (parts[0] + ":" + parts[1]).equalsIgnoreCase(fullId)) {
                try {
                    return parts.length > 2 ? Integer.parseInt(parts[2]) : 1;
                } catch (NumberFormatException e) {
                    return 1;
                }
            }
        }
        return 0;
    }

    private void setEffectLevel(String fullId, int level) {
        if (this.editingData.effects == null) this.editingData.effects = new ArrayList<>();
        this.editingData.effects.removeIf(s -> {
            String[] parts = s.split(":");
            return parts.length >= 2 && (parts[0] + ":" + parts[1]).equalsIgnoreCase(fullId);
        });
        if (level > 0) this.editingData.effects.add(fullId + ":" + level);
    }

    private EditorRow addStringField(String label, String startVal, java.util.function.Consumer<String> action) {
        return addStringField(label, startVal, null, null, action);
    }

    /** Igual addStringField, só que com uma dica cinza (placeholder — só aparece quando o campo
     *  está vazio, nunca é salvo) explicando o formato esperado. */
    private EditorRow addStringField(String label, String startVal, String placeholder, java.util.function.Consumer<String> action) {
        return addStringField(label, startVal, placeholder, null, action);
    }

    /** Igual addStringField, mas com autocomplete: {@code suggestions} é avaliado (só quando o
     *  campo está focado — ver render()) pra montar a lista de sugestões filtradas pelo texto
     *  digitado, desenhada como um dropdown por baixo do campo (ver renderAutocompleteDropdown).
     *  Devolve a EditorRow criada pra quem chamou poder encadear .withTooltip(...) (ver
     *  hoveredTooltipRow) — a explicação completa do campo mora no tooltip, não mais no label. */
    private EditorRow addStringField(String label, String startVal, String placeholder,
                                 java.util.function.Supplier<java.util.List<String>> suggestions,
                                 java.util.function.Consumer<String> action) {
        EditBox field = new EditBox(parent.getTextRenderer(), 0, 0, 140, 16, Component.literal(""));
        field.setMaxLength(128); field.setValue(startVal != null ? startVal : "");
        if (placeholder != null) field.setHint(Component.literal(placeholder).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        field.setResponder(text -> { hasUnsavedChanges = true; action.accept(text); });
        EditorRow row = new EditorRow(label, RowType.STRING, field);
        row.suggestions = suggestions;
        this.rows.add(row);
        return row;
    }

    private EditorRow addIntField(String label, int startVal, java.util.function.Consumer<Integer> action) {
        EditBox field = new EditBox(parent.getTextRenderer(), 0, 0, 140, 16, Component.literal(""));
        field.setMaxLength(10); field.setValue(String.valueOf(startVal));
        field.setResponder(text -> {
            hasUnsavedChanges = true;
            try { if (!text.isEmpty() && !text.equals("-")) action.accept(Integer.parseInt(text)); } catch (Exception ignored) {}
        });
        EditorRow row = new EditorRow(label, RowType.INT, field);
        this.rows.add(row);
        return row;
    }

    private EditorRow addDoubleField(String label, double startVal, java.util.function.Consumer<Double> action) {
        EditBox field = new EditBox(parent.getTextRenderer(), 0, 0, 140, 16, Component.literal(""));
        field.setMaxLength(20); field.setValue(String.valueOf(startVal));
        field.setResponder(text -> {
            hasUnsavedChanges = true;
            try { if (!text.isEmpty() && !text.equals("-") && !text.equals(".")) action.accept(Double.parseDouble(text)); } catch (Exception ignored) {}
        });
        EditorRow row = new EditorRow(label, RowType.DOUBLE, field);
        this.rows.add(row);
        return row;
    }

    /** Escaneia o resourcepack (qualquer namespace carregado, ou só "greatcosmetics" se filter
     *  exigir) por arquivos dentro de rootFolder, devolvendo só o NOME DO ARQUIVO (sem a pasta, sem
     *  o sufixo) — mesma convenção "nome solto" já usada em GreatCosmeticsClient (modelMap/geoMap) e
     *  no scan de ícones do SyncCosmeticsPayload. Usado pra alimentar autocomplete com o que JÁ
     *  EXISTE no resourcepack (usado ou não em algum cosmético ainda) em vez de só o que já está
     *  configurado — ver addStringField/EditorRow#suggestions. */
    private static List<String> scanResourceShortNames(String rootFolder, java.util.function.Predicate<net.minecraft.resources.ResourceLocation> filter, String stripSuffix) {
        net.minecraft.server.packs.resources.ResourceManager rm = Minecraft.getInstance().getResourceManager();
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (net.minecraft.resources.ResourceLocation resId : rm.listResources(rootFolder, filter).keySet()) {
            String fileName = resId.getPath().substring(resId.getPath().lastIndexOf('/') + 1);
            if (fileName.endsWith(stripSuffix)) fileName = fileName.substring(0, fileName.length() - stripSuffix.length());
            out.add(fileName);
        }
        return new ArrayList<>(out);
    }

    /** Todo valor de "Type" já configurado em algum cosmético (normal ou armadura-cosmético) —
     *  não existe arquivo nenhum no resourcepack representando isso, então aqui é sempre "os que já
     *  estão configurados no mod", sem opção de escanear resource. */
    private static List<String> scanConfiguredTypes() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (CosmeticData d : CosmeticsConfig.cosmeticsMap.values()) if (d.type != null && !d.type.isBlank()) out.add(d.type.trim());
        for (CosmeticData d : com.f4xizzz.greatcosmetics.client.ClientArmorCosmeticsCache.armorCosmetics.values()) if (d.type != null && !d.type.isBlank()) out.add(d.type.trim());
        return new ArrayList<>(out);
    }

    /** Filtra (contains, sem diferenciar maiúsc/minúsc), remove duplicatas/vazios e o valor que já
     *  é exatamente o texto atual do campo (não faz sentido "sugerir" o que já tá escrito), e limita
     *  a AUTOCOMPLETE_MAX_ITEMS — sem o limite, uma fonte grande (ex: todos os ids de partícula do
     *  registro) desenharia um dropdown maior que o próprio painel. */
    private List<String> filterSuggestions(List<String> all, String currentText) {
        String needle = currentText == null ? "" : currentText.toLowerCase().trim();
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String s : all) {
            if (s == null || s.isBlank()) continue;
            if (s.equalsIgnoreCase(currentText)) continue;
            if (needle.isEmpty() || s.toLowerCase().contains(needle)) out.add(s);
            if (out.size() >= AUTOCOMPLETE_MAX_ITEMS) break;
        }
        return new ArrayList<>(out);
    }

    /** Desenha o dropdown de autocomplete (ver openDropdownField/EditorRow#suggestions) por baixo
     *  do campo focado — chamado DEPOIS do loop de rows inteiro (não durante), senão as rows
     *  seguintes desenhariam POR CIMA dele (mesmo motivo de popup/tooltip sempre serem a última
     *  coisa desenhada num frame). */
    private void renderAutocompleteDropdown(GuiGraphics c, int mouseX, int mouseY) {
        if (openDropdownField == null || openDropdownFiltered.isEmpty()) return;

        // translate em Z (mesmo truque já usado no dropdown de categoria da Acessórios) — mesmo
        // desenhado por último no método, o texto de outras rows ainda furava por cima do dropdown
        // sem isso, porque DrawContext não garante ordem de pintor entre lotes de texto (cada
        // drawTextWithShadow pode acabar num vertex buffer/lote diferente do fill do dropdown).
        // Empurrar o dropdown pra frente garante que ele vence qualquer texto no mesmo plano.
        c.pose().pushPose();
        c.pose().translate(0, 0, 300);

        int itemH = AUTOCOMPLETE_ITEM_H;
        int boxH = openDropdownFiltered.size() * itemH;
        c.fill(openDropdownX - 1, openDropdownY - 1, openDropdownX + openDropdownW + 1, openDropdownY + boxH + 1, 0xFF1A1A1A);
        c.renderOutline(openDropdownX - 1, openDropdownY - 1, openDropdownW + 2, boxH + 2, 0xFFFFAA00);

        for (int i = 0; i < openDropdownFiltered.size(); i++) {
            int itemY = openDropdownY + (i * itemH);
            boolean hovered = mouseX >= openDropdownX && mouseX <= openDropdownX + openDropdownW && mouseY >= itemY && mouseY <= itemY + itemH;
            if (hovered) c.fill(openDropdownX, itemY, openDropdownX + openDropdownW, itemY + itemH, 0x66FFAA00);
            parent.enableScissorStacked(c, openDropdownX, itemY, openDropdownW, itemH);
            c.drawString(parent.getTextRenderer(), openDropdownFiltered.get(i), openDropdownX + 2, itemY + 2, hovered ? 0xFFFFFFFF : 0xFFCCCCCC);
            c.disableScissor();
        }

        c.pose().popPose();
    }

    /** Clique dentro do dropdown aberto — precisa ser testado ANTES de qualquer outra coisa (rows,
     *  popups) porque o dropdown desenha POR CIMA das rows seguintes (ver renderAutocompleteDropdown),
     *  então um clique ali dentro cairia, sem essa prioridade, na row de baixo por engano. */
    private boolean handleAutocompleteClick(double mx, double my) {
        if (openDropdownField == null || openDropdownFiltered.isEmpty()) return false;
        int itemH = AUTOCOMPLETE_ITEM_H;
        int boxH = openDropdownFiltered.size() * itemH;
        if (mx < openDropdownX - 1 || mx > openDropdownX + openDropdownW + 1 || my < openDropdownY - 1 || my > openDropdownY + boxH + 1) {
            return false;
        }
        int index = (int) ((my - openDropdownY) / itemH);
        if (index >= 0 && index < openDropdownFiltered.size()) {
            playClick();
            // setText já dispara o changedListener do campo (ver addStringField) — não precisa
            // chamar o consumer/action na mão, o mesmo caminho de "digitar" já salva o valor.
            openDropdownField.setValue(openDropdownFiltered.get(index));
            openDropdownFiltered = List.of();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics c, int mouseX, int mouseY, float delta, int x, int y, int width, int height) {
        INSTANCE = this;
        this.lastX = x; this.lastY = y; this.lastWidth = width; this.lastHeight = height;
        // Recalculado do zero a cada frame pelos dois loops de row (editor principal e FloatingPopup)
        // — ver renderHoveredTooltip, chamado por ÚLTIMO neste método.
        this.hoveredTooltipRow = null;

        boolean isMouseDown = GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (!isMouseDown) {
            isDraggingPopup = false;
            isDraggingScrollbar = false;
            isDraggingPopupScrollbar = false;
            GizmoManager.release();
        }

        int topY = y + 35;

        if (currentState == State.LIST) {
            boolean hovBack = mouseX >= x + 12 && mouseX <= x + 62 && mouseY >= topY && mouseY <= topY + 12;
            c.drawString(parent.getTextRenderer(), L("devstudio.common.back"), x + 12, topY + 2, hovBack ? 0xFF5555 : 0xAAAAAA);
            c.drawCenteredString(parent.getTextRenderer(), L("devstudio.cosmetic.list_title"), x + (width / 2), topY + 2, 0xFFFFFF);

            int newBtnMid = x + 10 + (width - 20) / 2;
            boolean hovNew = mouseX >= x + 10 && mouseX <= newBtnMid - 2 && mouseY >= topY + 15 && mouseY <= topY + 30;
            boolean hovNewArmor = mouseX >= newBtnMid + 2 && mouseX <= x + width - 10 && mouseY >= topY + 15 && mouseY <= topY + 30;
            c.fill(x + 10, topY + 15, newBtnMid - 2, topY + 30, hovNew ? 0xFF55FF55 : 0xFF22AA22);
            c.drawCenteredString(parent.getTextRenderer(), L("devstudio.cosmetic.btn.new_cosmetic"), x + 10 + (newBtnMid - 2 - (x + 10)) / 2, topY + 19, 0xFFFFFF);
            c.fill(newBtnMid + 2, topY + 15, x + width - 10, topY + 30, hovNewArmor ? 0xFF66AAFF : 0xFF3377CC);
            c.drawCenteredString(parent.getTextRenderer(), L("devstudio.cosmetic.btn.new_armor"), newBtnMid + 2 + (x + width - 10 - (newBtnMid + 2)) / 2, topY + 19, 0xFFFFFF);

            // ==========================================
            // BARRA DE PESQUISA + FILTRO DE TYPE
            // ==========================================
            int searchY = topY + 32;
            int typeFilterW = 90;
            int searchGap = 4;
            int searchFieldW = width - 20 - typeFilterW - searchGap;

            searchField.setX(x + 10);
            searchField.setY(searchY);
            searchField.setWidth(searchFieldW);
            searchField.visible = true;
            searchField.active = true;
            searchField.render(c, mouseX, mouseY, delta);

            int typeBoxX = x + 10 + searchFieldW + searchGap;
            boolean hovType = mouseX >= typeBoxX && mouseX <= typeBoxX + typeFilterW && mouseY >= searchY && mouseY <= searchY + 16;
            c.fill(typeBoxX, searchY, typeBoxX + typeFilterW, searchY + 16, hovType ? 0xFF333333 : 0xFF222222);
            c.fill(typeBoxX, searchY, typeBoxX + typeFilterW, searchY + 1, 0xFF555555);
            c.fill(typeBoxX, searchY + 15, typeBoxX + typeFilterW, searchY + 16, 0xFF111111);
            String typeLabel = "ALL".equals(typeFilter) ? L("devstudio.common.all") : typeFilter;
            parent.enableScissorStacked(c, typeBoxX + 3, searchY, typeFilterW - 12, 16);
            c.drawString(parent.getTextRenderer(), typeLabel, typeBoxX + 4, searchY + 4, 0xFFFFAA);
            c.disableScissor();
            c.drawString(parent.getTextRenderer(), isTypeDropdownOpen ? "▲" : "▼", typeBoxX + typeFilterW - 10, searchY + 4, 0xFFAAAAAA);

            if (isTypeDropdownOpen) {
                // Reconstrói as opções toda vez que o dropdown abre (não guarda em cache) — "type" é
                // texto livre editável a qualquer momento no Dev Studio, então a lista de valores já
                // configurados pode ter mudado desde a última vez que esse dropdown foi aberto.
                List<String> options = new ArrayList<>();
                options.add("ALL");
                options.addAll(scanConfiguredTypes());
                this.typeFilterOptions = options;

                c.pose().pushPose();
                c.pose().translate(0, 0, 300);
                int optH = 14;
                int listBoxH = options.size() * optH;
                c.fill(typeBoxX, searchY + 16, typeBoxX + typeFilterW, searchY + 16 + listBoxH, 0xFA111111);
                c.renderOutline(typeBoxX, searchY + 16, typeFilterW, listBoxH, 0xFFFFAA00);
                for (int i = 0; i < options.size(); i++) {
                    int optY = searchY + 16 + (i * optH);
                    boolean hovOpt = mouseX >= typeBoxX && mouseX <= typeBoxX + typeFilterW && mouseY >= optY && mouseY <= optY + optH;
                    if (hovOpt) c.fill(typeBoxX + 1, optY, typeBoxX + typeFilterW - 1, optY + optH, 0xFF444444);
                    String label = "ALL".equals(options.get(i)) ? L("devstudio.common.all") : options.get(i);
                    parent.enableScissorStacked(c, typeBoxX + 3, optY, typeFilterW - 6, optH);
                    c.drawString(parent.getTextRenderer(), label, typeBoxX + 4, optY + 3, hovOpt ? 0xFFFFFFFF : 0xFFAAAAAA);
                    c.disableScissor();
                }
                c.pose().popPose();
            }

            int[] regularCountBox = new int[1];
            List<String> ids = filteredListIds(regularCountBox);
            int regularCount = regularCountBox[0];
            int listY = topY + 55;
            int spacing = 6;
            int columns = gridColumns(width, spacing);
            int itemSize = (width - 20 - (spacing * (columns - 1))) / columns;
            int totalRows = (int) Math.ceil((double) ids.size() / columns);

            this.maxScrollY = Math.max(0, (totalRows * (itemSize + spacing)) - (height - (topY - y + 60)));
            // enablePerfectScissor (RenderSystem.enableScissor cru) só vale no instante da chamada —
            // texto do DrawContext é desenhado de forma adiada (batched), então o recorte bruto podia
            // já ter sido trocado por outra coisa quando o texto de verdade era pintado, deixando ele
            // "vazar" pra fora do quadrado do item mesmo com o scissor "certo" no papel. enableScissorStacked
            // usa o scissor do próprio DrawContext, que fica associado à draw call adiada corretamente
            // (mesma correção já aplicada no FloatingPopup e na lista de properties).
            parent.enableScissorStacked(c, x, listY, width, height - (topY - y + 60));

            for (int i = 0; i < ids.size(); i++) {
                int col = i % columns; int row = i / columns;
                int itemX = x + 10 + (col * (itemSize + spacing));
                int itemY = listY + (row * (itemSize + spacing)) - (int)scrollY;

                if (itemY + itemSize < listY || itemY > y + height) continue;

                boolean isArmor = i >= regularCount;
                boolean hovered = mouseX >= itemX && mouseX <= itemX + itemSize && mouseY >= itemY && mouseY <= itemY + itemSize;
                c.fill(itemX, itemY, itemX + itemSize, itemY + itemSize, hovered ? 0x66FFFFFF : 0x44000000);
                c.renderOutline(itemX, itemY, itemSize, itemSize, hovered ? 0xFFFFAA00 : (isArmor ? 0xFF3399FF : 0xFF444444));

                CosmeticData data = isArmor
                        ? com.f4xizzz.greatcosmetics.client.ClientArmorCosmeticsCache.getSyntheticCosmetic(ids.get(i))
                        : CosmeticsConfig.cosmeticsMap.get(ids.get(i));

                ItemStack renderStack = null;
                if (isArmor && data != null && data.realItemId != null) {
                    net.minecraft.resources.ResourceLocation realId = net.minecraft.resources.ResourceLocation.tryParse(data.realItemId);
                    net.minecraft.world.item.Item realItem = realId != null ? net.minecraft.core.registries.BuiltInRegistries.ITEM.get(realId) : Items.AIR;
                    if (realItem != Items.AIR) renderStack = new ItemStack(realItem);
                } else if (data != null && data.cmd > 0) {
                    renderStack = new ItemStack(Items.CARVED_PUMPKIN);
                    renderStack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(data.cmd));
                }
                if (renderStack != null) {
                    c.pose().pushPose();
                    c.pose().translate(itemX + (itemSize/2f) - 8, itemY + (itemSize/2f) - 8, 0);
                    c.renderItem(renderStack, 0, 0);
                    c.pose().popPose();
                }

                // getFormattedNameLines() (não getFormattedNameText()) — deixa o admin usar "\n"
                // literal no Display Name pra quebrar o nome em várias linhas aqui no ícone do grid.
                // MultilineMarqueeLabel ancora a ÚLTIMA linha exatamente em textY (onde um nome de 1
                // linha só sempre ficou) e empilha linhas extras PRA CIMA, com o mesmo marquee de
                // ida-e-volta de antes em qualquer linha que sozinha não caiba em maxW.
                java.util.List<Component> nameLines = data != null ? data.getFormattedNameLines(registries())
                        : java.util.List.of(Component.literal("§d" + ids.get(i)));
                int maxW = itemSize - 4;
                int textY = itemY + itemSize - 10;

                com.f4xizzz.greatcosmetics.client.gui.MultilineMarqueeLabel.draw(
                        c, parent, parent.getTextRenderer(), nameLines,
                        itemX + 2, itemX + (itemSize / 2), true, textY, maxW, 10, 0xAAAAAA);
            }
            c.disableScissor();

            if (maxScrollY > 0) {
                int sX = x + width - 6; c.fill(sX, listY, sX + 4, listY + height - (topY - y + 60), 0x44000000);
                int h = Math.max(10, (int) ((height - (topY - y + 60)) * ((float) (height - (topY - y + 60)) / (height - (topY - y + 60) + maxScrollY))));
                c.fill(sX, listY + (int) (((height - (topY - y + 60)) - h) * (scrollY / maxScrollY)), sX + 4, listY + (int) (((height - (topY - y + 60)) - h) * (scrollY / maxScrollY)) + h, 0xFFAAAAAA);
            }
        }
        else if (currentState == State.EDITOR && activePopup == null) {
            // Com o popup "Config Part" aberto, o resto do editor (cabeçalho, outliner,
            // properties) some inteiro — deixa só o popup e o preview 3D visíveis, sem a lista
            // inteira competindo por atenção atrás dele.
            boolean hovBack = mouseX >= x + 10 && mouseX <= x + 30 && mouseY >= topY && mouseY <= topY + 12;
            c.drawString(parent.getTextRenderer(), "<", x + 10, topY + 2, hovBack ? 0xFF5555 : 0xAAAAAA);

            boolean hovSave = mouseX >= x + width - 25 && mouseX <= x + width - 10 && mouseY >= topY && mouseY <= topY + 12;
            c.drawString(parent.getTextRenderer(), "S", x + width - 20, topY + 2, hovSave ? 0x55FF55 : 0xAAAAAA);

            boolean hovDelete = mouseX >= x + width - 45 && mouseX <= x + width - 30 && mouseY >= topY && mouseY <= topY + 12;
            c.drawString(parent.getTextRenderer(), "X", x + width - 40, topY + 2, hovDelete ? 0xFF5555 : 0xAAAAAA);

            Component title = editingData != null ? editingData.getFormattedNameText(registries()) : Component.literal("§d" + editingId.toUpperCase());
            int titleW = parent.getTextRenderer().width(title);
            int maxTitleW = width - 60;

            if (titleW > maxTitleW) {
                long time = Util.getMillis();
                int overflow = titleW - maxTitleW;
                double progress = (Math.sin(time / 500.0) + 1.0) / 2.0;
                int offset = (int) (progress * overflow);

                parent.enableScissorStacked(c, x + 30, topY, maxTitleW, 12);
                c.drawString(parent.getTextRenderer(), title, x + 30 - offset, topY + 2, 0xFFFFFF);
                c.disableScissor();
            } else {
                c.drawCenteredString(parent.getTextRenderer(), title, x + (width / 2), topY + 2, 0xFFFFFF);
            }

            int listY = topY + 20;
            int rowHeight = 35;
            int viewHeight = height - (topY - y + 25);
            this.maxScrollY = Math.max(0, (this.rows.size() * rowHeight) - viewHeight);

            // --- OUTLINER (estilo Blockbench: navega por seção clicando, igual um sumário) ---
            int outlinerW = outlinerWidth(width);
            int propsX = x + outlinerW + 6;
            int propsW = width - outlinerW - 6;

            c.fill(x, listY, x + outlinerW, listY + viewHeight, 0x33000000);
            int navY = listY + 2;
            int activeDividerIndex = activeDividerIndex(rowHeight);
            for (int i = 0; i < this.rows.size(); i++) {
                if (this.rows.get(i).type != RowType.DIVIDER) continue;
                if (navY + 16 > listY + viewHeight) break;

                boolean isCurrentSection = i == activeDividerIndex;
                boolean hovNav = mouseX >= x + 2 && mouseX <= x + outlinerW - 2 && mouseY >= navY && mouseY <= navY + 14;
                c.fill(x + 2, navY, x + outlinerW - 2, navY + 14, isCurrentSection ? 0x66FFAA00 : (hovNav ? 0x33FFFFFF : 0x22000000));
                if (isCurrentSection) c.fill(x + 2, navY, x + 4, navY + 14, 0xFFFFAA00);

                String navLabel = shortSectionLabel(this.rows.get(i).label);
                parent.enableScissorStacked(c, x + 6, navY, outlinerW - 10, 14);
                c.drawString(parent.getTextRenderer(), navLabel, x + 6, navY + 3, isCurrentSection ? 0xFFFFAA00 : 0xFFCCCCCC);
                c.disableScissor();

                navY += 16;

                // Sub-itens: cada Part vira um objeto selecionável de verdade (estilo outliner do
                // Blockbench) — clicar ativa o gizmo NA HORA, sem precisar achar/abrir o popup.
                if (this.rows.get(i).label.contains("MODELOS 3D")) {
                    for (int p = 0; p < outlinerParts.size(); p++) {
                        if (navY + 13 > listY + viewHeight) break;
                        boolean isSelectedPart = GizmoManager.activePart == outlinerParts.get(p);
                        boolean hovPart = mouseX >= x + 8 && mouseX <= x + outlinerW - 2 && mouseY >= navY && mouseY <= navY + 13;
                        c.fill(x + 8, navY, x + outlinerW - 2, navY + 13, isSelectedPart ? 0x6600FFAA : (hovPart ? 0x33FFFFFF : 0x00000000));
                        if (isSelectedPart) c.fill(x + 8, navY, x + 10, navY + 13, 0xFF00FFAA);
                        c.drawString(parent.getTextRenderer(), "• Part " + p, x + 13, navY + 2, isSelectedPart ? 0xFF00FFAA : 0xFFAAAAAA);
                        navY += 15;
                    }
                }
            }
            c.fill(x + outlinerW + 3, listY, x + outlinerW + 4, listY + viewHeight, 0xFF333333);

            // --- PROPERTIES (o mesmo sistema de rows de sempre, só que confinado à coluna direita) ---
            // enableScissorStacked, não enablePerfectScissor: TextFieldWidget desliga scissor cru
            // (RenderSystem.enableScissor direto) sem querer ao recortar o próprio texto — ver
            // explicação igual mais acima, no render() do FloatingPopup.
            parent.enableScissorStacked(c, propsX, listY, propsW, viewHeight);
            // Reseta ANTES do loop — se nenhuma row focada tiver suggestions nesse frame, o
            // dropdown simplesmente não é redesenhado (ver comentário no campo openDropdownField).
            this.openDropdownField = null;
            for (int i = 0; i < this.rows.size(); i++) {
                EditorRow row = this.rows.get(i);
                int rowY = listY + (i * rowHeight) - (int)scrollY;

                if (rowY < listY - rowHeight || rowY > y + height) {
                    if (row.textField != null) { row.textField.visible = false; row.textField.active = false; }
                    continue;
                }

                // Hover-check pro tooltip ANTES de qualquer "continue" — cobre DIVIDER e BUTTON
                // também, embora só rows com .withTooltip(...) != null acabem desenhando algo (ver
                // renderHoveredTooltip). Área = a row inteira (label + control), não só o texto do
                // label, pra não obrigar o jogador a mirar num pixel exato.
                if (row.tooltip != null && mouseX >= propsX + 10 && mouseX <= propsX + propsW - 10 && mouseY >= rowY - 2 && mouseY <= rowY + 30) {
                    hoveredTooltipRow = row;
                }

                if (row.type == RowType.DIVIDER) {
                    c.fill(propsX + 10, rowY + 4, propsX + propsW - 10, rowY + 20, 0x88FFAA00);
                    c.drawCenteredString(parent.getTextRenderer(), row.label, propsX + (propsW/2), rowY + 8, 0xFFFFFF);
                    continue;
                }

                if (row.type != RowType.BUTTON) {
                    c.drawString(parent.getTextRenderer(), "§f" + row.label, propsX + 15, rowY, 0xFFFFFF);
                }

                if (row.type == RowType.STRING || row.type == RowType.INT || row.type == RowType.DOUBLE) {
                    row.textField.visible = true; row.textField.active = true;
                    row.textField.setX(propsX + 15); row.textField.setY(rowY + 12);
                    row.textField.setWidth(propsW - 35);
                    row.textField.render(c, mouseX, mouseY, delta);

                    if (row.suggestions != null && row.textField.isFocused()) {
                        this.openDropdownField = row.textField;
                        this.openDropdownX = propsX + 15;
                        this.openDropdownY = rowY + 12 + 16;
                        this.openDropdownW = propsW - 35;
                        this.openDropdownFiltered = filterSuggestions(row.suggestions.get(), row.textField.getValue());
                    }
                }
                else if (row.type == RowType.TOGGLE) {
                    boolean hovTog = mouseX >= propsX + 15 && mouseX <= propsX + propsW - 20 && mouseY >= rowY + 12 && mouseY <= rowY + 28;
                    boolean isSneak = row.id.contains("SNEAK");
                    int colorOn = isSneak ? 0xFFBB00FF : 0xFF00FF00;
                    int colorBgOn = isSneak ? 0x66BB00FF : 0x66FFFFFF;

                    c.fill(propsX + 15, rowY + 12, propsX + propsW - 20, rowY + 28, hovTog ? colorBgOn : 0x44000000);
                    c.renderOutline(propsX + 15, rowY + 12, propsW - 35, 16, row.toggleValue ? colorOn : 0xFFFF0000);
                    c.drawCenteredString(parent.getTextRenderer(), L(row.toggleValue ? "devstudio.common.on" : "devstudio.common.off"), propsX + (propsW/2), rowY + 16, 0xFFFFFF);
                }
                else if (row.type == RowType.BUTTON) {
                    boolean hovBtn = mouseX >= propsX + 15 && mouseX <= propsX + propsW - 20 && mouseY >= rowY + 12 && mouseY <= rowY + 28;
                    int bgColor = row.disabled ? 0x33888888
                            : row.id.equals("btn_add_part") ? (hovBtn ? 0x6655FF55 : 0x4422AA22)
                            : row.id.startsWith("btn_remove_part") ? (hovBtn ? 0x66FF5555 : 0x44CC3333)
                            : (hovBtn ? 0x66FFAA00 : 0x44FFAA00);

                    c.fill(propsX + 15, rowY + 12, propsX + propsW - 20, rowY + 28, bgColor);
                    c.drawCenteredString(parent.getTextRenderer(), row.label, propsX + (propsW/2), rowY + 16, row.disabled ? 0xFF999999 : 0xFFFFFFFF);
                }
            }
            c.disableScissor();

            if (maxScrollY > 0) {
                int sX = propsX + propsW - 6; c.fill(sX, listY, sX + 4, listY + viewHeight, 0x44000000);
                int h = Math.max(10, (int) (viewHeight * ((float) viewHeight / (viewHeight + maxScrollY))));
                c.fill(sX, listY + (int) ((viewHeight - h) * (scrollY / maxScrollY)), sX + 4, listY + (int) ((viewHeight - h) * (scrollY / maxScrollY)) + h, 0xFFAAAAAA);
            }
        }

        renderAutocompleteDropdown(c, mouseX, mouseY);

        if (activePopup != null) activePopup.render(c, mouseX, mouseY, delta);
        renderExitPopup(c, mouseX, mouseY);
        renderConfirmPopup(c, mouseX, mouseY);

        // Desenhado por ÚLTIMO nesse método — depois do popup e de tudo mais — senão a caixinha
        // do tooltip ficaria escondida atrás da próxima coisa desenhada por cima dela.
        renderHoveredTooltip(c, mouseX, mouseY);
    }

    /** Ver campo hoveredTooltipRow: desenha a caixinha de explicação da row que o mouse está em
     *  cima agora (setado pelos dois loops de row, editor principal e FloatingPopup), se ela tiver
     *  um tooltip configurado. "\n" na string do Lang vira quebra de linha de verdade — drawTooltip
     *  trata cada elemento da lista como 1 linha e não quebra "\n" sozinho. */
    private void renderHoveredTooltip(GuiGraphics c, int mouseX, int mouseY) {
        if (hoveredTooltipRow == null || hoveredTooltipRow.tooltip == null) return;
        List<Component> lines = new ArrayList<>();
        for (String line : hoveredTooltipRow.tooltip.split("\n")) {
            lines.add(Component.literal(line));
        }
        c.renderComponentTooltip(parent.getTextRenderer(), lines, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (handlePopupClick(mx, my)) return true;
        if (handleConfirmPopupClick(mx, my)) return true;
        if (button != 0) return false;
        // Prioridade sobre TUDO abaixo — o dropdown desenha por cima das rows seguintes (ver
        // renderAutocompleteDropdown), então um clique dentro dele precisa ser resolvido antes de
        // qualquer hit-test de row/popup, senão cai por engano na row que está visualmente embaixo.
        if (handleAutocompleteClick(mx, my)) return true;

        if (activePopup != null) {
            if (activePopup.mouseClicked(mx, my, button)) return true;

            // Testa se o clique caiu numa seta do gizmo de verdade (ver GizmoManager) — se caiu,
            // agarra esse eixo e consome o clique (não deixa a câmera girar). Se não caiu perto de
            // nenhuma seta, deixa passar reto pra câmera girar normalmente.
            if (GizmoManager.tryGrab(mx, my)) return true;
            return false;
        }

        // Selecionar a Part só pelo outliner (sem abrir o popup "Config Part") também ativa o
        // gizmo — GizmoManager.activePart != null e o mixin desenha as setas — mas até aqui só o
        // ramo "activePopup != null" acima testava o clique contra elas. Sem isso, clicar numa
        // seta com o outliner (sem popup) nunca agarrava nada e o arraste caía pro comportamento
        // padrão de girar a câmera, deixando o gizmo visível mas 100% inclicável nesse fluxo.
        if (GizmoManager.activePart != null && GizmoManager.tryGrab(mx, my)) return true;

        int x = lastX; int y = lastY; int width = lastWidth; int height = lastHeight;
        int topY = y + 35;
        int listY = currentState == State.LIST ? topY + 55 : topY + 20;
        int rowHeight = 35;
        int outlinerW = outlinerWidth(width);
        int propsX = x + outlinerW + 6;
        int propsW = width - outlinerW - 6;
        int trackHeight = currentState == State.LIST ? height - (topY - y + 60) : height - (topY - y + 25);
        int sX = currentState == State.LIST ? x + width - 6 : propsX + propsW - 6;

        if (maxScrollY > 0 && mx >= sX && mx <= sX + 4 && my >= listY && my <= listY + trackHeight) {
            isDraggingScrollbar = true; return true;
        }

        if (currentState == State.EDITOR && mx >= x && mx <= x + outlinerW && my >= listY && my <= listY + trackHeight) {
            int navY = listY + 2;
            for (int i = 0; i < this.rows.size(); i++) {
                if (this.rows.get(i).type != RowType.DIVIDER) continue;
                if (navY + 16 > listY + trackHeight) break;
                if (mx >= x + 2 && mx <= x + outlinerW - 2 && my >= navY && my <= navY + 14) {
                    playClick();
                    scrollY = Math.min(i * (float) rowHeight, maxScrollY);
                    if (scrollY < 0) scrollY = 0;
                    return true;
                }
                navY += 16;

                if (this.rows.get(i).label.contains("MODELOS 3D")) {
                    for (int p = 0; p < outlinerParts.size(); p++) {
                        if (navY + 13 > listY + trackHeight) break;
                        if (mx >= x + 8 && mx <= x + outlinerW - 2 && my >= navY && my <= navY + 13) {
                            playClick();
                            openPartPopup(outlinerParts.get(p), p);
                            return true;
                        }
                        navY += 15;
                    }
                }
            }
            return true;
        }

        if (currentState == State.LIST) {
            int searchY = topY + 32;
            int typeFilterW = 90;
            int searchGap = 4;
            int searchFieldW = width - 20 - typeFilterW - searchGap;
            int typeBoxX = x + 10 + searchFieldW + searchGap;

            // Dropdown de type aberto tem prioridade sobre TUDO abaixo dele (mesmo motivo do
            // autocomplete: ele desenha por cima da grade, então um clique "nele" só é válido se
            // resolvido antes de qualquer hit-test de item).
            if (isTypeDropdownOpen) {
                int optH = 14;
                for (int i = 0; i < typeFilterOptions.size(); i++) {
                    int optY = searchY + 16 + (i * optH);
                    if (mx >= typeBoxX && mx <= typeBoxX + typeFilterW && my >= optY && my <= optY + optH) {
                        playClick();
                        typeFilter = typeFilterOptions.get(i);
                        isTypeDropdownOpen = false;
                        scrollY = 0;
                        return true;
                    }
                }
                isTypeDropdownOpen = false;
                playClick();
                return true;
            }

            if (mx >= searchField.getX() && mx <= searchField.getX() + searchField.getWidth() && my >= searchY && my <= searchY + 16) {
                searchField.setFocused(true);
                searchField.mouseClicked(mx, my, 0);
                return true;
            }
            searchField.setFocused(false);

            if (mx >= typeBoxX && mx <= typeBoxX + typeFilterW && my >= searchY && my <= searchY + 16) {
                playClick();
                isTypeDropdownOpen = true;
                return true;
            }

            if (mx >= x + 12 && mx <= x + 62 && my >= topY && my <= topY + 12) {
                playClick(); tryExit(() -> { this.onBack.run(); this.scrollY = 0; }); return true;
            }
            int newBtnMid = x + 10 + (width - 20) / 2;
            if (mx >= x + 10 && mx <= newBtnMid - 2 && my >= topY + 15 && my <= topY + 30) {
                playClick(); String newId = "new_cosmetic_" + UUID.randomUUID().toString().substring(0, 4); CosmeticData newData = new CosmeticData(); newData.id = newId; newData.DisplayName = "&dNew Cosmetic"; newData.parts.add(new CosmeticData.CosmeticPart(CosmeticData.Anchor.HEAD)); CosmeticsConfig.cosmeticsMap.put(newId, newData); com.f4xizzz.greatcosmetics.config.CosmeticsConfig.invalidateCosmeticIndex(); sendSaveCosmetic(newId, newId, newData, false); loadEditor(newId); return true;
            }
            if (mx >= newBtnMid + 2 && mx <= x + width - 10 && my >= topY + 15 && my <= topY + 30) {
                playClick();
                String newId = "armor_" + UUID.randomUUID().toString().substring(0, 4);
                CosmeticData newData = new CosmeticData();
                newData.id = newId;
                newData.realItemId = "minecraft:diamond_helmet";
                newData.DisplayName = "&dNew Armor Cosmetic";
                newData.slot = CosmeticData.VirtualSlot.HEAD;
                newData.type = "armor_cosmetic";
                newData.parts.add(new CosmeticData.CosmeticPart(CosmeticData.Anchor.HEAD));
                com.f4xizzz.greatcosmetics.client.ClientArmorCosmeticsCache.armorCosmetics.put(newId, newData);
                sendSaveCosmetic(newId, newId, newData, true);
                loadEditor(newId);
                return true;
            }
            List<String> ids = filteredListIds(null);
            int spacing = 6; int columns = gridColumns(width, spacing); int itemSize = (width - 20 - (spacing * (columns - 1))) / columns;
            for (int i = 0; i < ids.size(); i++) {
                int col = i % columns; int row = i / columns;
                int itemX = x + 10 + (col * (itemSize + spacing));
                int itemY = listY + (row * (itemSize + spacing)) - (int)scrollY;
                if (itemY + itemSize >= listY && itemY <= y + height) {
                    if (mx >= itemX && mx <= itemX + itemSize && my >= itemY && my <= itemY + itemSize) { playClick(); loadEditor(ids.get(i)); return true; }
                }
            }
        }
        else if (currentState == State.EDITOR) {
            if (mx >= x + 10 && mx <= x + 30 && my >= topY && my <= topY + 12) {
                playClick(); tryExit(() -> { this.currentState = State.LIST; this.activePopup = null; this.scrollY = this.listScrollY; Wardrobe3DScreen.previewCosmeticId = null; GizmoManager.activePart = null; }); return true;
            }
            if (mx >= x + width - 25 && mx <= x + width - 10 && my >= topY && my <= topY + 12) {
                playClick(); saveChanges(); hasUnsavedChanges = false; return true;
            }
            if (mx >= x + width - 45 && mx <= x + width - 30 && my >= topY && my <= topY + 12) {
                playClick();
                openConfirmPopup(L("devstudio.cosmetic.confirm.delete_title"), L("devstudio.cosmetic.confirm.delete_body"), () -> {
                    sendDeleteCosmetic(this.editingId, this.editingIsArmorCosmetic);
                    if (this.editingIsArmorCosmetic) {
                        com.f4xizzz.greatcosmetics.client.ClientArmorCosmeticsCache.armorCosmetics.remove(this.editingId);
                    } else {
                        CosmeticsConfig.cosmeticsMap.remove(this.editingId);
                        com.f4xizzz.greatcosmetics.config.CosmeticsConfig.invalidateCosmeticIndex();
                    }
                    hasUnsavedChanges = false;
                    this.currentState = State.LIST;
                    this.activePopup = null;
                    this.scrollY = this.listScrollY;
                    Wardrobe3DScreen.previewCosmeticId = null;
                    GizmoManager.activePart = null;
                });
                return true;
            }
            boolean clickedAny = false;
            int iterListY = topY + 20;
            for (EditorRow row : rows) {
                int rowY = iterListY + (rows.indexOf(row) * rowHeight) - (int)scrollY;
                if (rowY >= iterListY - rowHeight && rowY <= y + height) {
                    if (row.type == RowType.DIVIDER) continue;

                    if (row.type == RowType.STRING || row.type == RowType.INT || row.type == RowType.DOUBLE) {
                        row.textField.setX(propsX + 15); row.textField.setY(rowY + 12); row.textField.setWidth(propsW - 35);
                        if (mx >= propsX + 15 && mx <= propsX + propsW - 20 && my >= rowY + 12 && my <= rowY + 28) {
                            row.textField.setFocused(true); row.textField.mouseClicked(mx, my, button); clickedAny = true;
                            for (EditorRow r : rows) if (r != row && r.textField != null) r.textField.setFocused(false); return true;
                        }
                    } else if (row.type == RowType.TOGGLE || row.type == RowType.BUTTON) {
                        if (mx >= propsX + 15 && mx <= propsX + propsW - 20 && my >= rowY + 12 && my <= rowY + 28) {
                            if (row.disabled) return true; // consome o clique sem tocar sound/ação
                            playClick();
                            if (row.type == RowType.TOGGLE) { if (!row.id.contains("SNEAK")) { hasUnsavedChanges = true; } row.toggleValue = !row.toggleValue; row.onToggle.accept(row.toggleValue); }
                            else { row.onButtonClick.run(); }
                            return true;
                        }
                    }
                }
            }
            if (!clickedAny) { for (EditorRow r : rows) if (r.textField != null) r.textField.setFocused(false); }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double deltaX, double deltaY) {
        // O eixo já foi decidido no clique (GizmoManager.tryGrab, ver mouseClicked) — não precisa
        // mais segurar X/Y/Z no teclado, é só continuar arrastando a seta que foi agarrada.
        if (GizmoManager.isDragging && GizmoManager.currentAxis != GizmoManager.Axis.NONE) {
            GizmoManager.handleDrag(mx, my, Wardrobe3DScreen.isPreviewSneaking);
            return true;
        }

        if (isDraggingPopup && activePopup != null) { activePopup.x += (int) deltaX; activePopup.y += (int) deltaY; return true; }
        if (isDraggingPopupScrollbar && activePopup != null && activePopup.pMaxScrollY > 0) {
            int viewHeight = activePopup.height - 25; float h = Math.max(10, (float) viewHeight * ((float) viewHeight / (viewHeight + activePopup.pMaxScrollY))); float maxHandleMovement = viewHeight - h;
            if (maxHandleMovement > 0) { float scrollPerPixel = activePopup.pMaxScrollY / maxHandleMovement; activePopup.pScrollY += (deltaY * scrollPerPixel); if (activePopup.pScrollY < 0) activePopup.pScrollY = 0; if (activePopup.pScrollY > activePopup.pMaxScrollY) activePopup.pScrollY = activePopup.pMaxScrollY; }
            return true;
        }
        if (isDraggingScrollbar && maxScrollY > 0) {
            int y = lastY; int topY = y + 35; int listY = currentState == State.LIST ? topY + 55 : topY + 20; int trackHeight = currentState == State.LIST ? lastHeight - (topY - y + 60) : lastHeight - (topY - y + 25);
            float proportion = (float) (my - listY) / trackHeight; scrollY = proportion * maxScrollY; if (scrollY < 0) scrollY = 0; if (scrollY > maxScrollY) scrollY = maxScrollY; return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (activePopup != null) {
            if (mx >= activePopup.x && mx <= activePopup.x + activePopup.width && my >= activePopup.y && my <= activePopup.y + activePopup.height) {
                activePopup.pScrollY -= (float) (vAmount * 15f); if (activePopup.pScrollY < 0) activePopup.pScrollY = 0; if (activePopup.pScrollY > activePopup.pMaxScrollY) activePopup.pScrollY = activePopup.pMaxScrollY; return true;
            }
        }
        if (mx < lastX || mx > lastX + lastWidth || my < lastY || my > lastY + lastHeight) return false;
        this.scrollY -= (float) (vAmount * 15f); if (this.scrollY < 0) this.scrollY = 0; if (this.scrollY > this.maxScrollY) this.scrollY = this.maxScrollY; return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activePopup != null) { for (EditorRow row : activePopup.rows) if (row.textField != null && row.textField.isFocused() && row.textField.keyPressed(keyCode, scanCode, modifiers)) return true; return true; }
        if (currentState == State.LIST && searchField.isFocused() && searchField.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (currentState == State.EDITOR) { for (EditorRow row : rows) if (row.textField != null && row.textField.isFocused() && row.textField.keyPressed(keyCode, scanCode, modifiers)) return true; }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (activePopup != null) { for (EditorRow row : activePopup.rows) if (row.textField != null && row.textField.isFocused() && row.textField.charTyped(chr, modifiers)) return true; return true; }
        if (currentState == State.LIST && searchField.isFocused() && searchField.charTyped(chr, modifiers)) return true;
        if (currentState == State.EDITOR) { for (EditorRow row : rows) if (row.textField != null && row.textField.isFocused() && row.textField.charTyped(chr, modifiers)) return true; }
        return false;
    }

    /** Manda a edição atual pro SERVIDOR persistir de verdade (ver SaveCosmeticPayload) — sem
     *  isso o "Salvar" só gravava um armor_cosmetics.json/cosmeticsconfig.conf na máquina do
     *  próprio client, sem nenhum efeito no servidor de verdade numa conexão remota. */
    private void sendSaveCosmetic(String oldId, String newId, CosmeticData data, boolean isArmor) {
        String json = GSON.toJson(data);
        String realItemId = data.realItemId != null ? data.realItemId : "";
        com.f4xizzz.greatcosmetics.platform.GcNet.toServer(
                new com.f4xizzz.greatcosmetics.network.SaveCosmeticPayload(oldId, newId, realItemId, isArmor, json));
    }

    /** Manda o pedido de apagar de vez pro SERVIDOR (ver DeleteCosmeticPayload) — sem isso o botão
     *  só sumiria o cosmético da máquina do próprio client, voltando assim que o catálogo do
     *  servidor fosse resincronizado. */
    private void sendDeleteCosmetic(String id, boolean isArmor) {
        com.f4xizzz.greatcosmetics.platform.GcNet.toServer(
                new com.f4xizzz.greatcosmetics.network.DeleteCosmeticPayload(id, isArmor));
    }

    private void renderConfirmPopup(GuiGraphics c, int mx, int my) {
        if (!showConfirmPopup) return;

        Minecraft client = Minecraft.getInstance();
        float targetScale = client.getWindow().getHeight() / 450f;
        int vWidth = (int) (client.getWindow().getWidth() / targetScale);
        int vHeight = 450;

        c.pose().pushPose();
        c.pose().translate(0, 0, 400);
        c.fill(0, 0, vWidth, vHeight, 0xCC000000);

        int px = (vWidth / 2) - 130;
        int py = (vHeight / 2) - 45;
        c.fill(px, py, px + 260, py + 90, 0xFF222222);
        c.renderOutline(px, py, 260, 90, 0xFFFF5555);

        c.drawCenteredString(parent.getTextRenderer(), "§c§l" + confirmPopupTitle, vWidth / 2, py + 15, 0xFFFFFF);
        c.drawCenteredString(parent.getTextRenderer(), confirmPopupMessage, vWidth / 2, py + 32, 0xAAAAAA);

        boolean hovYes = mx >= px + 15 && mx <= px + 120 && my >= py + 55 && my <= py + 75;
        boolean hovNo = mx >= px + 140 && mx <= px + 245 && my >= py + 55 && my <= py + 75;
        c.fill(px + 15, py + 55, px + 120, py + 75, hovYes ? 0xFFFF5555 : 0xFFCC3333);
        c.drawCenteredString(parent.getTextRenderer(), L("devstudio.common.confirm"), px + 67, py + 61, 0xFFFFFF);
        c.fill(px + 140, py + 55, px + 245, py + 75, hovNo ? 0xFFAAAAAA : 0xFF666666);
        c.drawCenteredString(parent.getTextRenderer(), L("devstudio.common.cancel"), px + 192, py + 61, 0xFFFFFF);

        c.pose().popPose();
    }

    private boolean handleConfirmPopupClick(double mx, double my) {
        if (!showConfirmPopup) return false;

        Minecraft client = Minecraft.getInstance();
        float targetScale = client.getWindow().getHeight() / 450f;
        int vWidth = (int) (client.getWindow().getWidth() / targetScale);
        int vHeight = 450;
        int px = (vWidth / 2) - 130;
        int py = (vHeight / 2) - 45;

        if (mx >= px + 15 && mx <= px + 120 && my >= py + 55 && my <= py + 75) {
            playClick();
            showConfirmPopup = false;
            if (confirmPopupAction != null) confirmPopupAction.run();
            confirmPopupAction = null;
            return true;
        }
        if (mx >= px + 140 && mx <= px + 245 && my >= py + 55 && my <= py + 75) {
            playClick(); showConfirmPopup = false; confirmPopupAction = null; return true;
        }
        return true; // Consome o clique pra não vazar pro que está atrás do popup.
    }

    @Override
    public void saveChanges() {
        if (this.editingIsArmorCosmetic) {
            // Id não é renomeável aqui (ver loadEditor) — só persiste o resto dos campos de volta.
            com.f4xizzz.greatcosmetics.client.ClientArmorCosmeticsCache.armorCosmetics.put(this.editingId, this.editingData);
            sendSaveCosmetic(this.editingId, this.editingId, this.editingData, true);
        } else {
            if (this.tempId != null && !this.tempId.trim().isEmpty() && !this.tempId.equals(this.editingId)) {
                CosmeticsConfig.cosmeticsMap.remove(this.editingId); CosmeticsConfig.cosmeticsMap.put(this.tempId, this.editingData);
                com.f4xizzz.greatcosmetics.config.CosmeticsConfig.invalidateCosmeticIndex();
                sendSaveCosmetic(this.editingId, this.tempId, this.editingData, false);
                this.editingId = this.tempId;
            } else {
                com.f4xizzz.greatcosmetics.config.CosmeticsConfig.invalidateCosmeticIndex();
                sendSaveCosmetic(this.editingId, this.editingId, this.editingData, false);
            }
        }
        this.backupJson = GSON.toJson(this.editingData);
        this.backupRealItemId = this.editingData.realItemId;
        hasUnsavedChanges = false;
    }

    @Override
    public void discardChanges() {
        if (this.editingData == null || this.backupJson == null) return;
        CosmeticData reverted = GSON.fromJson(this.backupJson, CosmeticData.class);
        if (reverted == null) return;
        reverted.id = this.editingData.id;
        reverted.realItemId = this.backupRealItemId;

        // resolvedCmd é transient (nunca vai pro JSON do backup) — sem re-resolver aqui, todo
        // Part GeckoLib (.geo) voltava com resolvedCmd zerado depois de "Descartar alterações",
        // caindo no fallback de ícone chapado 2D até o próximo /gc reload (mesmo bug que o
        // comentário de CosmeticsConfig#resolveModelIds já descreve pro fluxo de Salvar — só que
        // esse caminho de Descartar nunca tinha recebido a mesma correção).
        CosmeticsConfig.resolveModelIds(reverted);

        if (this.editingIsArmorCosmetic) {
            com.f4xizzz.greatcosmetics.client.ClientArmorCosmeticsCache.armorCosmetics.put(this.editingId, reverted);
        } else {
            CosmeticsConfig.cosmeticsMap.put(this.editingId, reverted);
            com.f4xizzz.greatcosmetics.config.CosmeticsConfig.invalidateCosmeticIndex();
        }
        this.editingData = reverted;

        // BUG (2026-09): resolveModelIds() sozinho recalcula o NÚMERO certo de resolvedCmd, mas
        // não é o suficiente — quem de fato faz o GeckoLib renderizar é o GeoModelRegistry (tabela
        // separada cmd -> geo/textura/animação, ver GreatCosmeticsClient), e SÓ o fluxo de Salvar
        // populava ele de novo (via round-trip de rede: sendSaveCosmetic -> servidor manda
        // SyncCosmeticsPayload de volta -> o receiver chama rebuildAllGeoModels()). Descartar é
        // 100% local, nunca manda nada pro servidor, então nunca passava por esse rebuild — o
        // modelo continuava GeckoLib "no papel" (resolvedCmd certo) mas sem entrada na tabela de
        // verdade, caindo pro ícone chapado até o próximo Salvar (que aí sim reconstruía a tabela)
        // acidentalmente "consertar". Chama direto aqui, sem precisar de nenhum round-trip.
        com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.clientGeoRebuild.run();
    }
}