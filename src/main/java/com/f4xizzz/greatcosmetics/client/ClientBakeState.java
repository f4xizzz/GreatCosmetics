package com.f4xizzz.greatcosmetics.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.f4xizzz.greatcosmetics.util.AutoCMDManager;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** Persiste, entre reinícios do launcher, contra QUAL catálogo os models de cosmético já estão
 *  "bakeados" — pra que a 1ª entrada num servidor depois de reabrir o jogo seja tão silenciosa
 *  quanto a 2ª entrada na mesma sessão (sem a tela de recarregamento de 30s), quando nada mudou.
 *
 *  <p><b>Por que isso é preciso:</b> {@code ModelLoadingPlugin}/{@code modifyModelOnLoad} lê o
 *  {@link AutoCMDManager} NA HORA que os models bakeiam (no boot do jogo, ANTES de conectar em
 *  qualquer servidor). Sem nada persistido, o boot bakeia com os CMDs do config LOCAL do client
 *  (vazio/exemplo pra um player comum) — nunca os do servidor — então a 1ª entrada SEMPRE precisava
 *  de um {@code reloadResources()} pra re-bakear com os CMDs certos. Guardando aqui os CMDs que o
 *  servidor mandou da última vez (por servidor) + o hash do catálogo daquele momento, o
 *  {@code onInitializeClient()} pré-carrega o {@link AutoCMDManager} com os valores do ÚLTIMO
 *  servidor usado ANTES do primeiro bake — aí, se você reentrar nesse servidor e o catálogo não
 *  mudou, o hash bate e o reload é pulado inteiro.
 *
 *  <p>Guarda por {@code serverKey} (ver {@link ClientServerIdentity}) num mapa, mas o boot só
 *  consegue pré-carregar UM (não sabe em qual servidor você vai entrar) — pré-carrega o
 *  {@code lastServerKey}. Trocar de servidor custa 1 reload (e aí aquele passa a ser o "último").
 *  Arquivo: {@code config/GreatCosmetics/bake_state.json}. NÃO é sensível/secreto — é só um
 *  espelho de números de CustomModelData que já viajam abertos no payload de rede. */
public final class ClientBakeState {

    private ClientBakeState() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("GreatCosmetics-BakeState");
    private static final File FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "GreatCosmetics/bake_state.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final class ServerBake {
        String catalogHash;
        Map<String, Integer> models = new HashMap<>();
        Map<String, Integer> icons = new HashMap<>();
    }

    private static final class Root {
        @SerializedName("lastServerKey")
        String lastServerKey;
        Map<String, ServerBake> servers = new HashMap<>();
    }

    private static volatile Root root;

    private static synchronized Root load() {
        if (root != null) return root;
        root = new Root();
        if (FILE.exists()) {
            try (FileReader r = new FileReader(FILE)) {
                Root parsed = GSON.fromJson(r, Root.class);
                if (parsed != null) {
                    root = parsed;
                    if (root.servers == null) root.servers = new HashMap<>();
                }
            } catch (Exception e) {
                LOGGER.warn("Falha ao ler {} — o estado de bake será reconstruído do zero.", FILE, e);
            }
        }
        return root;
    }

    private static synchronized void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null) //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            File tmp = new File(FILE.getParentFile(), FILE.getName() + ".tmp");
            try (FileWriter w = new FileWriter(tmp)) {
                GSON.toJson(root, w);
            }
            Files.move(tmp.toPath(), FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            LOGGER.warn("Falha ao salvar {} — a 1ª entrada depois de reabrir o jogo vai recarregar de novo.", FILE, e);
        }
    }

    /** Chamado no onInitializeClient(), DEPOIS de CosmeticsConfig.loadConfig() (que popula o
     *  AutoCMDManager com o config LOCAL) e ANTES do primeiro bake de models. Se houver estado
     *  persistido do último servidor usado, SUBSTITUI o AutoCMDManager pelos CMDs daquele servidor
     *  e devolve o hash do catálogo daquele momento (pra virar o lastBakedCatalogHash em memória).
     *  Devolve null se não há nada persistido (instalação nova, singleplayer sem histórico). */
    public static synchronized String loadAtBoot() {
        Root rt = load();
        if (rt.lastServerKey == null) return null;
        ServerBake sb = rt.servers.get(rt.lastServerKey);
        if (sb == null || sb.catalogHash == null) return null;

        AutoCMDManager.registeredModels.clear();
        AutoCMDManager.registeredIcons.clear();
        if (sb.models != null) AutoCMDManager.registeredModels.putAll(sb.models);
        if (sb.icons != null) AutoCMDManager.registeredIcons.putAll(sb.icons);

        LOGGER.info("Pré-carregado o estado de bake do último servidor ({} models + {} icons, catalogHash={}).",
                AutoCMDManager.registeredModels.size(), AutoCMDManager.registeredIcons.size(),
                sb.catalogHash.length() >= 12 ? sb.catalogHash.substring(0, 12) + "…" : sb.catalogHash);
        return sb.catalogHash;
    }

    /** Chamado quando um reloadResources() de verdade termina (ver GreatCosmeticsClient#
     *  runAfterResourceReload): fotografa o AutoCMDManager atual (que nesse ponto tem os CMDs que o
     *  SERVIDOR mandou) + o hash do catálogo, sob o serverKey atual, e marca esse como o "último". */
    public static synchronized void record(String serverKey, String catalogHash) {
        if (serverKey == null || catalogHash == null || catalogHash.isEmpty()) return;
        Root rt = load();
        ServerBake sb = rt.servers.computeIfAbsent(serverKey, k -> new ServerBake());
        sb.catalogHash = catalogHash;
        sb.models = new TreeMap<>(AutoCMDManager.registeredModels);
        sb.icons = new TreeMap<>(AutoCMDManager.registeredIcons);
        rt.lastServerKey = serverKey;
        save();
        LOGGER.info("Estado de bake gravado pro servidor atual ({} models + {} icons).", sb.models.size(), sb.icons.size());
    }

    /** Hash do catálogo persistido pra ESSE servidor (ou null) — usado pelo receiver de
     *  SyncCatalogStatePayload como um 2º sinal (além do lastBakedCatalogHash em memória) de que a
     *  1ª entrada pode ser silenciosa. */
    public static synchronized String hashForServer(String serverKey) {
        if (serverKey == null) return null;
        ServerBake sb = load().servers.get(serverKey);
        return sb == null ? null : sb.catalogHash;
    }
}
