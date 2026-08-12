package com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev;

import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
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

    private enum State { LIST, EDITOR }
    private State currentState = State.LIST;

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

    // --- DROPDOWN DE PARTÍCULAS (Particle ID) ---
    private static List<String> ALL_PARTICLE_IDS = null;
    private EditorRow particleIdRow = null;
    private boolean particleDropdownOpen = false;
    private final List<String> filteredParticleIds = new ArrayList<>();
    private float particleDropdownScrollY = 0f;
    private float particleDropdownMaxScrollY = 0f;

    private boolean isDraggingScrollbar = false;
    private boolean isDraggingGizmo = false; // Novo
    private double lastMouseX = 0, lastMouseY = 0; // Novo
    private long lastTick = 0;

    private enum RowType { STRING, INT, DOUBLE, BUTTON, DIVIDER, GIZMO_INFO } // Novos tipos
    private static class EditorRow {
        String label; RowType type; TextFieldWidget textField; Runnable onButtonClick;
        EditorRow(String label, RowType type, TextFieldWidget field) { this.label = label; this.type = type; this.textField = field; }
        EditorRow(String label, Runnable onButtonClick) { this.label = label; this.type = RowType.BUTTON; this.onButtonClick = onButtonClick; }
    }

    private final List<EditorRow> rows = new ArrayList<>();

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

    /** Refiltra a lista do dropdown com base no texto atual do campo (substring, case-insensitive). */
    private void filterParticles(String query) {
        String q = query == null ? "" : query.toLowerCase().trim();
        filteredParticleIds.clear();
        for (String id : getAllParticleIds()) {
            if (q.isEmpty() || id.contains(q)) filteredParticleIds.add(id);
        }
        particleDropdownScrollY = 0f;
    }

    private void syncGizmoToFields() {
        if (this.editingData == null) return;
        for (EditorRow r : rows) {
            if (r.textField == null) continue;
            try {
                if (r.label.startsWith("Offset X")) r.textField.setText(String.valueOf(Math.round(this.editingData.offsetX * 1000.0) / 1000.0));
                if (r.label.startsWith("Offset Y")) r.textField.setText(String.valueOf(Math.round(this.editingData.offsetY * 1000.0) / 1000.0));
                if (r.label.startsWith("Offset Z")) r.textField.setText(String.valueOf(Math.round(this.editingData.offsetZ * 1000.0) / 1000.0));
            } catch (Exception ignored) {}
        }
    }

    private void loadEditor(String id) {
        this.editingId = id;
        this.tempId = id; // Inicia com o mesmo nome
        this.editingData = EffectConfig.effectsMap.get(id);
        this.currentState = State.EDITOR;
        this.scrollY = 0; // Reseta o scroll ao entrar no editor
        this.rows.clear();

        if (this.editingData == null) return;

        addDivider("=== IDENTIFICAÇÃO ===");
        addStringField("ID do Efeito (Nome no Arquivo)", this.tempId, text -> this.tempId = text);
        addStringField("Particle ID (Ex: minecraft:flame)", this.editingData.particleId, text -> {
            this.editingData.particleId = text;
            filterParticles(text);
        });
        this.particleIdRow = this.rows.get(this.rows.size() - 1);
        this.particleDropdownOpen = false;
        filterParticles(this.editingData.particleId);

        addDivider("=== CONFIGURAÇÃO ===");
        addIntField("Quantidade (Count)", this.editingData.count, val -> this.editingData.count = val);
        addIntField("Intervalo (Ticks)", this.editingData.tickInterval, val -> this.editingData.tickInterval = val);

        addDivider("=== FERRAMENTA 3D ===");
        this.rows.add(new EditorRow("Segure X, Y ou Z e arraste o mouse!", RowType.GIZMO_INFO, null));

        addDivider("=== OFFSETS (POSIÇÃO) ===");
        addDoubleField("Offset X (Lados)", this.editingData.offsetX, val -> this.editingData.offsetX = val);
        addDoubleField("Offset Y (Altura)", this.editingData.offsetY, val -> this.editingData.offsetY = val);
        addDoubleField("Offset Z (Frente/Trás)", this.editingData.offsetZ, val -> this.editingData.offsetZ = val);

        addDivider("=== ESPALHAMENTO ===");
        addDoubleField("Spread X (Espalhamento X)", this.editingData.spreadX, val -> this.editingData.spreadX = val);
        addDoubleField("Spread Y (Espalhamento Y)", this.editingData.spreadY, val -> this.editingData.spreadY = val);
        addDoubleField("Spread Z (Espalhamento Z)", this.editingData.spreadZ, val -> this.editingData.spreadZ = val);
        addDoubleField("Velocidade (Speed)", this.editingData.speed, val -> this.editingData.speed = val);

        addDivider("=== PERIGO ===");
        this.rows.add(new EditorRow("DELETAR EFEITO", () -> {
            EffectConfig.effectsMap.remove(this.editingId);
            EffectConfig.saveEffects();
            sendDeleteEffect(this.editingId);
            this.hasUnsavedChanges = false;
            this.currentState = State.LIST;
            this.scrollY = 0;
        }));

        this.hasUnsavedChanges = false;
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

    private void addDoubleField(String label, double startVal, java.util.function.Consumer<Double> action) {
        TextFieldWidget field = new TextFieldWidget(parent.getTextRenderer(), 0, 0, 140, 16, Text.literal(""));
        field.setMaxLength(20); field.setText(String.valueOf(startVal));
        field.setChangedListener(text -> {
            hasUnsavedChanges = true;
            try { if (!text.isEmpty() && !text.equals("-") && !text.equals(".")) action.accept(Double.parseDouble(text)); } catch (Exception ignored) {}
        });
        this.rows.add(new EditorRow(label, RowType.DOUBLE, field));
    }

    // ==========================================
    // SPAWNER DE PARTÍCULAS EM TEMPO REAL
    // ==========================================
    private void spawnPreviewParticles() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || this.editingData == null) return;

        net.minecraft.util.Identifier pId = net.minecraft.util.Identifier.tryParse(this.editingData.particleId);
        if (pId == null) return;

        net.minecraft.particle.ParticleType<?> type = net.minecraft.registry.Registries.PARTICLE_TYPE.get(pId);
        if (type instanceof net.minecraft.particle.ParticleEffect effect) {
            // offsetX/offsetZ são "Lados"/"Frente-Trás" — relativos ao corpo do jogador, então
            // precisam rotacionar com bodyYaw igual o spawn de verdade (GreatCosmetics#
            // spawnCosmeticParticle) faz. Sem isso, o preview mostrava a partícula num lugar fixo
            // do mundo (não relativo ao corpo), então só "parecia certo" enquanto o player ficava
            // olhando pra mesma direção de quando foi ajustado no gizmo.
            // Mesma convenção corrigida de GreatCosmetics#spawnCosmeticParticle (forward=(-sin,cos),
            // right=(-cos,-sin) pra yaw do Minecraft) — tinha o sinal do offsetX (lados) invertido.
            double yawRad = Math.toRadians(client.player.bodyYaw);
            double baseX = client.player.getX() - (this.editingData.offsetX * Math.cos(yawRad)) - (this.editingData.offsetZ * Math.sin(yawRad));
            double baseZ = client.player.getZ() - (this.editingData.offsetX * Math.sin(yawRad)) + (this.editingData.offsetZ * Math.cos(yawRad));

            for (int i = 0; i < this.editingData.count; i++) {
                double px = baseX + (client.world.random.nextDouble() - 0.5) * this.editingData.spreadX * 2;
                double py = client.player.getY() + this.editingData.offsetY + (client.world.random.nextDouble() - 0.5) * this.editingData.spreadY * 2;
                double pz = baseZ + (client.world.random.nextDouble() - 0.5) * this.editingData.spreadZ * 2;

                double vx = (client.world.random.nextDouble() - 0.5) * this.editingData.speed;
                double vy = (client.world.random.nextDouble() - 0.5) * this.editingData.speed;
                double vz = (client.world.random.nextDouble() - 0.5) * this.editingData.speed;

                client.world.addParticle(effect, px, py, pz, vx, vy, vz);
            }
        }
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta, int x, int y, int width, int height) {
        INSTANCE = this; // <--- ADICIONE ESTA LINHA
        this.lastX = x; this.lastY = y; this.lastWidth = width; this.lastHeight = height;

        boolean isMouseDown = GLFW.glfwGetMouseButton(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (!isMouseDown) {
            isDraggingScrollbar = false;
            isDraggingGizmo = false;
        }

        // --- LOOP PARA MOSTRAR A PARTÍCULA NO BONECO ---
        if (currentState == State.EDITOR && this.editingData != null) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world != null) {
                long currentTick = client.world.getTime();
                if (currentTick != lastTick) {
                    lastTick = currentTick;
                    if (currentTick % Math.max(1, this.editingData.tickInterval) == 0) {
                        spawnPreviewParticles();
                    }
                }
            }
        }

        int topY = y + 35; // Espaço de segurança para não sobrepor o cabeçalho Dev Studio

        if (currentState == State.LIST) {
            boolean hovBack = mouseX >= x + 12 && mouseX <= x + 62 && mouseY >= topY && mouseY <= topY + 12;
            c.drawTextWithShadow(parent.getTextRenderer(), "< Voltar", x + 12, topY + 2, hovBack ? 0xFF5555 : 0xAAAAAA);
            c.drawCenteredTextWithShadow(parent.getTextRenderer(), "§dEfeitos", x + (width / 2), topY + 2, 0xFFFFFF);

            // Botão Novo Efeito
            boolean hovNew = mouseX >= x + 10 && mouseX <= x + width - 10 && mouseY >= topY + 15 && mouseY <= topY + 30;
            c.fill(x + 10, topY + 15, x + width - 10, topY + 30, hovNew ? 0xFF55FF55 : 0xFF22AA22);
            c.drawCenteredTextWithShadow(parent.getTextRenderer(), "+ Novo Efeito", x + (width/2), topY + 19, 0xFFFFFF);

            List<String> ids = new ArrayList<>(EffectConfig.effectsMap.keySet());
            int listY = topY + 35;
            int itemHeight = 25;

            this.maxScrollY = Math.max(0, (ids.size() * itemHeight) - (height - (topY - y + 40)));
            parent.enablePerfectScissor(c, x, listY, width, height - (topY - y + 40));

            for (int i = 0; i < ids.size(); i++) {
                int itemY = listY + (i * itemHeight) - (int)scrollY;
                if (itemY + itemHeight < listY || itemY > y + height) continue;

                boolean hovered = mouseX >= x + 10 && mouseX <= x + width - 15 && mouseY >= itemY && mouseY <= itemY + itemHeight - 2;
                c.fill(x + 10, itemY, x + width - 15, itemY + itemHeight - 2, hovered ? 0x88FFFFFF : 0x44000000);
                c.fill(x + 10, itemY, x + 12, itemY + itemHeight - 2, 0xFFFF55FF); // Bordinha rosa

                c.drawTextWithShadow(parent.getTextRenderer(), ids.get(i), x + 18, itemY + 6, 0xFFFFFF);
            }
            RenderSystem.disableScissor();
            drawScrollbar(c, x + width - 6, listY, height - (topY - y + 40));

        } else if (currentState == State.EDITOR) {
            boolean hovBack = mouseX >= x + 10 && mouseX <= x + 30 && mouseY >= topY && mouseY <= topY + 12;
            c.drawTextWithShadow(parent.getTextRenderer(), "<", x + 10, topY + 2, hovBack ? 0xFF5555 : 0xAAAAAA);

            boolean hovSave = mouseX >= x + width - 25 && mouseX <= x + width - 10 && mouseY >= topY && mouseY <= topY + 12;
            c.drawTextWithShadow(parent.getTextRenderer(), "S", x + width - 20, topY + 2, hovSave ? 0x55FF55 : 0xAAAAAA);

            // --- EFEITO MARQUEE NO TÍTULO ---
            String title = "§e" + editingId;
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
                }
                else if (row.type == RowType.BUTTON) {
                    boolean isDelete = row.label.equals("DELETAR EFEITO");
                    boolean hovBtn = mouseX >= x + 15 && mouseX <= x + width - 20 && mouseY >= rowY + 12 && mouseY <= rowY + 28;

                    c.fill(x + 15, rowY + 12, x + width - 20, rowY + 28, hovBtn ? (isDelete ? 0x66FF0000 : 0x66FFAA00) : (isDelete ? 0x44AA0000 : 0x44FFAA00));
                    c.drawCenteredTextWithShadow(parent.getTextRenderer(), row.label, x + (width/2), rowY + 16, 0xFFFFFF);
                }
            }
            RenderSystem.disableScissor();
            drawScrollbar(c, x + width - 6, listY, height - (topY - y + 25));

            if (particleDropdownOpen && particleIdRow != null) {
                int idx = this.rows.indexOf(particleIdRow);
                int fieldRowY = listY + (idx * rowHeight) - (int) scrollY;
                if (fieldRowY >= listY - rowHeight && fieldRowY <= y + height) {
                    renderParticleDropdown(c, x, fieldRowY, width, mouseX, mouseY);
                }
            }
        }

        // --- RENDERIZANDO O POPUP UNIVERSAL DE SAÍDA POR CIMA DE TUDO ---
        renderExitPopup(c, mouseX, mouseY);
    }

    /** Lista suspensa com todas as partículas registradas, filtrada pelo texto do campo. Desenhada
     *  com z alto (igual ao popup/dropdown de categoria em AcessoriesPage) pra ficar por CIMA das
     *  linhas seguintes do editor, independente da ordem de desenho. */
    private void renderParticleDropdown(DrawContext c, int panelX, int fieldRowY, int panelWidth, int mouseX, int mouseY) {
        int ddX = panelX + 15;
        int ddY = fieldRowY + 28;
        int ddW = panelWidth - 35;
        int rowH = 12;
        int maxVisible = 8;
        int visibleCount = Math.max(1, Math.min(maxVisible, filteredParticleIds.size()));
        int listH = visibleCount * rowH;

        this.particleDropdownMaxScrollY = Math.max(0, (filteredParticleIds.size() * rowH) - listH);
        if (this.particleDropdownScrollY > this.particleDropdownMaxScrollY) this.particleDropdownScrollY = this.particleDropdownMaxScrollY;

        c.getMatrices().push();
        c.getMatrices().translate(0, 0, 300);

        c.fill(ddX, ddY, ddX + ddW, ddY + listH + 4, 0xFA111111);
        c.drawBorder(ddX, ddY, ddW, listH + 4, 0xFF666666);

        if (filteredParticleIds.isEmpty()) {
            c.drawTextWithShadow(parent.getTextRenderer(), "§7(nenhuma partícula encontrada)", ddX + 3, ddY + 4, 0xFFAAAAAA);
        } else {
            parent.enablePerfectScissor(c, ddX, ddY + 2, ddW, listH);
            for (int i = 0; i < filteredParticleIds.size(); i++) {
                int itemY = ddY + 2 + (i * rowH) - (int) particleDropdownScrollY;
                if (itemY + rowH < ddY + 2 || itemY > ddY + 2 + listH) continue;

                boolean hov = mouseX >= ddX && mouseX <= ddX + ddW && mouseY >= itemY && mouseY <= itemY + rowH;
                if (hov) c.fill(ddX, itemY, ddX + ddW, itemY + rowH, 0xFF333333);
                c.drawTextWithShadow(parent.getTextRenderer(), filteredParticleIds.get(i), ddX + 3, itemY + 2, hov ? 0xFFFFFF : 0xFFAAAAAA);
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
        // --- SE O POPUP DE SAÍDA ESTIVER ABERTO, INTERCEPTA TUDO ---
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
                    this.scrollY = 0; // Reset ao voltar pro menu principal
                });
                return true;
            }

            // Criar Novo
            if (mx >= x + 10 && mx <= x + width - 10 && my >= topY + 15 && my <= topY + 30) {
                playClick();
                String newId = "novo_efeito_" + UUID.randomUUID().toString().substring(0, 4);
                EffectData newData = new EffectData();
                newData.particleId = "minecraft:flame";
                EffectConfig.effectsMap.put(newId, newData);
                EffectConfig.saveEffects();
                sendSaveEffect(newId, newId, newData);
                loadEditor(newId);
                return true;
            }

            List<String> ids = new ArrayList<>(EffectConfig.effectsMap.keySet());
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
                    this.scrollY = 0; // Reset ao voltar pra lista de efeitos
                    this.particleDropdownOpen = false;
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
            if (particleDropdownOpen && particleIdRow != null) {
                int fieldRowY = iterListY + (rows.indexOf(particleIdRow) * rowHeight) - (int) scrollY;
                int ddX = x + 15; int ddY = fieldRowY + 28; int ddW = width - 35;
                int rowH = 12;
                int visibleCount = Math.max(1, Math.min(8, filteredParticleIds.size()));
                int listH = visibleCount * rowH;

                if (mx >= ddX && mx <= ddX + ddW && my >= ddY && my <= ddY + listH + 4) {
                    int clickedIndex = (int) ((my - (ddY + 2) + particleDropdownScrollY) / rowH);
                    if (clickedIndex >= 0 && clickedIndex < filteredParticleIds.size()) {
                        String chosen = filteredParticleIds.get(clickedIndex);
                        editingData.particleId = chosen;
                        particleIdRow.textField.setText(chosen);
                        hasUnsavedChanges = true;
                        playClick();
                    }
                    particleDropdownOpen = false;
                    return true;
                }

                boolean onFieldItself = mx >= x + 15 && mx <= x + width - 20 && my >= fieldRowY + 12 && my <= fieldRowY + 28;
                if (!onFieldItself) {
                    particleDropdownOpen = false;
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
                            if (row == particleIdRow) {
                                particleDropdownOpen = true;
                                filterParticles(row.textField.getText());
                            }
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

                // Inicia o arrasto do Gizmo se clicar no fundo vazio
                isDraggingGizmo = true;
                lastMouseX = mx;
                lastMouseY = my;
                return false; // Retorna false para permitir a câmera girar
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double deltaX, double deltaY) {
        if (isDraggingGizmo && currentState == State.EDITOR && this.editingData != null) {
            long window = MinecraftClient.getInstance().getWindow().getHandle();
            boolean isX = net.minecraft.client.util.InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_X);
            boolean isY = net.minecraft.client.util.InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_Y);
            boolean isZ = net.minecraft.client.util.InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_Z);

            if (isX || isY || isZ) {
                // Matemática corrigida para fluidez: deltaX afeta eixos X e Z, deltaY afeta eixo Y (invertido)
                float moveX = (float) (mx - lastMouseX) * 0.01f;
                float moveY = (float) (my - lastMouseY) * -0.01f; // Invertido porque a tela cresce pra baixo

                if (isX) this.editingData.offsetX += moveX;
                else if (isY) this.editingData.offsetY += moveY;
                else if (isZ) this.editingData.offsetZ += moveX; // Z usa movimento horizontal do mouse

                syncGizmoToFields();
                this.hasUnsavedChanges = true;

                lastMouseX = mx;
                lastMouseY = my;
                return true; // Bloqueia a câmera
            } else {
                lastMouseX = mx;
                lastMouseY = my;
                // Deixa o return false rodar lá embaixo para girar a câmera
            }
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

        if (currentState == State.EDITOR && particleDropdownOpen && particleIdRow != null) {
            int x = lastX; int y = lastY; int width = lastWidth; int height = lastHeight;
            int topY = y + 35; int iterListY = topY + 20; int rowHeight = 35;
            int fieldRowY = iterListY + (rows.indexOf(particleIdRow) * rowHeight) - (int) scrollY;
            int ddX = x + 15; int ddY = fieldRowY + 28; int ddW = width - 35;
            int rowH = 12;
            int visibleCount = Math.max(1, Math.min(8, filteredParticleIds.size()));
            int listH = visibleCount * rowH;

            if (mx >= ddX && mx <= ddX + ddW && my >= ddY && my <= ddY + listH + 4) {
                particleDropdownScrollY -= (float) (vAmount * 15f);
                if (particleDropdownScrollY < 0) particleDropdownScrollY = 0;
                if (particleDropdownScrollY > particleDropdownMaxScrollY) particleDropdownScrollY = particleDropdownMaxScrollY;
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
        // --- LÓGICA DE RENOMEAÇÃO DO MAP ---
        String oldId = this.editingId;
        if (this.tempId != null && !this.tempId.trim().isEmpty() && !this.tempId.equals(this.editingId)) {
            EffectConfig.effectsMap.remove(this.editingId);
            EffectConfig.effectsMap.put(this.tempId, this.editingData);
            this.editingId = this.tempId;
        }
        EffectConfig.saveEffects();
        sendSaveEffect(oldId, this.editingId, this.editingData);
    }

    @Override
    public void discardChanges() { EffectConfig.loadEffects(); }

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