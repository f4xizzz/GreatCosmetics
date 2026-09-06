package com.f4xizzz.greatcosmetics.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import dev.architectury.platform.Platform;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MainConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final File DIR = new File(Platform.getConfigFolder().toFile(), "GreatCosmetics");

    // O ARQUIVO GERAL DO MOD
    private static final File CONFIG_FILE = new File(DIR, "mainconfig.conf");

    // Instância global que guarda as configurações ativas
    public static ConfigData config = new ConfigData();

    // ==========================================
    // ESTRUTURAS DE LIMITES E TIPOS
    // ==========================================
    public static class SlotLimit {
        public int defaultLimit = 1;
        public String permission = "gc.slot."; // Base mais limpa
    }

    public static class AccessoryType {
        public String slot = "NECK";
        public int limitPerPlayer = 1;
        public String permission = "gc.type."; // Base mais limpa
    }

    // Classe que mapeia as opções do arquivo
    public static class ConfigData {
        public boolean autoDetectModels = true;

        // --- HUD: BARRAS DE VIDA/ARMADURA QUE "SE MULTIPLICAM" ---
        // Quando a vida MÁXIMA ou a armadura do player passa de 2 barras cheias (20 pontos), em
        // vez de empilhar fileiras achatadas (vida) ou sumir com o excedente (armadura, que o
        // vanilla trava em 20), o HUD mostra 1 barra + um "xN" do lado. Só tem efeito visual se
        // algum player de fato tiver vida/armadura acima de 20 (atributo/equipamento modificado) —
        // num servidor com atributos vanilla nunca dispara. Ver InGameHudMixin.
        public boolean compactStatusBars = true;

        // --- PERMISSÃO DO DEV MODE ---
        public String devModePermission = "gc.perm.devmode";

        // --- BLACKLIST DE GRUPOS NO SISTEMA DE TAGS ---
        // Grupos do LuckPerms listados aqui nunca são importados como tag no GUI de Tags (ex:
        // "default", ou grupos internos/administrativos que não fazem sentido como tag
        // escolhível pelo player). Nome do grupo, não case-sensitive.
        public java.util.List<String> tagGroupBlacklist = new java.util.ArrayList<>(java.util.List.of("default"));

        // --- VARIÁVEIS DE DATABASE ---
        public boolean useMySQL = false;
        public String mysqlHost = "localhost";
        public int mysqlPort = 3306;
        public String mysqlDatabase = "greatcosmetics";
        public String mysqlUser = "root";
        public String mysqlPassword = "password";

        // --- SISTEMA DE ACESSÓRIOS VIRTUAIS ---
        public Map<String, SlotLimit> slots = new HashMap<>();
        public Map<String, AccessoryType> types = new HashMap<>();

        // --- RESOURCE PACK FORÇADO PELO MOD ---
        // Em vez de configurar resource-pack/resource-pack-sha1 no server.properties (que exige
        // reiniciar o servidor pra qualquer mudança pegar), o mod manda o pacote de resource pack
        // direto pro client no join — e de novo pra todo mundo já online no /gc reload, sem precisar
        // reiniciar nada. textureId pode ser qualquer texto (não precisa ser um UUID de verdade).
        public boolean forceTexture = false;
        public String textureId = "greatcosmetics";
        public String textureUrl = "";
        public String textureSha1 = "";

        // --- COMANDOS RODADOS QUANDO O SERVIDOR TERMINA DE LIGAR ---
        // Executados como console (permissão total) via ServerLifecycleEvents.SERVER_STARTED —
        // só depois que TODOS os mods/plugins já terminaram de carregar, diferente de SERVER_STARTING
        // (que roda antes disso, cedo demais pra comandos que dependem de outro mod já registrado,
        // ex: "styledsidebar reload").
        public java.util.List<String> startupCommands = new java.util.ArrayList<>();
    }

    public static void loadConfig() {
        if (!DIR.exists()) DIR.mkdirs();

        if (!CONFIG_FILE.exists()) {
            createDefaultConfig();
        }

        boolean shouldResave = false;

        try (JsonReader reader = new JsonReader(new FileReader(CONFIG_FILE))) {
            reader.setLenient(true);

            ConfigData loadedData = GSON.fromJson(reader, ConfigData.class);
            if (loadedData != null) {
                config = loadedData;
                System.out.println("[GreatCosmetics] Main config (mainconfig.conf) loaded successfully!");
                com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("MainConfig: mainconfig.conf loaded — " + config.slots.size() + " slots, " + config.types.size() + " types.");
                shouldResave = true;
            }
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Error loading mainconfig.conf!");
            com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("MainConfig: FAILED to load mainconfig.conf — " + e);
            e.printStackTrace();
        }

        // Regrava só DEPOIS do try-with-resources fechar o reader — abrir o FileWriter (dentro
        // de saveConfig()) enquanto o reader ainda está aberto pro MESMO arquivo é um conflito de
        // lock clássico no Windows, que corrompia/esvaziava o mainconfig.conf.
        if (shouldResave) saveConfig();
    }

    public static void createDefaultConfig() {
        ConfigData defaultData = new ConfigData();

        // --- Default slots ---
        String[] defaultSlots = {"HEAD", "FACE", "NECK", "CHEST", "BACK", "WAIST", "LEGS", "FEET", "HAND"};
        for (String s : defaultSlots) {
            SlotLimit limit = new SlotLimit();
            limit.defaultLimit = 1;
            limit.permission = "gc.slot." + s.toLowerCase();
            defaultData.slots.put(s, limit);
        }

        // --- Example types ---
        AccessoryType necklace = new AccessoryType();
        necklace.slot = "NECK";
        necklace.limitPerPlayer = 1;
        necklace.permission = "gc.type.necklace";
        defaultData.types.put("necklace", necklace);

        AccessoryType scarf = new AccessoryType();
        scarf.slot = "NECK";
        scarf.limitPerPlayer = 1;
        scarf.permission = "gc.type.scarf";
        defaultData.types.put("scarf", scarf);

        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(defaultData, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    // ==========================================
    // SISTEMA DE GRAVAÇÃO (IN-GAME STUDIO)
    // ==========================================
    public static void saveConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(config, writer);
            System.out.println("[GreatCosmetics] Main config saved successfully by the In-Game Studio!");
            com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("MainConfig: mainconfig.conf saved.");
        } catch (IOException e) {
            System.err.println("[GreatCosmetics] Fatal error while saving mainconfig.conf!");
            e.printStackTrace();
        }
    }
}