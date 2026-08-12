package com.f4xizzz.greatcosmetics.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class CosmeticsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final File DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), "greatcosmetics");
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

                System.out.println("\n[DEBUG-SERVER] --- INICIANDO LEITURA DE COSMETICOS ---");

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
                        System.err.println("[GreatCosmetics] AVISO: cosmético '" + id + "' está com 'render' inválido ou ausente no cosmeticsconfig.conf — valores aceitos são HEAD, CHEST, LEGS ou FEET. Usando HEAD como padrão; corrija pelo Dev Studio se não for o slot certo.");
                        data.render = CosmeticData.RenderSlot.HEAD;
                    }

                    System.out.println("[DEBUG-SERVER] Lendo item do config: '" + id + "' - Nome: " + data.DisplayName + " - Slot: " + data.slot + " - Render: " + data.render);

                    resolveModelIds(data);
                }
                System.out.println("[DEBUG-SERVER] --- LEITURA FINALIZADA ---\n");

                shouldResave = true;
            }
        } catch (Exception e) {
            System.err.println("[Cosmetics] Erro no arquivo .conf! Restaurando backup...");
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
          // COSMETICSCONFIG.CONF — gerado automaticamente na primeira vez que o servidor liga.
          // Edite pelo Dev Studio in-game (recomendado) ou direto aqui — o "//" só funciona porque
          // o mod lê esse arquivo em modo "lenient"; não é JSON padrão, não cole isso em outro
          // lugar esperando que funcione.
          //
          // COMO O MOD ACHA O MODEL DE CADA "Part" (dentro de "parts" abaixo) — existem DOIS tipos,
          // cada Part usa só UM (se os dois campos estiverem preenchidos, o GeckoLib sempre ganha):
          //
          //  1) "customModelData_or_ID" -> ícone/model "vanilla" (item JSON comum). Espera um
          //     arquivo em assets/<namespace>/models/**/<nome>.json.
          //  2) "geoModelId" -> model 3D de verdade via GeckoLib. Espera TRÊS arquivos com o
          //     MESMO nome: geo/**/<nome>.geo.json + textures/**/<nome>.png (+
          //     animations/**/<nome>.animation.json, opcional).
          //
          //  Por padrão ("useExactPath": false) os dois acima procuram só pelo NOME DO ARQUIVO
          //  (sem pasta), em QUALQUER subpasta — ex: "cigarro" acha tanto models/item/cigarro.json
          //  quanto models/sas/cigarro.json, o primeiro que aparecer.
          //
          //  Se ligar "useExactPath": true, o campo passa a ser o CAMINHO RELATIVO COMPLETO, sem
          //  extensão (ex: "sas/cigarro" para assets/<qualquer namespace>/models/sas/cigarro.json,
          //  ou "item/faxihat" para assets/<qualquer namespace>/geo/item/faxihat.geo.json) — use
          //  isso quando tiver dois arquivos com o mesmo nome em pastas diferentes e a busca por
          //  nome estiver pegando o errado.
          // ====================================================================================
          "examplehat": {
            "cmd": 0,
            "render": "HEAD",
            "slot": "HEAD",
            "type": "hat",
            "parts": [
              {
                // Exemplo usando o tipo 1 (model vanilla, busca por nome — troque pra "geoModelId"
                // se quiser um model 3D de verdade via GeckoLib, ver explicação acima).
                "customModelData_or_ID": "examplehat",
                "geoModelId": "",
                "useExactPath": false,
                "anchor": "HEAD",
                "offsetX": 0.0,
                "offsetY": 0.0,
                "offsetZ": 0.0,
                "rotationX": 0.0,
                "rotationY": 0.0,
                "rotationZ": 0.0,
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
            "DisplayName": "<yellow>Chapéu de Exemplo",
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
            System.out.println("[DEBUG-SERVER] Arquivo cosmeticsconfig.conf salvo com sucesso pelo In-Game Studio!");
        } catch (IOException e) {
            System.err.println("[Cosmetics] Erro fatal ao tentar salvar o cosmeticsconfig.conf!");
            e.printStackTrace();
        }
    }
}