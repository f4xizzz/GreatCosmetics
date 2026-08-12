package com.f4xizzz.greatcosmetics.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MainConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final File DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), "greatcosmetics");

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
                System.out.println("[Cosmetics] Configuração Principal (mainconfig.conf) carregada com sucesso!");
                shouldResave = true;
            }
        } catch (Exception e) {
            System.err.println("[Cosmetics] Erro ao carregar mainconfig.conf!");
            e.printStackTrace();
        }

        // Regrava só DEPOIS do try-with-resources fechar o reader — abrir o FileWriter (dentro
        // de saveConfig()) enquanto o reader ainda está aberto pro MESMO arquivo é um conflito de
        // lock clássico no Windows, que corrompia/esvaziava o mainconfig.conf.
        if (shouldResave) saveConfig();
    }

    public static void createDefaultConfig() {
        ConfigData defaultData = new ConfigData();

        // --- Criando os Slots Padrões ---
        String[] defaultSlots = {"HEAD", "FACE", "NECK", "CHEST", "BACK", "WAIST", "LEGS", "FEET", "HAND"};
        for (String s : defaultSlots) {
            SlotLimit limit = new SlotLimit();
            limit.defaultLimit = 1;
            // Sem o .limit no final
            limit.permission = "gc.slot." + s.toLowerCase();
            defaultData.slots.put(s, limit);
        }

        // --- Criando um Tipo de Exemplo ---
        AccessoryType colar = new AccessoryType();
        colar.slot = "NECK";
        colar.limitPerPlayer = 1;
        // Sem o .limit no final
        colar.permission = "gc.type.colar";
        defaultData.types.put("colar", colar);

        AccessoryType cachecol = new AccessoryType();
        cachecol.slot = "NECK";
        cachecol.limitPerPlayer = 1;
        // Sem o .limit no final
        cachecol.permission = "gc.type.cachecol";
        defaultData.types.put("cachecol", cachecol);

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
            System.out.println("[Cosmetics] Configuração Principal salva com sucesso pelo In-Game Studio!");
        } catch (IOException e) {
            System.err.println("[Cosmetics] Erro fatal ao tentar salvar o mainconfig.conf!");
            e.printStackTrace();
        }
    }
}