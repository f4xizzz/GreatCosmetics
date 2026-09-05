package com.f4xizzz.greatcosmetics.config;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.annotations.SerializedName;

public class CosmeticData {
    public transient String id;

    /**
     * Se setado, esse cosmético é uma ARMADURA CONVERTIDA (ver ArmorCosmeticsConfig): o ícone e o
     * render usam o item real (ex: "minecraft:diamond_helmet") em vez do ghost carved_pumpkin —
     * renderizada de verdade dobrada no corpo via ArmorFeatureRenderer nativo, mas nunca ocupa um
     * slot de armadura vanilla de verdade (só o slot virtual normal dos cosméticos). Nunca é lido
     * do cosmeticsconfig.conf — sempre sintetizado em runtime a partir do armor_cosmetics.json.
     */
    public transient String realItemId = null;

    public int cmd = 0;
    public transient int resolvedIconCmd = 0;

    /** Nome do arquivo de ícone (em textures/icons/, sem ".png") a usar em vez do id do cosmético.
     *  Vazio/null = comportamento de sempre (procura "textures/icons/&lt;id&gt;.png"). Serve pra
     *  reaproveitar o mesmo arquivo de ícone entre vários cosméticos (ex: variações de cor de uma
     *  mesma peça) sem precisar duplicar a textura com nomes diferentes. */
    public String iconId = "";

    public RenderSlot render = RenderSlot.HEAD;
    public VirtualSlot slot = VirtualSlot.HEAD;
    public String type = "default";

    public List<CosmeticPart> parts = new ArrayList<>();

    public int maxDurability = 0;

    // --- STATUS DE COMBATE ---
    public int armor = 0;
    public double toughness = 0.0;

    public List<String> effectVisual = new ArrayList<>();
    public List<String> flyParticle = new ArrayList<>();

    @SerializedName(value = "DisplayName", alternate = {"displayName", "displayname"})
    public String DisplayName = null;
    public String permission = "";

    public LureStats lure = new LureStats();

    public static class LureStats {
        public boolean enabled = false;
        public double lureShinyMultiplier = 0.0;
        public int lureIV = 0;
        public double lureChanceIV = 0.0;
        public String lureTYPE = "";
        public double lureUltraRAREMultiplier = 0.0;
        public double lureHiddenAbilityMultiplier = 0.0;
        public double lureExpAllMultiplier = 0.0;
        public double lureAmizadeMultiplier = 0.0;
        public double lurePescaShiny = 0.0;
        public double lurePescaUltraRare = 0.0;
        public double lurePescaIvChance = 0.0;
        public int lurePescaIv = 0;
        public double lurePescaVelocidade = 0.0;
        public double lureEXP = 0.0;
        public double lureEV = 0.0;
        public double lureChanceDeCaptura = 0.0;
        public double lureDePesca = 0.0;
    }

    // ==========================================
    // SISTEMA DE SONS PERSONALIZADOS
    // ==========================================
    public CosmeticSounds sounds = new CosmeticSounds();

    public static class CosmeticSounds {
        public String equipSound = "";
        public String unequipSound = "";
        public String walkSound = "";
        public String flySound = "";
        public String shiftSound = "";
        public String backpackSound = "";
        public String idleSound = ""; // <--- NOVO AQUI

        // Volume/pitch por som — só os que de fato são tocados em algum lugar (walk/fly/shift/
        // backpack/idle; equip/unequip ainda não têm nenhum código que os toca, então não
        // ganharam volume/pitch próprio pra não ser configuração morta).
        public double walkVolume = 1.0;
        public double walkPitch = 1.0;
        public double flyVolume = 1.0;
        public double flyPitch = 1.0;
        public double shiftVolume = 1.0;
        public double shiftPitch = 1.0;
        public double backpackVolume = 1.0;
        public double backpackPitch = 1.0;
        public double idleVolume = 1.0;
        public double idlePitch = 1.0;
    }

    public boolean isBackpack = false;
    public int backpackRows = 3;
    public String backpackDisplayName = "<dark_gray>Traveler's Backpack";
    public boolean EnableFly = false;
    public boolean AutoFeed = false;
    public List<String> effects = new ArrayList<>();

    // --- VELOCIDADE (1.0 = normal, valores >1 aceleram, <1 desaceleram) ---
    public double flySpeedMultiplier = 1.0;
    public double groundSpeedMultiplier = 1.0;
    public double swimSpeedMultiplier = 1.0;

    public enum Anchor { HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }
    public enum RenderSlot { HEAD, CHEST, LEGS, FEET }
    public enum VirtualSlot { HEAD, NECK, CHEST, BACK, WAIST, LEGS, FEET, FACE, HAND }

    public CosmeticData() {}

    public static class CosmeticPart {
        public String customModelData_or_ID = "";
        public transient int resolvedCmd = 0;
        public Anchor anchor;

        /** Se setado, essa parte desenha um modelo 3D de verdade via GeckoLib (ver
         *  com.f4xizzz.greatcosmetics.geckolib.BuiltinGeoModels) em vez do ícone chapado — resolvido
         *  pro MESMO resolvedCmd acima ao carregar o config, então tudo mais (offset/rotação/escala,
         *  sincronização pro client) continua igual. Vazio = ícone chapado, comportamento de sempre. */
        public String geoModelId = "";

        /** Quando true, "customModelData_or_ID" (modelo vanilla/ícone) e "geoModelId" (GeckoLib)
         *  passam a ser tratados como o CAMINHO RELATIVO COMPLETO do arquivo (ex: "sas/cigarro" para
         *  assets/&lt;qualquer namespace carregado&gt;/models/sas/cigarro.json), buscado por caminho
         *  EXATO em qualquer namespace — em vez do modo antigo (só o nome do arquivo, sem pasta,
         *  restrito ao namespace "greatcosmetics" no caso do modelo vanilla). */
        public boolean useExactPath = false;

        public float offsetX = 0.0F;
        public float offsetY = 0.0F;
        public float offsetZ = 0.0F;
        public float rotationX = 0.0F;
        public float rotationY = 0.0F;
        public float rotationZ = 0.0F;

        public float scaleX = 1.0F;
        public float scaleY = 1.0F;
        public float scaleZ = 1.0F;

        public float shiftOffsetX = 0.0F;
        public float shiftOffsetY = 0.0F;
        public float shiftOffsetZ = 0.0F;
        public float shiftRotationX = 0.0F;
        public float shiftRotationY = 0.0F;
        public float shiftRotationZ = 0.0F;

        public CosmeticPart(Anchor anchor) {
            this.anchor = anchor;
        }
    }

    // ==========================================
    // SISTEMA DE NOME COM MINIMESSAGE
    // ==========================================
    public String getFormattedName() {
        if (this.DisplayName != null && !this.DisplayName.trim().isEmpty() && !this.DisplayName.equals("&dCosmético Desconhecido") && !this.DisplayName.equals("<light_purple>Cosmético Desconhecido")) {
            return com.f4xizzz.greatcosmetics.util.TextUtils.parseToString(this.DisplayName);
        }
        if (this.id != null && !this.id.trim().isEmpty()) {
            return com.f4xizzz.greatcosmetics.util.TextUtils.parseToString("<light_purple>" + this.id.substring(0, 1).toUpperCase() + this.id.substring(1));
        }
        return com.f4xizzz.greatcosmetics.util.TextUtils.parseToString(com.f4xizzz.greatcosmetics.config.LangConfig.raw("items.name_fallback"));
    }

    /** Igual getFormattedName(), mas devolve um Text de verdade (com Style/TextColor real) em vez
     *  de uma String com códigos §. É a ÚNICA forma de mostrar hex de verdade na GUI — o
     *  TextRenderer do Minecraft, quando desenha uma String crua, só reconhece os 16 códigos de
     *  cor legados (Formatting), nunca hex, não importa que truque de escape a String use. Usar
     *  esse método + as sobrecargas de drawTextWithShadow/drawCenteredTextWithShadow que recebem
     *  Text (não String) em vez do de sempre. */
    public net.minecraft.text.Text getFormattedNameText(net.minecraft.registry.RegistryWrapper.WrapperLookup registries) {
        return com.f4xizzz.greatcosmetics.util.TextUtils.parseToText(resolveRawDisplayName(), registries);
    }

    /** Mesma resolução de getFormattedNameText() (DisplayName -> id capitalizado -> "Cosmético"),
     *  sem passar pelo MiniMessage ainda — usado por getFormattedNameLines() abaixo pra poder
     *  quebrar em "\n" ANTES de parsear cada pedaço (MiniMessage não entende "\n" como quebra de
     *  linha, só formata tags). */
    private String resolveRawDisplayName() {
        if (this.DisplayName != null && !this.DisplayName.trim().isEmpty() && !this.DisplayName.equals("&dCosmético Desconhecido") && !this.DisplayName.equals("<light_purple>Cosmético Desconhecido")) {
            return this.DisplayName;
        }
        if (this.id != null && !this.id.trim().isEmpty()) {
            return "<light_purple>" + this.id.substring(0, 1).toUpperCase() + this.id.substring(1);
        }
        return com.f4xizzz.greatcosmetics.config.LangConfig.raw("items.name_fallback");
    }

    /** Igual getFormattedNameText(), mas quebra "\n" literal (as duas letras — nenhum campo do Dev
     *  Studio é multi-linha de verdade, então é a única forma de indicar quebra de linha numa
     *  caixinha de texto normal) em várias linhas ANTES de mandar cada pedaço pro MiniMessage,
     *  devolvendo um Text por linha. Usado em qualquer render que já colava esse nome no rodapé de
     *  um ícone/item (grid do Dev Studio, Acessórios) — ver MultilineMarqueeLabel, que empilha essas
     *  linhas ancoradas por baixo e faz o marquee de cada uma que não couber sozinha. */
    public java.util.List<net.minecraft.text.Text> getFormattedNameLines(net.minecraft.registry.RegistryWrapper.WrapperLookup registries) {
        // "\\\\n|\n" — dois padrões, não só o "\n" literal (as duas letras) digitado no Dev
        // Studio: um DisplayName editado à mão direto no .json também pode ter uma quebra de linha
        // DE VERDADE (byte 0x0A), se quem editou usou o escape "\n" do PRÓPRIO JSON (o Gson
        // interpreta isso como caractere real, não como as duas letras) em vez de "\\n" (escapado).
        String[] parts = resolveRawDisplayName().split("\\\\n|\n", -1);
        java.util.List<net.minecraft.text.Text> lines = new java.util.ArrayList<>(parts.length);
        for (String part : parts) {
            lines.add(com.f4xizzz.greatcosmetics.util.TextUtils.parseToText(part, registries));
        }
        return lines;
    }

    /** DisplayName cru (sem passar pelo fallback id/"Cosmético", igual o campo direto) pronto pra
     *  mensagem de chat: troca cada quebra de linha (tanto "\n" literal — as duas letras — quanto
     *  um byte 0x0A de verdade, ver comentário em getFormattedNameLines) por um espaço único, em
     *  vez de quebrar em várias linhas como getFormattedNameLines() faz pra GUI — chat não tem como
     *  mostrar uma quebra de linha de verdade dentro de uma mensagem sem ficar estranho. */
    public String getChatSafeDisplayName() {
        if (this.DisplayName == null) return null;
        return this.DisplayName.replace("\\n", " ").replace("\n", " ");
    }
}