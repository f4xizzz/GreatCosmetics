package com.f4xizzz.greatcosmetics.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import dev.architectury.platform.Platform;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class CosmeticsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final File DIR = new File(Platform.getConfigFolder().toFile(), "GreatCosmetics");
    private static final File CONFIG_FILE = new File(DIR, "cosmeticsconfig.conf");

    public static Map<String, CosmeticData> cosmeticsMap = new HashMap<>();
    private static Map<String, CosmeticData> backupMap = new HashMap<>();

    public static void loadConfig() {
        if (!DIR.exists()) DIR.mkdirs();
        if (!CONFIG_FILE.exists()) {
            createDefaultConfig();
        }

        boolean shouldResave = false;

        try (JsonReader reader = new JsonReader(new FileReader(CONFIG_FILE))) {
            reader.setLenient(true);

            Type type = new TypeToken<Map<String, CosmeticData>>(){}.getType();
            Map<String, CosmeticData> loadedMap = GSON.fromJson(reader, type);

            if (loadedMap != null) {
                backupMap = new HashMap<>(cosmeticsMap);
                cosmeticsMap = loadedMap;

                // NÃO chama AutoCMDManager.clear() aqui! cosmeticsMap é um HashMap comum — a ordem
                // de iteração dele muda sempre que um cosmético é adicionado/removido/editado, e
                // como getOrCreateCmd() distribui os números em sequência a partir de um contador
                // ÚNICO compartilhado, um clear()+reatribuição do zero a cada /gc reload fazia TODO
                // cosmético já existente ganhar um número de custom_model_data DIFERENTE do que
                // tinha antes. Itens-fantasma que jogadores já estavam segurando/vestindo continuam
                // com o número ANTIGO gravado no ItemStack (só é recalculado ao reequipar), então
                // depois do reload esse número não batia com nada válido e todo mundo colapsava pro
                // mesmo modelo (o último registrado) — só voltava ao normal ao relogar (que reequipa
                // com os números novos). getOrCreateCmd()/getOrCreateIconCmd() já são idempotentes
                // (só criam número novo pra chave que ainda não existe), então sem o clear() os
                // cosméticos existentes mantêm sempre o mesmo número, e só cosmético/ícone
                // genuinamente novo ganha um número novo em cima do contador atual.

                com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("CosmeticsConfig: starting to read " + cosmeticsMap.size() + " cosmetics from cosmeticsconfig.conf.");

                for (Map.Entry<String, CosmeticData> entry : cosmeticsMap.entrySet()) {
                    String id = entry.getKey();
                    CosmeticData data = entry.getValue();
                    data.id = id;

                    // ==========================================
                    // SISTEMA INTELIGENTE DE AUTO-NAMING COM MINIMESSAGE
                    // ==========================================
                    if (data.DisplayName == null || data.DisplayName.trim().isEmpty() || data.DisplayName.equals("&dCosmético Desconhecido") || data.DisplayName.equals("<light_purple>Cosmético Desconhecido")) {
                        String formattedName = id.substring(0, 1).toUpperCase() + id.substring(1);
                        data.DisplayName = "<light_purple>" + formattedName;
                    }

                    // "render" (HEAD/CHEST/LEGS/FEET) é texto livre no Dev Studio — um valor que não
                    // bate com nenhuma das 4 constantes (ex: "BACK", "WAIST", digitado achando que é
                    // igual ao "slot") faz o Gson silenciosamente deixar esse campo null, e sem esse
                    // fallback o cosmético fica PERMANENTEMENTE invisível em qualquer slot, sem
                    // nenhum erro visível pro admin (ArmorFeatureRendererMixin pula `render == null`
                    // nos 4 passes de renderização). Corrigido pra HEAD aqui e regravado no disco.
                    if (data.render == null) {
                        System.err.println("[GreatCosmetics] WARNING: cosmetic '" + id + "' has an invalid or missing 'render' in cosmeticsconfig.conf — accepted values are HEAD, CHEST, LEGS or FEET. Using HEAD as default; fix it in the Dev Studio if that's not the right slot.");
                        data.render = CosmeticData.RenderSlot.HEAD;
                    }

                    com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("CosmeticsConfig: lendo '" + id + "' - Nome: " + data.DisplayName + " - Slot: " + data.slot + " - Render: " + data.render);

                    resolveModelIds(data);
                }
                com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("CosmeticsConfig: read finished — " + cosmeticsMap.size() + " cosmetics loadeds.");

                shouldResave = true;
            }
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Error in the .conf file! Restoring backup...");
            com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("CosmeticsConfig: FAILED to read cosmeticsconfig.conf — " + e + ". Restoring backup (" + backupMap.size() + " cosmetics).");
            e.printStackTrace();
        }

        // Regrava só DEPOIS do try-with-resources fechar o FileReader/JsonReader — chamar
        // saveConfig() (abre um FileWriter pro MESMO arquivo) enquanto o reader ainda está
        // aberto é um conflito de lock clássico no Windows, que pode corromper/esvaziar o
        // cosmeticsconfig.conf. Qualquer campo novo do CosmeticData que não existia num
        // cosmético antigo (ex: flySpeedMultiplier) já foi preenchido pelo Gson com o valor
        // padrão da classe no parse acima — só faltava persistir de volta no disco.
        if (shouldResave) saveConfig();
    }

    /** Resolve os CMDs (ícone chapado via auto-detect/manual, e modelo GeckoLib via geoModelId) de
     *  um cosmético a partir do que está configurado. Roda tanto no loadConfig() completo quanto
     *  toda vez que o Dev Studio salva uma edição (ver SaveCosmeticPayload em GreatCosmetics.java)
     *  — sem isso, resolvedIconCmd/resolvedCmd (campos transient, nunca vêm no JSON que o client
     *  manda) ficavam zerados até o próximo /gc reload, fazendo o modelo virar ícone chapado (ou
     *  sumir) só de clicar em Salvar. */
    public static void resolveModelIds(CosmeticData data) {
        if (data.id == null || data.parts == null) return;

        if (MainConfig.config.autoDetectModels) {
            data.cmd = com.f4xizzz.greatcosmetics.util.AutoCMDManager.getOrCreateCmd(data.id);
            String iconKey = (data.iconId != null && !data.iconId.isBlank()) ? data.iconId : data.id;
            data.resolvedIconCmd = com.f4xizzz.greatcosmetics.util.AutoCMDManager.getOrCreateIconCmd(iconKey);

            for (CosmeticData.CosmeticPart part : data.parts) {
                String partName = part.customModelData_or_ID;
                if (partName == null || partName.isEmpty()) {
                    part.resolvedCmd = 0;
                } else {
                    // Prefixo "exact:" é só uma chave interna pro AutoCMDManager/GreatCosmeticsClient
                    // saberem (na hora de montar os overrides) que esse valor é um CAMINHO EXATO
                    // (ver CosmeticPart#useExactPath), não um nome pra procurar por toda parte.
                    String key = part.useExactPath ? "exact:" + partName : partName;
                    part.resolvedCmd = com.f4xizzz.greatcosmetics.util.AutoCMDManager.getOrCreateCmd(key);
                }
            }
        } else {
            for (CosmeticData.CosmeticPart part : data.parts) {
                try {
                    part.resolvedCmd = Integer.parseInt(part.customModelData_or_ID);
                } catch (NumberFormatException e) {
                    part.resolvedCmd = 0;
                }
            }
        }

        // Modelo 3D GeckoLib (geo/textura no resourcepack, mesma convenção de sempre — ver
        // GreatCosmeticsClient) — sempre tem prioridade sobre o ícone chapado acima, seja qual for
        // o modo de auto-detect.
        for (CosmeticData.CosmeticPart part : data.parts) {
            if (part.geoModelId != null && !part.geoModelId.isBlank()) {
                part.resolvedCmd = com.f4xizzz.greatcosmetics.util.AutoCMDManager.getOrCreateCmd("geo:" + part.geoModelId);
            }
        }
    }

    public static void createDefaultConfig() {
        String defaultConf = """
        {
          // ====================================================================================
          // COSMETICSCONFIG.CONF — generated automatically the first time the server starts.
          // Edit it through the in-game Dev Studio (recommended) or directly here — the "//"
          // comments only work because the mod reads this file in "lenient" mode; it is not
          // standard JSON, don't paste it elsewhere expecting it to parse.
          //
          // HOW THE MOD FINDS THE MODEL FOR EACH "Part" (inside "parts" below) — there are TWO
          // kinds, each Part uses only ONE (if both fields are filled, GeckoLib always wins):
          //
          //  1) "customModelData_or_ID" -> a "vanilla" icon/model (a plain item JSON). Expects a
          //     file at assets/<namespace>/models/**/<name>.json.
          //  2) "geoModelId" -> a real 3D model via GeckoLib. Expects THREE files with the SAME
          //     name: geo/**/<name>.geo.json + textures/**/<name>.png (+
          //     animations/**/<name>.animation.json, optional).
          //
          //  By default ("useExactPath": false) both of the above match by FILE NAME only (no
          //  folder), in ANY subfolder — e.g. "cigarette" finds both models/item/cigarette.json
          //  and models/sas/cigarette.json, whichever shows up first.
          //
          //  If you turn on "useExactPath": true, the field becomes the FULL RELATIVE PATH,
          //  without extension (e.g. "sas/cigarette" for assets/<any namespace>/models/sas/
          //  cigarette.json, or "item/faxihat" for assets/<any namespace>/geo/item/faxihat.geo.json)
          //  — use this when you have two files with the same name in different folders and the
          //  name search is picking the wrong one.
          // ====================================================================================
          "examplehat": {
            "cmd": 0,
            "render": "HEAD",
            "slot": "HEAD",
            "type": "hat",
            // GUI icon (Wardrobe/Acessórios list) at textures/icons/squirtle_glasses_icon.png,
            // namespace "greatcosmetics" (icon lookup, unlike the GeckoLib model below, is always
            // restricted to this mod's own namespace — see SyncCosmeticsPayload receiver).
            "iconId": "squirtle_glasses_icon",
            "parts": [
              {
                // Example using kind 2 (real 3D model via GeckoLib, see the explanation above) —
                // geo/item/squirtle_glasses.geo.json + textures/item/squirtle_glasses.png.
                "customModelData_or_ID": "",
                "geoModelId": "squirtle_glasses",
                "useExactPath": false,
                "anchor": "HEAD",
                "offsetX": 0.0,
                "offsetY": 1.792,
                "offsetZ": 0.0,
                "rotationX": 0.0,
                "rotationY": 0.0,
                "rotationZ": 0.0,
                "scaleX": 1.61,
                "scaleY": 1.61,
                "scaleZ": 1.61,
                "shiftOffsetX": 0.0,
                "shiftOffsetY": 0.0,
                "shiftOffsetZ": 0.0,
                "shiftRotationX": 0.0,
                "shiftRotationY": 0.0,
                "shiftRotationZ": 0.0
              }
            ],
            "maxDurability": 0,
            "effectVisual": [],
            "flyParticle": [],
            "DisplayName": "<yellow>Example Hat",
            "permission": "",
            "isBackpack": false,
            "backpackRows": 3,
            "backpackDisplayName": "",
            "EnableFly": false,
            "effects": [],
            "sounds": {
              "equipSound": "",
              "unequipSound": "",
              "walkSound": "",
              "flySound": "",
              "shiftSound": "",
              "backpackSound": "",
              "idleSound": ""
            }
          }
        }
        """;

        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            writer.write(defaultConf);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(cosmeticsMap, writer);
            com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("CosmeticsConfig: cosmeticsconfig.conf saved with " + cosmeticsMap.size() + " cosmetics.");
        } catch (IOException e) {
            System.err.println("[GreatCosmetics] Fatal error while saving cosmeticsconfig.conf!");
            com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("CosmeticsConfig: FAILED to save cosmeticsconfig.conf — " + e);
            e.printStackTrace();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Resolução de cosmético por id / cmd. Estava em GreatCosmetics (entrypoint Fabric); moveu pra
    // cá no split multiloader porque é chamado tanto de código server-side (comandos,
    // LivingEntityMixin) quanto client-side (ArmorFeatureRendererMixin, EquippedSlotsWidget), todo
    // em common agora.
    // ---------------------------------------------------------------------------------------------

    public static CosmeticData getCosmeticData(int cmdToFind) {
        for (CosmeticData data : cosmeticsMap.values()) {
            if (data.cmd == cmdToFind) {
                return data;
            }
        }
        return null;
    }

    // Índice case-insensitive de cosmeticsMap, reconstruído só quando a REFERÊNCIA do map muda de
    // verdade (reload/save/sync sempre trocam a referência inteira, nunca mutam o map existente —
    // ver loadConfig e o receiver de SyncCosmeticsPayload). Sem isso, getCosmeticById fazia uma
    // varredura linear com equalsIgnoreCase em TODOS os cosméticos pra cada chamada — e é chamado
    // uma vez POR COSMÉTICO EQUIPADO, POR JOGADOR, TODO TICK (além de toda vez que
    // LivingEntity#getArmor() roda) — com muitos jogadores e cosméticos configurados isso vira uma
    // varredura O(cosméticos) desnecessária centenas de vezes por segundo.
    private static Map<String, CosmeticData> cosmeticsByIdLower = java.util.Collections.emptyMap();
    private static Map<String, CosmeticData> cosmeticsByIdLowerSource = null;

    /** cosmeticsMap.put()/remove() diretos (edição individual pelo Dev Studio, sem passar por um
     *  reload completo) MUTAM o map existente em vez de trocar a referência — a detecção "trocou a
     *  referência" sozinha em getCosmeticById() nunca pegaria isso, deixando o índice desatualizado
     *  até o próximo /gc reload. Chamar isso logo depois de qualquer put()/remove() direto no
     *  cosmeticsMap força o rebuild na próxima chamada. */
    public static void invalidateCosmeticIndex() {
        cosmeticsByIdLowerSource = null;
    }

    public static CosmeticData getCosmeticById(String idProcurado) {
        if (idProcurado == null) return null;
        Map<String, CosmeticData> currentMap = cosmeticsMap;
        if (currentMap != cosmeticsByIdLowerSource) {
            Map<String, CosmeticData> rebuilt = new HashMap<>();
            // Indexa pela CHAVE DE VERDADE do mapa (entry.getKey()), não por data.id — data.id é só
            // um campo transient que o loadConfig()/save reatribui pra bater com a chave, mas se ELE
            // alguma vez ficar fora de sincronia com a chave real (ex: um bug futuro em algum fluxo
            // de salvar/duplicar cosmético que reaproveite a mesma instância de CosmeticData pra dois
            // ids diferentes), indexar por data.id faz esse índice silenciosamente MESCLAR duas
            // mochilas/cosméticos DIFERENTES num só — getCosmeticById(idA) e getCosmeticById(idB)
            // passam a devolver o MESMO objeto, cada edição/leitura de um "vaza" pro outro. Indexar
            // pela chave real do map é imune a esse tipo de bug em qualquer outro lugar do código.
            for (Map.Entry<String, CosmeticData> entry : currentMap.entrySet()) {
                if (entry.getKey() != null) rebuilt.put(entry.getKey().toLowerCase(), entry.getValue());
            }
            cosmeticsByIdLower = rebuilt;
            cosmeticsByIdLowerSource = currentMap;
        }
        CosmeticData found = cosmeticsByIdLower.get(idProcurado.toLowerCase());
        if (found != null) return found;
        // Armadura convertida em cosmético (ver ArmorCosmeticsConfig) — nunca fica no cosmeticsMap
        // de verdade, é sintetizada sob demanda a partir do armor_cosmetics.json. Esse método é
        // chamado tanto de código SERVER-side (comandos, LivingEntityMixin) quanto CLIENT-side
        // (ArmorFeatureRendererMixin, EquippedSlotsWidget) — só UM dos dois mapas abaixo está de
        // fato populado em cada lado (ArmorCosmeticsConfig.load() só roda no servidor;
        // ClientArmorCosmeticsCache só é preenchido via SyncArmorCosmeticsPayload no client). Sem
        // tentar os dois aqui, TODO código client-side que resolvia uma armadura-cosmético por id
        // (renderização no corpo, EquippedSlotsWidget) sempre recebia null numa conexão remota de
        // verdade — a armadura "equipava" no banco mas nunca aparecia em lugar nenhum no client.
        //
        // ORDEM IMPORTA no client: em singleplayer/hospedando, o servidor integrado E o client
        // rodam na mesma JVM, então ArmorCosmeticsConfig (servidor) E ClientArmorCosmeticsCache
        // (client) ficam OS DOIS populados ao mesmo tempo — como objetos DIFERENTES (o client
        // sempre recebe o catálogo por rede, mesmo hospedando local, nunca lê o do servidor
        // direto). Tentar o do servidor primeiro fazia esse método devolver, do lado do client, um
        // CosmeticData/CosmeticPart que NUNCA era o mesmo objeto que o Dev Studio estava editando —
        // o Gizmo 3D (GizmoManager, que compara "é essa a part ativa?" por referência de objeto)
        // nunca reconhecia a part como ativa e nunca desenhava/ficava clicável. No client, sempre
        // prioriza o cache client-side.
        // Platform.getEnvironment(), NUNCA Minecraft.getInstance() aqui — esse método roda no tick
        // do SERVIDOR também (equipar cosmético, etc), e net.minecraft.client.Minecraft nem existe
        // no classpath de um servidor dedicado. Só a REFERÊNCIA à classe (mesmo dentro de um
        // "!= null") já derruba o server tick loop inteiro com NoClassDefFoundError.
        if (dev.architectury.platform.Platform.getEnvironment() == dev.architectury.utils.Env.CLIENT) {
            CosmeticData clientArmorData = com.f4xizzz.greatcosmetics.client.ClientArmorCosmeticsCache.getSyntheticCosmetic(idProcurado);
            if (clientArmorData != null) return clientArmorData;
        }
        CosmeticData serverArmorData = ArmorCosmeticsConfig.getSyntheticCosmetic(idProcurado);
        if (serverArmorData != null) return serverArmorData;
        return com.f4xizzz.greatcosmetics.client.ClientArmorCosmeticsCache.getSyntheticCosmetic(idProcurado);
    }
}