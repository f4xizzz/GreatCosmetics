package com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev;

import com.f4xizzz.greatcosmetics.client.ClientMainConfigCache;
import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import com.f4xizzz.greatcosmetics.config.MainConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class DevTypesSubPage extends DevSubPage {
    public static DevTypesSubPage INSTANCE;

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

    private String editingId = null;
    private String tempId = null;
    private MainConfig.AccessoryType editingData = null;
    private String backupJson = null;

    private boolean isDraggingScrollbar = false;

    // Guardados no render() (que recebe x/y/width/height DE VERDADE, 320x300 quando uma subpágina
    // Dev está aberta) — sem isso mouseClicked/mouseDragged/mouseScrolled usavam 180x260 fixo,
    // desalinhando clique/scroll do que era desenhado de verdade (mesmo bug já corrigido nas
    // outras subpáginas Dev).
    private int lastX = 30, lastY = 50, lastWidth = 180, lastHeight = 260;

    private enum RowType { STRING, INT, BUTTON }
    private static class EditorRow {
        String label; RowType type; TextFieldWidget textField; Runnable onButtonClick;
        EditorRow(String label, RowType type, TextFieldWidget field) { this.label = label; this.type = type; this.textField = field; }
        EditorRow(String label, Runnable onButtonClick) { this.label = label; this.type = RowType.BUTTON; this.onButtonClick = onButtonClick; }
    }

    private final List<EditorRow> rows = new ArrayList<>();

    public DevTypesSubPage(Wardrobe3DScreen parent, Runnable onBack) {
        super(parent, onBack);
        this.scrollY = 0;
    }

    private void loadEditor(String id) {
        this.editingId = id;
        this.tempId = id;
        this.editingData = ClientMainConfigCache.config.types.get(id);
        this.backupJson = ClientMainConfigCache.snapshotJson();
        this.currentState = State.EDITOR;
        this.scrollY = 0; // Reset do scroll ao entrar no editor
        this.rows.clear();

        if (this.editingData == null) return;

        addStringField("ID do Tipo (Nome único)", this.tempId, text -> this.tempId = text);
        addStringField("Slot Base (Ex: NECK)", this.editingData.slot, text -> this.editingData.slot = text);
        addIntField("Limite Por Jogador", this.editingData.limitPerPlayer, val -> this.editingData.limitPerPlayer = val);
        addStringField("Permissão", this.editingData.permission, text -> this.editingData.permission = text);

        this.rows.add(new EditorRow("DELETAR TIPO", () -> {
            ClientMainConfigCache.config.types.remove(this.editingId);
            ClientMainConfigCache.sendSave();
            this.hasUnsavedChanges = false;
            this.currentState = State.LIST;
            this.scrollY = 0; // Reset do scroll ao apagar
        }));
    }

    private void addStringField(String label, String startVal, java.util.function.Consumer<String> action) {
        TextFieldWidget field = new TextFieldWidget(parent.getTextRenderer(), 0, 0, 140, 16, Text.literal(""));
        field.setMaxLength(128); field.setText(startVal != null ? startVal : "");
        field.setChangedListener(text -> {
            hasUnsavedChanges = true;
            action.accept(text);
        });
        this.rows.add(new EditorRow(label, RowType.STRING, field));
    }

    private void addIntField(String label, int startVal, java.util.function.Consumer<Integer> action) {
        TextFieldWidget field = new TextFieldWidget(parent.getTextRenderer(), 0, 0, 140, 16, Text.literal(""));
        field.setMaxLength(10); field.setText(String.valueOf(startVal));
        field.setChangedListener(text -> {
            hasUnsavedChanges = true;
            try { if (!text.isEmpty() && !text.equals("-")) action.accept(Integer.parseInt(text)); } catch (Exception ignored) {}
        });
        this.rows.add(new EditorRow(label, RowType.INT, field));
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta, int x, int y, int width, int height) {
        INSTANCE = this;
        this.lastX = x; this.lastY = y; this.lastWidth = width; this.lastHeight = height;

        boolean isMouseDown = GLFW.glfwGetMouseButton(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (!isMouseDown) {
            isDraggingScrollbar = false;
        }

        int topY = y + 35; // Desce tudo para não bater no título principal

        if (currentState == State.LIST) {
            boolean hovBack = mouseX >= x + 12 && mouseX <= x + 62 && mouseY >= topY && mouseY <= topY + 12;
            c.drawTextWithShadow(parent.getTextRenderer(), "< Voltar", x + 12, topY + 2, hovBack ? 0xFF5555 : 0xAAAAAA);

            // --- NOME ENCURTADO E DESLOCADO PARA NÃO BATER NO VOLTAR ---
            c.drawCenteredTextWithShadow(parent.getTextRenderer(), "§bTypes", x + (width / 2) + 8, topY + 2, 0xFFFFFF);

            boolean hovNew = mouseX >= x + 10 && mouseX <= x + width - 10 && mouseY >= topY + 15 && mouseY <= topY + 30;
            c.fill(x + 10, topY + 15, x + width - 10, topY + 30, hovNew ? 0xFF5555FF : 0xFF2222AA);
            c.drawCenteredTextWithShadow(parent.getTextRenderer(), "+ Novo Type", x + (width/2), topY + 19, 0xFFFFFF);

            List<String> ids = new ArrayList<>(ClientMainConfigCache.config.types.keySet());
            int listY = topY + 35;
            int itemHeight = 25;

            this.maxScrollY = Math.max(0, (ids.size() * itemHeight) - (height - (topY - y + 40)));
            parent.enablePerfectScissor(c, x, listY, width, height - (topY - y + 40));

            for (int i = 0; i < ids.size(); i++) {
                int itemY = listY + (i * itemHeight) - (int)scrollY;
                if (itemY + itemHeight < listY || itemY > y + height) continue;

                boolean hovered = mouseX >= x + 10 && mouseX <= x + width - 15 && mouseY >= itemY && mouseY <= itemY + itemHeight - 2;
                c.fill(x + 10, itemY, x + width - 15, itemY + itemHeight - 2, hovered ? 0x88FFFFFF : 0x44000000);
                c.fill(x + 10, itemY, x + 12, itemY + itemHeight - 2, 0xFF55AAFF);

                // --- EFEITO MARQUEE PARA NOMES DE TIPOS GRANDES ---
                String typeId = ids.get(i);
                int nameW = parent.getTextRenderer().getWidth(typeId);
                int maxW = width - 40;
                int textY = itemY + 6;

                if (nameW > maxW) {
                    long time = Util.getMeasuringTimeMs();
                    int overflow = nameW - maxW;
                    double progress = (Math.sin(time / 700.0) + 1.0) / 2.0;
                    int offset = (int) (progress * overflow);

                    parent.enablePerfectScissor(c, x + 18, textY - 2, maxW, 12);
                    c.drawTextWithShadow(parent.getTextRenderer(), typeId, x + 18 - offset, textY, 0xFFFFFF);
                    RenderSystem.disableScissor();

                    parent.enablePerfectScissor(c, x, listY, width, height - (topY - y + 40));
                } else {
                    c.drawTextWithShadow(parent.getTextRenderer(), typeId, x + 18, textY, 0xFFFFFF);
                }
            }
            RenderSystem.disableScissor();
            drawScrollbar(c, x + width - 6, listY, height - (topY - y + 40));

        } else if (currentState == State.EDITOR) {
            boolean hovBack = mouseX >= x + 10 && mouseX <= x + 30 && mouseY >= topY && mouseY <= topY + 12;
            c.drawTextWithShadow(parent.getTextRenderer(), "<", x + 10, topY + 2, hovBack ? 0xFF5555 : 0xAAAAAA);

            boolean hovSave = mouseX >= x + width - 25 && mouseX <= x + width - 10 && mouseY >= topY && mouseY <= topY + 12;
            c.drawTextWithShadow(parent.getTextRenderer(), "S", x + width - 20, topY + 2, hovSave ? 0x55FF55 : 0xAAAAAA);

            // Animação de Letreiro
            String title = "§b" + editingId;
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
                int rowY = listY + (i * rowHeight) - (int)scrollY;

                if (rowY < listY - rowHeight || rowY > y + height) {
                    if (row.textField != null) { row.textField.visible = false; row.textField.active = false; }
                    continue;
                }

                // SÓ DESENHA A LABEL EXTERNA SE NÃO FOR UM BOTÃO!
                if (row.type != RowType.BUTTON) {
                    c.drawTextWithShadow(parent.getTextRenderer(), "§f" + row.label, x + 15, rowY, 0xFFFFFF);
                }

                if (row.type == RowType.STRING || row.type == RowType.INT) {
                    row.textField.visible = true; row.textField.active = true;
                    row.textField.setX(x + 15); row.textField.setY(rowY + 12);
                    row.textField.setWidth(width - 35);
                    row.textField.render(c, mouseX, mouseY, delta);
                }
                else if (row.type == RowType.BUTTON) {
                    boolean isDelete = row.label.equals("DELETAR TIPO");
                    boolean hovBtn = mouseX >= x + 15 && mouseX <= x + width - 20 && mouseY >= rowY + 12 && mouseY <= rowY + 28;

                    c.fill(x + 15, rowY + 12, x + width - 20, rowY + 28, hovBtn ? (isDelete ? 0x66FF0000 : 0x66FFAA00) : (isDelete ? 0x44AA0000 : 0x44FFAA00));
                    // DESENHA O NOME DA AÇÃO DENTRO DO BOTÃO!
                    c.drawCenteredTextWithShadow(parent.getTextRenderer(), row.label, x + (width/2), rowY + 16, 0xFFFFFF);
                }
            }
            RenderSystem.disableScissor();
            drawScrollbar(c, x + width - 6, listY, height - (topY - y + 25));
        }

        renderExitPopup(c, mouseX, mouseY);
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

        int x = lastX; int y = lastY; int width = lastWidth; int height = lastHeight;
        int topY = y + 35;

        // --- LÓGICA DE CLIQUE NA BARRA DE SCROLL ---
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
                tryExit(() -> {
                    this.onBack.run();
                    this.scrollY = 0; // Reset ao voltar pro menu
                });
                return true;
            }

            if (mx >= x + 10 && mx <= x + width - 10 && my >= topY + 15 && my <= topY + 30) {
                playClick();
                String newId = "novo_type_" + (ClientMainConfigCache.config.types.size() + 1);
                MainConfig.AccessoryType newType = new MainConfig.AccessoryType();
                newType.slot = "NECK";
                newType.limitPerPlayer = 1;
                newType.permission = "greatcosmetics.type." + newId + ".limit";

                ClientMainConfigCache.config.types.put(newId, newType);
                ClientMainConfigCache.sendSave();
                loadEditor(newId);
                return true;
            }

            List<String> ids = new ArrayList<>(ClientMainConfigCache.config.types.keySet());
            int iterListY = topY + 35; int itemHeight = 25;

            for (int i = 0; i < ids.size(); i++) {
                int itemY = iterListY + (i * itemHeight) - (int)scrollY;
                if (itemY + itemHeight >= iterListY && itemY <= y + height) {
                    if (mx >= x + 10 && mx <= x + width - 15 && my >= itemY && my <= itemY + itemHeight - 2) {
                        playClick(); loadEditor(ids.get(i)); return true;
                    }
                }
            }
        }
        else if (currentState == State.EDITOR) {
            if (mx >= x + 10 && mx <= x + 30 && my >= topY && my <= topY + 12) {
                playClick();
                tryExit(() -> {
                    this.currentState = State.LIST;
                    this.scrollY = 0; // Reset ao voltar pra lista
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
            int iterListY = topY + 20; int rowHeight = 35;
            for (EditorRow row : rows) {
                int rowY = iterListY + (rows.indexOf(row) * rowHeight) - (int)scrollY;
                if (rowY >= iterListY - rowHeight && rowY <= y + height) {
                    if (row.type == RowType.STRING || row.type == RowType.INT) {

                        // Foco blindado
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
                    } else if (row.type == RowType.BUTTON) {
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

    public void playClick() {
        try { MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F)); } catch (Exception ignored) {}
    }

    @Override
    public void saveChanges() {
        if (this.tempId != null && !this.tempId.trim().isEmpty() && !this.tempId.equals(this.editingId)) {
            ClientMainConfigCache.config.types.remove(this.editingId);
            ClientMainConfigCache.config.types.put(this.tempId, this.editingData);
            this.editingId = this.tempId;
        }
        ClientMainConfigCache.sendSave();
        this.backupJson = ClientMainConfigCache.snapshotJson();
    }

    @Override
    public void discardChanges() {
        ClientMainConfigCache.restoreFromJson(this.backupJson);
    }
}