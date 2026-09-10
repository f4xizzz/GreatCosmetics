package com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev;

import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager;
import com.f4xizzz.greatcosmetics.config.EffectConfig;
import com.f4xizzz.greatcosmetics.config.EffectData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DevEffectsSubPage extends DevSubPage {

    public static DevEffectsSubPage INSTANCE;

    public boolean hasPendingChanges() {
        return this.hasUnsavedChanges;
    }

    public void attemptTabSwitch(Runnable onConfirm) {
        tryExit(onConfirm);
    }

    private enum State { LIST, EDITOR, GROUP_EDITOR }
    private State currentState = State.LIST;

    // --- GRUPOS DE EFEITO ---
    // editingGroupId != null enquanto o EDITOR está sendo aberto DE DENTRO de um grupo (via
    // GROUP_EDITOR) — controla o "< Back" (volta pro grupo, não pra lista), o preview dos outros
    // membros e o botão "E" da barrinha (ver isEditingGroupEffect / Wardrobe3DScreen).
    private String editingGroupId = null;
    // GROUP_EDITOR: o grupo sendo editado agora.
    private String groupId = null;
    private String groupTempId = null;
    private com.f4xizzz.greatcosmetics.config.EffectGroupData editingGroup = null;
    // Modo "escolher efeito existente pra adicionar" dentro do GROUP_EDITOR.
    private boolean groupPickerOpen = false;

    /** Botão "E" (isolar) da barrinha lateral — liga: só o efeito sendo editado aparece no
     *  preview do grupo. Default off. Resetado em loadEditor / ao sair. */
    public static boolean isolateGroupPreview = false;

    /** true quando o EDITOR está aberto a partir de um grupo — usado pelo botão "E" (ver
     *  Wardrobe3DScreen#renderGizmoSidebar). */
    public boolean isEditingGroupEffect() {
        return currentState == State.EDITOR && editingGroupId != null;
    }

    private float scrollY = 0;
    private float maxScrollY = 0;

    // Guardados no render() (que recebe x/y/width/height DE VERDADE, 320x300 quando uma subpágina
    // Dev está aberta — ver Wardrobe3DScreen#targetPanelW/H) pra mouseClicked/mouseDragged/
    // mouseScrolled (que não recebem bounds) testarem contra o MESMO retângulo desenhado nesse
    // frame, em vez do 180x260 fixo (tamanho do painel comum, sem nenhuma subpágina aberta) que
    // esta classe usava antes — daí o scroll/clique ficarem desalinhados da hitbox real do GUI.
    private int lastX = 30, lastY = 50, lastWidth = 180, lastHeight = 260;

    private String editingId = null;
    private String tempId = null; // Variável para permitir renomear o Efeito
    private EffectData editingData = null;
    /** Snapshot JSON do efeito/grupo no estado em que o editor abriu — usado por discardChanges()
     *  pra reverter SÓ o que está sendo editado, sem tocar em disco (o client não deve escrever o
     *  effects.json — ver saveChanges/sendSaveEffect: quem persiste é o SERVIDOR). */
    private String editorBackupJson = null;
    private static final com.google.gson.Gson BACKUP_GSON = new com.google.gson.Gson();
    // >= 0 quando editingData é um efeito PRÓPRIO do grupo (editingGroup.ownedEffects[idx]).
    private int editingOwnedIndex = -1;

    // --- DROPDOWN GENÉRICO (Particle ID, Shape, Preset...) ---
    private static List<String> ALL_PARTICLE_IDS = null;
    private EditorRow dropdownRow = null;      // qual row está com o dropdown aberto (null = fechado)
    private boolean dropdownOpen = false;
    private final List<String> dropdownFiltered = new ArrayList<>();
    private float dropdownScrollY = 0f;
    private float dropdownMaxScrollY = 0f;

    private boolean isDraggingScrollbar = false;
    /** Row SLIDER sendo arrastada agora (limpa quando o botão do mouse solta — ver render()). */
    private EditorRow draggingSliderRow = null;
    private long lastTick = 0;

    // --- Busca + filtro da LISTA (igual AcessoriesPage) ---
    private TextFieldWidget listSearchField = null;
    /** 0 = Tudo, 1 = só Efeitos, 2 = só Grupos. */
    private int listFilterMode = 0;
    private boolean listFilterDropdownOpen = false;
    private static final String[] LIST_FILTER_KEYS = {"devstudio.effect.filter.all", "devstudio.effect.filter.effects", "devstudio.effect.filter.groups"};

    // Popup de confirmação de delete (lixeira da lista + botões DELETE do editor).
    private boolean showConfirmPopup = false;
    private String confirmTitle = "", confirmMessage = "";
    private Runnable confirmAction = null;

    private void openConfirmPopup(String title, String message, Runnable onConfirm) {
        this.confirmTitle = title; this.confirmMessage = message; this.confirmAction = onConfirm; this.showConfirmPopup = true;
    }

    private void renderConfirmPopup(DrawContext c, int mx, int my) {
        if (!showConfirmPopup) return;
        MinecraftClient client = MinecraftClient.getInstance();
        float targetScale = client.getWindow().getFramebufferHeight() / 450f;
        int vWidth = (int) (client.getWindow().getFramebufferWidth() / targetScale);
        int vHeight = 450;
        c.getMatrices().push();
        c.getMatrices().translate(0, 0, 400);
        c.fill(0, 0, vWidth, vHeight, 0xCC000000);
        int px = (vWidth / 2) - 130, py = (vHeight / 2) - 45;
        c.fill(px, py, px + 260, py + 90, 0xFF222222);
        c.drawBorder(px, py, 260, 90, 0xFFFF5555);
        c.drawCenteredTextWithShadow(parent.getTextRenderer(), "§c§l" + confirmTitle, vWidth / 2, py + 15, 0xFFFFFF);
        c.drawCenteredTextWithShadow(parent.getTextRenderer(), confirmMessage, vWidth / 2, py + 32, 0xAAAAAA);
        boolean hovYes = mx >= px + 15 && mx <= px + 120 && my >= py + 55 && my <= py + 75;
        boolean hovNo = mx >= px + 140 && mx <= px + 245 && my >= py + 55 && my <= py + 75;
        c.fill(px + 15, py + 55, px + 120, py + 75, hovYes ? 0xFFFF5555 : 0xFFCC3333);
        c.drawCenteredTextWithShadow(parent.getTextRenderer(), L("devstudio.common.confirm"), px + 67, py + 61, 0xFFFFFF);
        c.fill(px + 140, py + 55, px + 245, py + 75, hovNo ? 0xFFAAAAAA : 0xFF666666);
        c.drawCenteredTextWithShadow(parent.getTextRenderer(), L("devstudio.common.cancel"), px + 192, py + 61, 0xFFFFFF);
        c.getMatrices().pop();
    }

    private boolean handleConfirmPopupClick(double mx, double my) {
        if (!showConfirmPopup) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        float targetScale = client.getWindow().getFramebufferHeight() / 450f;
        int vWidth = (int) (client.getWindow().getFramebufferWidth() / targetScale);
        int vHeight = 450;
        int px = (vWidth / 2) - 130, py = (vHeight / 2) - 45;
        if (mx >= px + 15 && mx <= px + 120 && my >= py + 55 && my <= py + 75) {
            playClick(); showConfirmPopup = false;
            if (confirmAction != null) confirmAction.run();
            confirmAction = null;
            return true;
        }
        if (mx >= px + 140 && mx <= px + 245 && my >= py + 55 && my <= py + 75) {
            playClick(); showConfirmPopup = false; confirmAction = null; return true;
        }
        return true;
    }

    private enum RowType { STRING, INT, DOUBLE, TOGGLE, SLIDER, BUTTON, DIVIDER, GIZMO_INFO } // Novos tipos
    private static class EditorRow {
        String label; String id; RowType type; TextFieldWidget textField; Runnable onButtonClick;
        boolean toggleValue; java.util.function.Consumer<Boolean> onToggle;
        // Rows SLIDER (barra rolante): valor atual + limites + callback.
        int sliderVal, sliderMin, sliderMax; java.util.function.IntConsumer onSlider;
        // Rows STRING com dropdown: fonte das opções + o que fazer quando escolhe uma.
        java.util.function.Supplier<List<String>> dropdownOptions;
        java.util.function.Consumer<String> dropdownOnPick;
        boolean dropdownIsAction;   // true = campo é uma AÇÃO (fica sempre vazio; ex: "Preset")
        // Explicação em tooltip ao passar o mouse em cima da row — ver hoveredTooltipRow/
        // renderHoveredTooltip, mesmo mecanismo do DevCosmeticsSubPage.
        String tooltip;
        EditorRow(String label, RowType type, TextFieldWidget field) { this.label = label; this.id = label; this.type = type; this.textField = field; }
        EditorRow(String label, Runnable onButtonClick) { this.label = label; this.id = label; this.type = RowType.BUTTON; this.onButtonClick = onButtonClick; }
        EditorRow(String label, boolean startVal, java.util.function.Consumer<Boolean> onToggle) { this.label = label; this.id = label; this.type = RowType.TOGGLE; this.toggleValue = startVal; this.onToggle = onToggle; }
        EditorRow(String label, int startVal, int min, int max, java.util.function.IntConsumer onSlider) {
            this.label = label; this.id = label; this.type = RowType.SLIDER;
            this.sliderVal = startVal; this.sliderMin = min; this.sliderMax = max; this.onSlider = onSlider;
        }
        EditorRow withId(String id) { this.id = id; return this; }
        EditorRow withTooltip(String tooltip) { this.tooltip = tooltip; return this; }
    }

    private final List<EditorRow> rows = new ArrayList<>();

    /** Ver DevCosmeticsSubPage#hoveredTooltipRow — mesmo mecanismo, preenchido no loop de rows do
     *  render() e desenhado por último (ver renderHoveredTooltip). */
    private EditorRow hoveredTooltipRow = null;

    private static String L(String key, Object... ph) {
        return com.f4xizzz.greatcosmetics.config.LangConfig.legacy(key, ph);
    }

    public DevEffectsSubPage(Wardrobe3DScreen parent, Runnable onBack) {
        super(parent, onBack);
        this.scrollY = 0;
    }

    private void addDivider(String title) {
        this.rows.add(new EditorRow(title, RowType.DIVIDER, null));
    }

    /** Lista (cacheada) de todas as partículas registradas, vanilla + mods, "namespace:path". */
    private static List<String> getAllParticleIds() {
        if (ALL_PARTICLE_IDS == null) {
            ALL_PARTICLE_IDS = new ArrayList<>();
            for (Identifier id : Registries.PARTICLE_TYPE.getIds()) {
                ALL_PARTICLE_IDS.add(id.toString());
            }
            java.util.Collections.sort(ALL_PARTICLE_IDS);
        }
        return ALL_PARTICLE_IDS;
    }

    /** Refiltra a lista do dropdown ABERTO com base no texto do campo (substring, case-insensitive). */
    private void filterDropdown(String query) {
        String q = query == null ? "" : query.toLowerCase().trim();
        dropdownFiltered.clear();
        if (dropdownRow != null && dropdownRow.dropdownOptions != null) {
            for (String id : dropdownRow.dropdownOptions.get()) {
                if (q.isEmpty() || id.toLowerCase().contains(q)) dropdownFiltered.add(id);
            }
        }
        dropdownScrollY = 0f;
    }

    /** Campo STRING com dropdown. {@code isAction} = o campo fica sempre vazio (é um comando, ex
     *  "Preset"); senão mostra {@code current} e escrever direto também vale. */
    private EditorRow addDropdownField(String label, String current, boolean isAction,
                                       java.util.function.Supplier<List<String>> options,
                                       java.util.function.Consumer<String> onPick) {
        TextFieldWidget field = new TextFieldWidget(parent.getTextRenderer(), 0, 0, 140, 16, Text.literal(""));
        field.setMaxLength(64);
        field.setText(isAction ? "" : (current != null ? current : ""));
        EditorRow row = new EditorRow(label, RowType.STRING, field);
        row.dropdownOptions = options;
        row.dropdownOnPick = onPick;
        row.dropdownIsAction = isAction;
        field.setChangedListener(text -> {
            if (isAction) return; // ação só pelo clique na lista
            hasUnsavedChanges = true;
            onPick.accept(text);
        });
        this.rows.add(row);
        return row;
    }

    private void syncGizmoToFields() {
        if (this.editingData == null) return;
        // BUG (2026-09, ver o mesmo fix em DevCosmeticsSubPage#syncGizmoToFields):
        // TextFieldWidget#setText() dispara o changedListener SEMPRE, mesmo escrevendo o MESMO
        // valor que já tava lá — esse método só espelha o valor atual, não deveria por si só
        // marcar "alteração não salva". Snapshot/restore cancela o disparo espúrio; quem chamou
        // isso por uma mudança de verdade (arraste com delta != 0) já setou hasUnsavedChanges=true
        // ANTES de chamar, então o valor restaurado continua correto nesse caso.
        boolean wasUnsavedBeforeSync = this.hasUnsavedChanges;
        for (EditorRow r : rows) {
            if (r.textField == null) continue;
            try {
                if (r.label.startsWith("Offset X")) r.textField.setText(String.valueOf(Math.round(this.editingData.offsetX * 1000.0) / 1000.0));
                if (r.label.startsWith("Offset Y")) r.textField.setText(String.valueOf(Math.round(this.editingData.offsetY * 1000.0) / 1000.0));
                if (r.label.startsWith("Offset Z")) r.textField.setText(String.valueOf(Math.round(this.editingData.offsetZ * 1000.0) / 1000.0));
            } catch (Exception ignored) {}
        }
        this.hasUnsavedChanges = wasUnsavedBeforeSync;
    }

    private void loadEditor(String id) {
        loadEditor(id, null);
    }

    /** Abre o editor de um efeito PRÓPRIO do grupo (guardado em editingGroup.ownedEffects). */
    private void loadOwnedEffectEditor(int index) {
        this.editingOwnedIndex = index;
        this.editingId = "owned:" + index;
        this.editingGroupId = this.groupId;
        loadEditorFor(this.editingGroup.ownedEffects.get(index));
    }

    private void loadEditor(String id, String groupContext) {
        this.editingOwnedIndex = -1;
        this.editingId = id;
        this.editingGroupId = groupContext;
        loadEditorFor(EffectConfig.effectsMap.get(id));
    }

    private void loadEditorFor(EffectData data) {
        this.tempId = this.editingOwnedIndex < 0 ? this.editingId : null;
        this.editingData = data;
        this.editorBackupJson = data != null ? BACKUP_GSON.toJson(data) : null;
        isolateGroupPreview = false;
        this.currentState = State.EDITOR;
        this.scrollY = 0; // Reseta o scroll ao entrar no editor
        this.rows.clear();

        // Ativa o MESMO gizmo 3D de setas que o editor de Cosméticos usa (ver GizmoManager,
        // ArmorFeatureRendererMixin) — só TRANSLATE, Effect não tem rotação/escala.
        // activePart = null de propósito: os dois alvos são mutuamente exclusivos.
        GizmoManager.activePart = null;
        GizmoManager.activeEffect = null;
        GizmoManager.currentAxis = GizmoManager.Axis.NONE;

        if (this.editingData == null) return;

        GizmoManager.activeEffect = this.editingData;
        GizmoManager.currentMode = GizmoManager.Mode.TRANSLATE;
        GizmoManager.onUpdate = () -> {
            this.hasUnsavedChanges = true;
            this.syncGizmoToFields();
        };

        this.dropdownOpen = false;
        this.dropdownRow = null;

        addDivider(L("devstudio.effect.divider.identification"));
        if (this.editingOwnedIndex < 0) {
            addStringField(L("devstudio.effect.field.effect_id"), this.tempId, text -> this.tempId = text)
                    .withTooltip(L("devstudio.effect.tooltip.effect_id"));
        }

        // --- PRESET (ação: preenche todos os campos e recarrega o editor) ---
        addDivider(L("devstudio.effect.divider.preset"));
        addDropdownField(L("devstudio.effect.field.preset"), "", true,
                com.f4xizzz.greatcosmetics.config.EffectPresets::names,
                name -> {
                    com.f4xizzz.greatcosmetics.config.EffectPresets.apply(this.editingData, name);
                    this.hasUnsavedChanges = true;
                    reloadCurrentEditor();
                }).withTooltip(L("devstudio.effect.tooltip.preset"));

        addDivider(L("devstudio.effect.divider.shape"));
        addDropdownField(L("devstudio.effect.field.shape"), this.editingData.shape, false,
                () -> java.util.List.of("SIMPLE", "CIRCLE", "HELIX", "BEAM", "PULSE"),
                v -> {
                    String s = v.toUpperCase().trim();
                    if (!s.equals(this.editingData.shape)) {
                        this.editingData.shape = s;
                        this.hasUnsavedChanges = true;
                        reloadCurrentEditor();
                    }
                }).withTooltip(L("devstudio.effect.tooltip.shape"));

        addDivider(L("devstudio.effect.divider.configuration"));
        addDropdownField(L("devstudio.effect.field.particle_id"), this.editingData.particleId, false,
                DevEffectsSubPage::getAllParticleIds,
                text -> this.editingData.particleId = text)
                .withTooltip(L("devstudio.effect.tooltip.particle_id"));
        addIntField(L("devstudio.effect.field.count"), this.editingData.count, val -> this.editingData.count = val)
                .withTooltip(L("devstudio.effect.tooltip.count"));
        addIntField(L("devstudio.effect.field.tick_interval"), this.editingData.tickInterval, val -> this.editingData.tickInterval = val)
                .withTooltip(L("devstudio.effect.tooltip.tick_interval"));

        // --- CAMPOS DA FORMA (só quando shape != SIMPLE) ---
        addShapeFields();

        addDivider(L("devstudio.effect.divider.color"));
        boolean colorOn = this.editingData.hasColor();
        addToggleField(L("devstudio.effect.field.color_enabled"), colorOn, on -> {
            if (on) {
                if (this.editingData.colorR < 0) this.editingData.colorR = 255;
                if (this.editingData.colorG < 0) this.editingData.colorG = 255;
                if (this.editingData.colorB < 0) this.editingData.colorB = 255;
            } else {
                this.editingData.colorR = -1; this.editingData.colorG = -1; this.editingData.colorB = -1;
            }
            reloadCurrentEditor();
        }).withTooltip(L("devstudio.effect.tooltip.color"));
        if (colorOn) {
            addSliderField(L("devstudio.effect.field.color_r"), Math.max(0, this.editingData.colorR), 0, 255, v -> this.editingData.colorR = v)
                    .withTooltip(L("devstudio.effect.tooltip.color"));
            addSliderField(L("devstudio.effect.field.color_g"), Math.max(0, this.editingData.colorG), 0, 255, v -> this.editingData.colorG = v)
                    .withTooltip(L("devstudio.effect.tooltip.color"));
            addSliderField(L("devstudio.effect.field.color_b"), Math.max(0, this.editingData.colorB), 0, 255, v -> this.editingData.colorB = v)
                    .withTooltip(L("devstudio.effect.tooltip.color"));
        }

        addDivider(L("devstudio.effect.divider.tool_3d"));
        this.rows.add(new EditorRow(L("devstudio.effect.gizmo_info"), RowType.GIZMO_INFO, null));

        addDivider(L("devstudio.effect.divider.offsets"));
        addDoubleField(L("devstudio.effect.field.offset_x"), this.editingData.offsetX, val -> this.editingData.offsetX = val)
                .withTooltip(L("devstudio.effect.tooltip.offset_x"));
        addDoubleField(L("devstudio.effect.field.offset_y"), this.editingData.offsetY, val -> this.editingData.offsetY = val)
                .withTooltip(L("devstudio.effect.tooltip.offset_y"));
        addDoubleField(L("devstudio.effect.field.offset_z"), this.editingData.offsetZ, val -> this.editingData.offsetZ = val)
                .withTooltip(L("devstudio.effect.tooltip.offset_z"));

        addDivider(L("devstudio.effect.divider.spread"));
        addDoubleField(L("devstudio.effect.field.spread_x"), this.editingData.spreadX, val -> this.editingData.spreadX = val)
                .withTooltip(L("devstudio.effect.tooltip.spread_x"));
        addDoubleField(L("devstudio.effect.field.spread_y"), this.editingData.spreadY, val -> this.editingData.spreadY = val)
                .withTooltip(L("devstudio.effect.tooltip.spread_y"));
        addDoubleField(L("devstudio.effect.field.spread_z"), this.editingData.spreadZ, val -> this.editingData.spreadZ = val)
                .withTooltip(L("devstudio.effect.tooltip.spread_z"));
        addDoubleField(L("devstudio.effect.field.speed"), this.editingData.speed, val -> this.editingData.speed = val)
                .withTooltip(L("devstudio.effect.tooltip.speed"));

        addDivider(L("devstudio.effect.divider.danger"));
        this.rows.add(new EditorRow(L("devstudio.effect.delete"), () -> openConfirmPopup(
                L("devstudio.effect.confirm.delete_effect_title"),
                L("devstudio.effect.confirm.delete_body", "id", this.editingId), () -> {
            EffectConfig.effectsMap.remove(this.editingId);
            sendDeleteEffect(this.editingId); // servidor persiste + rebroadcast
            this.hasUnsavedChanges = false;
            this.currentState = State.LIST;
            this.scrollY = 0;
            GizmoManager.activeEffect = null;
            GizmoManager.onUpdate = null;
        })).withId("delete"));

        this.hasUnsavedChanges = false;
    }

    // ===== GRUPOS DE EFEITO =====

    /** Efeitos primeiro (borda rosa), grupos depois (borda ciano). Filtrado pela busca + modo. */
    private List<String> listEntries() {
        String q = listSearchField != null ? listSearchField.getText().trim().toLowerCase() : "";
        List<String> out = new ArrayList<>();
        if (listFilterMode != 2) {
            for (String id : EffectConfig.effectsMap.keySet())
                if (q.isEmpty() || id.toLowerCase().contains(q)) out.add(id);
        }
        if (listFilterMode != 1) {
            for (String id : com.f4xizzz.greatcosmetics.config.EffectGroupConfig.groupsMap.keySet())
                if (q.isEmpty() || id.toLowerCase().contains(q)) out.add(id);
        }
        return out;
    }

    private void openGroupEditor(String id) {
        this.groupId = id;
        this.groupTempId = id;
        this.editingGroup = com.f4xizzz.greatcosmetics.config.EffectGroupConfig.groupsMap.get(id);
        if (this.editingGroup == null) { this.currentState = State.LIST; return; }
        if (this.editingGroup.effectIds == null) this.editingGroup.effectIds = new ArrayList<>();
        if (this.editingGroup.ownedEffects == null) this.editingGroup.ownedEffects = new ArrayList<>();
        this.editorBackupJson = BACKUP_GSON.toJson(this.editingGroup);
        this.groupPickerOpen = false;
        this.editingGroupId = null;
        this.editingOwnedIndex = -1;
        this.currentState = State.GROUP_EDITOR;
        this.scrollY = 0;
        GizmoManager.activeEffect = null;
        GizmoManager.onUpdate = null;
        buildGroupEditorRows();
        this.hasUnsavedChanges = false;
    }

    private void buildGroupEditorRows() {
        this.rows.clear();
        this.dropdownOpen = false;
        this.dropdownRow = null;
        if (this.editingGroup == null) return;
        if (this.editingGroup.effectIds == null) this.editingGroup.effectIds = new ArrayList<>();
        if (this.editingGroup.ownedEffects == null) this.editingGroup.ownedEffects = new ArrayList<>();

        if (groupPickerOpen) {
            addDivider(L("devstudio.effect.group.pick_title"));
            this.rows.add(new EditorRow(L("devstudio.effect.group.pick_cancel"), () -> { groupPickerOpen = false; buildGroupEditorRows(); }).withId("cancel"));
            for (String eid : new ArrayList<>(EffectConfig.effectsMap.keySet())) {
                if (this.editingGroup.effectIds.contains(eid)) continue;
                String pick = eid;
                this.rows.add(new EditorRow("+ " + eid, () -> {
                    this.editingGroup.effectIds.add(pick);
                    hasUnsavedChanges = true;
                    groupPickerOpen = false;
                    buildGroupEditorRows();
                }).withId("add"));
            }
            return;
        }

        addDivider(L("devstudio.effect.group.divider.id"));
        addStringField(L("devstudio.effect.group.field.id"), this.groupTempId, t -> this.groupTempId = t);

        addDivider(L("devstudio.effect.group.divider.preset"));
        addDropdownField(L("devstudio.effect.field.preset"), "", true,
                com.f4xizzz.greatcosmetics.config.EffectGroupPresets::names,
                this::loadGroupPreset)
                .withTooltip(L("devstudio.effect.group.tooltip.preset"));

        addDivider(L("devstudio.effect.group.divider.members"));
        // efeitos PRÓPRIOS do grupo
        for (int i = 0; i < this.editingGroup.ownedEffects.size(); i++) {
            EffectData ow = this.editingGroup.ownedEffects.get(i);
            int idx = i;
            String label = "#" + (i + 1) + " " + (ow != null && ow.particleId != null ? ow.particleId.replace("minecraft:", "") : "?")
                    + (ow != null && ow.isShape() ? " (" + ow.shape.toLowerCase() + ")" : "");
            this.rows.add(new EditorRow(L("devstudio.effect.group.btn.edit_owned", "label", label), () -> loadOwnedEffectEditor(idx)).withId("edit_owned_" + i));
            this.rows.add(new EditorRow(L("devstudio.effect.group.btn.remove_member", "id", "#" + (idx + 1)), () -> openConfirmPopup(
                    L("devstudio.effect.confirm.remove_owned_title"),
                    L("devstudio.effect.confirm.remove_owned_body"), () -> {
                this.editingGroup.ownedEffects.remove(idx);
                hasUnsavedChanges = true;
                saveGroup();
                buildGroupEditorRows();
            })).withId("remove_owned_" + i));
        }
        // efeitos COMPARTILHADOS referenciados
        for (int i = 0; i < this.editingGroup.effectIds.size(); i++) {
            String memberId = this.editingGroup.effectIds.get(i);
            int idx = i;
            this.rows.add(new EditorRow(L("devstudio.effect.group.btn.edit_member", "id", memberId), () -> loadEditor(memberId, this.groupId)).withId("edit_member_" + i));
            this.rows.add(new EditorRow(L("devstudio.effect.group.btn.remove_member", "id", memberId), () -> openConfirmPopup(
                    L("devstudio.effect.confirm.remove_member_title"),
                    L("devstudio.effect.confirm.remove_member_body", "id", memberId), () -> {
                this.editingGroup.effectIds.remove(idx);
                hasUnsavedChanges = true;
                saveGroup();
                buildGroupEditorRows();
            })).withId("remove_member_" + i));
        }
        this.rows.add(new EditorRow(L("devstudio.effect.group.btn.create_new"), () -> {
            EffectData nd = new EffectData();
            nd.particleId = "minecraft:flame";
            this.editingGroup.ownedEffects.add(nd);
            saveGroup();
            loadOwnedEffectEditor(this.editingGroup.ownedEffects.size() - 1);
        }).withId("add_create"));
        this.rows.add(new EditorRow(L("devstudio.effect.group.btn.add_existing"), () -> { groupPickerOpen = true; buildGroupEditorRows(); }).withId("add_existing"));

        addDivider(L("devstudio.effect.divider.danger"));
        this.rows.add(new EditorRow(L("devstudio.effect.group.delete"), () -> openConfirmPopup(
                L("devstudio.effect.confirm.delete_group_title"),
                L("devstudio.effect.confirm.delete_body", "id", this.groupId), () -> {
            com.f4xizzz.greatcosmetics.config.EffectGroupConfig.groupsMap.remove(this.groupId);
            sendDeleteEffectGroup(this.groupId); // servidor persiste + rebroadcast
            this.hasUnsavedChanges = false;
            this.currentState = State.LIST;
            this.scrollY = 0;
        })).withId("delete"));
    }

    /** Aplica um preset COMBO: SUBSTITUI os efeitos próprios do grupo pelos templates. Não cria
     *  nada em effectsMap — os efeitos ficam DENTRO do grupo (ownedEffects). */
    private void loadGroupPreset(String name) {
        if (this.editingGroup == null) return;
        List<EffectData> templates = com.f4xizzz.greatcosmetics.config.EffectGroupPresets.templates(name);
        if (templates.isEmpty()) return;
        this.editingGroup.ownedEffects = new ArrayList<>(templates);
        this.editingGroup.effectIds = new ArrayList<>();
        this.hasUnsavedChanges = true;
        saveGroup();
        buildGroupEditorRows();
    }

    /** Persiste o grupo atual (aplica rename via groupTempId) — local + servidor. */
    private void saveGroup() {
        if (this.editingGroup == null) return;
        String oldId = this.groupId;
        String newId = (this.groupTempId != null && !this.groupTempId.trim().isEmpty()) ? this.groupTempId.trim() : oldId;
        if (!newId.equals(oldId)) {
            com.f4xizzz.greatcosmetics.config.EffectGroupConfig.groupsMap.remove(oldId);
            this.groupId = newId;
        }
        com.f4xizzz.greatcosmetics.config.EffectGroupConfig.groupsMap.put(newId, this.editingGroup);
        // Persistência é do SERVIDOR (SaveEffectGroupPayload → server salva + rebroadcast). O client
        // NÃO escreve o effect_groups.json (no dedicado ele teria um arquivo próprio que fica velho).
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.f4xizzz.greatcosmetics.network.SaveEffectGroupPayload(oldId, newId, new com.google.gson.Gson().toJson(this.editingGroup)));
    }

    private void sendDeleteEffectGroup(String id) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.f4xizzz.greatcosmetics.network.DeleteEffectGroupPayload(id));
    }

    private EditorRow addStringField(String label, String startVal, java.util.function.Consumer<String> action) {
        TextFieldWidget field = new TextFieldWidget(parent.getTextRenderer(), 0, 0, 140, 16, Text.literal(""));
        field.setMaxLength(128); field.setText(startVal != null ? startVal : "");
        field.setChangedListener(text -> {
            hasUnsavedChanges = true;
            action.accept(text);
        });
        EditorRow row = new EditorRow(label, RowType.STRING, field);
        this.rows.add(row);
        return row;
    }

    private EditorRow addIntField(String label, int startVal, java.util.function.Consumer<Integer> action) {
        TextFieldWidget field = new TextFieldWidget(parent.getTextRenderer(), 0, 0, 140, 16, Text.literal(""));
        field.setMaxLength(10); field.setText(String.valueOf(startVal));
        field.setChangedListener(text -> {
            hasUnsavedChanges = true;
            try { if (!text.isEmpty() && !text.equals("-")) action.accept(Integer.parseInt(text)); } catch (Exception ignored) {}
        });
        EditorRow row = new EditorRow(label, RowType.INT, field);
        this.rows.add(row);
        return row;
    }

    /** Barra rolante (slider) genérica — clique/arraste na trilha define o valor entre min e max. */
    private EditorRow addSliderField(String label, int startVal, int min, int max, java.util.function.IntConsumer action) {
        EditorRow row = new EditorRow(label, Math.max(min, Math.min(max, startVal)), min, max, v -> {
            hasUnsavedChanges = true;
            action.accept(v);
        });
        this.rows.add(row);
        return row;
    }

    /** Traduz a posição X do mouse na trilha [tx0, tx1] pro valor do slider e dispara o callback. */
    private void applySliderFromMouse(EditorRow row, double mx, int tx0, int tx1) {
        if (row == null) return;
        float frac = (float) ((mx - tx0) / Math.max(1, tx1 - tx0));
        frac = Math.max(0f, Math.min(1f, frac));
        int v = row.sliderMin + Math.round(frac * (row.sliderMax - row.sliderMin));
        if (v != row.sliderVal) {
            row.sliderVal = v;
            if (row.onSlider != null) row.onSlider.accept(v);
        }
    }

    /** Recarrega o editor atual pelo caminho certo (efeito próprio do grupo vs. efeito normal). */
    private void reloadCurrentEditor() {
        if (this.editingOwnedIndex >= 0) loadOwnedEffectEditor(this.editingOwnedIndex);
        else loadEditor(this.editingId, this.editingGroupId);
    }

    /** Campos específicos da forma — chamado no loadEditor. Nada pra SIMPLE. */
    private void addShapeFields() {
        EffectData d = this.editingData;
        if (d == null || !d.isShape()) return;
        String s = d.shape.toUpperCase();

        addDivider(L("devstudio.effect.divider.shape_params"));
        addDoubleField(L("devstudio.effect.field.radius"), d.radius, v -> d.radius = v).withTooltip(L("devstudio.effect.tooltip.radius"));
        addIntField(L("devstudio.effect.field.points"), d.points, v -> d.points = v).withTooltip(L("devstudio.effect.tooltip.points"));
        addIntField(L("devstudio.effect.field.strands"), d.strands, v -> d.strands = v).withTooltip(L("devstudio.effect.tooltip.strands"));
        addDoubleField(L("devstudio.effect.field.phase"), d.phase, v -> d.phase = v).withTooltip(L("devstudio.effect.tooltip.phase"));
        addToggleField(L("devstudio.effect.field.clockwise"), d.clockwise, v -> d.clockwise = v).withTooltip(L("devstudio.effect.tooltip.clockwise"));

        if (s.equals("HELIX")) {
            addDoubleField(L("devstudio.effect.field.helix_height"), d.helixHeight, v -> d.helixHeight = v).withTooltip(L("devstudio.effect.tooltip.helix_height"));
            addDoubleField(L("devstudio.effect.field.turns"), d.turns, v -> d.turns = v).withTooltip(L("devstudio.effect.tooltip.turns"));
            addToggleField(L("devstudio.effect.field.reverse"), d.reverse, v -> d.reverse = v).withTooltip(L("devstudio.effect.tooltip.reverse"));
        } else if (s.equals("BEAM")) {
            addDoubleField(L("devstudio.effect.field.beam_height"), d.beamHeight, v -> d.beamHeight = v).withTooltip(L("devstudio.effect.tooltip.beam_height"));
            addDoubleField(L("devstudio.effect.field.spacing"), d.spacing, v -> d.spacing = v).withTooltip(L("devstudio.effect.tooltip.spacing"));
            addToggleField(L("devstudio.effect.field.upwards"), d.upwards, v -> d.upwards = v).withTooltip(L("devstudio.effect.tooltip.upwards"));
        } else if (s.equals("PULSE")) {
            addDoubleField(L("devstudio.effect.field.end_radius"), d.endRadius, v -> d.endRadius = v).withTooltip(L("devstudio.effect.tooltip.end_radius"));
            addIntField(L("devstudio.effect.field.end_points"), d.endPoints, v -> d.endPoints = v).withTooltip(L("devstudio.effect.tooltip.end_points"));
            addIntField(L("devstudio.effect.field.rings"), d.rings, v -> d.rings = v).withTooltip(L("devstudio.effect.tooltip.rings"));
            addToggleField(L("devstudio.effect.field.outwards"), d.outwards, v -> d.outwards = v).withTooltip(L("devstudio.effect.tooltip.outwards"));
        }

        addDoubleField(L("devstudio.effect.field.rot_x"), d.rotX, v -> d.rotX = v).withTooltip(L("devstudio.effect.tooltip.rot"));
        addDoubleField(L("devstudio.effect.field.rot_y"), d.rotY, v -> d.rotY = v).withTooltip(L("devstudio.effect.tooltip.rot"));
        addDoubleField(L("devstudio.effect.field.rot_z"), d.rotZ, v -> d.rotZ = v).withTooltip(L("devstudio.effect.tooltip.rot"));
        addIntField(L("devstudio.effect.field.anim_ticks"), d.animTicks, v -> d.animTicks = v).withTooltip(L("devstudio.effect.tooltip.anim_ticks"));
    }

    private EditorRow addToggleField(String label, boolean startVal, java.util.function.Consumer<Boolean> action) {
        EditorRow row = new EditorRow(label, startVal, v -> { hasUnsavedChanges = true; action.accept(v); });
        this.rows.add(row);
        return row;
    }

    private EditorRow addDoubleField(String label, double startVal, java.util.function.Consumer<Double> action) {
        TextFieldWidget field = new TextFieldWidget(parent.getTextRenderer(), 0, 0, 140, 16, Text.literal(""));
        field.setMaxLength(20); field.setText(String.valueOf(startVal));
        field.setChangedListener(text -> {
            hasUnsavedChanges = true;
            try { if (!text.isEmpty() && !text.equals("-") && !text.equals(".")) action.accept(Double.parseDouble(text)); } catch (Exception ignored) {}
        });
        EditorRow row = new EditorRow(label, RowType.DOUBLE, field);
        this.rows.add(row);
        return row;
    }

    // ==========================================
    // SPAWNER DE PARTÍCULAS EM TEMPO REAL — espelha GreatCosmetics#spawnEffect (decide sozinho
    // pelo worldTime SE spawna neste tick; SIMPLE = count+spread, forma = ParticleShapes).
    // ==========================================
    private void spawnPreviewParticles(EffectData data) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || data == null) return;

        net.minecraft.particle.ParticleEffect pe = com.f4xizzz.greatcosmetics.util.ParticleFx.resolve(data);
        if (pe == null) return;

        long ticks = client.world.getTime();
        double yawRad = Math.toRadians(client.player.bodyYaw);
        double cos = Math.cos(yawRad), sin = Math.sin(yawRad);
        double baseX = client.player.getX() - (data.offsetX * cos) - (data.offsetZ * sin);
        double baseZ = client.player.getZ() - (data.offsetX * sin) + (data.offsetZ * cos);
        double baseY = client.player.getY() + data.offsetY;
        int interval = Math.max(1, data.tickInterval);

        if (!data.isShape()) {
            if (ticks % interval != 0) return;
            for (int i = 0; i < data.count; i++) {
                double px = baseX + (client.world.random.nextDouble() - 0.5) * data.spreadX * 2;
                double py = baseY + (client.world.random.nextDouble() - 0.5) * data.spreadY * 2;
                double pz = baseZ + (client.world.random.nextDouble() - 0.5) * data.spreadZ * 2;
                double vx = (client.world.random.nextDouble() - 0.5) * data.speed;
                double vy = (client.world.random.nextDouble() - 0.5) * data.speed;
                double vz = (client.world.random.nextDouble() - 0.5) * data.speed;
                client.world.addParticle(pe, px, py, pz, vx, vy, vz);
            }
            return;
        }

        double anim;
        if (data.animTicks > 0) {
            long cycle = data.animTicks + Math.max(0, data.tickInterval);
            long inCycle = ticks % cycle;
            if (inCycle >= data.animTicks) return;
            anim = inCycle / (double) data.animTicks;
        } else {
            if (ticks % interval != 0) return;
            anim = 1.0;
        }

        for (org.joml.Vector3d p : com.f4xizzz.greatcosmetics.util.ParticleShapes.points(data, anim)) {
            double wx = baseX + (p.x * cos - p.z * sin);
            double wz = baseZ + (p.x * sin + p.z * cos);
            client.world.addParticle(pe, wx, baseY + p.y, wz,
                    (client.world.random.nextDouble() - 0.5) * data.speed,
                    (client.world.random.nextDouble() - 0.5) * data.speed,
                    (client.world.random.nextDouble() - 0.5) * data.speed);
        }
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta, int x, int y, int width, int height) {
        INSTANCE = this; // <--- ADICIONE ESTA LINHA
        this.lastX = x; this.lastY = y; this.lastWidth = width; this.lastHeight = height;
        this.hoveredTooltipRow = null;

        boolean isMouseDown = GLFW.glfwGetMouseButton(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (!isMouseDown) {
            isDraggingScrollbar = false;
            draggingSliderRow = null;
            GizmoManager.release();
        }

        // --- LOOP PARA MOSTRAR A PARTÍCULA NO BONECO --- (spawnPreviewParticles decide sozinho SE
        // spawna neste tick, pelo worldTime — só chamamos 1x por tick de mundo)
        {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world != null) {
                long currentTick = client.world.getTime();
                if (currentTick != lastTick) {
                    lastTick = currentTick;
                    if (currentState == State.EDITOR && this.editingData != null) {
                        spawnPreviewParticles(this.editingData);
                        // Contexto de grupo: os OUTROS membros também aparecem no preview, a menos que
                        // o botão "E" (isolar) esteja ligado.
                        if (this.editingGroupId != null && !isolateGroupPreview && this.editingGroup != null) {
                            List<EffectData> ow = this.editingGroup.ownedEffects;
                            if (ow != null) for (int mi = 0; mi < ow.size(); mi++) {
                                if (mi == this.editingOwnedIndex) continue;
                                EffectData m = ow.get(mi);
                                if (m != null) spawnPreviewParticles(m);
                            }
                            if (this.editingGroup.effectIds != null) for (String memberId : this.editingGroup.effectIds) {
                                if (memberId == null || memberId.equals(this.editingId)) continue;
                                EffectData m = EffectConfig.effectsMap.get(memberId);
                                if (m != null) spawnPreviewParticles(m);
                            }
                        }
                    }
                    // Tela do GRUPO aberta (lista de membros): mostra TODOS os efeitos do grupo
                    // juntos, pra ver o combo completo (pedido do usuário).
                    else if (currentState == State.GROUP_EDITOR && this.editingGroup != null) {
                        if (this.editingGroup.ownedEffects != null)
                            for (EffectData m : this.editingGroup.ownedEffects) if (m != null) spawnPreviewParticles(m);
                        if (this.editingGroup.effectIds != null)
                            for (String memberId : this.editingGroup.effectIds) {
                                EffectData m = EffectConfig.effectsMap.get(memberId);
                                if (m != null) spawnPreviewParticles(m);
                            }
                    }
                }
            }
        }

        int topY = y + 35; // Espaço de segurança para não sobrepor o cabeçalho Dev Studio

        if (currentState == State.LIST) {
            boolean hovBack = mouseX >= x + 12 && mouseX <= x + 62 && mouseY >= topY && mouseY <= topY + 12;
            c.drawTextWithShadow(parent.getTextRenderer(), L("devstudio.common.back"), x + 12, topY + 2, hovBack ? 0xFF5555 : 0xAAAAAA);
            c.drawCenteredTextWithShadow(parent.getTextRenderer(), L("devstudio.effect.list_title"), x + (width / 2), topY + 2, 0xFFFFFF);

            // Botões "+ New Effect" (esquerda) e "+ New Group" (direita), lado a lado.
            int half = (width - 24) / 2;
            boolean hovNew = mouseX >= x + 10 && mouseX <= x + 10 + half && mouseY >= topY + 15 && mouseY <= topY + 30;
            c.fill(x + 10, topY + 15, x + 10 + half, topY + 30, hovNew ? 0xFF55FF55 : 0xFF22AA22);
            c.drawCenteredTextWithShadow(parent.getTextRenderer(), L("devstudio.effect.new"), x + 10 + half / 2, topY + 19, 0xFFFFFF);
            boolean hovNewGrp = mouseX >= x + 14 + half && mouseX <= x + 14 + half * 2 && mouseY >= topY + 15 && mouseY <= topY + 30;
            c.fill(x + 14 + half, topY + 15, x + 14 + half * 2, topY + 30, hovNewGrp ? 0xFF55DDFF : 0xFF2288BB);
            c.drawCenteredTextWithShadow(parent.getTextRenderer(), L("devstudio.effect.new_group"), x + 14 + half + half / 2, topY + 19, 0xFFFFFF);

            // --- Busca + filtro (igual AcessoriesPage) ---
            int srchY = topY + 33;
            int filterBtnW = 62;
            if (listSearchField == null) {
                listSearchField = new TextFieldWidget(parent.getTextRenderer(), 0, 0, 10, 14, Text.literal(""));
                listSearchField.setMaxLength(64);
                listSearchField.setDrawsBackground(true);
            }
            listSearchField.setX(x + 10); listSearchField.setY(srchY);
            listSearchField.setWidth(width - 24 - filterBtnW - 4);
            listSearchField.render(c, mouseX, mouseY, delta);
            if (listSearchField.getText().isEmpty() && !listSearchField.isFocused())
                c.drawTextWithShadow(parent.getTextRenderer(), "§7" + L("devstudio.effect.search_hint"), x + 14, srchY + 3, 0x808080);

            int fbX = x + width - 14 - filterBtnW;
            boolean hovFilter = mouseX >= fbX && mouseX <= fbX + filterBtnW && mouseY >= srchY && mouseY <= srchY + 14;
            c.fill(fbX, srchY, fbX + filterBtnW, srchY + 14, hovFilter ? 0x66FFFFFF : 0x44000000);
            c.drawBorder(fbX, srchY, filterBtnW, 14, 0xFF666666);
            c.drawCenteredTextWithShadow(parent.getTextRenderer(), L(LIST_FILTER_KEYS[listFilterMode]) + " ▾", fbX + filterBtnW / 2, srchY + 3, 0xFFDDDDDD);

            List<String> ids = listEntries();
            int listY = topY + 52;
            int itemHeight = 25;

            this.maxScrollY = Math.max(0, (ids.size() * itemHeight) - (height - (topY - y + 57)));
            parent.enablePerfectScissor(c, x, listY, width, height - (topY - y + 57));

            int trashW = 16;
            for (int i = 0; i < ids.size(); i++) {
                int itemY = listY + (i * itemHeight) - (int)scrollY;
                if (itemY + itemHeight < listY || itemY > y + height) continue;

                String entry = ids.get(i);
                boolean isGroup = com.f4xizzz.greatcosmetics.config.EffectGroupConfig.groupsMap.containsKey(entry);
                boolean hovered = mouseX >= x + 10 && mouseX <= x + width - 15 && mouseY >= itemY && mouseY <= itemY + itemHeight - 2;
                boolean hovTrash = hovered && mouseX >= x + width - 15 - trashW;
                c.fill(x + 10, itemY, x + width - 15, itemY + itemHeight - 2, hovered ? 0x88FFFFFF : 0x44000000);
                c.fill(x + 10, itemY, x + 12, itemY + itemHeight - 2, isGroup ? 0xFF55DDFF : 0xFFFF55FF);

                c.drawTextWithShadow(parent.getTextRenderer(), (isGroup ? "§b[G] §f" : "") + entry, x + 18, itemY + 6, 0xFFFFFF);

                if (hovered) {
                    // lixeira à direita, dentro da box do item
                    c.fill(x + width - 15 - trashW, itemY, x + width - 15, itemY + itemHeight - 2, hovTrash ? 0x66FF0000 : 0x33FF3333);
                    c.drawCenteredTextWithShadow(parent.getTextRenderer(), "🗑", x + width - 15 - trashW / 2, itemY + 6, hovTrash ? 0xFFFF6666 : 0xFFCCCCCC);
                }
            }
            RenderSystem.disableScissor();
            drawScrollbar(c, x + width - 6, listY, height - (topY - y + 57));

            // Dropdown do filtro — desenhado por último, por cima da lista.
            if (listFilterDropdownOpen) {
                int fbX2 = x + width - 14 - filterBtnW;
                c.getMatrices().push();
                c.getMatrices().translate(0, 0, 300);
                c.fill(fbX2, srchY + 15, fbX2 + filterBtnW, srchY + 15 + 3 * 13 + 2, 0xF0111111);
                c.drawBorder(fbX2, srchY + 15, filterBtnW, 3 * 13 + 2, 0xFF666666);
                for (int fi = 0; fi < 3; fi++) {
                    int oy = srchY + 17 + fi * 13;
                    boolean hovO = mouseX >= fbX2 && mouseX <= fbX2 + filterBtnW && mouseY >= oy - 1 && mouseY <= oy + 11;
                    if (hovO) c.fill(fbX2 + 1, oy - 1, fbX2 + filterBtnW - 1, oy + 11, 0x44FFFFFF);
                    c.drawTextWithShadow(parent.getTextRenderer(), L(LIST_FILTER_KEYS[fi]),
                            fbX2 + 5, oy, fi == listFilterMode ? 0xFF55FF55 : 0xFFDDDDDD);
                }
                c.getMatrices().pop();
            }

        } else if (currentState == State.EDITOR || currentState == State.GROUP_EDITOR) {
            boolean hovBack = mouseX >= x + 10 && mouseX <= x + 30 && mouseY >= topY && mouseY <= topY + 12;
            c.drawTextWithShadow(parent.getTextRenderer(), "<", x + 10, topY + 2, hovBack ? 0xFF5555 : 0xAAAAAA);

            boolean hovSave = mouseX >= x + width - 25 && mouseX <= x + width - 10 && mouseY >= topY && mouseY <= topY + 12;
            c.drawTextWithShadow(parent.getTextRenderer(), "S", x + width - 20, topY + 2, hovSave ? 0x55FF55 : 0xAAAAAA);

            // --- EFEITO MARQUEE NO TÍTULO ---
            String title = currentState == State.GROUP_EDITOR ? "§b[G] " + groupId : "§e" + editingId;
            int titleW = parent.getTextRenderer().getWidth(title);
            int maxTitleW = width - 60; // Espaço seguro

            if (titleW > maxTitleW) {
                long time = Util.getMeasuringTimeMs();
                int overflow = titleW - maxTitleW;
                double progress = (Math.sin(time / 500.0) + 1.0) / 2.0;
                int offset = (int) (progress * overflow);

                parent.enablePerfectScissor(c, x + 30, topY, maxTitleW, 12);
                c.drawTextWithShadow(parent.getTextRenderer(), title, x + 30 - offset, topY + 2, 0xFFFFFF);
                RenderSystem.disableScissor();
            } else {
                c.drawCenteredTextWithShadow(parent.getTextRenderer(), title, x + (width / 2), topY + 2, 0xFFFFFF);
            }

            int listY = topY + 20; int rowHeight = 35;
            this.maxScrollY = Math.max(0, (this.rows.size() * rowHeight) - (height - (topY - y + 25)));

            parent.enablePerfectScissor(c, x, listY, width, height - (topY - y + 25));
            for (int i = 0; i < this.rows.size(); i++) {
                EditorRow row = this.rows.get(i);
                int rowY = listY + (i * rowHeight) - (int)scrollY;

                if (rowY < listY - rowHeight || rowY > y + height) {
                    if (row.textField != null) { row.textField.visible = false; row.textField.active = false; }
                    continue;
                }

                if (row.tooltip != null && mouseX >= x + 10 && mouseX <= x + width - 10 && mouseY >= rowY - 2 && mouseY <= rowY + 30) {
                    hoveredTooltipRow = row;
                }

                if (row.type == RowType.DIVIDER) {
                    c.fill(x + 10, rowY + 4, x + width - 10, rowY + 20, 0x88FFAA00);
                    c.drawCenteredTextWithShadow(parent.getTextRenderer(), row.label, x + (width/2), rowY + 8, 0xFFFFFF);
                    continue;
                }

                if (row.type == RowType.GIZMO_INFO) {
                    c.drawTextWithShadow(parent.getTextRenderer(), "§e" + row.label, x + 15, rowY, 0xFFFFFF);
                    continue;
                }

                if (row.type != RowType.BUTTON) {
                    c.drawTextWithShadow(parent.getTextRenderer(), "§f" + row.label, x + 15, rowY, 0xFFFFFF);
                }

                if (row.type == RowType.STRING || row.type == RowType.INT || row.type == RowType.DOUBLE) {
                    row.textField.visible = true; row.textField.active = true;
                    row.textField.setX(x + 15); row.textField.setY(rowY + 12);
                    row.textField.setWidth(width - 35);
                    row.textField.render(c, mouseX, mouseY, delta);
                    if (row.dropdownOptions != null) {
                        c.drawTextWithShadow(parent.getTextRenderer(), "▾", x + width - 28, rowY + 15, 0xFFAAAAAA);
                    }
                }
                else if (row.type == RowType.TOGGLE) {
                    boolean hovT = mouseX >= x + 15 && mouseX <= x + width - 20 && mouseY >= rowY + 12 && mouseY <= rowY + 28;
                    int onC = row.toggleValue ? 0xFF22AA22 : 0x44000000;
                    c.fill(x + 15, rowY + 12, x + width - 20, rowY + 28, hovT ? (row.toggleValue ? 0xFF33CC33 : 0x66FFFFFF) : onC);
                    c.drawBorder(x + 15, rowY + 12, width - 35, 16, row.toggleValue ? 0xFF55FF55 : 0xFF888888);
                    c.drawCenteredTextWithShadow(parent.getTextRenderer(), L(row.toggleValue ? "devstudio.common.on" : "devstudio.common.off"), x + (width/2), rowY + 16, 0xFFFFFF);
                }
                else if (row.type == RowType.SLIDER) {
                    int tx0 = x + 15, tx1 = x + width - 20;
                    int ty0 = rowY + 14, ty1 = rowY + 24;
                    boolean hovS = mouseX >= x + 13 && mouseX <= x + width - 18 && mouseY >= rowY + 8 && mouseY <= rowY + 30;
                    float frac = (row.sliderMax > row.sliderMin)
                            ? (row.sliderVal - row.sliderMin) / (float) (row.sliderMax - row.sliderMin) : 0f;
                    int fillX = tx0 + Math.round(frac * (tx1 - tx0));
                    c.fill(tx0, ty0, tx1, ty1, 0xFF333333);                                  // trilha
                    c.fill(tx0, ty0, fillX, ty1, hovS ? 0xFF55AAFF : 0xFF3A82C4);            // preenchido
                    c.fill(fillX - 1, rowY + 11, fillX + 2, rowY + 27, 0xFFFFFFFF);          // punho
                    c.drawCenteredTextWithShadow(parent.getTextRenderer(), String.valueOf(row.sliderVal), (tx0 + tx1) / 2, rowY + 15, 0xFFFFFFFF);
                }
                else if (row.type == RowType.BUTTON) {
                    boolean hovBtn = mouseX >= x + 15 && mouseX <= x + width - 20 && mouseY >= rowY + 12 && mouseY <= rowY + 28;
                    c.fill(x + 15, rowY + 12, x + width - 20, rowY + 28, devButtonFill(row.id, hovBtn));
                    c.drawCenteredTextWithShadow(parent.getTextRenderer(), row.label, x + (width/2), rowY + 16, 0xFFFFFF);
                }
            }
            RenderSystem.disableScissor();
            drawScrollbar(c, x + width - 6, listY, height - (topY - y + 25));

            if (dropdownOpen && dropdownRow != null) {
                int idx = this.rows.indexOf(dropdownRow);
                int fieldRowY = listY + (idx * rowHeight) - (int) scrollY;
                if (fieldRowY >= listY - rowHeight && fieldRowY <= y + height) {
                    renderDropdown(c, x, fieldRowY, width, mouseX, mouseY);
                }
            }
        }

        // --- RENDERIZANDO O POPUP UNIVERSAL DE SAÍDA POR CIMA DE TUDO ---
        renderExitPopup(c, mouseX, mouseY);
        renderConfirmPopup(c, mouseX, mouseY);

        renderHoveredTooltip(c, mouseX, mouseY);
    }

    /** Ver DevCosmeticsSubPage#renderHoveredTooltip. */
    private void renderHoveredTooltip(DrawContext c, int mouseX, int mouseY) {
        if (hoveredTooltipRow == null || hoveredTooltipRow.tooltip == null) return;
        List<Text> lines = new ArrayList<>();
        for (String line : hoveredTooltipRow.tooltip.split("\n")) {
            lines.add(Text.literal(line));
        }
        c.drawTooltip(parent.getTextRenderer(), lines, mouseX, mouseY);
    }

    /** Lista suspensa com todas as partículas registradas, filtrada pelo texto do campo. Desenhada
     *  com z alto (igual ao popup/dropdown de categoria em AcessoriesPage) pra ficar por CIMA das
     *  linhas seguintes do editor, independente da ordem de desenho. */
    private void renderDropdown(DrawContext c, int panelX, int fieldRowY, int panelWidth, int mouseX, int mouseY) {
        int ddX = panelX + 15;
        int ddY = fieldRowY + 28;
        int ddW = panelWidth - 35;
        int rowH = 12;
        int maxVisible = 8;
        int visibleCount = Math.max(1, Math.min(maxVisible, dropdownFiltered.size()));
        int listH = visibleCount * rowH;

        this.dropdownMaxScrollY = Math.max(0, (dropdownFiltered.size() * rowH) - listH);
        if (this.dropdownScrollY > this.dropdownMaxScrollY) this.dropdownScrollY = this.dropdownMaxScrollY;

        c.getMatrices().push();
        c.getMatrices().translate(0, 0, 300);

        c.fill(ddX, ddY, ddX + ddW, ddY + listH + 4, 0xFA111111);
        c.drawBorder(ddX, ddY, ddW, listH + 4, 0xFF666666);

        if (dropdownFiltered.isEmpty()) {
            c.drawTextWithShadow(parent.getTextRenderer(), L("devstudio.effect.particles_none"), ddX + 3, ddY + 4, 0xFFAAAAAA);
        } else {
            parent.enablePerfectScissor(c, ddX, ddY + 2, ddW, listH);
            for (int i = 0; i < dropdownFiltered.size(); i++) {
                int itemY = ddY + 2 + (i * rowH) - (int) dropdownScrollY;
                if (itemY + rowH < ddY + 2 || itemY > ddY + 2 + listH) continue;

                boolean hov = mouseX >= ddX && mouseX <= ddX + ddW && mouseY >= itemY && mouseY <= itemY + rowH;
                if (hov) c.fill(ddX, itemY, ddX + ddW, itemY + rowH, 0xFF333333);
                c.drawTextWithShadow(parent.getTextRenderer(), dropdownFiltered.get(i), ddX + 3, itemY + 2, hov ? 0xFFFFFF : 0xFFAAAAAA);
            }
            RenderSystem.disableScissor();
        }

        c.getMatrices().pop();
    }

    private void drawScrollbar(DrawContext c, int x, int y, int height) {
        if (maxScrollY > 0) {
            c.fill(x, y, x + 4, y + height, 0x44000000);
            int handleHeight = Math.max(10, (int) (height * ((float) height / (height + maxScrollY))));
            int handleY = y + (int) ((height - handleHeight) * (scrollY / maxScrollY));
            c.fill(x, handleY, x + 4, handleY + handleHeight, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // --- SE UM POPUP ESTIVER ABERTO, INTERCEPTA TUDO ---
        if (handleConfirmPopupClick(mx, my)) return true;
        if (handlePopupClick(mx, my)) return true;

        if (button != 0) return false;

        // Testa se o clique caiu numa seta do gizmo de verdade (ver GizmoManager) ANTES de
        // qualquer hit-test de row — mesma prioridade que DevCosmeticsSubPage já dá pro gizmo de
        // Parts. Se caiu, agarra o eixo e consome o clique; senão deixa passar reto (câmera gira /
        // clique de fundo, ver o fallback mais abaixo).
        if (currentState == State.EDITOR && GizmoManager.activeEffect != null && GizmoManager.tryGrab(mx, my)) return true;

        int x = lastX; int y = lastY; int width = lastWidth; int height = lastHeight;
        int topY = y + 35;

        // --- LÓGICA DE CLIQUE NA BARRA DE SCROLL ---
        int listY = currentState == State.LIST ? topY + 52 : topY + 20;
        int trackHeight = currentState == State.LIST ? height - (topY - y + 57) : height - (topY - y + 25);
        int sX = x + width - 6;

        if (maxScrollY > 0 && mx >= sX && mx <= sX + 4 && my >= listY && my <= listY + trackHeight) {
            isDraggingScrollbar = true;
            return true;
        }

        if (currentState == State.LIST) {
            if (mx >= x + 12 && mx <= x + 62 && my >= topY && my <= topY + 12) {
                playClick();
                tryExit(() -> {
                    this.onBack.run();
                    this.scrollY = 0; // Reset ao voltar pro menu principal
                });
                return true;
            }

            // "+ New Effect" (esquerda) / "+ New Group" (direita)
            int half = (width - 24) / 2;
            if (my >= topY + 15 && my <= topY + 30) {
                if (mx >= x + 10 && mx <= x + 10 + half) {
                    playClick();
                    String newId = "new_effect_" + UUID.randomUUID().toString().substring(0, 4);
                    EffectData newData = new EffectData();
                    newData.particleId = "minecraft:flame";
                    EffectConfig.effectsMap.put(newId, newData);
                    sendSaveEffect(newId, newId, newData); // servidor persiste + rebroadcast
                    loadEditor(newId);
                    return true;
                }
                if (mx >= x + 14 + half && mx <= x + 14 + half * 2) {
                    playClick();
                    String newId = "new_group_" + UUID.randomUUID().toString().substring(0, 4);
                    com.f4xizzz.greatcosmetics.config.EffectGroupData g = new com.f4xizzz.greatcosmetics.config.EffectGroupData();
                    com.f4xizzz.greatcosmetics.config.EffectGroupConfig.groupsMap.put(newId, g);
                    openGroupEditor(newId);
                    saveGroup();
                    return true;
                }
            }

            // --- Busca + filtro ---
            int srchY = topY + 33; int filterBtnW = 62; int fbX = x + width - 14 - filterBtnW;
            if (listFilterDropdownOpen) {
                for (int fi = 0; fi < 3; fi++) {
                    int oy = srchY + 17 + fi * 13;
                    if (mx >= fbX && mx <= fbX + filterBtnW && my >= oy - 1 && my <= oy + 11) {
                        playClick(); listFilterMode = fi; listFilterDropdownOpen = false; scrollY = 0; return true;
                    }
                }
                listFilterDropdownOpen = false;
                // não retorna — deixa o clique cair no resto (fechar clicando fora)
            }
            if (mx >= fbX && mx <= fbX + filterBtnW && my >= srchY && my <= srchY + 14) {
                playClick(); listFilterDropdownOpen = !listFilterDropdownOpen; return true;
            }
            if (listSearchField != null && mx >= x + 10 && mx <= x + width - 18 - filterBtnW && my >= srchY && my <= srchY + 14) {
                listSearchField.setFocused(true);
                listSearchField.mouseClicked(mx, my, button);
                return true;
            }
            if (listSearchField != null) listSearchField.setFocused(false);

            List<String> ids = listEntries();
            int iterListY = topY + 52; int itemHeight = 25; int trashW = 16;

            for (int i = 0; i < ids.size(); i++) {
                int itemY = iterListY + (i * itemHeight) - (int)scrollY;
                if (itemY + itemHeight >= iterListY && itemY <= y + height) {
                    if (mx >= x + 10 && mx <= x + width - 15 && my >= itemY && my <= itemY + itemHeight - 2) {
                        String entry = ids.get(i);
                        boolean isGroup = com.f4xizzz.greatcosmetics.config.EffectGroupConfig.groupsMap.containsKey(entry);
                        if (mx >= x + width - 15 - trashW) {
                            // clique na lixeira → confirmação
                            playClick();
                            openConfirmPopup(L(isGroup ? "devstudio.effect.confirm.delete_group_title" : "devstudio.effect.confirm.delete_effect_title"),
                                    L("devstudio.effect.confirm.delete_body", "id", entry), () -> {
                                if (isGroup) {
                                    com.f4xizzz.greatcosmetics.config.EffectGroupConfig.groupsMap.remove(entry);
                                    sendDeleteEffectGroup(entry); // servidor persiste + rebroadcast
                                } else {
                                    EffectConfig.effectsMap.remove(entry);
                                    sendDeleteEffect(entry); // servidor persiste + rebroadcast
                                }
                            });
                            return true;
                        }
                        playClick();
                        if (isGroup) openGroupEditor(entry);
                        else loadEditor(entry);
                        return true;
                    }
                }
            }
        }
        else if (currentState == State.EDITOR || currentState == State.GROUP_EDITOR) {
            if (mx >= x + 10 && mx <= x + 30 && my >= topY && my <= topY + 12) {
                playClick();
                final State st = currentState;
                final String backToGroup = this.editingGroupId;
                tryExit(() -> {
                    this.dropdownOpen = false;
                    GizmoManager.activeEffect = null;
                    GizmoManager.onUpdate = null;
                    this.scrollY = 0;
                    if (st == State.EDITOR && backToGroup != null && com.f4xizzz.greatcosmetics.config.EffectGroupConfig.groupsMap.containsKey(backToGroup)) {
                        openGroupEditor(backToGroup);
                    } else {
                        this.currentState = State.LIST;
                    }
                });
                return true;
            }
            if (mx >= x + width - 25 && mx <= x + width - 10 && my >= topY && my <= topY + 12) {
                playClick();
                saveChanges();
                hasUnsavedChanges = false;
                return true;
            }

            int iterListY = topY + 20; int rowHeight = 35;

            // --- DROPDOWN DE PARTÍCULAS ABERTO: intercepta o clique antes de qualquer outra linha ---
            if (dropdownOpen && dropdownRow != null) {
                int fieldRowY = iterListY + (rows.indexOf(dropdownRow) * rowHeight) - (int) scrollY;
                int ddX = x + 15; int ddY = fieldRowY + 28; int ddW = width - 35;
                int rowH = 12;
                int visibleCount = Math.max(1, Math.min(8, dropdownFiltered.size()));
                int listH = visibleCount * rowH;

                if (mx >= ddX && mx <= ddX + ddW && my >= ddY && my <= ddY + listH + 4) {
                    int clickedIndex = (int) ((my - (ddY + 2) + dropdownScrollY) / rowH);
                    if (clickedIndex >= 0 && clickedIndex < dropdownFiltered.size()) {
                        String chosen = dropdownFiltered.get(clickedIndex);
                        EditorRow r = dropdownRow;
                        dropdownOpen = false;
                        playClick();
                        if (!r.dropdownIsAction) r.textField.setText(chosen);   // dispara o changedListener -> onPick
                        else if (r.dropdownOnPick != null) r.dropdownOnPick.accept(chosen);   // ação
                        return true;
                    }
                    dropdownOpen = false;
                    return true;
                }

                boolean onFieldItself = mx >= x + 15 && mx <= x + width - 20 && my >= fieldRowY + 12 && my <= fieldRowY + 28;
                if (!onFieldItself) {
                    dropdownOpen = false;
                    playClick();
                    return true;
                }
            }

            boolean clickedAny = false;
            for (EditorRow row : rows) {
                int rowY = iterListY + (rows.indexOf(row) * rowHeight) - (int)scrollY;
                if (rowY >= iterListY - rowHeight && rowY <= y + height) {
                    if (row.type == RowType.STRING || row.type == RowType.INT || row.type == RowType.DOUBLE) {

                        // TRAVA BLINDADA DE FOCO
                        row.textField.setX(x + 15);
                        row.textField.setY(rowY + 12);
                        row.textField.setWidth(width - 35);

                        if (mx >= x + 15 && mx <= x + width - 20 && my >= rowY + 12 && my <= rowY + 28) {
                            row.textField.setFocused(true);
                            row.textField.mouseClicked(mx, my, button);
                            clickedAny = true;
                            for (EditorRow r : rows) if (r != row && r.textField != null) r.textField.setFocused(false);
                            if (row.dropdownOptions != null) {
                                dropdownRow = row;
                                dropdownOpen = true;
                                filterDropdown(row.textField.getText());
                            } else {
                                dropdownOpen = false;
                            }
                            return true;
                        }
                    } else if (row.type == RowType.TOGGLE) {
                        if (mx >= x + 15 && mx <= x + width - 20 && my >= rowY + 12 && my <= rowY + 28) {
                            playClick();
                            row.toggleValue = !row.toggleValue;
                            if (row.onToggle != null) row.onToggle.accept(row.toggleValue);
                            return true;
                        }
                    } else if (row.type == RowType.SLIDER) {
                        if (mx >= x + 13 && mx <= x + width - 18 && my >= rowY + 8 && my <= rowY + 30) {
                            this.draggingSliderRow = row;
                            applySliderFromMouse(row, mx, x + 15, x + width - 20);
                            return true;
                        }
                    } else if (row.type == RowType.BUTTON) {
                        if (mx >= x + 15 && mx <= x + width - 20 && my >= rowY + 12 && my <= rowY + 28) {
                            playClick(); row.onButtonClick.run(); return true;
                        }
                    }
                }
            }
            if (!clickedAny) {
                for (EditorRow r : rows) if (r.textField != null) r.textField.setFocused(false);
                // Não caiu em nenhuma seta do gizmo (já testado no topo do método) nem em nenhuma
                // row — deixa passar reto pra câmera girar normalmente.
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double deltaX, double deltaY) {
        // O eixo já foi decidido no clique (GizmoManager.tryGrab, ver mouseClicked) — só continua
        // arrastando a seta agarrada, igual DevCosmeticsSubPage.
        if (GizmoManager.isDragging && GizmoManager.currentAxis != GizmoManager.Axis.NONE) {
            GizmoManager.handleDrag(mx, my, Wardrobe3DScreen.isPreviewSneaking);
            return true;
        }
        if (this.draggingSliderRow != null) {
            applySliderFromMouse(this.draggingSliderRow, mx, lastX + 15, lastX + lastWidth - 20);
            return true;
        }
        if (isDraggingScrollbar && maxScrollY > 0) {
            int y = lastY;
            int topY = y + 35;
            int listY = currentState == State.LIST ? topY + 35 : topY + 20;
            int trackHeight = currentState == State.LIST ? lastHeight - (topY - y + 40) : lastHeight - (topY - y + 25);

            float proportion = (float) (my - listY) / trackHeight;
            scrollY = proportion * maxScrollY;
            if (scrollY < 0) scrollY = 0;
            if (scrollY > maxScrollY) scrollY = maxScrollY;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (currentState == State.LIST && listSearchField != null && listSearchField.isFocused()) {
            if (listSearchField.keyPressed(keyCode, scanCode, modifiers)) { scrollY = 0; return true; }
        }
        if (currentState == State.EDITOR || currentState == State.GROUP_EDITOR) {
            for (EditorRow row : rows) if (row.textField != null && row.textField.isFocused() && row.textField.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (currentState == State.LIST && listSearchField != null && listSearchField.isFocused()) {
            if (listSearchField.charTyped(chr, modifiers)) { scrollY = 0; return true; }
        }
        if (currentState == State.EDITOR || currentState == State.GROUP_EDITOR) {
            for (EditorRow row : rows) if (row.textField != null && row.textField.isFocused() && row.textField.charTyped(chr, modifiers)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (mx < lastX || mx > lastX + lastWidth || my < lastY || my > lastY + lastHeight) return false;

        if ((currentState == State.EDITOR || currentState == State.GROUP_EDITOR) && dropdownOpen && dropdownRow != null) {
            int x = lastX; int y = lastY; int width = lastWidth; int height = lastHeight;
            int topY = y + 35; int iterListY = topY + 20; int rowHeight = 35;
            int fieldRowY = iterListY + (rows.indexOf(dropdownRow) * rowHeight) - (int) scrollY;
            int ddX = x + 15; int ddY = fieldRowY + 28; int ddW = width - 35;
            int rowH = 12;
            int visibleCount = Math.max(1, Math.min(8, dropdownFiltered.size()));
            int listH = visibleCount * rowH;

            if (mx >= ddX && mx <= ddX + ddW && my >= ddY && my <= ddY + listH + 4) {
                dropdownScrollY -= (float) (vAmount * 15f);
                if (dropdownScrollY < 0) dropdownScrollY = 0;
                if (dropdownScrollY > dropdownMaxScrollY) dropdownScrollY = dropdownMaxScrollY;
                return true;
            }
        }

        this.scrollY -= (float) (vAmount * 15f);
        if (this.scrollY < 0) this.scrollY = 0;
        if (this.scrollY > this.maxScrollY) this.scrollY = this.maxScrollY;
        return true;
    }

    public void playClick() {
        try { MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F)); } catch (Exception ignored) {}
    }

    @Override
    public void saveChanges() {
        if (currentState == State.GROUP_EDITOR) {
            saveGroup();
            return;
        }
        if (this.editingOwnedIndex >= 0) {
            // efeito próprio do grupo: editingData É o objeto guardado em ownedEffects → só
            // persistir o grupo inteiro.
            saveGroup();
            return;
        }
        // --- LÓGICA DE RENOMEAÇÃO DO MAP ---
        String oldId = this.editingId;
        if (this.tempId != null && !this.tempId.trim().isEmpty() && !this.tempId.equals(this.editingId)) {
            EffectConfig.effectsMap.remove(this.editingId);
            EffectConfig.effectsMap.put(this.tempId, this.editingData);
            this.editingId = this.tempId;
        }
        sendSaveEffect(oldId, this.editingId, this.editingData);
    }

    @Override
    public void discardChanges() {
        // NUNCA recarregar do disco: no servidor dedicado o client tem o PRÓPRIO effects.json, que
        // fica velho e clobberava o catálogo sincronizado por rede (bug: "cliquei em não salvar e os
        // efeitos perderam o nome"). Reverte SÓ o efeito/grupo que estava sendo editado, do snapshot.
        if (this.editorBackupJson == null) return;
        try {
            if (currentState == State.GROUP_EDITOR && this.editingGroup != null) {
                com.f4xizzz.greatcosmetics.config.EffectGroupData reverted =
                        BACKUP_GSON.fromJson(this.editorBackupJson, com.f4xizzz.greatcosmetics.config.EffectGroupData.class);
                if (reverted != null && this.groupId != null) {
                    com.f4xizzz.greatcosmetics.config.EffectGroupConfig.groupsMap.put(this.groupId, reverted);
                }
            } else if (this.editingData != null) {
                EffectData reverted = BACKUP_GSON.fromJson(this.editorBackupJson, EffectData.class);
                if (reverted != null) com.f4xizzz.greatcosmetics.config.EffectPresets.copyInto(this.editingData, reverted);
            }
        } catch (Exception ignored) {}
    }

    /** Manda o pedido de salvar de vez pro SERVIDOR (ver SaveEffectPayload) — sem isso o efeito só
     *  existia no effects.json/memória do client que editou, e o servidor (que é quem spawna a
     *  partícula de verdade no tick loop) nunca ficava sabendo, então nenhum cosmético referenciando
     *  esse ID novo spawnava nada até o próximo restart coincidir os dois lados por acaso. */
    private void sendSaveEffect(String oldId, String newId, EffectData data) {
        String json = new com.google.gson.Gson().toJson(data);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.f4xizzz.greatcosmetics.network.SaveEffectPayload(oldId, newId, json));
    }

    private void sendDeleteEffect(String id) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.f4xizzz.greatcosmetics.network.DeleteEffectPayload(id));
    }
}