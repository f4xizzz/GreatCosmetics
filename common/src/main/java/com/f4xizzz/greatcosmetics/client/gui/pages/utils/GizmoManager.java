package com.f4xizzz.greatcosmetics.client.gui.pages.utils;

import com.f4xizzz.greatcosmetics.config.CosmeticData.CosmeticPart;
import com.f4xizzz.greatcosmetics.config.EffectData;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Gizmo 3D arrastável com o mouse, igual Blockbench/Axiom — clica numa seta/anel/alça
 * DESENHADA NA TELA e arrasta, em vez do sistema antigo (segurar X/Y/Z no teclado + arrastar em
 * qualquer direção).
 *
 * Funciona em duas metades, porque o desenho do gizmo acontece no MUNDO (dentro do mixin de
 * renderização, ArmorFeatureRendererMixin, que roda uma vez por frame ANTES da GUI) e o clique do
 * mouse acontece na TELA (Wardrobe3DScreen/DevCosmeticsSubPage, que roda DEPOIS) — não tem como
 * essas duas partes se falarem direto, então esse gerenciador estático é a ponte:
 *
 *  1. LADO MUNDO (updateScreenProjection, chamado pelo mixin todo frame que o gizmo é desenhado):
 *     pega a MESMA matriz de posição usada pra desenhar as linhas do gizmo, projeta a origem e
 *     cada ponta de eixo pro PIXEL de tela de verdade (não a coordenada "virtual" da GUI) — clip
 *     space -> NDC -> pixel, matemática padrão de projeção 3D.
 *
 *  2. LADO TELA (tryGrab/drag/release, chamado pelos mouseClicked/mouseDragged/mouseReleased da
 *     GUI): testa se o clique caiu perto de alguma ponta projetada (pega o eixo mais próximo
 *     dentro de uma margem de pixels), e durante o arraste projeta o movimento do mouse na
 *     DIREÇÃO 2D daquele eixo na tela (produto escalar) pra virar um delta 3D — é assim que
 *     "puxar a seta vermelha pra direita" vira "aumenta o offsetX", não importa de que ângulo a
 *     câmera está olhando.
 */
public class GizmoManager {
    public static CosmeticPart activePart = null;

    // Alvo alternativo pro MESMO gizmo, usado pelo editor de Effects (DevEffectsSubPage) — um
    // EffectData só tem offsetX/Y/Z (posição do emissor de partícula relativa ao jogador), sem
    // rotação/escala, então o modo fica travado em TRANSLATE sempre que este estiver preenchido.
    // Nunca os dois preenchidos ao mesmo tempo — activePart e activeEffect são mutuamente
    // exclusivos (ver DevCosmeticsSubPage/DevEffectsSubPage, cada um zera o outro ao abrir).
    public static EffectData activeEffect = null;

    public static boolean hasTarget() {
        return activePart != null || activeEffect != null;
    }

    public enum Mode { TRANSLATE, ROTATE, SCALE }
    public enum Axis { NONE, X, Y, Z }

    public static Mode currentMode = Mode.TRANSLATE;
    public static Axis currentAxis = Axis.NONE;

    public static boolean isDragging = false;
    public static double lastMouseX = 0;
    public static double lastMouseY = 0;

    // Eixo (e ponto ao longo dele) mais perto do mouse AGORA — atualizado todo frame pelo
    // Wardrobe3DScreen (não só ao clicar) — usado só pra desenhar a bolinha de "mira" no gizmo
    // (ver ArmorFeatureRendererMixin#greatcosmetics$drawHoverDot). NONE = mouse longe demais de
    // qualquer eixo, não desenha bolinha nenhuma.
    public static Axis hoveredAxis = Axis.NONE;
    private static float hoveredParam = 0f;

    public static Runnable onUpdate = null;

    // --- Projeção da última vez que o mixin desenhou o gizmo (pixels de framebuffer DE VERDADE,
    // não a coordenada "virtual" que a GUI usa — quem lê isso pra comparar com o mouse precisa
    // converter o mouse virtual pra pixel real primeiro, ver screenToFramebuffer). null = gizmo
    // não foi desenhado nesse frame (ex: parte fora da tela), não tem o que testar clique.
    private static Vector2f originScreen = null;
    private static Vector2f xAxisScreen = null;
    private static Vector2f yAxisScreen = null;
    private static Vector2f zAxisScreen = null;

    // Pontos do contorno do anel de rotação de cada eixo, já projetados pra pixel de tela (mesma
    // ideia dos *AxisScreen acima, só que uma polilinha fechada em vez de um único ponto — usado
    // pra desenhar o anel (ArmorFeatureRendererMixin) e testar clique nele em modo ROTATE.
    private static final int RING_SEGMENTS = 32;
    private static Vector2f[] xRingScreen = null;
    private static Vector2f[] yRingScreen = null;
    private static Vector2f[] zRingScreen = null;

    // Público porque o ArmorFeatureRendererMixin precisa desenhar as linhas com ESSE MESMO
    // comprimento — se os dois valores divergirem, a área clicável (calculada aqui a partir da
    // projeção) fica dessincronizada da seta desenhada na tela. Maior que antes (era 0.5) porque
    // 0.5 mundo ficava curto demais e as pontas ficavam soterradas dentro do próprio modelo do
    // jogador, quase impossível de mirar.
    public static final float AXIS_WORLD_LENGTH = 0.9f;
    // AXIS_WORLD_LENGTH também é usado pra converter pixel de mouse -> unidade de mundo em
    // handleDrag (quanto maior a seta, mais unidades de mundo por pixel arrastado) — aumentar ele
    // (de 0.5 pra 0.9, pra facilitar mirar/clicar) sem querer deixou o arraste ~1.8x mais sensível
    // também. Esse multiplicador separado corrige só a sensibilidade, sem mexer no tamanho/alcance
    // de clique da seta.
    private static final float TRANSLATE_SENSITIVITY = 0.45f;
    // Precisa ser IGUAL (ou menor) que HOVER_RADIUS_PX abaixo — a bolinha de mira mostrava um
    // alcance de 18px, mas o clique de verdade só aceitava até 10px, então às vezes a mira
    // "grudava" no anel/eixo num ponto que o clique ainda recusava (parecia funcionar mas não
    // funcionava). Aumentado também no geral: 10px era meio apertado pra mirar num anel fino.
    private static final float PICK_RADIUS_PX = 16.0f;

    // RenderLayer própria pro gizmo, IGUAL RenderLayer.getLines() só que com depth test sempre
    // "passa" — dá pra ver o gizmo através de blocos/parede, útil quando o offset da part empurra
    // ela pra dentro de uma parede/objeto. Vanilla não expõe essa combinação pronta (nem a do
    // getLines() normal dá pra reaproveitar trocando só o depth test de fora), então é montada na
    // mão com as peças internas do RenderPhase — exige greatcosmetics.accesswidener pra acessar os
    // campos protected (LINES_PROGRAM etc), já que não existe getter público pra eles.
    public static final RenderType GIZMO_LINES_NO_DEPTH = new RenderType.CompositeRenderType(
            "greatcosmetics_gizmo_lines_no_depth",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            256,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(RenderStateShard.DEFAULT_LINE)
                    .setLayeringState(RenderStateShard.NO_LAYERING)
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setDepthTestState(new RenderStateShard.DepthTestStateShard("greatcosmetics_always_depth", org.lwjgl.opengl.GL11.GL_ALWAYS))
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)
    );

    // Ângulo (graus) calculado no frame anterior de um arraste em modo ROTATE — usado pra tirar um
    // DELTA angular real a cada frame (ver computeScreenAngle), em vez do hack antigo que reusava a
    // matemática de "arrastar eixo reto" e multiplicava por 90.
    private static float lastAngle = 0f;

    // DIAGNÓSTICO TEMPORÁRIO — remover depois que o bug de clique for confirmado resolvido.
    // Só existe pra saber, de fora, SE o mixin (ArmorFeatureRendererMixin) está de fato chamando
    // updateScreenProjection todo frame — sem isso não dava pra distinguir "gizmo nunca é
    // desenhado" de "gizmo é desenhado mas o clique erra o alvo".
    private static long lastProjectionUpdateMs = 0;
    private static float lastOriginClipW = Float.NaN;

    // Preenchido pelo tryGrab a cada clique (sucesso ou falha) — mostrado na barrinha (ver
    // Wardrobe3DScreen#renderGizmoSidebar), não no chat: o HUD/chat fica escondido com a
    // Wardrobe3DScreen aberta, uma mensagem de chat nunca apareceria pro jogador ver.
    public static String lastClickDebug = "(hasn't clicked yet)";

    public static String debugStatus() {
        long since = lastProjectionUpdateMs == 0 ? -1 : System.currentTimeMillis() - lastProjectionUpdateMs;
        String offsets = activeEffect != null
                ? String.format(java.util.Locale.US, "off=%.2f,%.2f,%.2f (effect)", activeEffect.offsetX, activeEffect.offsetY, activeEffect.offsetZ)
                : activePart != null
                ? String.format(java.util.Locale.US, "off=%.2f,%.2f,%.2f", activePart.offsetX, activePart.offsetY, activePart.offsetZ)
                : "off=N/A";
        return "part=" + (hasTarget() ? "YES" : "NO")
                + " origin=" + (originScreen != null ? "YES" : "NO")
                + " clipW=" + (Float.isNaN(lastOriginClipW) ? "N/A" : String.format(java.util.Locale.US, "%.3f", lastOriginClipW))
                + " proj_ago=" + (since < 0 ? "NEVER" : since + "ms")
                + " " + offsets
                + " mode=" + currentMode;
    }

    /** Chamado pelo ArmorFeatureRendererMixin logo depois de desenhar as linhas do gizmo, com a
     *  MESMA matriz de posição (já é a matriz "model-view" — o WorldRenderer já aplicou a posição
     *  e rotação da câmera nela antes de chamar o render de entidades, só falta a projeção). */
    public static void updateScreenProjection(Matrix4f modelViewPosition) {
        lastProjectionUpdateMs = System.currentTimeMillis();

        // BUG ENCONTRADO (confirmado via diagnóstico em jogo: clique e origem calculada batiam
        // ~800px de diferença): "modelViewPosition" (a matriz que o mixin captura em
        // matrices.peek()) é só TRANSLAÇÃO relativa à câmera — o mundo/entidades renderizam com
        // eixos alinhados ao MUNDO, não rotacionados pra encarar a câmera. A ROTAÇÃO da câmera é
        // aplicada à parte, então RenderSystem.getProjectionMatrix() sozinho NUNCA incluía pitch/
        // yaw nenhum — a "projeção" inteira ficava sem a rotação da câmera, dando uma posição de
        // tela completamente errada. Confirma o mesmo padrão já usado em PlayerEntityRendererMixin
        // (dispatcher.getRotation() pro nametag encarar a câmera) — aqui precisa do INVERSO dessa
        // rotação (view matrix = inverso da orientação da câmera no mundo).
        org.joml.Quaternionf viewRotation = new org.joml.Quaternionf(
                Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation()).invert();
        Matrix4f mvp = new Matrix4f(RenderSystem.getProjectionMatrix())
                .rotate(viewRotation)
                .mul(modelViewPosition);

        // DIAGNÓSTICO TEMPORÁRIO — guarda o W (espaço clip) da origem antes de projectToScreen
        // decidir null/não-null, só pra saber SE o cálculo tá pegando um valor perto de zero
        // (câmera/projeção meio certa, ponto raspando o plano) ou um número absurdo (matriz
        // errada de verdade).
        lastOriginClipW = new Vector4f(0, 0, 0, 1.0f).mul(mvp).w;

        originScreen = projectToScreen(mvp, 0, 0, 0);
        xAxisScreen = projectToScreen(mvp, AXIS_WORLD_LENGTH, 0, 0);
        yAxisScreen = projectToScreen(mvp, 0, AXIS_WORLD_LENGTH, 0);
        zAxisScreen = projectToScreen(mvp, 0, 0, AXIS_WORLD_LENGTH);

        xRingScreen = projectRing(mvp, Axis.X);
        yRingScreen = projectRing(mvp, Axis.Y);
        zRingScreen = projectRing(mvp, Axis.Z);
    }

    /** Ponto (espaço local do bone, mesmo de offsetX/Y/Z) na borda do anel de rotação do eixo
     *  dado. Único lugar onde essa fórmula existe — tanto o desenho do anel (mixin) quanto a
     *  projeção pra pixel abaixo (projectRing) chamam ESTA função, então anel desenhado e área
     *  clicável nunca podem divergir um do outro. */
    public static Vector3f ringPoint(Axis axis, float radius, float angleRad) {
        float c = (float) Math.cos(angleRad) * radius;
        float s = (float) Math.sin(angleRad) * radius;
        return switch (axis) {
            case X -> new Vector3f(0f, c, s);
            case Y -> new Vector3f(s, 0f, c);
            case Z -> new Vector3f(c, s, 0f);
            case NONE -> new Vector3f();
        };
    }

    private static Vector2f[] projectRing(Matrix4f mvp, Axis axis) {
        Vector2f[] pts = new Vector2f[RING_SEGMENTS + 1];
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            float angle = (float) (i * Math.PI * 2.0 / RING_SEGMENTS);
            Vector3f p = ringPoint(axis, AXIS_WORLD_LENGTH, angle);
            pts[i] = projectToScreen(mvp, p.x, p.y, p.z);
        }
        return pts;
    }

    /** null se o ponto ficou atrás da câmera (w <= 0) — não dá pra testar clique nesse caso. */
    private static Vector2f projectToScreen(Matrix4f mvp, float x, float y, float z) {
        Vector4f clip = new Vector4f(x, y, z, 1.0f).mul(mvp);
        if (clip.w <= 0.00001f) return null;

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;

        var window = Minecraft.getInstance().getWindow();
        float px = (ndcX * 0.5f + 0.5f) * window.getWidth();
        float py = (1.0f - (ndcY * 0.5f + 0.5f)) * window.getHeight();
        return new Vector2f(px, py);
    }

    /** Mouse da GUI vem em coordenada "virtual" (a tela inteira reescalada pra caber uma altura
     *  fixa de 450px, ver Wardrobe3DScreen) — precisa desfazer isso pra comparar com os pontos
     *  projetados acima, que estão em pixel de framebuffer de verdade. */
    private static Vector2f virtualMouseToFramebuffer(double virtualX, double virtualY) {
        var window = Minecraft.getInstance().getWindow();
        float scaleMultiplier = (window.getHeight() / 450f) / (float) window.getGuiScale();
        // As coordenadas de mouseClicked/mouseDragged do vanilla já vêm em "scaled coordinates"
        // (guiScaleFactor já aplicado) — Wardrobe3DScreen divide de novo por scaleMultiplier pra
        // chegar na coordenada "virtual" de 450px. Desfazer os dois passos volta pro pixel real.
        double realX = virtualX * scaleMultiplier * window.getGuiScale();
        double realY = virtualY * scaleMultiplier * window.getGuiScale();
        return new Vector2f((float) realX, (float) realY);
    }

    /** Chamado no mouseClicked da GUI (coordenadas virtuais). Retorna true se agarrou um eixo —
     *  quem chama deve considerar o clique "consumido" nesse caso (não deixar passar pra rotação
     *  de câmera etc). */
    public static boolean tryGrab(double virtualMouseX, double virtualMouseY) {
        if (!hasTarget()) {
            lastClickDebug = "tryGrab: no active target (neither Part nor Effect selected)";
            return false;
        }
        // Effect não tem rotação/escala — trava em TRANSLATE mesmo que o modo tenha ficado em
        // ROTATE/SCALE de uma sessão anterior (editando cosméticos) antes de abrir um Effect.
        if (activeEffect != null && currentMode != Mode.TRANSLATE) currentMode = Mode.TRANSLATE;
        if (originScreen == null) {
            lastClickDebug = "tryGrab: originScreen == null (gizmo not projected this frame)";
            return false;
        }

        Vector2f mouse = virtualMouseToFramebuffer(virtualMouseX, virtualMouseY);

        Axis best = Axis.NONE;
        float bestDist = PICK_RADIUS_PX;
        float dx, dy, dz;

        if (currentMode == Mode.ROTATE) {
            // Em modo ROTATE o alvo clicável é o anel (a linha reta nem é desenhada nesse modo,
            // ver mixin), não o segmento origem->ponta.
            dx = distancePointToPolyline(mouse, xRingScreen);
            if (dx < bestDist) { bestDist = dx; best = Axis.X; }
            dy = distancePointToPolyline(mouse, yRingScreen);
            if (dy < bestDist) { bestDist = dy; best = Axis.Y; }
            dz = distancePointToPolyline(mouse, zRingScreen);
            if (dz < bestDist) { bestDist = dz; best = Axis.Z; }
        } else {
            dx = distancePointToSegment(mouse, originScreen, xAxisScreen);
            if (xAxisScreen != null && dx < bestDist) { bestDist = dx; best = Axis.X; }
            dy = distancePointToSegment(mouse, originScreen, yAxisScreen);
            if (yAxisScreen != null && dy < bestDist) { bestDist = dy; best = Axis.Y; }
            dz = distancePointToSegment(mouse, originScreen, zAxisScreen);
            if (zAxisScreen != null && dz < bestDist) { bestDist = dz; best = Axis.Z; }
        }

        // DIAGNÓSTICO TEMPORÁRIO — mostra a distância de verdade (não travada no raio de 10px) até
        // cada eixo, pra saber se é "quase acertou" (uns 20-30px) ou "muito longe" (centenas de
        // px, indicando que o gizmo está sendo desenhado num lugar bem diferente de onde a gente
        // acha que ele está). Guardado num campo (não manda no chat) porque o HUD/chat fica
        // escondido com a Wardrobe3DScreen aberta — ver Wardrobe3DScreen#renderGizmoSidebar.
        lastClickDebug = String.format(java.util.Locale.US,
                "click=(%.0f,%.0f) origin=(%.0f,%.0f) distX=%.0f distY=%.0f distZ=%.0f -> %s",
                mouse.x, mouse.y, originScreen.x, originScreen.y, dx, dy, dz,
                best == Axis.NONE ? "NONE (outside the " + (int) PICK_RADIUS_PX + "px radius)" : ("GRABBED " + best));

        if (best == Axis.NONE) return false;

        currentAxis = best;
        isDragging = true;
        lastMouseX = virtualMouseX;
        lastMouseY = virtualMouseY;

        if (currentMode == Mode.ROTATE) {
            float angle = computeScreenAngle(best, virtualMouseX, virtualMouseY);
            lastAngle = Float.isNaN(angle) ? 0f : angle;
        }
        return true;
    }

    /** Ângulo (graus) do mouse ao redor do CENTRO do gizmo NA TELA (2D puro, em volta de
     *  originScreen) — não tenta mais calcular o ângulo 3D "exato" (nem por decomposição numa base
     *  de outros eixos, nem por raycast contra o plano do anel, nem pelo ponto mais próximo na
     *  polilinha do anel — as três abordagens anteriores degeneravam/ficavam instáveis em algum
     *  ângulo de câmera ou distância, travando/tremendo o arraste). Ângulo de tela em volta de um
     *  ponto fixo não tem NENHUMA singularidade a não ser o próprio centro (já protegido abaixo) —
     *  sempre suave, qualquer ângulo de câmera, qualquer distância. Não é geometricamente "exato"
     *  pro ângulo 3D real do eixo, mas só usamos a DIFERENÇA entre dois frames consecutivos
     *  (deltaDeg em handleDrag) — pra isso, "girar o mouse ao redor do gizmo na tela" já é
     *  exatamente a interação que o usuário espera, eixo nenhum precisa entrar na conta.
     *  Retorna NaN se a mira estiver perto demais do centro (direção fica ruído). */
    private static float computeScreenAngle(Axis axis, double virtualMouseX, double virtualMouseY) {
        if (originScreen == null) return Float.NaN;

        Vector2f mouse = virtualMouseToFramebuffer(virtualMouseX, virtualMouseY);
        float dx = mouse.x - originScreen.x, dy = mouse.y - originScreen.y;
        if (dx * dx + dy * dy < 20f * 20f) return Float.NaN;

        return (float) Math.toDegrees(Math.atan2(dy, dx));
    }

    /** [distância, t] — t em [0,1] é onde ao longo do segmento a-b cai o ponto mais perto de p.
     *  distancePointToSegment (usado pelo tryGrab) só quer o [0]; a bolinha de mira (updateHover)
     *  também precisa do [1] pra saber ONDE desenhar, não só SE está perto o bastante. */
    private static float[] closestOnSegment(Vector2f p, Vector2f a, Vector2f b) {
        if (a == null || b == null) return new float[]{Float.MAX_VALUE, 0f};
        float abx = b.x - a.x, aby = b.y - a.y;
        float lenSq = abx * abx + aby * aby;
        float t = lenSq < 0.0001f ? 0f : ((p.x - a.x) * abx + (p.y - a.y) * aby) / lenSq;
        t = Math.max(0f, Math.min(1f, t));
        float projX = a.x + abx * t, projY = a.y + aby * t;
        float dx = p.x - projX, dy = p.y - projY;
        return new float[]{(float) Math.sqrt(dx * dx + dy * dy), t};
    }

    private static float distancePointToSegment(Vector2f p, Vector2f a, Vector2f b) {
        return closestOnSegment(p, a, b)[0];
    }

    private static float distancePointToPolyline(Vector2f p, Vector2f[] points) {
        if (points == null) return Float.MAX_VALUE;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < points.length - 1; i++) {
            float d = distancePointToSegment(p, points[i], points[i + 1]);
            if (d < best) best = d;
        }
        return best;
    }

    /** Igual distancePointToPolyline, só que também devolve o ÂNGULO (radianos) do ponto do anel
     *  mais perto de p — [distância, ângulo]. */
    private static float[] closestOnRingPolyline(Vector2f p, Vector2f[] points) {
        if (points == null) return new float[]{Float.MAX_VALUE, 0f};
        float best = Float.MAX_VALUE;
        float bestAngle = 0f;
        float angleStep = (float) (Math.PI * 2.0 / RING_SEGMENTS);
        for (int i = 0; i < points.length - 1; i++) {
            float[] r = closestOnSegment(p, points[i], points[i + 1]);
            if (r[0] < best) {
                best = r[0];
                bestAngle = (i + r[1]) * angleStep;
            }
        }
        return new float[]{best, bestAngle};
    }

    /** Chamado todo frame pelo Wardrobe3DScreen (não só ao clicar) enquanto uma Part está ativa —
     *  acha o eixo/ponto mais perto do mouse AGORA pra desenhar a bolinha de mira. Durante um
     *  arraste, trava no eixo já agarrado (não teria sentido "achar mais perto" outro eixo no meio
     *  do arraste) e só recalcula ONDE ao longo dele. */
    public static void updateHover(double virtualMouseX, double virtualMouseY) {
        if (!hasTarget() || originScreen == null) { hoveredAxis = Axis.NONE; return; }

        Vector2f mouse = virtualMouseToFramebuffer(virtualMouseX, virtualMouseY);

        if (isDragging) {
            hoveredAxis = currentAxis;
            hoveredParam = currentMode == Mode.ROTATE
                    ? closestOnRingPolyline(mouse, greatcosmetics$ringFor(currentAxis))[1]
                    : closestOnSegment(mouse, originScreen, greatcosmetics$axisFor(currentAxis))[1];
            return;
        }

        Axis best = Axis.NONE;
        float bestDist = PICK_RADIUS_PX;
        float bestParam = 0f;

        for (Axis axis : new Axis[]{Axis.X, Axis.Y, Axis.Z}) {
            float[] r = currentMode == Mode.ROTATE
                    ? closestOnRingPolyline(mouse, greatcosmetics$ringFor(axis))
                    : closestOnSegment(mouse, originScreen, greatcosmetics$axisFor(axis));
            if (r[0] < bestDist) { bestDist = r[0]; best = axis; bestParam = r[1]; }
        }

        hoveredAxis = best;
        hoveredParam = bestParam;
    }

    private static Vector2f greatcosmetics$axisFor(Axis axis) {
        return switch (axis) {
            case X -> xAxisScreen;
            case Y -> yAxisScreen;
            case Z -> zAxisScreen;
            case NONE -> null;
        };
    }

    private static Vector2f[] greatcosmetics$ringFor(Axis axis) {
        return switch (axis) {
            case X -> xRingScreen;
            case Y -> yRingScreen;
            case Z -> zRingScreen;
            case NONE -> null;
        };
    }

    /** Ponto local (mesmo espaço de offsetX/Y/Z) onde desenhar a bolinha de mira, ou null se o
     *  mouse não está perto de nenhum eixo/anel agora — ver updateHover. */
    public static Vector3f getHoverLocalPoint() {
        if (hoveredAxis == Axis.NONE) return null;
        if (currentMode == Mode.ROTATE) {
            return ringPoint(hoveredAxis, AXIS_WORLD_LENGTH, hoveredParam);
        }
        float t = hoveredParam * AXIS_WORLD_LENGTH;
        return switch (hoveredAxis) {
            case X -> new Vector3f(t, 0f, 0f);
            case Y -> new Vector3f(0f, t, 0f);
            case Z -> new Vector3f(0f, 0f, t);
            case NONE -> null;
        };
    }

    /** Chamado no mouseDragged da GUI (coordenadas virtuais) enquanto isDragging == true. Projeta
     *  o movimento do mouse na direção 2D do eixo agarrado na tela — puxar a seta pra onde ela
     *  aponta na tela sempre aumenta o valor, não importa o ângulo da câmera. */
    public static void handleDrag(double virtualMouseX, double virtualMouseY, boolean isSneaking) {
        if (!hasTarget() || currentAxis == Axis.NONE || !isDragging) return;
        if (activeEffect != null && currentMode != Mode.TRANSLATE) currentMode = Mode.TRANSLATE;

        // Segurando Shift no teclado (não confundir com "isSneaking" acima, que é o toggle
        // "Visualizar Posição: SNEAK" da GUI — outra coisa): aplica o MESMO delta desse arraste
        // nos 3 eixos de uma vez, não importa qual seta/anel foi agarrado.
        boolean allAxes = net.minecraft.client.gui.screens.Screen.hasShiftDown();

        if (currentMode == Mode.ROTATE) {
            float angle = computeScreenAngle(currentAxis, virtualMouseX, virtualMouseY);
            if (!Float.isNaN(angle)) {
                float deltaDeg = Mth.wrapDegrees(angle - lastAngle);
                // Trava o quanto pode girar NUM ÚNICO FRAME — só de proteção contra algum soluço
                // raro no cálculo (não pra frear arraste normal: com o cálculo por polilinha do
                // anel, de novo, precisão não é mais o problema — um valor baixo aqui só deixava o
                // arraste "preso"/lento por trás do cursor. 90°/frame (~16ms) já é bem mais rápido
                // que qualquer arraste humano de propósito, só pega os casos de verdade anômalos.
                deltaDeg = Mth.clamp(deltaDeg, -90f, 90f);
                if (!isSneaking) {
                    if (allAxes || currentAxis == Axis.X) activePart.rotationX += deltaDeg;
                    if (allAxes || currentAxis == Axis.Y) activePart.rotationY += deltaDeg;
                    if (allAxes || currentAxis == Axis.Z) activePart.rotationZ += deltaDeg;
                } else {
                    if (allAxes || currentAxis == Axis.X) activePart.shiftRotationX += deltaDeg;
                    if (allAxes || currentAxis == Axis.Y) activePart.shiftRotationY += deltaDeg;
                    if (allAxes || currentAxis == Axis.Z) activePart.shiftRotationZ += deltaDeg;
                }
                lastAngle = angle;
                // BUG (2026-09): onUpdate.run() (marca "tem alteração não salva") rodava até
                // quando deltaDeg saía zero (mouse mexeu um pixel dentro do mesmo bucket de
                // ângulo — muito comum num "clique parado", já que segurar o botão sem soltar já
                // dispara mouseDragged com deslocamento mínimo/nenhum) — o usuário via o popup de
                // "salvar alterações?" só de ter clicado numa seta, sem girar nada de verdade.
                if (deltaDeg != 0f && onUpdate != null) onUpdate.run();
            }
            lastMouseX = virtualMouseX;
            lastMouseY = virtualMouseY;
            return;
        }

        Vector2f axisScreenEnd = switch (currentAxis) {
            case X -> xAxisScreen;
            case Y -> yAxisScreen;
            case Z -> zAxisScreen;
            case NONE -> null;
        };

        float delta;
        if (axisScreenEnd == null || originScreen == null) {
            // Fallback (gizmo não visível nesse frame, ex: saiu da tela durante o arraste): volta
            // pro comportamento antigo, melhor que travar o arraste no meio.
            double deltaX = virtualMouseX - lastMouseX;
            double deltaY = virtualMouseY - lastMouseY;
            delta = (float) (deltaX - deltaY) * 0.005f * TRANSLATE_SENSITIVITY;
        } else {
            Vector2f dir = new Vector2f(axisScreenEnd).sub(originScreen);
            float dirLen = dir.length();
            if (dirLen < 0.5f) {
                delta = 0f;
            } else {
                dir.div(dirLen);
                Vector2f mouseNow = virtualMouseToFramebuffer(virtualMouseX, virtualMouseY);
                Vector2f mouseBefore = virtualMouseToFramebuffer(lastMouseX, lastMouseY);
                float mouseDeltaPx = (mouseNow.x - mouseBefore.x) * dir.x + (mouseNow.y - mouseBefore.y) * dir.y;
                // Pixels de tela -> unidade de mundo: a seta tem AXIS_WORLD_LENGTH de comprimento
                // e ocupa dirLen pixels na tela nesse frame, então essa razão já compensa zoom/
                // distância da câmera sozinha (perto da câmera a seta fica grande na tela = precisa
                // de mais pixels pra mover a mesma distância; longe, o oposto).
                delta = mouseDeltaPx * (AXIS_WORLD_LENGTH / dirLen) * TRANSLATE_SENSITIVITY;
            }
        }

        if (currentMode == Mode.SCALE) delta *= 2f;

        switch (currentMode) {
            case TRANSLATE:
                if (activeEffect != null) {
                    // Effect não tem variante "shift" (agachado) — sempre grava no offset normal.
                    // Os TRÊS eixos com sinal invertido (-= em vez de +=) — ver o comentário no
                    // bloco de desenho do gizmo em ArmorFeatureRendererMixin (translate usa
                    // -offsetX/-offsetY/-offsetZ pelo mesmo motivo). Testado ao vivo: só invertendo
                    // X (primeira tentativa) Y e Z continuavam espelhados — os três precisam.
                    if (allAxes || currentAxis == Axis.X) activeEffect.offsetX -= delta;
                    if (allAxes || currentAxis == Axis.Y) activeEffect.offsetY -= delta;
                    if (allAxes || currentAxis == Axis.Z) activeEffect.offsetZ -= delta;
                } else if (!isSneaking) {
                    if (allAxes || currentAxis == Axis.X) activePart.offsetX += delta;
                    if (allAxes || currentAxis == Axis.Y) activePart.offsetY += delta;
                    if (allAxes || currentAxis == Axis.Z) activePart.offsetZ += delta;
                } else {
                    if (allAxes || currentAxis == Axis.X) activePart.shiftOffsetX += delta;
                    if (allAxes || currentAxis == Axis.Y) activePart.shiftOffsetY += delta;
                    if (allAxes || currentAxis == Axis.Z) activePart.shiftOffsetZ += delta;
                }
                break;
            case SCALE:
                if (allAxes || currentAxis == Axis.X) activePart.scaleX += delta;
                if (allAxes || currentAxis == Axis.Y) activePart.scaleY += delta;
                if (allAxes || currentAxis == Axis.Z) activePart.scaleZ += delta;
                break;
            case ROTATE:
                // Inalcançável: o modo ROTATE retorna mais acima (ver computeScreenAngle), antes
                // de chegar em qualquer código deste bloco.
                break;
        }

        // Mesmo bug do bloco ROTATE acima: delta pode sair 0 (dirLen < 0.5f logo ali em cima, ou
        // um mouseDragged com deslocamento residual/nenhum — comum num clique "parado" que o
        // Minecraft ainda assim reporta como drag) — sem essa checagem, um clique único na seta
        // já marcava "tem alteração não salva" mesmo o offset/escala não tendo mudado NADA.
        if (delta != 0f && onUpdate != null) onUpdate.run();

        lastMouseX = virtualMouseX;
        lastMouseY = virtualMouseY;
    }

    /** Chamado no mouseReleased da GUI (ou quando o botão do mouse solta, hoje detectado por
     *  polling GLFW igual o resto da GUI já faz). */
    public static void release() {
        isDragging = false;
        currentAxis = Axis.NONE;
        lastAngle = 0f;
    }
}
