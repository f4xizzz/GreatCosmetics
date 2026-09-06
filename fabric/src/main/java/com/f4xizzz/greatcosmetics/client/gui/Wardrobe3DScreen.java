package com.f4xizzz.greatcosmetics.client.gui;

import com.f4xizzz.greatcosmetics.GreatCosmetics;
import com.f4xizzz.greatcosmetics.client.ClientCosmeticCache;
import com.f4xizzz.greatcosmetics.client.gui.pages.*;
import com.f4xizzz.greatcosmetics.config.CosmeticData;
import com.f4xizzz.greatcosmetics.network.CloseWardrobePayload;
import com.f4xizzz.greatcosmetics.network.EquipCosmeticPayload;
import com.f4xizzz.greatcosmetics.network.ToggleVisibilityPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Wardrobe3DScreen extends Screen {

    public enum Tab {
        ACESSORIES("wardrobe.tab.accessories"),
        CUSTOMIZE("wardrobe.tab.customize"),
        PARTY("wardrobe.tab.party"),
        TAGS("wardrobe.tab.tags"),
        DEV("wardrobe.tab.dev");

        private final String langKey;

        Tab(String langKey) {
            this.langKey = langKey;
        }

        /** Resolvido do Lang a cada chamada (não fixo no construtor) — pega /gc reload. */
        public String getDisplayName() {
            return com.f4xizzz.greatcosmetics.config.LangConfig.legacy(this.langKey);
        }
    }

    private Tab currentTab = null;

    /** Gate da aba "Dev Studio" (edição global de cosméticos/efeitos/config do servidor) —
     * continua exigindo OP de verdade, igual sempre foi. Lê do ClientPermissionCache (calculado
     * no servidor) em vez de hasPermissionLevel() direto — ver SyncDevPermissionsPayload. */
    private boolean hasDevPermission() {
        return com.f4xizzz.greatcosmetics.client.ClientPermissionCache.isOperator;
    }

    private final Map<Tab, WardrobePage> pages = new HashMap<>();

    public static float targetYaw, currentYaw;
    public static float targetPitch, currentPitch;
    public static double targetDistance, currentDistance;
    public static float targetFocusY, currentFocusY;
    public static float targetPan, currentPan;

    public static boolean isFreecamActive = false;
    public static boolean isZoomLocked = false;

    public static boolean isDevTabActive = false;
    public static boolean isPartyTabActive = false; // <--- VARIÁVEL DE CONTROLE ADICIONADA
    public static boolean isTagsTabActive = false;
    public static String previewCosmeticId = null;
    public static boolean isPreviewSneaking = false;

    // Visibilidade do CORPO do jogador (não afeta armadura/cosméticos, ver PlayerEntityRendererMixin
    // + o reset em ArmorFeatureRendererMixin) — 3 estados só, ciclados por clique (não mais um
    // slider arrastável): 1.0 = normal, 0.5 = meio transparente, 0.0 = 100% invisível (cancela o
    // render do corpo inteiro, ver greatcosmetics$startBodyAlpha). Só usada/visível enquanto uma
    // Part está sendo configurada (barrinha lateral esquerda, ver renderGizmoSidebar).
    public static float characterAlpha = 1.0f;

    // Segurar o botão +/- do offset do pivô do gizmo (barrinha lateral, só .geo) repete o passo
    // sozinho enquanto o botão do mouse continuar pressionado — ver updateGeoOffsetHold().
    private boolean isHoldingGeoOffsetButton = false;
    private int geoOffsetHoldDir = 0;
    private long geoOffsetHoldStartMs = 0;
    private long geoOffsetLastStepMs = 0;

    private final boolean hasBackground;

    private boolean isInitialized = false;
    private float lockedPlayerYaw;
    private CameraType oldPerspective;
    private boolean oldHudHidden;
    private Integer oldFov;

    private final com.f4xizzz.greatcosmetics.client.gui.widgets.EquippedSlotsWidget equippedSlotsWidget = new com.f4xizzz.greatcosmetics.client.gui.widgets.EquippedSlotsWidget(this);

    private static final int TAB_BG_INACTIVE = 0x66000000;
    private static final int TAB_BG_ACTIVE = 0xCC000000;
    private static final int ACCENT_WHITE = 0xFFFFFFFF;
    private static final int ACCENT_GRAY = 0xFF444444;
    private static final int SHADOW_GLOW = 0x44000000;
    private static final int TEXT_ACTIVE = 0xFFFFFFFF;
    private static final int TEXT_MUTED = 0xFFAAAAAA;
    private static final int BG_PANEL_DARK = 0xEF0A0A0A;

    private int topBarX = 30;
    private int topBarY = 20;
    private int tabBaseW = 24;
    private int tabH = 22;
    private int tabSpacing = 6;

    private int panelW = 180;
    private int panelH = 260;

    // Tamanho do painel ANIMADO em direção ao alvo (mesma ideia do targetYaw/currentYaw acima) —
    // sem isso, DevPage#preferredPanelSize trocando de tamanho (menu inicial <-> subpágina) fazia o
    // painel "teleportar" de tamanho de um frame pro outro, feio comparado com o resto da UI, que
    // já anima tudo (largura das abas, câmera, etc).
    private float currentPanelW = 180, currentPanelH = 260;

    private int fullWAcc, fullWCus, fullWPrt, fullWTag, fullWDev;
    private float widthAcc = 24f, widthCus = 24f, widthPrt = 24f, widthTag = 24f, widthDev = 24f;

    private final String[] equipSlots = {"HEAD", "NECK", "CHEST", "BACK", "WAIST", "LEGS", "FEET"};

    public Wardrobe3DScreen(boolean hasBackground) {
        super(com.f4xizzz.greatcosmetics.config.LangConfig.text("wardrobe.title"));
        this.hasBackground = hasBackground;

        // Reseta os flags de dev-mode TODA VEZ que o wardrobe abre — sem isso, um flag que
        // ficou "true" (ex: ClientCosmeticCache.isDevModeActive, que nunca era resetado em
        // removed()) vazava pro resto da sessão do client, deixando o "Dev: ON" ligado sozinho
        // pra qualquer conta usada depois nesse mesmo processo, mesmo sem permissão nenhuma.
        isDevTabActive = false;
        com.f4xizzz.greatcosmetics.client.ClientCosmeticCache.isDevModeActive = false;

        this.pages.put(Tab.ACESSORIES, new AcessoriesPage(this));
        this.pages.put(Tab.CUSTOMIZE, new CustomizePage(this));
        this.pages.put(Tab.PARTY, new PartyPage(this));
        this.pages.put(Tab.TAGS, new TagsPage(this));

        // A instância existe pra todo mundo (só existe um jar — ver build.gradle); quem não tem
        // permissão nunca vê o botão da aba nem consegue trocar pra ela (ver hasDevPermission()).
        this.pages.put(Tab.DEV, new DevPage(this));
    }

    public Font getTextRenderer() {
        return this.font;
    }

    public void enablePerfectScissor(GuiGraphics c, int x, int y, int width, int height) {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        double scale = client.getWindow().getGuiScale();
        org.joml.Matrix4f matrix = c.pose().last().pose();
        org.joml.Vector4f start = new org.joml.Vector4f((float) x, (float) y, 0.0F, 1.0F).mul(matrix);
        org.joml.Vector4f end = new org.joml.Vector4f((float) (x + width), (float) (y + height), 0.0F, 1.0F).mul(matrix);

        int screenX = (int) Math.round(start.x() * scale);
        int screenY = (int) Math.round(client.getWindow().getHeight() - end.y() * scale);
        int screenW = (int) Math.round((end.x() - start.x()) * scale);
        int screenH = (int) Math.round((end.y() - start.y()) * scale);

        com.mojang.blaze3d.systems.RenderSystem.enableScissor(
                Math.max(0, screenX), Math.max(0, screenY), Math.max(0, screenW), Math.max(0, screenH)
        );
    }

    /**
     * Versão "empilhável" de enablePerfectScissor. DrawContext.enableScissor(x1,y1,x2,y2) (o
     * nativo do jogo) NUNCA passa x/y pela matrix ATIVA — ele só multiplica direto pelo GUI
     * Scale, assumindo que os números recebidos já são coordenada "de tela normal". Isso funciona
     * numa tela comum, mas esse wardrobe renderiza um canvas VIRTUAL (450 de altura) escalado à
     * parte via matrices.scale() — então um recorte feito com DrawContext.enableScissor() direto
     * (como passei a fazer na PartyPage pra ganhar o empilhamento/interseção com o recorte próprio
     * do ModelWidget do Cobblemon) fica DESALINHADO do conteúdo de verdade renderizado, e o quanto
     * desalinha muda com a resolução/GUI Scale (só nesse ponto é que os dois viram proporcionais
     * de novo) — exatamente o sintoma reportado: cabeçalho de grupo com a primeira letra cortada,
     * lista inteira parecendo cortada errado. Aqui faço a MESMA transformação manual pela matrix
     * que enablePerfectScissor já fazia (pra compensar o canvas virtual), só que em vez de chamar
     * RenderSystem.enableScissor() cru no final, repasso pro c.enableScissor()/disableScissor() —
     * que aí sim empilha/intersecta direito com qualquer recorte pai ou filho.
     */
    public void enableScissorStacked(GuiGraphics c, int x, int y, int width, int height) {
        org.joml.Matrix4f matrix = c.pose().last().pose();
        org.joml.Vector4f start = new org.joml.Vector4f((float) x, (float) y, 0.0F, 1.0F).mul(matrix);
        org.joml.Vector4f end = new org.joml.Vector4f((float) (x + width), (float) (y + height), 0.0F, 1.0F).mul(matrix);

        c.enableScissor(Math.round(start.x()), Math.round(start.y()), Math.round(end.x()), Math.round(end.y()));
    }

    @Override
    protected void init() {
        if (this.minecraft != null && this.minecraft.player != null) {
            if (!this.isInitialized) {
                com.f4xizzz.greatcosmetics.GreatCosmeticsClient.debugLog("Wardrobe3DScreen: opening for " + this.minecraft.player.getName().getString() + ".");
                this.lockedPlayerYaw = this.minecraft.player.getYRot();
                this.oldPerspective = this.minecraft.options.getCameraType();
                this.oldHudHidden = this.minecraft.options.hideGui;
                this.oldFov = this.minecraft.options.fov().get();

                this.fullWAcc = this.font.width(Tab.ACESSORIES.getDisplayName()) + 16;
                this.fullWCus = this.font.width(Tab.CUSTOMIZE.getDisplayName()) + 16;
                this.fullWPrt = this.font.width(Tab.PARTY.getDisplayName()) + 16;
                this.fullWTag = this.font.width(Tab.TAGS.getDisplayName()) + 16;
                this.fullWDev = this.font.width(Tab.DEV.getDisplayName()) + 16;

                this.isInitialized = true;
            }

            this.minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            this.minecraft.options.hideGui = true;
            this.minecraft.options.fov().set(45);
            isFreecamActive = true;

            resetCameraToNeutral();
            currentYaw = targetYaw; currentPitch = targetPitch;
            currentDistance = targetDistance; currentFocusY = targetFocusY;
            currentPan = targetPan;
        }
    }

    private void resetCameraToNeutral() {
        if (this.minecraft != null && this.minecraft.player != null) {
            targetYaw = this.lockedPlayerYaw + 180f;
            targetPitch = this.hasBackground ? this.minecraft.player.getXRot() : 0f;
            targetDistance = 3.5;
            targetFocusY = this.minecraft.player.getEyeHeight() - 0.5f;
            targetPan = 0.0f;
            isZoomLocked = false;
        }
    }

    private void toggleTab(Tab clickedTab, float focusY, double zoom, float pan) {
        boolean wasDevTab = isDevTabActive;

        if (this.currentTab == clickedTab) {
            this.currentTab = null;
            isDevTabActive = false;
            isPartyTabActive = false; // <---
            isTagsTabActive = false;
            resetCameraToNeutral();
        } else {
            this.currentTab = clickedTab;
            isDevTabActive = (clickedTab == Tab.DEV);
            isPartyTabActive = (clickedTab == Tab.PARTY); // <--- ATIVA/DESATIVA JUNTO COM A ABA
            isTagsTabActive = (clickedTab == Tab.TAGS);
            targetFocusY = focusY;
            targetDistance = zoom;
            targetPan = pan;
            isZoomLocked = false;

            WardrobePage page = pages.get(clickedTab);
            if (page != null) page.onOpen();
        }

        // BUG (2026-09): sair da aba DEV (pra outra aba, ou fechando ela) sem passar pelo botão
        // "Voltar" de dentro do editor de Cosméticos/Effects deixava GizmoManager.activePart/
        // activeEffect setado pra sempre — a barrinha lateral (botão "S", slider de transparência,
        // o texto de diagnóstico) e o gizmo 3D no mundo continuavam desenhando em CIMA de outras
        // abas, porque nada além do próprio editor (DevCosmeticsSubPage/DevEffectsSubPage) limpava
        // esse estado. Trocar de aba é exatamente um dos jeitos de "sair" que não passava por lá —
        // limpa aqui, no único lugar por onde QUALQUER troca de aba passa.
        if (wasDevTab && !isDevTabActive) {
            com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager.activePart = null;
            com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager.activeEffect = null;
            com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager.currentAxis = com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager.Axis.NONE;
            com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager.isDragging = false;
            com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager.onUpdate = null;
        }
    }

    @Override
    public void render(GuiGraphics c, int mouseX, int mouseY, float delta) {
        if (this.minecraft == null || this.minecraft.player == null) return;

        // Rede de segurança: por mais que PlayerEntityRendererMixin sempre resete a cor do shader
        // depois de aplicar a transparência do corpo (ver characterAlpha), esse reset é um TAIL de
        // mixin — se algum outro código cancelar o render do player antes de chegar lá (a própria
        // Party tab já faz isso, embora mutuamente exclusiva com o Dev Studio hoje), a cor "vazada"
        // ia contaminar o resto da tela. Resetar aqui, toda vez que a GUI desenha, garante que o
        // vazamento nunca sobrevive além de um único frame.
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        boolean shouldSneak = isDevTabActive && isPreviewSneaking;
        this.minecraft.options.keyShift.setDown(shouldSneak);

        this.minecraft.player.setYRot(this.lockedPlayerYaw);
        this.minecraft.player.yRotO = this.lockedPlayerYaw;
        this.minecraft.player.setYBodyRot(this.lockedPlayerYaw);
        this.minecraft.player.yBodyRotO = this.lockedPlayerYaw;
        this.minecraft.player.setYHeadRot(this.lockedPlayerYaw);
        this.minecraft.player.yHeadRotO = this.lockedPlayerYaw;
        this.minecraft.player.setXRot(0f);
        this.minecraft.player.xRotO = 0f;

        float animSpeed = 0.15f;
        currentFocusY += (targetFocusY - currentFocusY) * animSpeed;
        currentDistance += (targetDistance - currentDistance) * animSpeed;
        currentPan += (targetPan - currentPan) * animSpeed;
        currentPitch += (targetPitch - currentPitch) * animSpeed;
        currentYaw = Mth.rotLerp(animSpeed, currentYaw, targetYaw);

        float targetScale = this.minecraft.getWindow().getHeight() / 450f;
        float scaleMultiplier = targetScale / (float) this.minecraft.getWindow().getGuiScale();

        int vMouseX = (int) (mouseX / scaleMultiplier);
        int vMouseY = (int) (mouseY / scaleMultiplier);
        int vWidth = (int) (this.minecraft.getWindow().getWidth() / targetScale);
        int vHeight = (int) (this.minecraft.getWindow().getHeight() / targetScale);

        c.pose().pushPose();
        c.pose().scale(scaleMultiplier, scaleMultiplier, 1.0f);

        c.fillGradient(0, 0, vWidth, 60, 0x88000000, 0x00000000);
        c.fillGradient(0, vHeight - 40, vWidth, vHeight, 0x00000000, 0x88000000);

        int startX = topBarX;

        boolean hovAcc = over(vMouseX, vMouseY, startX, topBarY, (int)widthAcc, tabH);
        float targetWAcc = (this.currentTab == Tab.ACESSORIES || hovAcc) ? fullWAcc : tabBaseW;
        widthAcc += (targetWAcc - widthAcc) * 0.2f;
        drawTechTab(c, Tab.ACESSORIES, "A", Tab.ACESSORIES.getDisplayName(), startX, topBarY, (int)widthAcc, hovAcc);
        startX += (int)widthAcc + tabSpacing;

        boolean hovCus = over(vMouseX, vMouseY, startX, topBarY, (int)widthCus, tabH);
        float targetWCus = (this.currentTab == Tab.CUSTOMIZE || hovCus) ? fullWCus : tabBaseW;
        widthCus += (targetWCus - widthCus) * 0.2f;
        drawTechTab(c, Tab.CUSTOMIZE, "C", Tab.CUSTOMIZE.getDisplayName(), startX, topBarY, (int)widthCus, hovCus);
        startX += (int)widthCus + tabSpacing;

        boolean hovPrt = over(vMouseX, vMouseY, startX, topBarY, (int)widthPrt, tabH);
        float targetWPrt = (this.currentTab == Tab.PARTY || hovPrt) ? fullWPrt : tabBaseW;
        widthPrt += (targetWPrt - widthPrt) * 0.2f;
        drawTechTab(c, Tab.PARTY, "P", Tab.PARTY.getDisplayName(), startX, topBarY, (int)widthPrt, hovPrt);
        startX += (int)widthPrt + tabSpacing;

        boolean hovTag = over(vMouseX, vMouseY, startX, topBarY, (int)widthTag, tabH);
        float targetWTag = (this.currentTab == Tab.TAGS || hovTag) ? fullWTag : tabBaseW;
        widthTag += (targetWTag - widthTag) * 0.2f;
        drawTechTab(c, Tab.TAGS, "T", Tab.TAGS.getDisplayName(), startX, topBarY, (int)widthTag, hovTag);
        startX += (int)widthTag + tabSpacing;

        if (hasDevPermission()) {
            boolean hovDev = over(vMouseX, vMouseY, startX, topBarY, (int)widthDev, tabH);
            float targetWDev = (this.currentTab == Tab.DEV || hovDev) ? fullWDev : tabBaseW;
            widthDev += (targetWDev - widthDev) * 0.2f;
            drawTechTab(c, Tab.DEV, "D", Tab.DEV.getDisplayName(), startX, topBarY, (int)widthDev, hovDev);
            startX += (int)widthDev + tabSpacing;
        } else if (this.currentTab == Tab.DEV) {
            // Rede de segurança: se por qualquer motivo currentTab ainda estiver em DEV sem
            // permissão (perdeu OP no meio da sessão, etc), força de volta pra uma aba normal
            // ANTES de renderizar o conteúdo — o painel de Dev nunca deve ficar nem visível.
            this.currentTab = Tab.ACESSORIES;
        }

        if (this.currentTab != null) {
            int panelX = topBarX;
            int panelY = topBarY + tabH + 8;

            // A aba DEV precisa de bem mais espaço que as outras (outliner + painel de
            // propriedades lado a lado, estilo Blockbench) — as outras abas continuam no
            // retângulo pequeno de sempre. vWidth/vHeight já são a tela virtual inteira (450 de
            // altura sempre, largura varia com a proporção da tela) calculados mais acima nesse
            // mesmo método.
            // Painel Dev bem maior que os outros (outliner + properties lado a lado), mas SEM tomar
            // a tela toda — 460x380 (quase 85% da altura virtual) deixava o personagem quase
            // invisível atrás do painel. 320x300 ainda cabe outliner+properties confortavelmente e
            // sobra espaço de verdade pra ver o preview 3D.
            boolean isDev = this.currentTab == Tab.DEV;
            int targetPanelW = panelW, targetPanelH = panelH;
            if (isDev) {
                WardrobePage devPageInstance = pages.get(Tab.DEV);
                int[] preferred = devPageInstance != null ? devPageInstance.preferredPanelSize() : null;
                targetPanelW = preferred != null ? preferred[0] : 320;
                targetPanelH = preferred != null ? preferred[1] : 300;
            } else {
                // Generaliza preferredPanelSize() pras outras abas também (antes só a DEV era
                // consultada) — a PARTY usa isso pra pedir um painel um pouco mais largo, só o
                // suficiente pra sobrar uma margem dedicada pra barra de scroll da lista de skins,
                // sem ela invadir o ícone/texto das linhas (ver PartyPage#preferredPanelSize).
                WardrobePage currentPageInstance = this.currentTab != null ? pages.get(this.currentTab) : null;
                int[] preferred = currentPageInstance != null ? currentPageInstance.preferredPanelSize() : null;
                if (preferred != null) {
                    targetPanelW = preferred[0];
                    targetPanelH = preferred[1];
                }
            }
            currentPanelW += (targetPanelW - currentPanelW) * 0.2f;
            currentPanelH += (targetPanelH - currentPanelH) * 0.2f;
            int effPanelW = Math.min(Math.round(currentPanelW), vWidth - topBarX - 20);
            int effPanelH = Math.min(Math.round(currentPanelH), vHeight - panelY - 20);

            // Com o popup "Config Part" aberto, o fundo/moldura do painel também some — só o popup
            // (desenhado por dentro de activePage.render logo abaixo) e o preview 3D ficam visíveis.
            boolean hidePanelChrome = isDev
                    && com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev.DevCosmeticsSubPage.INSTANCE != null
                    && com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev.DevCosmeticsSubPage.INSTANCE.isPartPopupOpen();

            if (!hidePanelChrome) {
                c.fill(panelX + 4, panelY + 4, panelX + effPanelW + 4, panelY + effPanelH + 4, SHADOW_GLOW);
                c.fill(panelX, panelY, panelX + effPanelW, panelY + effPanelH, BG_PANEL_DARK);

                int cs = 6;
                c.fill(panelX, panelY, panelX + cs, panelY + 1, ACCENT_WHITE);
                c.fill(panelX, panelY, panelX + 1, panelY + cs, ACCENT_WHITE);
                c.fill(panelX + effPanelW - cs, panelY, panelX + effPanelW, panelY + 1, ACCENT_WHITE);
                c.fill(panelX + effPanelW - 1, panelY, panelX + effPanelW, panelY + cs, ACCENT_WHITE);
                c.fill(panelX, panelY + effPanelH - 1, panelX + cs, panelY + effPanelH, ACCENT_WHITE);
                c.fill(panelX, panelY + effPanelH - cs, panelX + 1, panelY + effPanelH, ACCENT_WHITE);
                c.fill(panelX + effPanelW - cs, panelY + effPanelH - 1, panelX + effPanelW, panelY + effPanelH, ACCENT_WHITE);
                c.fill(panelX + effPanelW - 1, panelY + effPanelH - cs, panelX + effPanelW, panelY + effPanelH, ACCENT_WHITE);

                c.fill(panelX + cs, panelY, panelX + effPanelW - cs, panelY + 1, ACCENT_GRAY);
                c.fillGradient(panelX, panelY + cs, panelX + 1, panelY + effPanelH - cs, ACCENT_GRAY, 0x00FFFFFF);

                if (!isDev) {
                    String title = com.f4xizzz.greatcosmetics.config.LangConfig.legacy("wardrobe.panel.title", "tab", this.currentTab.getDisplayName().toUpperCase());
                    c.drawString(this.font, title, panelX + 12, panelY + 14, ACCENT_WHITE);
                    c.fill(panelX + 12, panelY + 28, panelX + effPanelW - 12, panelY + 29, 0xFF333333);
                }
            }

            WardrobePage activePage = pages.get(this.currentTab);
            if (activePage != null) {
                activePage.render(c, vMouseX, vMouseY, delta, panelX, panelY, effPanelW, effPanelH);
            }
        }

        if (com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager.hasTarget()) {
            com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager.updateHover(vMouseX, vMouseY);
            updateGeoOffsetHold();
            renderGizmoSidebar(c, vMouseX, vMouseY);
        }

        this.equippedSlotsWidget.render(c, vMouseX, vMouseY, vWidth, vHeight, this.currentTab == Tab.DEV);

        c.pose().popPose();
    }

    // Layout da barrinha, compartilhado entre render/click/drag — muda aqui, muda nos três juntos.
    private static final int SIDEBAR_X = 4, SIDEBAR_W = 26, SIDEBAR_Y = 70;
    private static final int SIDEBAR_SNEAK_H = 22, SIDEBAR_ALPHA_GAP = 10, SIDEBAR_ALPHA_BTN_H = 22;
    private static final int SIDEBAR_GEO_GAP = 10, SIDEBAR_GEO_BTN_H = 14;
    private static final float GEO_Y_OFFSET_STEP = 0.02f;

    /** Só a Part GeckoLib (.geo) tem a seção extra de ajuste do pivô do gizmo — cosmético "ícone
     *  chapado" já fica certinho sem ajuste nenhum (ver ArmorFeatureRendererMixin). */
    private boolean showGeoOffsetSection() {
        var part = com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager.activePart;
        return part != null && part.geoModelId != null && !part.geoModelId.isBlank();
    }

    private int sidebarAlphaBtnTop() { return SIDEBAR_Y + SIDEBAR_SNEAK_H + SIDEBAR_ALPHA_GAP; }
    private int sidebarAlphaBtnBottom() { return sidebarAlphaBtnTop() + SIDEBAR_ALPHA_BTN_H; }
    private int sidebarGeoRowY() { return sidebarAlphaBtnBottom() + 14 + SIDEBAR_GEO_GAP; }
    private int sidebarHeight() {
        int bottom = sidebarAlphaBtnBottom() + 14;
        if (showGeoOffsetSection()) bottom = sidebarGeoRowY() + SIDEBAR_GEO_BTN_H + 12;
        return bottom - SIDEBAR_Y + 6;
    }

    /** Avança characterAlpha pro próximo dos 3 estados (Visível -> Meio -> Invisível -> Visível),
     *  ver botão único na barrinha (era um slider arrastável antes — trocado a pedido do usuário
     *  por um clique que cicla, igual visibilidade de camada em editor de imagem). */
    private void cycleCharacterAlpha() {
        if (characterAlpha >= 0.99f) characterAlpha = 0.5f;
        else if (characterAlpha > 0.01f) characterAlpha = 0.0f;
        else characterAlpha = 1.0f;
    }

    /** Barrinha vertical fixa na borda esquerda da tela — só aparece enquanto uma Part está sendo
     *  configurada no Dev Studio (GizmoManager.activePart != null). Botão "S" (Visualizar Posição:
     *  SNEAK, antes um toggle perdido no meio da lista de propriedades) em cima, botão de
     *  visibilidade do CORPO do jogador no meio (3 estados por clique — ver
     *  PlayerEntityRendererMixin#greatcosmetics$startBodyAlpha/cycleCharacterAlpha), e — só pra
     *  Parts GeckoLib — o ajuste do pivô do gizmo (ver GizmoDevConfig) embaixo. */
    private void renderGizmoSidebar(GuiGraphics c, int mouseX, int mouseY) {
        int sbH = sidebarHeight();

        c.fill(SIDEBAR_X - 2, SIDEBAR_Y - 4, SIDEBAR_X + SIDEBAR_W + 2, SIDEBAR_Y + sbH + 4, SHADOW_GLOW);
        c.fill(SIDEBAR_X, SIDEBAR_Y, SIDEBAR_X + SIDEBAR_W, SIDEBAR_Y + sbH, BG_PANEL_DARK);
        c.renderOutline(SIDEBAR_X, SIDEBAR_Y, SIDEBAR_W, sbH, ACCENT_GRAY);

        boolean hovSneak = over(mouseX, mouseY, SIDEBAR_X + 2, SIDEBAR_Y + 2, SIDEBAR_W - 4, SIDEBAR_SNEAK_H);
        c.fill(SIDEBAR_X + 2, SIDEBAR_Y + 2, SIDEBAR_X + SIDEBAR_W - 2, SIDEBAR_Y + 2 + SIDEBAR_SNEAK_H,
                isPreviewSneaking ? 0xFFBB00FF : (hovSneak ? 0x66FFFFFF : 0x44000000));
        c.renderOutline(SIDEBAR_X + 2, SIDEBAR_Y + 2, SIDEBAR_W - 4, SIDEBAR_SNEAK_H, isPreviewSneaking ? 0xFFDD66FF : 0xFF666666);
        c.drawCenteredString(this.font, "S", SIDEBAR_X + SIDEBAR_W / 2, SIDEBAR_Y + 2 + SIDEBAR_SNEAK_H / 2 - 4, 0xFFFFFF);

        // Botão único de visibilidade do corpo — clicar cicla Visível -> Meio -> Invisível ->
        // Visível (ver cycleCharacterAlpha). Letra + cor mudam por estado pra ficar óbvio de
        // relance sem precisar ler o "%" embaixo.
        int alphaBtnY = sidebarAlphaBtnTop();
        boolean hovAlphaBtn = over(mouseX, mouseY, SIDEBAR_X + 2, alphaBtnY, SIDEBAR_W - 4, SIDEBAR_ALPHA_BTN_H);
        String alphaLabel;
        int alphaBg, alphaBorder;
        if (characterAlpha >= 0.99f) {
            alphaLabel = "V";
            alphaBg = hovAlphaBtn ? 0x66FFFFFF : 0x44000000;
            alphaBorder = 0xFF666666;
        } else if (characterAlpha > 0.01f) {
            alphaLabel = "H";
            alphaBg = 0xFFAA8800;
            alphaBorder = 0xFFFFCC44;
        } else {
            alphaLabel = "I";
            alphaBg = 0xFFAA2222;
            alphaBorder = 0xFFFF6666;
        }
        c.fill(SIDEBAR_X + 2, alphaBtnY, SIDEBAR_X + SIDEBAR_W - 2, alphaBtnY + SIDEBAR_ALPHA_BTN_H, alphaBg);
        c.renderOutline(SIDEBAR_X + 2, alphaBtnY, SIDEBAR_W - 4, SIDEBAR_ALPHA_BTN_H, alphaBorder);
        c.drawCenteredString(this.font, alphaLabel, SIDEBAR_X + SIDEBAR_W / 2, alphaBtnY + SIDEBAR_ALPHA_BTN_H / 2 - 4, 0xFFFFFF);
        c.drawCenteredString(this.font, Math.round(characterAlpha * 100) + "%", SIDEBAR_X + SIDEBAR_W / 2, alphaBtnY + SIDEBAR_ALPHA_BTN_H + 4, 0xFFAAAAAA);

        if (showGeoOffsetSection()) {
            int rowY = sidebarGeoRowY();
            int btnW = (SIDEBAR_W - 6) / 2;
            boolean hovMinus = over(mouseX, mouseY, SIDEBAR_X + 2, rowY, btnW, SIDEBAR_GEO_BTN_H);
            boolean hovPlus = over(mouseX, mouseY, SIDEBAR_X + 4 + btnW, rowY, btnW, SIDEBAR_GEO_BTN_H);
            c.fill(SIDEBAR_X + 2, rowY, SIDEBAR_X + 2 + btnW, rowY + SIDEBAR_GEO_BTN_H, hovMinus ? 0x66FFFFFF : 0x44000000);
            c.fill(SIDEBAR_X + 4 + btnW, rowY, SIDEBAR_X + 4 + btnW * 2, rowY + SIDEBAR_GEO_BTN_H, hovPlus ? 0x66FFFFFF : 0x44000000);
            c.drawCenteredString(this.font, "-", SIDEBAR_X + 2 + btnW / 2, rowY + 3, 0xFFFFFFFF);
            c.drawCenteredString(this.font, "+", SIDEBAR_X + 4 + btnW + btnW / 2, rowY + 3, 0xFFFFFFFF);
            c.drawCenteredString(this.font,
                    String.format(java.util.Locale.US, "%.2f", com.f4xizzz.greatcosmetics.client.GizmoDevConfig.geoGizmoYOffset),
                    SIDEBAR_X + SIDEBAR_W / 2, rowY + SIDEBAR_GEO_BTN_H + 2, 0xFFFFAA55);
        }

        // DIAGNÓSTICO TEMPORÁRIO (ver GizmoManager#debugStatus/lastClickDebug) — some assim que
        // confirmarmos que o gizmo tá funcionando de verdade. Duas linhas: estado geral (atualiza
        // todo frame) e o resultado do ÚLTIMO clique tentado no gizmo (só atualiza ao clicar).
        // BUG (2026-09) — duas rodadas: primeiro vazava pra fora do painel (sem largura travando o
        // desenho); tentei cortar com enablePerfectScissor()/RenderSystem.disableScissor() cru, só
        // que esse é o MESMO padrão errado já documentado em PartyPage#enableScissorStacked — não
        // empilha com o resto do sistema de recorte da tela, então o corte saía desalinhado do
        // texto de verdade (exatamente o "cabeçalho cortado errado" que motivou criar
        // enableScissorStacked em primeiro lugar). Trocado pro método certo.
        int dbgX = SIDEBAR_X + SIDEBAR_W + 6;
        enableScissorStacked(c, dbgX, SIDEBAR_Y, 260, 20);
        c.drawString(this.font, com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager.debugStatus(),
                dbgX, SIDEBAR_Y, 0xFFFF5555);
        c.drawString(this.font, com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager.lastClickDebug,
                dbgX, SIDEBAR_Y + 10, 0xFFFFFF55);
        c.disableScissor();
    }

    /** Chamado todo frame enquanto o botão +/- do offset do pivô está pressionado (ver
     *  mouseClicked/mouseReleased) — depois de um atraso inicial (senão um clique único rápido já
     *  dispararia um segundo passo sem querer), repete o passo sozinho, acelerando quanto mais
     *  tempo o botão continuar segurado. */
    private void updateGeoOffsetHold() {
        if (!isHoldingGeoOffsetButton) return;
        if (com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager.activePart == null) {
            isHoldingGeoOffsetButton = false;
            return;
        }
        long now = System.currentTimeMillis();
        long heldFor = now - geoOffsetHoldStartMs;
        if (heldFor < 350) return;

        long interval = Math.max(20L, 90L - (heldFor - 350) / 40);
        if (now - geoOffsetLastStepMs >= interval) {
            com.f4xizzz.greatcosmetics.client.GizmoDevConfig.geoGizmoYOffset += geoOffsetHoldDir * GEO_Y_OFFSET_STEP;
            com.f4xizzz.greatcosmetics.client.GizmoDevConfig.save();
            geoOffsetLastStepMs = now;
        }
    }

    private void drawTechTab(GuiGraphics c, Tab tab, String letter, String fullName, int x, int y, int currentW, boolean isHovered) {
        boolean isSelected = (this.currentTab == tab);
        int bgColor = (isSelected || isHovered) ? (tab == Tab.DEV ? 0xCC221100 : TAB_BG_ACTIVE) : TAB_BG_INACTIVE;
        c.fill(x, y, x + currentW, y + tabH, bgColor);

        int lineColor = isSelected ? (tab == Tab.DEV ? 0xFFFFAA00 : ACCENT_WHITE) : (isHovered ? 0xFFAAAAAA : ACCENT_GRAY);
        c.fill(x, y + tabH - 1, x + currentW, y + tabH, lineColor);

        enablePerfectScissor(c, x, y, currentW, tabH);
        int textColor = (isSelected || isHovered) ? (tab == Tab.DEV ? 0xFFFFAA00 : TEXT_ACTIVE) : TEXT_MUTED;
        if (currentW < tabBaseW + 10) {
            c.drawCenteredString(this.font, letter, x + (tabBaseW / 2), y + (tabH / 2) - 4, textColor);
        } else {
            c.drawString(this.font, fullName, x + 8, y + (tabH / 2) - 4, textColor);
        }
        com.mojang.blaze3d.systems.RenderSystem.disableScissor();
    }

    private boolean over(double mx, double my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    // =========================================================================
    // NOVO SISTEMA DE INTERCEPTAÇÃO DE TROCA DE TABS (Força o Popup de Salvar!)
    // =========================================================================
    public void attemptTabSwitch(Tab tab, float focusY, double zoom, float pan) {
        Runnable doSwitch = () -> {
            toggleTab(tab, focusY, zoom, pan);
            playClick();
        };

        if (this.currentTab == Tab.DEV) {
            // 1. Checa se tem alterações pendentes nos COSMÉTICOS
            if (com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev.DevCosmeticsSubPage.INSTANCE != null) {
                if (com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev.DevCosmeticsSubPage.INSTANCE.hasPendingChanges()) {
                    com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev.DevCosmeticsSubPage.INSTANCE.attemptTabSwitch(doSwitch);
                    return;
                }
            }

            // 2. Checa se tem alterações pendentes nos EFEITOS
            if (com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev.DevEffectsSubPage.INSTANCE != null) {
                if (com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev.DevEffectsSubPage.INSTANCE.hasPendingChanges()) {
                    com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev.DevEffectsSubPage.INSTANCE.attemptTabSwitch(doSwitch);
                    return;
                }
            }

            // 3. Checa se tem alterações pendentes nas CONFIGURAÇÕES DO SERVIDOR
            if (com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev.DevServerConfigSubPage.INSTANCE != null) {
                if (com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev.DevServerConfigSubPage.INSTANCE.hasPendingChanges()) {
                    com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev.DevServerConfigSubPage.INSTANCE.attemptTabSwitch(doSwitch);
                    return;
                }
            }

            // 4. Checa se tem alterações pendentes nos TIPOS (Types)
            if (com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev.DevTypesSubPage.INSTANCE != null) {
                if (com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev.DevTypesSubPage.INSTANCE.hasPendingChanges()) {
                    com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev.DevTypesSubPage.INSTANCE.attemptTabSwitch(doSwitch);
                    return;
                }
            }

            // 5. Checa se tem alterações pendentes nos SLOTS
            if (com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev.DevSlotsSubPage.INSTANCE != null) {
                if (com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev.DevSlotsSubPage.INSTANCE.hasPendingChanges()) {
                    com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev.DevSlotsSubPage.INSTANCE.attemptTabSwitch(doSwitch);
                    return;
                }
            }
        }

        doSwitch.run();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (this.minecraft == null || this.minecraft.player == null) return false;

        float targetScale = this.minecraft.getWindow().getHeight() / 450f;
        float scaleMultiplier = targetScale / (float) this.minecraft.getWindow().getGuiScale();

        int vMouseX = (int) (mouseX / scaleMultiplier);
        int vMouseY = (int) (mouseY / scaleMultiplier);
        int vWidth = (int) (this.minecraft.getWindow().getWidth() / targetScale);
        int vHeight = (int) (this.minecraft.getWindow().getHeight() / targetScale);

        if (this.equippedSlotsWidget.mouseClicked(vMouseX, vMouseY, vWidth, vHeight, this.currentTab == Tab.DEV)) return true;

        if (com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager.hasTarget()) {
            if (over(vMouseX, vMouseY, SIDEBAR_X + 2, SIDEBAR_Y + 2, SIDEBAR_W - 4, SIDEBAR_SNEAK_H)) {
                isPreviewSneaking = !isPreviewSneaking;
                if (com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev.DevCosmeticsSubPage.INSTANCE != null) {
                    com.f4xizzz.greatcosmetics.client.gui.pages.subpages.dev.DevCosmeticsSubPage.INSTANCE.syncGizmoPopupIfOpen();
                }
                playClick();
                return true;
            }
            if (over(vMouseX, vMouseY, SIDEBAR_X + 2, sidebarAlphaBtnTop(), SIDEBAR_W - 4, SIDEBAR_ALPHA_BTN_H)) {
                cycleCharacterAlpha();
                playClick();
                return true;
            }
            if (showGeoOffsetSection()) {
                int rowY = sidebarGeoRowY();
                int btnW = (SIDEBAR_W - 6) / 2;
                if (over(vMouseX, vMouseY, SIDEBAR_X + 2, rowY, btnW, SIDEBAR_GEO_BTN_H)) {
                    com.f4xizzz.greatcosmetics.client.GizmoDevConfig.geoGizmoYOffset -= GEO_Y_OFFSET_STEP;
                    com.f4xizzz.greatcosmetics.client.GizmoDevConfig.save();
                    isHoldingGeoOffsetButton = true;
                    geoOffsetHoldDir = -1;
                    geoOffsetHoldStartMs = System.currentTimeMillis();
                    geoOffsetLastStepMs = geoOffsetHoldStartMs;
                    playClick();
                    return true;
                }
                if (over(vMouseX, vMouseY, SIDEBAR_X + 4 + btnW, rowY, btnW, SIDEBAR_GEO_BTN_H)) {
                    com.f4xizzz.greatcosmetics.client.GizmoDevConfig.geoGizmoYOffset += GEO_Y_OFFSET_STEP;
                    com.f4xizzz.greatcosmetics.client.GizmoDevConfig.save();
                    isHoldingGeoOffsetButton = true;
                    geoOffsetHoldDir = 1;
                    geoOffsetHoldStartMs = System.currentTimeMillis();
                    geoOffsetLastStepMs = geoOffsetHoldStartMs;
                    playClick();
                    return true;
                }
            }
        }

        if (this.currentTab != null) {
            WardrobePage activePage = pages.get(this.currentTab);
            if (activePage != null && activePage.mouseClicked(vMouseX, vMouseY, button)) {
                return true;
            }
        }

        int startX = topBarX;
        if (over(vMouseX, vMouseY, startX, topBarY, (int)widthAcc, tabH)) {
            attemptTabSwitch(Tab.ACESSORIES, 1.0f, 3.5, 0.4f); return true;
        }
        startX += (int)widthAcc + tabSpacing;

        if (over(vMouseX, vMouseY, startX, topBarY, (int)widthCus, tabH)) {
            attemptTabSwitch(Tab.CUSTOMIZE, 1.0f, 3.5, 0.4f); return true;
        }
        startX += (int)widthCus + tabSpacing;

        if (over(vMouseX, vMouseY, startX, topBarY, (int)widthPrt, tabH)) {
            attemptTabSwitch(Tab.PARTY, 0.5f, 3.5, 0.6f); return true;
        }
        startX += (int)widthPrt + tabSpacing;

        if (over(vMouseX, vMouseY, startX, topBarY, (int)widthTag, tabH)) {
            attemptTabSwitch(Tab.TAGS, 1.6f, 1.8, 0.0f); return true;
        }
        startX += (int)widthTag + tabSpacing;

        if (hasDevPermission()) {
            if (over(vMouseX, vMouseY, startX, topBarY, (int)widthDev, tabH)) {
                // Pan maior que as outras abas (0.4f) de propósito: o painel do Dev Studio é bem
                // mais largo (até 320px, contra 180px das outras), e com o mesmo pan de sempre o
                // personagem ficava atrás/perto demais do painel.
                attemptTabSwitch(Tab.DEV, 1.0f, 3.5, 0.7f);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.currentTab != null) {
            WardrobePage activePage = pages.get(this.currentTab);
            if (activePage != null && activePage.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (this.currentTab != null) {
            WardrobePage activePage = pages.get(this.currentTab);
            if (activePage != null && activePage.charTyped(chr, modifiers)) {
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double deltaX, double deltaY) {
        if (this.minecraft == null || this.minecraft.getWindow() == null) return super.mouseDragged(mx, my, button, deltaX, deltaY);

        float targetScale = this.minecraft.getWindow().getHeight() / 450f;
        float scaleMultiplier = targetScale / (float) this.minecraft.getWindow().getGuiScale();

        double vMx = mx / scaleMultiplier;
        double vMy = my / scaleMultiplier;
        double vDeltaX = deltaX / scaleMultiplier;
        double vDeltaY = deltaY / scaleMultiplier;

        if (this.equippedSlotsWidget.mouseDragged(vMx, vMy, button, vDeltaX, vDeltaY)) return true;

        if (this.currentTab != null) {
            WardrobePage activePage = pages.get(this.currentTab);
            if (activePage != null && activePage.mouseDragged(vMx, vMy, button, vDeltaX, vDeltaY)) {
                return true;
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            targetYaw += (float) deltaX * 1.5f;
            targetPitch = Mth.clamp(targetPitch + (float) deltaY * 1.5f, -90f, 90f);
            return true;
        }
        return super.mouseDragged(mx, my, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double verticalAmount) {
        if (this.minecraft == null || this.minecraft.getWindow() == null) return super.mouseScrolled(mx, my, horizontalAmount, verticalAmount);

        float targetScale = this.minecraft.getWindow().getHeight() / 450f;
        float scaleMultiplier = targetScale / (float) this.minecraft.getWindow().getGuiScale();

        double vMx = mx / scaleMultiplier;
        double vMy = my / scaleMultiplier;

        if (this.equippedSlotsWidget.mouseScrolled(vMx, vMy, horizontalAmount, verticalAmount)) return true;

        if (this.currentTab != null) {
            WardrobePage activePage = pages.get(this.currentTab);
            if (activePage != null && activePage.mouseScrolled(vMx, vMy, horizontalAmount, verticalAmount)) {
                return true;
            }
        }
        if (isZoomLocked) return true;
        targetDistance -= verticalAmount * 0.5;
        targetDistance = Mth.clamp(targetDistance, 0.5, 7.0);
        return true;
    }

    private void playClick() {
        if (this.minecraft != null) {
            try {
                this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            } catch (Exception ignored) {}
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        com.f4xizzz.greatcosmetics.GreatCosmeticsClient.debugLog("Wardrobe3DScreen: closing.");
        isFreecamActive = false;
        isDevTabActive = false;
        com.f4xizzz.greatcosmetics.client.ClientCosmeticCache.isDevModeActive = false;
        isPartyTabActive = false; // <--- LIMPA AO FECHAR A TELA
        isTagsTabActive = false;
        isPreviewSneaking = false;
        characterAlpha = 1.0f;
        previewCosmeticId = null;
        com.f4xizzz.greatcosmetics.client.gui.pages.TagsPage.devPreviewEquippedId = null;
        // Mesmo motivo do reset em toggleTab() — fechar a tela inteira (Esc, clicar fora) é OUTRO
        // jeito de "sair" do editor de Cosméticos/Effects sem passar pelo botão "Voltar" deles.
        com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager.activePart = null;
        com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager.activeEffect = null;
        com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager.currentAxis = com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager.Axis.NONE;
        com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager.isDragging = false;
        com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager.onUpdate = null;
        if (this.minecraft != null) {
            this.minecraft.options.setCameraType(this.oldPerspective);
            this.minecraft.options.hideGui = this.oldHudHidden;
            this.minecraft.options.fov().set(this.oldFov);

            this.minecraft.options.keyShift.setDown(false);
        }

        // Passando pelo PC (botão da PartyPage fecha essa tela só pra abrir o PC por cima) não
        // conta como sair do wardrobe de verdade — não avisa o servidor, senão ele teleportava o
        // player de volta pro lugar de ANTES de entrar no studio, mesmo o wardrobe reabrindo
        // sozinho assim que o PC fechar (MinecraftClientMixin cuida do reabrir).
        if (!com.f4xizzz.greatcosmetics.client.gui.pages.PartyPage.waitingForPcToOpen) {
            ClientPlayNetworking.send(new CloseWardrobePayload());
        }
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.isHoldingGeoOffsetButton = false;
        this.equippedSlotsWidget.onMouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }
}