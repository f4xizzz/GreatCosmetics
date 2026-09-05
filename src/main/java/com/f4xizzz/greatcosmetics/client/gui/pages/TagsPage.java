package com.f4xizzz.greatcosmetics.client.gui.pages;

import com.f4xizzz.greatcosmetics.client.ClientTagsCache;
import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import com.f4xizzz.greatcosmetics.config.TagData;
import com.f4xizzz.greatcosmetics.network.DeleteTagPayload;
import com.f4xizzz.greatcosmetics.network.EquipTagPayload;
import com.f4xizzz.greatcosmetics.network.SaveTagPayload;
import com.f4xizzz.greatcosmetics.util.TextUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Gerenciador de Tags do wardrobe. O botão "DEV" (canto superior direito, mesmo estilo/posição
 * do botão DEV da PartyPage) só aparece pra OP ou quem tem a permissão "gc.dev". Ligado, ele:
 * (1) mostra TODAS as tags como se o player possuísse (preview/teste, igual o unlock-all de
 * skins da Party), e (2) revela os controles de administração (criar, editar via lápis no
 * hover, apagar — tags de grupo do LuckPerms nunca são apagáveis). Desligado, o player só vê
 * suas próprias tags e, no fim da lista, as que não tem (com cadeado).
 */
public class TagsPage extends WardrobePage {

    private static final int PANEL_X = 30, PANEL_Y = 50, PANEL_W = 180, PANEL_H = 260;

    private float scrollY = 0f;
    private float maxScrollY = 0f;
    private boolean checkedDevState = false;

    private enum DevState { LIST, EDITOR }
    private DevState devState = DevState.LIST;

    // Preview 100% client-side da chavinha de Dev (nunca manda EquipTagPayload pra tag que o
    // player não possui de verdade) — evita que o "equipar" de preview vaze uma linha real de
    // posse no banco do servidor, que ficava desbloqueada pra sempre mesmo depois do preview.
    // Static de propósito: PlayerEntityRendererMixin (nametag da aba Tags) lê esse valor direto,
    // sem precisar de referência à instância da página.
    public static String devPreviewEquippedId = null;

    private String editingId = null; // null = criando tag nova
    private boolean editingIsGroupTag = false;
    private TextFieldWidget idField, displayNameField, descriptionField, tagField, permissionsField, minecraftTagField;
    private List<TextFieldWidget> editorFields = new ArrayList<>();

    public TagsPage(Wardrobe3DScreen parent) {
        super(parent);
    }

    @Override
    public void onOpen() {
        this.scrollY = 0;
        this.devState = DevState.LIST;
        this.devPreviewEquippedId = null;

        // Igual a PartyPage: ativa o Dev Mode global automaticamente na primeira vez que quem
        // pode (OP ou "gc.dev") abre essa aba, sem forçar toda vez que reabrir depois.
        if (!checkedDevState) {
            if (hasDevPermission()) {
                com.f4xizzz.greatcosmetics.client.ClientCosmeticCache.isDevModeActive = true;
            }
            checkedDevState = true;
        }
    }

    // Lê do ClientPermissionCache (calculado no servidor) em vez de hasPermissionLevel()/
    // Permissions.check() direto no client — ver SyncDevPermissionsPayload.
    private boolean hasDevPermission() {
        return com.f4xizzz.greatcosmetics.client.ClientPermissionCache.isOperator
                || com.f4xizzz.greatcosmetics.client.ClientPermissionCache.hasGcDev;
    }

    private boolean isDevModeActive() {
        return com.f4xizzz.greatcosmetics.client.ClientCosmeticCache.isDevModeActive && hasDevPermission();
    }

    private String stripFormatting(String s) {
        return s == null ? "" : s.replaceAll("<[^>]*>", "").replaceAll("§.", "");
    }

    /** Tags de grupo por weight DESC, depois tags criadas na GUI em ordem alfabética. */
    private List<TagData> sortTags(List<TagData> input) {
        List<TagData> group = new ArrayList<>();
        List<TagData> custom = new ArrayList<>();
        for (TagData data : input) {
            if (data.isGroupTag) group.add(data); else custom.add(data);
        }
        group.sort((a, b) -> Integer.compare(b.weight, a.weight));
        custom.sort(Comparator.comparing(a -> stripFormatting(a.displayName), String.CASE_INSENSITIVE_ORDER));

        List<TagData> result = new ArrayList<>(group);
        result.addAll(custom);
        return result;
    }

    private List<TagData> getPlayerOrderedTags() {
        List<TagData> owned = new ArrayList<>();
        List<TagData> locked = new ArrayList<>();
        for (TagData data : ClientTagsCache.allTags.values()) {
            if (ClientTagsCache.hasTag(data.id)) owned.add(data); else locked.add(data);
        }
        List<TagData> result = sortTags(owned);
        result.addAll(sortTags(locked));
        return result;
    }

    private List<TagData> getVisibleTags(boolean devMode) {
        return devMode ? sortTags(new ArrayList<>(ClientTagsCache.allTags.values())) : getPlayerOrderedTags();
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta, int x, int y, int width, int height) {
        if (devState == DevState.EDITOR) {
            renderEditor(c, mouseX, mouseY, delta, x, y, width, height, y + 32);
            return;
        }

        boolean devMode = isDevModeActive();
        var world = MinecraftClient.getInstance().world;
        net.minecraft.registry.RegistryWrapper.WrapperLookup regs = world != null ? world.getRegistryManager() : null;

        // --- BOTÃO "DEV" (mesmo estilo/posição do botão DEV da PartyPage) ---
        if (hasDevPermission()) {
            int devBtnW = 40;
            int devBtnX = (x + width - 12) - devBtnW;
            int devBtnY = y + 32;
            boolean devHover = over(mouseX, mouseY, devBtnX, devBtnY, devBtnW, 14);
            int devColor = com.f4xizzz.greatcosmetics.client.ClientCosmeticCache.isDevModeActive ? (devHover ? 0xFF00FF00 : 0xFF00AA00) : (devHover ? 0xFFFF5555 : 0xFFAA0000);

            c.fill(devBtnX, devBtnY, devBtnX + devBtnW, devBtnY + 14, 0x44000000);
            c.drawBorder(devBtnX, devBtnY, devBtnW, 14, devColor);
            c.drawCenteredTextWithShadow(getTextRenderer(), "DEV", devBtnX + (devBtnW / 2), devBtnY + 3, devColor);
        }

        c.drawTextWithShadow(getTextRenderer(), com.f4xizzz.greatcosmetics.config.LangConfig.legacy("tags.header"), x + 12, y + 35, 0xFFFFFF);
        c.fill(x + 12, y + 48, x + width - 12, y + 49, 0xFF444444);

        int listY = y + 55;

        if (devMode) {
            boolean hovNew = over(mouseX, mouseY, x + 10, listY, width - 20, 14);
            c.fill(x + 10, listY, x + width - 10, listY + 14, hovNew ? 0xFF55FF55 : 0xFF22AA22);
            c.drawCenteredTextWithShadow(getTextRenderer(), com.f4xizzz.greatcosmetics.config.LangConfig.legacy("tags.create_new"), x + (width / 2), listY + 3, 0xFFFFFF);
            listY += 18;
        }

        int rowHeight = 22;
        List<TagData> tags = getVisibleTags(devMode);
        this.maxScrollY = Math.max(0, (tags.size() * rowHeight) - (height - (listY - y)));

        parent.enablePerfectScissor(c, x, listY, width, height - (listY - y));

        List<Text> activeTooltip = null;

        for (int i = 0; i < tags.size(); i++) {
            TagData data = tags.get(i);
            int rowY = listY + (i * rowHeight) - (int) scrollY;
            if (rowY + rowHeight < listY || rowY > y + height) continue;

            boolean realOwned = ClientTagsCache.hasTag(data.id);
            boolean owned = devMode || realOwned;
            // Enquanto um preview de Dev estiver ativo, ele manda sozinho no "equipada" (verde) —
            // sem isso, a tag REAL equipada de antes continuava marcada verde AO MESMO TEMPO que
            // a tag em preview, parecendo que duas tags tavam equipadas juntas.
            boolean equipped = devPreviewEquippedId != null
                    ? data.id.equals(devPreviewEquippedId)
                    : (realOwned && ClientTagsCache.isEquipped(data.id));
            boolean hovered = over(mouseX, mouseY, x + 10, rowY, width - 20, rowHeight - 2);

            int bg = equipped ? 0x4400FF00 : (hovered ? 0x88FFFFFF : 0x44000000);
            c.fill(x + 10, rowY, x + width - 10, rowY + rowHeight - 2, bg);
            if (equipped) c.drawBorder(x + 10, rowY, width - 20, rowHeight - 2, 0xFF00FF00);
            if (devMode) c.fill(x + 10, rowY, x + 12, rowY + rowHeight - 2, data.isGroupTag ? 0xFFFFAA00 : 0xFF55FFAA);

            // Text de verdade (não String com códigos §) — só assim hex (<#RRGGBB>) renderiza com a
            // cor exata; o TextRenderer só reconhece as 16 cores legadas quando desenha uma String crua.
            Text label = data.tag != null && !data.tag.isBlank()
                    ? TextUtils.parseToText(data.tag, regs)
                    : TextUtils.parseToText(data.displayName, regs);
            if (label.getString().isBlank()) label = Text.literal(data.id);

            c.drawTextWithShadow(getTextRenderer(), label, x + 15, rowY + 6, owned ? 0xFFFFFF : 0xFF888888);

            if (!owned) {
                c.fill(x + 10, rowY, x + width - 10, rowY + rowHeight - 2, 0x99000000);
                c.drawTextWithShadow(getTextRenderer(), "🔒", x + width - 22, rowY + 6, 0xFFCCCCCC);
            }

            if (devMode && hovered) {
                boolean hovPencil = over(mouseX, mouseY, x + width - 22, rowY, 14, rowHeight - 2);
                c.drawTextWithShadow(getTextRenderer(), "✏", x + width - 20, rowY + 6, hovPencil ? 0xFFFF00 : 0xFFFFFF);
            }

            if (hovered) {
                activeTooltip = new ArrayList<>();
                activeTooltip.add(TextUtils.parseToText(data.displayName, regs));
                if (data.description != null && !data.description.isBlank()) {
                    activeTooltip.add(TextUtils.parseToText(data.description, regs));
                }
                if (owned) {
                    activeTooltip.add(com.f4xizzz.greatcosmetics.config.LangConfig.text(equipped ? "tags.tooltip.click_remove" : "tags.tooltip.click_equip"));
                } else {
                    activeTooltip.add(com.f4xizzz.greatcosmetics.config.LangConfig.text("tags.tooltip.not_owned"));
                }
            }
        }
        RenderSystem.disableScissor();

        if (activeTooltip != null) {
            c.drawTooltip(getTextRenderer(), activeTooltip, mouseX, mouseY);
        }
    }

    // =========================================================================
    // EDITOR (criar/editar — entra nesse estado clicando no lápis ou em "+ Criar Nova Tag")
    // =========================================================================
    private void renderEditor(DrawContext c, int mouseX, int mouseY, float delta, int x, int y, int width, int height, int topY) {
        boolean hovBack = over(mouseX, mouseY, x + 10, topY, 20, 12);
        c.drawTextWithShadow(getTextRenderer(), "<", x + 10, topY + 2, hovBack ? 0xFF5555 : 0xAAAAAA);

        String title = editingId == null
                ? com.f4xizzz.greatcosmetics.config.LangConfig.legacy("tags.editor.title_new")
                : com.f4xizzz.greatcosmetics.config.LangConfig.legacy("tags.editor.title_edit", "id", editingId);
        c.drawCenteredTextWithShadow(getTextRenderer(), title, x + (width / 2), topY + 2, 0xFFFFFF);

        int rowY = topY + 16;
        int rowHeight = 32;

        rowY = renderEditorRow(c, mouseX, mouseY, delta, x, width, rowY, rowHeight, com.f4xizzz.greatcosmetics.config.LangConfig.legacy("tags.editor.field.id"), editingId == null ? idField : null,
                editingId != null ? editingId : null);
        rowY = renderEditorRow(c, mouseX, mouseY, delta, x, width, rowY, rowHeight, com.f4xizzz.greatcosmetics.config.LangConfig.legacy("tags.editor.field.display_name"), displayNameField, null);
        rowY = renderEditorRow(c, mouseX, mouseY, delta, x, width, rowY, rowHeight, com.f4xizzz.greatcosmetics.config.LangConfig.legacy("tags.editor.field.description"), descriptionField, null);
        rowY = renderEditorRow(c, mouseX, mouseY, delta, x, width, rowY, rowHeight, com.f4xizzz.greatcosmetics.config.LangConfig.legacy("tags.editor.field.tag"), tagField, null);
        rowY = renderEditorRow(c, mouseX, mouseY, delta, x, width, rowY, rowHeight, com.f4xizzz.greatcosmetics.config.LangConfig.legacy("tags.editor.field.permissions"), permissionsField, null);
        rowY = renderEditorRow(c, mouseX, mouseY, delta, x, width, rowY, rowHeight, com.f4xizzz.greatcosmetics.config.LangConfig.legacy("tags.editor.field.minecraft_tag"), minecraftTagField, null);

        boolean hovSave = over(mouseX, mouseY, x + 10, rowY + 4, width - 20, 16);
        c.fill(x + 10, rowY + 4, x + width - 10, rowY + 20, hovSave ? 0xFF55FF55 : 0xFF22AA22);
        c.drawCenteredTextWithShadow(getTextRenderer(), com.f4xizzz.greatcosmetics.config.LangConfig.legacy("tags.editor.save"), x + (width / 2), rowY + 8, 0xFFFFFF);

        if (!editingIsGroupTag && editingId != null) {
            int delY = rowY + 24;
            boolean hovDelete = over(mouseX, mouseY, x + 10, delY, width - 20, 16);
            c.fill(x + 10, delY, x + width - 10, delY + 16, hovDelete ? 0xFFFF5555 : 0xFFAA0000);
            c.drawCenteredTextWithShadow(getTextRenderer(), com.f4xizzz.greatcosmetics.config.LangConfig.legacy("tags.editor.delete"), x + (width / 2), delY + 4, 0xFFFFFF);
        }
    }

    private int renderEditorRow(DrawContext c, int mouseX, int mouseY, float delta, int x, int width, int rowY, int rowHeight,
                                 String label, TextFieldWidget field, String staticValue) {
        c.drawTextWithShadow(getTextRenderer(), "§f" + label, x + 15, rowY, 0xFFFFFF);

        if (field != null) {
            field.setX(x + 15);
            field.setY(rowY + 11);
            field.setWidth(width - 30);
            field.visible = true;
            field.active = true;
            field.render(c, mouseX, mouseY, delta);
        } else if (staticValue != null) {
            c.drawTextWithShadow(getTextRenderer(), "§7" + staticValue, x + 15, rowY + 12, 0xAAAAAA);
        }

        return rowY + rowHeight;
    }

    private void openEditor(TagData data) {
        this.editingId = data == null ? null : data.id;
        this.editingIsGroupTag = data != null && data.isGroupTag;

        this.idField = newField(data == null ? "" : data.id);
        this.displayNameField = newField(data == null ? "" : TextUtils.parseToString(data.displayName));
        this.descriptionField = newField(data == null ? "" : TextUtils.parseToString(data.description));
        this.tagField = newField(data == null ? "" : data.tag);
        this.permissionsField = newField(data == null || data.permissions == null ? "" : String.join(", ", data.permissions));
        this.minecraftTagField = newField(data == null ? "" : data.minecraftTag);

        this.editorFields = new ArrayList<>(List.of(displayNameField, descriptionField, tagField, permissionsField, minecraftTagField));
        if (editingId == null) this.editorFields.add(0, idField);

        this.devState = DevState.EDITOR;
    }

    private TextFieldWidget newField(String startValue) {
        TextFieldWidget field = new TextFieldWidget(parent.getTextRenderer(), 0, 0, 140, 16, Text.literal(""));
        field.setMaxLength(256);
        field.setText(startValue != null ? startValue : "");
        return field;
    }

    private void saveTagFromEditor() {
        String id = editingId != null ? editingId : idField.getText().trim().toLowerCase();
        if (id.isEmpty()) return;

        List<String> permissions = new ArrayList<>();
        for (String part : permissionsField.getText().split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) permissions.add(trimmed);
        }

        ClientPlayNetworking.send(new SaveTagPayload(
                id, displayNameField.getText(), descriptionField.getText(), tagField.getText(),
                permissions, minecraftTagField.getText()
        ));

        this.devState = DevState.LIST;
    }

    // =========================================================================
    // INPUT
    // =========================================================================
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (devState == DevState.EDITOR) return editorMouseClicked(mouseX, mouseY);

        if (hasDevPermission()) {
            int devBtnW = 40;
            int devBtnX = (PANEL_X + PANEL_W - 12) - devBtnW;
            int devBtnY = PANEL_Y + 32;
            if (over(mouseX, mouseY, devBtnX, devBtnY, devBtnW, 14)) {
                playClick();
                com.f4xizzz.greatcosmetics.client.ClientCosmeticCache.isDevModeActive = !com.f4xizzz.greatcosmetics.client.ClientCosmeticCache.isDevModeActive;
                // Saindo do Dev Mode, o preview de tag (só client-side) some — o nametag volta a
                // mostrar a tag REAL do player, não a que estava sendo testada.
                if (!com.f4xizzz.greatcosmetics.client.ClientCosmeticCache.isDevModeActive) {
                    devPreviewEquippedId = null;
                }
                return true;
            }
        }

        boolean devMode = isDevModeActive();
        int listY = PANEL_Y + 55;

        if (devMode) {
            if (over(mouseX, mouseY, PANEL_X + 10, listY, PANEL_W - 20, 14)) {
                playClick();
                openEditor(null);
                return true;
            }
            listY += 18;
        }

        int rowHeight = 22;
        List<TagData> tags = getVisibleTags(devMode);

        for (int i = 0; i < tags.size(); i++) {
            int rowY = listY + (i * rowHeight) - (int) scrollY;
            if (rowY + rowHeight < listY || rowY > PANEL_Y + PANEL_H) continue;

            TagData data = tags.get(i);

            if (devMode && over(mouseX, mouseY, PANEL_X + PANEL_W - 22, rowY, 14, rowHeight - 2)) {
                playClick();
                openEditor(data);
                return true;
            }

            if (over(mouseX, mouseY, PANEL_X + 10, rowY, PANEL_W - 20, rowHeight - 2)) {
                boolean realOwned = ClientTagsCache.hasTag(data.id);
                if (realOwned) {
                    playClick();
                    // Clicar numa tag que você tem de verdade sempre vale mais que um preview de
                    // Dev que tava ativo — limpa o preview pra não ficarem as duas marcadas juntas.
                    devPreviewEquippedId = null;
                    ClientPlayNetworking.send(new EquipTagPayload(data.id, false));
                } else if (devMode) {
                    // Preview puramente visual, sem mandar nada pro servidor — nunca cria posse real.
                    playClick();
                    devPreviewEquippedId = data.id.equals(devPreviewEquippedId) ? null : data.id;
                } else {
                    playErrorSound();
                }
                return true;
            }
        }
        return false;
    }

    private boolean editorMouseClicked(double mouseX, double mouseY) {
        int topY = PANEL_Y + 32;

        if (over(mouseX, mouseY, PANEL_X + 10, topY, 20, 12)) {
            playClick();
            devState = DevState.LIST;
            return true;
        }

        for (TextFieldWidget field : editorFields) {
            if (over(mouseX, mouseY, field.getX(), field.getY(), field.getWidth(), field.getHeight())) {
                field.setFocused(true);
                field.mouseClicked(mouseX, mouseY, 0);
                for (TextFieldWidget other : editorFields) if (other != field) other.setFocused(false);
                return true;
            }
        }

        // 6 linhas fixas sempre renderizadas em renderEditor: ID, Display Name, Descrição, TAG, Permissions, Minecraft Tag.
        int saveY = topY + 16 + (6 * 32);
        if (over(mouseX, mouseY, PANEL_X + 10, saveY + 4, PANEL_W - 20, 16)) {
            playClick();
            saveTagFromEditor();
            return true;
        }

        if (!editingIsGroupTag && editingId != null) {
            int delY = saveY + 24;
            if (over(mouseX, mouseY, PANEL_X + 10, delY, PANEL_W - 20, 16)) {
                playClick();
                ClientPlayNetworking.send(new DeleteTagPayload(editingId));
                devState = DevState.LIST;
                return true;
            }
        }

        for (TextFieldWidget field : editorFields) field.setFocused(false);
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (devState == DevState.EDITOR) return false;
        // Só consome o scroll (pra rolar a lista de tags) quando o mouse está de fato em cima do
        // painel — fora dele, deixa o evento passar pro Wardrobe3DScreen controlar o zoom da câmera.
        if (!over(mouseX, mouseY, PANEL_X, PANEL_Y, PANEL_W, PANEL_H)) return false;
        this.scrollY -= (float) (verticalAmount * 15f);
        if (this.scrollY < 0) this.scrollY = 0;
        if (this.scrollY > this.maxScrollY) this.scrollY = this.maxScrollY;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (devState == DevState.EDITOR) {
            for (TextFieldWidget field : editorFields) {
                if (field.isFocused() && field.keyPressed(keyCode, scanCode, modifiers)) return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (devState == DevState.EDITOR) {
            for (TextFieldWidget field : editorFields) {
                if (field.isFocused() && field.charTyped(chr, modifiers)) return true;
            }
        }
        return false;
    }

    private boolean over(double mx, double my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    private void playClick() {
        try {
            Identifier soundId = Identifier.of("cobblemon", "gui_click");
            SoundEvent soundEvent = SoundEvent.of(soundId);
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(soundEvent, 1.0F));
        } catch (Exception ignored) {}
    }

    private void playErrorSound() {
        try {
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_VILLAGER_NO, 1.0F));
        } catch (Exception ignored) {}
    }
}
