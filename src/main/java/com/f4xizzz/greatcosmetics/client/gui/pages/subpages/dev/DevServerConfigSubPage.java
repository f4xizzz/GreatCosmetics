package com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev;

import com.f4xizzz.greatcosmetics.client.ClientMainConfigCache;
import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import com.f4xizzz.greatcosmetics.config.MainConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class DevServerConfigSubPage extends DevSubPage {

    public static DevServerConfigSubPage INSTANCE; // <--- ADICIONE ESTA LINHA

    public boolean hasPendingChanges() {
        return this.hasUnsavedChanges;
    }

    public void attemptTabSwitch(Runnable onConfirm) {
        tryExit(onConfirm);
    }

    private float scrollY = 0;
    private float maxScrollY = 0;
    private boolean isDraggingScrollbar = false;

    // Guardados no render() (que recebe x/y/width/height DE VERDADE, 320x300 quando uma subpágina
    // Dev está aberta) — sem isso mouseClicked/mouseDragged/mouseScrolled usavam 180x260 fixo,
    // desalinhando clique/scroll do que era desenhado de verdade (mesmo bug já corrigido em
    // DevEffectsSubPage/DevCosmeticsSubPage/DevSlotsSubPage).
    private int lastX = 30, lastY = 50, lastWidth = 180, lastHeight = 260;

    private static String L(String key, Object... ph) {
        return com.f4xizzz.greatcosmetics.config.LangConfig.legacy(key, ph);
    }

    private enum RowType { STRING, INT, TOGGLE, BUTTON }
    private static class EditorRow {
        String label; String id; RowType type; TextFieldWidget textField;
        boolean toggleValue; java.util.function.Consumer<Boolean> onToggle;
        Runnable onButtonClick;
        // Ver DevCosmeticsSubPage#EditorRow.tooltip — mesmo mecanismo de tooltip por hover.
        String tooltip;

        EditorRow(String label, TextFieldWidget field) { this.label = label; this.id = label; this.type = RowType.STRING; this.textField = field; }
        EditorRow(String label, boolean startVal, java.util.function.Consumer<Boolean> onToggle) { this.label = label; this.id = label; this.type = RowType.TOGGLE; this.toggleValue = startVal; this.onToggle = onToggle; }
        EditorRow(String label, Runnable onButtonClick) { this.label = label; this.id = label; this.type = RowType.BUTTON; this.onButtonClick = onButtonClick; }
        EditorRow withId(String id) { this.id = id; return this; }
        EditorRow withTooltip(String tooltip) { this.tooltip = tooltip; return this; }
    }

    private final List<EditorRow> rows = new ArrayList<>();
    private String backupJson = null;

    /** Ver DevCosmeticsSubPage#hoveredTooltipRow. */
    private EditorRow hoveredTooltipRow = null;

    public DevServerConfigSubPage(Wardrobe3DScreen parent, Runnable onBack) {
        super(parent, onBack);
        loadEditor();
    }

    private void loadEditor() {
        this.rows.clear();
        this.scrollY = 0;
        this.backupJson = ClientMainConfigCache.snapshotJson();

        addToggleField(L("devstudio.serverconfig.field.auto_detect_models"), ClientMainConfigCache.config.autoDetectModels, val -> ClientMainConfigCache.config.autoDetectModels = val)
                .withTooltip(L("devstudio.serverconfig.tooltip.auto_detect_models"));
        addToggleField(L("devstudio.serverconfig.field.use_mysql"), ClientMainConfigCache.config.useMySQL, val -> ClientMainConfigCache.config.useMySQL = val)
                .withTooltip(L("devstudio.serverconfig.tooltip.use_mysql"));
        addStringField(L("devstudio.serverconfig.field.mysql_host"), ClientMainConfigCache.config.mysqlHost, val -> ClientMainConfigCache.config.mysqlHost = val)
                .withTooltip(L("devstudio.serverconfig.tooltip.mysql_host"));
        addIntField(L("devstudio.serverconfig.field.mysql_port"), ClientMainConfigCache.config.mysqlPort, val -> ClientMainConfigCache.config.mysqlPort = val)
                .withTooltip(L("devstudio.serverconfig.tooltip.mysql_port"));
        addStringField(L("devstudio.serverconfig.field.mysql_database"), ClientMainConfigCache.config.mysqlDatabase, val -> ClientMainConfigCache.config.mysqlDatabase = val)
                .withTooltip(L("devstudio.serverconfig.tooltip.mysql_database"));
        addStringField(L("devstudio.serverconfig.field.mysql_user"), ClientMainConfigCache.config.mysqlUser, val -> ClientMainConfigCache.config.mysqlUser = val)
                .withTooltip(L("devstudio.serverconfig.tooltip.mysql_user"));
        addStringField(L("devstudio.serverconfig.field.mysql_password"), ClientMainConfigCache.config.mysqlPassword, val -> ClientMainConfigCache.config.mysqlPassword = val)
                .withTooltip(L("devstudio.serverconfig.tooltip.mysql_password"));

        // --- RESOURCE PACK FORÇADO (ver GreatCosmetics#sendForcedResourcePack) ---
        addToggleField(L("devstudio.serverconfig.field.force_resource_pack"), ClientMainConfigCache.config.forceTexture, val -> ClientMainConfigCache.config.forceTexture = val)
                .withTooltip(L("devstudio.serverconfig.tooltip.force_resource_pack"));
        addStringField(L("devstudio.serverconfig.field.texture_id"), ClientMainConfigCache.config.textureId, val -> ClientMainConfigCache.config.textureId = val)
                .withTooltip(L("devstudio.serverconfig.tooltip.texture_id"));
        addStringField(L("devstudio.serverconfig.field.texture_url"), ClientMainConfigCache.config.textureUrl, val -> ClientMainConfigCache.config.textureUrl = val)
                .withTooltip(L("devstudio.serverconfig.tooltip.texture_url"));
        addStringField(L("devstudio.serverconfig.field.texture_sha1"), ClientMainConfigCache.config.textureSha1, val -> ClientMainConfigCache.config.textureSha1 = val)
                .withTooltip(L("devstudio.serverconfig.tooltip.texture_sha1"));

        // --- COMANDOS RODADOS NO BOOT (MainConfig#startupCommands) ---
        // Uma STRING field por comando já salvo + um botão "Remover" logo abaixo de cada um, e um
        // botão "+ Adicionar Comando" no final. Precisa de loadEditor() de novo a cada add/remove
        // (não dá pra só inserir uma EditorRow no meio) porque o índice de cada Consumer é capturado
        // na hora que a lista é montada — reconstruir do zero garante que os índices dos closures
        // batem com o estado ATUAL da lista.
        java.util.List<String> commands = ClientMainConfigCache.config.startupCommands;
        for (int i = 0; i < commands.size(); i++) {
            int idx = i;
            addStringField(L("devstudio.serverconfig.field.boot_command", "n", (i + 1)), commands.get(i),
                    val -> ClientMainConfigCache.config.startupCommands.set(idx, val))
                    .withTooltip(L("devstudio.serverconfig.tooltip.boot_command"));
            this.rows.add(new EditorRow(L("devstudio.serverconfig.btn.remove_command", "n", (i + 1)), () -> {
                ClientMainConfigCache.config.startupCommands.remove(idx);
                hasUnsavedChanges = true;
                loadEditor();
            }).withId("btn_remove_command"));
        }
        this.rows.add(new EditorRow(L("devstudio.serverconfig.btn.add_command"), () -> {
            ClientMainConfigCache.config.startupCommands.add("");
            hasUnsavedChanges = true;
            loadEditor();
        }).withTooltip(L("devstudio.serverconfig.tooltip.add_command")));
    }

    private EditorRow addStringField(String label, String startVal, java.util.function.Consumer<String> action) {
        TextFieldWidget field = new TextFieldWidget(parent.getTextRenderer(), 0, 0, 140, 16, Text.literal(""));
        field.setMaxLength(128); field.setText(startVal != null ? startVal : "");
        field.setChangedListener(text -> {
            hasUnsavedChanges = true;
            action.accept(text);
        });
        EditorRow row = new EditorRow(label, field);
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
        EditorRow row = new EditorRow(label, field);
        this.rows.add(row);
        return row;
    }

    private EditorRow addToggleField(String label, boolean startVal, java.util.function.Consumer<Boolean> action) {
        EditorRow row = new EditorRow(label, startVal, action);
        this.rows.add(row);
        return row;
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta, int x, int y, int width, int height) {
        INSTANCE = this; // <--- ADICIONE ESTA LINHA BEM AQUI
        this.lastX = x; this.lastY = y; this.lastWidth = width; this.lastHeight = height;
        this.hoveredTooltipRow = null;

        // Checa se o mouse foi solto para parar o arrasto da barra
        boolean isMouseDown = GLFW.glfwGetMouseButton(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (!isMouseDown) {
            isDraggingScrollbar = false;
        }

        int topY = y + 35; // Espaço de segurança para não sobrepor o cabeçalho Dev Studio

        boolean hovBack = mouseX >= x + 10 && mouseX <= x + 30 && mouseY >= topY && mouseY <= topY + 12;
        c.drawTextWithShadow(parent.getTextRenderer(), "<", x + 10, topY + 2, hovBack ? 0xFF5555 : 0xAAAAAA);

        boolean hovSave = mouseX >= x + width - 25 && mouseX <= x + width - 10 && mouseY >= topY && mouseY <= topY + 12;
        c.drawTextWithShadow(parent.getTextRenderer(), "S", x + width - 20, topY + 2, hovSave ? 0x55FF55 : 0xAAAAAA);

        c.drawCenteredTextWithShadow(parent.getTextRenderer(), L("devstudio.serverconfig.title"), x + (width / 2), topY + 2, 0xFFFFFF);

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

            // SÓ DESENHA A LABEL EXTERNA SE NÃO FOR UM BOTÃO!
            if (row.type != RowType.BUTTON) {
                c.drawTextWithShadow(parent.getTextRenderer(), "§f" + row.label, x + 15, rowY, 0xFFFFFF);
            }

            if (row.type == RowType.STRING || row.type == RowType.INT) {
                row.textField.visible = true; row.textField.active = true;
                row.textField.setX(x + 15);
                row.textField.setY(rowY + 12);
                row.textField.setWidth(width - 35);
                row.textField.render(c, mouseX, mouseY, delta);
            }
            else if (row.type == RowType.TOGGLE) {
                boolean hovTog = mouseX >= x + 15 && mouseX <= x + width - 20 && mouseY >= rowY + 12 && mouseY <= rowY + 28;
                c.fill(x + 15, rowY + 12, x + width - 20, rowY + 28, hovTog ? 0x66FFFFFF : 0x44000000);
                c.drawBorder(x + 15, rowY + 12, width - 35, 16, row.toggleValue ? 0xFF00FF00 : 0xFFFF0000);
                c.drawCenteredTextWithShadow(parent.getTextRenderer(), L(row.toggleValue ? "devstudio.common.on" : "devstudio.common.off"), x + (width/2), rowY + 16, 0xFFFFFF);
            }
            else if (row.type == RowType.BUTTON) {
                boolean isRemove = row.id.equals("btn_remove_command");
                boolean hovBtn = mouseX >= x + 15 && mouseX <= x + width - 20 && mouseY >= rowY + 12 && mouseY <= rowY + 28;
                c.fill(x + 15, rowY + 12, x + width - 20, rowY + 28, hovBtn ? (isRemove ? 0x66FF0000 : 0x66FFAA00) : (isRemove ? 0x44AA0000 : 0x44FFAA00));
                c.drawCenteredTextWithShadow(parent.getTextRenderer(), row.label, x + (width/2), rowY + 16, 0xFFFFFF);
            }
        }
        RenderSystem.disableScissor();

        // Renderiza a Scrollbar
        if (maxScrollY > 0) {
            int sX = x + width - 6;
            c.fill(sX, listY, sX + 4, listY + height - (topY - y + 25), 0x44000000);
            int h = Math.max(10, (int) ((height - (topY - y + 25)) * ((float) (height - (topY - y + 25)) / (height - (topY - y + 25) + maxScrollY))));
            int handleY = listY + (int) (((height - (topY - y + 25)) - h) * (scrollY / maxScrollY));
            c.fill(sX, handleY, sX + 4, handleY + h, 0xFFAAAAAA);
        }

        // --- POPUP UNIVERSAL ---
        renderExitPopup(c, mouseX, mouseY);

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

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (handlePopupClick(mx, my)) return true;
        if (button != 0) return false;

        int x = lastX; int y = lastY; int width = lastWidth; int height = lastHeight;
        int topY = y + 35;

        // --- LÓGICA DE CLIQUE NA BARRA DE SCROLL ---
        int listY = topY + 20;
        int trackHeight = height - (topY - y + 25);
        int sX = x + width - 6;

        if (maxScrollY > 0 && mx >= sX && mx <= sX + 4 && my >= listY && my <= listY + trackHeight) {
            isDraggingScrollbar = true;
            return true;
        }

        if (mx >= x + 10 && mx <= x + 30 && my >= topY && my <= topY + 12) {
            playClick();
            tryExit(() -> {
                this.onBack.run();
                this.scrollY = 0; // Reset ao voltar pro menu principal
            });
            return true;
        }

        if (mx >= x + width - 25 && mx <= x + width - 10 && my >= topY && my <= topY + 12) {
            playClick();
            saveChanges();
            hasUnsavedChanges = false;
            return true;
        }

        boolean clickedAny = false;
        int rowHeight = 35;
        for (EditorRow row : rows) {
            int rowY = listY + (rows.indexOf(row) * rowHeight) - (int)scrollY;
            if (rowY >= listY - rowHeight && rowY <= y + height) {
                if (row.type == RowType.STRING || row.type == RowType.INT) {

                    // TRAVA BLINDADA DE FOCO
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
                } else if (row.type == RowType.TOGGLE) {
                    if (mx >= x + 15 && mx <= x + width - 20 && my >= rowY + 12 && my <= rowY + 28) {
                        playClick();
                        hasUnsavedChanges = true;
                        row.toggleValue = !row.toggleValue;
                        row.onToggle.accept(row.toggleValue);
                        return true;
                    }
                } else if (row.type == RowType.BUTTON) {
                    if (mx >= x + 15 && mx <= x + width - 20 && my >= rowY + 12 && my <= rowY + 28) {
                        playClick();
                        row.onButtonClick.run();
                        return true;
                    }
                }
            }
        }

        // Se clicou fora de qualquer caixa, tira o foco de todas
        if (!clickedAny) {
            for (EditorRow r : rows) if (r.textField != null) r.textField.setFocused(false);
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double deltaX, double deltaY) {
        if (isDraggingScrollbar && maxScrollY > 0) {
            int y = lastY;
            int topY = y + 35;
            int listY = topY + 20;
            int trackHeight = lastHeight - (topY - y + 25);

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
        for (EditorRow row : rows) if (row.textField != null && row.textField.isFocused() && row.textField.keyPressed(keyCode, scanCode, modifiers)) return true;
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        for (EditorRow row : rows) if (row.textField != null && row.textField.isFocused() && row.textField.charTyped(chr, modifiers)) return true;
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

    public void playClick() {
        try { MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F)); } catch (Exception ignored) {}
    }

    @Override
    public void saveChanges() {
        ClientMainConfigCache.sendSave();
        this.backupJson = ClientMainConfigCache.snapshotJson();
    }

    @Override
    public void discardChanges() {
        ClientMainConfigCache.restoreFromJson(this.backupJson);
    }
}