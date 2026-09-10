package com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev;

import com.f4xizzz.greatcosmetics.client.ClientTagsCache;
import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import com.f4xizzz.greatcosmetics.config.TagData;
import com.f4xizzz.greatcosmetics.network.DeleteTagPayload;
import com.f4xizzz.greatcosmetics.network.SaveTagPayload;
import com.f4xizzz.greatcosmetics.util.TextUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Página "Chat Tags" do Dev Studio — lista, edita, cria e apaga as tags de chat
 * (config/GreatCosmetics/tags/*.json). Mesma capacidade da aba Dev do /wardrobe → Tags
 * (TagsPage), só que dentro do Dev Studio. Reusa os payloads SaveTagPayload / DeleteTagPayload
 * e os receivers do servidor (GreatCosmetics#SaveTagPayload / #DeleteTagPayload). Não há cache
 * local mutável — toda escrita vai pela rede e volta pelo SyncTagsPayload.
 */
public class DevTagsSubPage extends DevSubPage {

    public static DevTagsSubPage INSTANCE;

    public boolean hasPendingChanges() {
        return this.hasUnsavedChanges;
    }

    public void attemptTabSwitch(Runnable onConfirm) {
        tryExit(onConfirm);
    }

    private enum State { LIST, EDITOR }
    private State currentState = State.LIST;

    private float scrollY = 0;
    private float maxScrollY = 0;
    private boolean isDraggingScrollbar = false;

    private int lastX = 30, lastY = 50, lastWidth = 180, lastHeight = 260;

    // null = criando uma tag nova. Cópias de trabalho (o ClientTagsCache não é mutável aqui).
    private String editingId = null;
    private boolean editingIsGroupTag = false;
    private String fId = "", fDisplay = "", fDesc = "", fTag = "", fPerms = "", fMcTag = "";

    private static String L(String key, Object... ph) {
        return com.f4xizzz.greatcosmetics.config.LangConfig.legacy(key, ph);
    }

    private enum RowType { STRING, BUTTON }
    private static class EditorRow {
        String label; String id; RowType type; TextFieldWidget textField; Runnable onButtonClick;
        String tooltip;
        EditorRow(String label, RowType type, TextFieldWidget field) { this.label = label; this.id = label; this.type = type; this.textField = field; }
        EditorRow(String label, String id, Runnable onButtonClick) { this.label = label; this.id = id; this.type = RowType.BUTTON; this.onButtonClick = onButtonClick; }
        EditorRow withTooltip(String tooltip) { this.tooltip = tooltip; return this; }
    }

    private final List<EditorRow> rows = new ArrayList<>();
    private EditorRow hoveredTooltipRow = null;

    public DevTagsSubPage(Wardrobe3DScreen parent, Runnable onBack) {
        super(parent, onBack);
        this.scrollY = 0;
    }

    private String stripFormatting(String s) {
        return s == null ? "" : s.replaceAll("<[^>]*>", "").replaceAll("§.", "");
    }

    /** Tags de grupo por weight DESC, depois as criadas na GUI em ordem alfabética. Igual TagsPage. */
    private List<TagData> sortedTags() {
        List<TagData> group = new ArrayList<>();
        List<TagData> custom = new ArrayList<>();
        for (TagData data : ClientTagsCache.allTags.values()) {
            if (data == null) continue;
            if (data.isGroupTag) group.add(data); else custom.add(data);
        }
        group.sort((a, b) -> Integer.compare(b.weight, a.weight));
        custom.sort(Comparator.comparing(a -> stripFormatting(a.displayName), String.CASE_INSENSITIVE_ORDER));
        List<TagData> result = new ArrayList<>(group);
        result.addAll(custom);
        return result;
    }

    private void loadEditor(String id) {
        this.editingId = id;
        this.currentState = State.EDITOR;
        this.scrollY = 0;
        this.rows.clear();
        this.hasUnsavedChanges = false;

        TagData data = id == null ? null : ClientTagsCache.allTags.get(id);
        this.editingIsGroupTag = data != null && data.isGroupTag;

        this.fId = id == null ? "" : id;
        this.fDisplay = data == null ? "" : TextUtils.parseToString(data.displayName);
        this.fDesc = data == null ? "" : TextUtils.parseToString(data.description);
        this.fTag = data == null ? "" : (data.tag != null ? data.tag : "");
        this.fPerms = (data == null || data.permissions == null) ? "" : String.join(", ", data.permissions);
        this.fMcTag = data == null ? "" : (data.minecraftTag != null ? data.minecraftTag : "");

        // ID: editável só ao criar; em edição o título já mostra o id.
        if (id == null) {
            addStringField(L("devstudio.tags.field.id"), this.fId, 64, text -> this.fId = text.trim().toLowerCase())
                    .withTooltip(L("devstudio.tags.tooltip.id"));
        }
        addStringField(L("devstudio.tags.field.display_name"), this.fDisplay, 256, text -> this.fDisplay = text)
                .withTooltip(L("devstudio.tags.tooltip.display_name"));
        addStringField(L("devstudio.tags.field.description"), this.fDesc, 256, text -> this.fDesc = text)
                .withTooltip(L("devstudio.tags.tooltip.description"));
        addStringField(L("devstudio.tags.field.prefix"), this.fTag, 256, text -> this.fTag = text)
                .withTooltip(L("devstudio.tags.tooltip.prefix"));
        addStringField(L("devstudio.tags.field.permissions"), this.fPerms, 512, text -> this.fPerms = text)
                .withTooltip(L("devstudio.tags.tooltip.permissions"));
        addStringField(L("devstudio.tags.field.minecraft_tag"), this.fMcTag, 128, text -> this.fMcTag = text)
                .withTooltip(L("devstudio.tags.tooltip.minecraft_tag"));

        if (this.editingIsGroupTag) {
            this.rows.add(new EditorRow(L("devstudio.tags.group_locked"), "note", () -> {}));
        } else if (id != null) {
            this.rows.add(new EditorRow(L("devstudio.tags.delete"), "delete", () -> {
                ClientPlayNetworking.send(new DeleteTagPayload(this.editingId));
                this.hasUnsavedChanges = false;
                this.currentState = State.LIST;
                this.scrollY = 0;
            }));
        }
    }

    private EditorRow addStringField(String label, String startVal, int maxLen, java.util.function.Consumer<String> action) {
        TextFieldWidget field = new TextFieldWidget(parent.getTextRenderer(), 0, 0, 140, 16, Text.literal(""));
        field.setMaxLength(maxLen); field.setText(startVal != null ? startVal : "");
        field.setChangedListener(text -> {
            hasUnsavedChanges = true;
            action.accept(text);
        });
        EditorRow row = new EditorRow(label, RowType.STRING, field);
        this.rows.add(row);
        return row;
    }

    private void sendSave() {
        String id = this.editingId != null ? this.editingId : this.fId.trim().toLowerCase();
        if (id.isEmpty()) { playError(); return; }

        List<String> permissions = new ArrayList<>();
        for (String part : this.fPerms.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) permissions.add(trimmed);
        }

        ClientPlayNetworking.send(new SaveTagPayload(id, this.fDisplay, this.fDesc, this.fTag, permissions, this.fMcTag));
        this.hasUnsavedChanges = false;
        this.currentState = State.LIST;
        this.scrollY = 0;
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta, int x, int y, int width, int height) {
        INSTANCE = this;
        this.lastX = x; this.lastY = y; this.lastWidth = width; this.lastHeight = height;
        this.hoveredTooltipRow = null;

        boolean isMouseDown = GLFW.glfwGetMouseButton(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (!isMouseDown) isDraggingScrollbar = false;

        int topY = y + 35;

        if (currentState == State.LIST) {
            boolean hovBack = mouseX >= x + 12 && mouseX <= x + 62 && mouseY >= topY && mouseY <= topY + 12;
            c.drawTextWithShadow(parent.getTextRenderer(), L("devstudio.common.back"), x + 12, topY + 2, hovBack ? 0xFF5555 : 0xAAAAAA);
            c.drawCenteredTextWithShadow(parent.getTextRenderer(), L("devstudio.tags.list_title"), x + (width / 2) + 8, topY + 2, 0xFFFFFF);

            boolean hovNew = mouseX >= x + 10 && mouseX <= x + width - 10 && mouseY >= topY + 15 && mouseY <= topY + 30;
            c.fill(x + 10, topY + 15, x + width - 10, topY + 30, hovNew ? 0xFF55FF55 : 0xFF22AA22);
            c.drawCenteredTextWithShadow(parent.getTextRenderer(), L("devstudio.tags.new"), x + (width / 2), topY + 19, 0xFFFFFF);

            List<TagData> tags = sortedTags();
            int listY = topY + 35;
            int itemHeight = 25;

            this.maxScrollY = Math.max(0, (tags.size() * itemHeight) - (height - (topY - y + 40)));
            parent.enablePerfectScissor(c, x, listY, width, height - (topY - y + 40));

            var world = MinecraftClient.getInstance().world;
            net.minecraft.registry.RegistryWrapper.WrapperLookup regs = world != null ? world.getRegistryManager() : null;

            for (int i = 0; i < tags.size(); i++) {
                int itemY = listY + (i * itemHeight) - (int) scrollY;
                if (itemY + itemHeight < listY || itemY > y + height) continue;

                TagData data = tags.get(i);
                boolean hovered = mouseX >= x + 10 && mouseX <= x + width - 15 && mouseY >= itemY && mouseY <= itemY + itemHeight - 2;
                c.fill(x + 10, itemY, x + width - 15, itemY + itemHeight - 2, hovered ? 0x88FFFFFF : 0x44000000);
                c.fill(x + 10, itemY, x + 12, itemY + itemHeight - 2, data.isGroupTag ? 0xFFFFAA00 : 0xFF55FFAA);

                Text label = data.tag != null && !data.tag.isBlank()
                        ? TextUtils.parseToText(data.tag, regs)
                        : TextUtils.parseToText(data.displayName, regs);
                if (label.getString().isBlank()) label = Text.literal(data.id);
                c.drawTextWithShadow(parent.getTextRenderer(), label, x + 18, itemY + 6, 0xFFFFFF);
            }
            RenderSystem.disableScissor();
            drawScrollbar(c, x + width - 6, listY, height - (topY - y + 40));

        } else if (currentState == State.EDITOR) {
            boolean hovBack = mouseX >= x + 10 && mouseX <= x + 30 && mouseY >= topY && mouseY <= topY + 12;
            c.drawTextWithShadow(parent.getTextRenderer(), "<", x + 10, topY + 2, hovBack ? 0xFF5555 : 0xAAAAAA);

            boolean hovSave = mouseX >= x + width - 25 && mouseX <= x + width - 10 && mouseY >= topY && mouseY <= topY + 12;
            c.drawTextWithShadow(parent.getTextRenderer(), "S", x + width - 20, topY + 2, hovSave ? 0x55FF55 : 0xAAAAAA);

            String title = editingId == null ? L("devstudio.tags.title_new") : "§b" + editingId;
            int titleW = parent.getTextRenderer().getWidth(title);
            int maxTitleW = width - 60;
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
                int rowY = listY + (i * rowHeight) - (int) scrollY;

                if (rowY < listY - rowHeight || rowY > y + height) {
                    if (row.textField != null) { row.textField.visible = false; row.textField.active = false; }
                    continue;
                }

                if (row.tooltip != null && mouseX >= x + 10 && mouseX <= x + width - 10 && mouseY >= rowY - 2 && mouseY <= rowY + 30) {
                    hoveredTooltipRow = row;
                }

                if (row.type != RowType.BUTTON) {
                    c.drawTextWithShadow(parent.getTextRenderer(), "§f" + row.label, x + 15, rowY, 0xFFFFFF);
                }

                if (row.type == RowType.STRING) {
                    row.textField.visible = true; row.textField.active = true;
                    row.textField.setX(x + 15); row.textField.setY(rowY + 12);
                    row.textField.setWidth(width - 35);
                    row.textField.render(c, mouseX, mouseY, delta);
                } else if (row.type == RowType.BUTTON) {
                    if (row.id.equals("note")) {
                        c.drawCenteredTextWithShadow(parent.getTextRenderer(), "§7" + row.label, x + (width / 2), rowY + 16, 0xAAAAAA);
                    } else {
                        boolean hovBtn = mouseX >= x + 15 && mouseX <= x + width - 20 && mouseY >= rowY + 12 && mouseY <= rowY + 28;
                        c.fill(x + 15, rowY + 12, x + width - 20, rowY + 28, devButtonFill(row.id, hovBtn));
                        c.drawCenteredTextWithShadow(parent.getTextRenderer(), row.label, x + (width / 2), rowY + 16, 0xFFFFFF);
                    }
                }
            }
            RenderSystem.disableScissor();
            drawScrollbar(c, x + width - 6, listY, height - (topY - y + 25));
        }

        renderExitPopup(c, mouseX, mouseY);
        renderHoveredTooltip(c, mouseX, mouseY);
    }

    private void renderHoveredTooltip(DrawContext c, int mouseX, int mouseY) {
        if (hoveredTooltipRow == null || hoveredTooltipRow.tooltip == null) return;
        List<Text> lines = new ArrayList<>();
        for (String line : hoveredTooltipRow.tooltip.split("\n")) lines.add(Text.literal(line));
        c.drawTooltip(parent.getTextRenderer(), lines, mouseX, mouseY);
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
        if (handlePopupClick(mx, my)) return true;
        if (button != 0) return false;

        int x = lastX, y = lastY, width = lastWidth, height = lastHeight;
        int topY = y + 35;
        int listY = currentState == State.LIST ? topY + 35 : topY + 20;
        int trackHeight = currentState == State.LIST ? height - (topY - y + 40) : height - (topY - y + 25);
        int sX = x + width - 6;

        if (maxScrollY > 0 && mx >= sX && mx <= sX + 4 && my >= listY && my <= listY + trackHeight) {
            isDraggingScrollbar = true;
            return true;
        }

        if (currentState == State.LIST) {
            if (mx >= x + 12 && mx <= x + 62 && my >= topY && my <= topY + 12) {
                playClick();
                tryExit(() -> { this.onBack.run(); this.scrollY = 0; });
                return true;
            }
            if (mx >= x + 10 && mx <= x + width - 10 && my >= topY + 15 && my <= topY + 30) {
                playClick();
                loadEditor(null);
                return true;
            }

            List<TagData> tags = sortedTags();
            int iterListY = topY + 35; int itemHeight = 25;
            for (int i = 0; i < tags.size(); i++) {
                int itemY = iterListY + (i * itemHeight) - (int) scrollY;
                if (itemY + itemHeight >= iterListY && itemY <= y + height) {
                    if (mx >= x + 10 && mx <= x + width - 15 && my >= itemY && my <= itemY + itemHeight - 2) {
                        playClick(); loadEditor(tags.get(i).id); return true;
                    }
                }
            }
        } else if (currentState == State.EDITOR) {
            if (mx >= x + 10 && mx <= x + 30 && my >= topY && my <= topY + 12) {
                playClick();
                tryExit(() -> { this.currentState = State.LIST; this.scrollY = 0; });
                return true;
            }
            if (mx >= x + width - 25 && mx <= x + width - 10 && my >= topY && my <= topY + 12) {
                playClick();
                sendSave();
                return true;
            }

            boolean clickedAny = false;
            int iterListY = topY + 20; int rowHeight = 35;
            for (EditorRow row : rows) {
                int rowY = iterListY + (rows.indexOf(row) * rowHeight) - (int) scrollY;
                if (rowY >= iterListY - rowHeight && rowY <= y + height) {
                    if (row.type == RowType.STRING) {
                        row.textField.setX(x + 15);
                        row.textField.setY(rowY + 12);
                        row.textField.setWidth(width - 35);
                        if (mx >= x + 15 && mx <= x + width - 20 && my >= rowY + 12 && my <= rowY + 28) {
                            row.textField.setFocused(true);
                            row.textField.mouseClicked(mx, my, button);
                            clickedAny = true;
                            for (EditorRow r : rows) if (r != row && r.textField != null) r.textField.setFocused(false);
                            return true;
                        }
                    } else if (row.type == RowType.BUTTON && !row.id.equals("note")) {
                        if (mx >= x + 15 && mx <= x + width - 20 && my >= rowY + 12 && my <= rowY + 28) {
                            playClick(); row.onButtonClick.run(); return true;
                        }
                    }
                }
            }
            if (!clickedAny) {
                for (EditorRow r : rows) if (r.textField != null) r.textField.setFocused(false);
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double deltaX, double deltaY) {
        if (isDraggingScrollbar && maxScrollY > 0) {
            int y = lastY; int topY = y + 35;
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
        if (currentState == State.EDITOR) {
            for (EditorRow row : rows) if (row.textField != null && row.textField.isFocused() && row.textField.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (currentState == State.EDITOR) {
            for (EditorRow row : rows) if (row.textField != null && row.textField.isFocused() && row.textField.charTyped(chr, modifiers)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (mx < lastX || mx > lastX + lastWidth || my < lastY || my > lastY + lastHeight) return false;
        this.scrollY -= (float) (vAmount * 15f);
        if (this.scrollY < 0) this.scrollY = 0;
        if (this.scrollY > this.maxScrollY) this.scrollY = this.maxScrollY;
        return true;
    }

    private void playError() {
        try { MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.ENTITY_VILLAGER_NO, 1.0F)); } catch (Exception ignored) {}
    }

    /** Save vai direto pela rede; não há estado local pra restaurar. */
    @Override
    public void saveChanges() {
        sendSave();
    }

    @Override
    public void discardChanges() {
        // Nada foi mandado pro servidor — só descartar as cópias de trabalho.
    }
}
