package com.f4xizzz.greatcosmetics.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/** Configurações client-side só pra ferramentas do Dev Studio (gizmo 3D) — nunca sincroniza com o
 *  servidor nem afeta como cosméticos renderizam pra outros jogadores, só ajuda a mirar o gizmo. */
public class GizmoDevConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static File FILE;

    /** Offset manual (eixo Y, mesmo espaço "cru" que offsetY das Parts) só do PIVÔ VISUAL do
     *  gizmo quando a Part ativa usa um modelo GeckoLib (.geo) — ver ArmorFeatureRendererMixin. O
     *  GeckoLib tem o próprio pipeline de posicionamento (auto-escala pro bounding box do .geo +
     *  transforms por osso) que a gente não consegue ler/replicar de fora, então em vez de ficar
     *  chutando um número fixo no código (e recompilando toda hora pra testar), esse valor é
     *  ajustável ao vivo pela barrinha lateral do gizmo e fica salvo — muda uma vez, vale pra
     *  todo cosmético .geo que você for editar depois, até mudar de novo. */
    public static float geoGizmoYOffset = 0.4f;

    public static void load() {
        File configDir = new File(FabricLoader.getInstance().getConfigDir().toFile(), "greatcosmetics");
        if (!configDir.exists()) configDir.mkdirs();
        FILE = new File(configDir, "gizmo_dev_config.json");

        if (!FILE.exists()) {
            save();
            return;
        }

        try (FileReader reader = new FileReader(FILE)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data != null) geoGizmoYOffset = data.geoGizmoYOffset;
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Erro ao carregar gizmo_dev_config.json: " + e.getMessage());
        }
    }

    public static void save() {
        if (FILE == null) return;
        try (FileWriter writer = new FileWriter(FILE)) {
            Data data = new Data();
            data.geoGizmoYOffset = geoGizmoYOffset;
            GSON.toJson(data, writer);
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Erro ao salvar gizmo_dev_config.json: " + e.getMessage());
        }
    }

    private static class Data {
        float geoGizmoYOffset = 0.4f;
    }
}
