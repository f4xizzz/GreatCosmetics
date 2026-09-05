package com.f4xizzz.greatcosmetics.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AutoCMDManager {
    // Começamos a gerar IDs automáticos a partir do 10000 para evitar conflitos com itens normais do seu servidor
    private static final int STARTING_CMD = 10000;
    private static int currentCmd = STARTING_CMD;

    // ConcurrentHashMap, não HashMap comum — o /gc reload manda SyncArmorCosmeticsPayload e
    // SyncCosmeticsPayload praticamente juntos, e o PRIMEIRO payload a chegar já dispara
    // reloadResources() (que lê estes mapas em threads de fundo, dentro de modifyModelOnLoad) ANTES
    // do SEGUNDO payload (o que de fato repopula esses mapas) terminar de rodar na thread principal.
    // Com HashMap comum isso é uma leitura concorrente sem nenhuma sincronização enquanto o outro
    // lado ainda está inserindo — sem lançar exceção nenhuma, só devolvendo uma iteração vazia/
    // incompleta pra thread de fundo, que então monta a lista de overrides do carved_pumpkin com ZERO
    // entradas novas, deixando tudo travado na última versão que funcionou (por isso só "acerta" nos
    // ícones/models quando não tem essa corrida, tipo ao entrar no mundo).
    public static final Map<String, Integer> registeredModels = new ConcurrentHashMap<>();

    // Mapa que guarda os ícones: "diglethat" -> 10001
    public static final Map<String, Integer> registeredIcons = new ConcurrentHashMap<>();

    // Limpa a lista no reload
    public static void clear() {
        com.f4xizzz.greatcosmetics.GreatCosmetics.debugLog("AutoCMDManager.clear(): resetting counter (had " + registeredModels.size() + " models + " + registeredIcons.size() + " icons registered).");
        registeredModels.clear();
        registeredIcons.clear();
        currentCmd = STARTING_CMD;
    }

    // Pega o ID de uma Model. Se ela não existir ainda, cria um ID novo!
    public static int getOrCreateCmd(String modelName) {
        if (modelName == null || modelName.isEmpty()) return 0;

        String cleanName = modelName.toLowerCase().trim();
        if (!registeredModels.containsKey(cleanName)) {
            registeredModels.put(cleanName, currentCmd++);
        }
        return registeredModels.get(cleanName);
    }

    // Pega o ID de um Icon. Se ele não existir ainda, cria um ID novo!
    public static int getOrCreateIconCmd(String iconName) {
        if (iconName == null || iconName.isEmpty()) return 0;

        String cleanName = iconName.toLowerCase().trim();
        if (!registeredIcons.containsKey(cleanName)) {
            registeredIcons.put(cleanName, currentCmd++);
        }
        return registeredIcons.get(cleanName);
    }
}