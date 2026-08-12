package com.f4xizzz.greatcosmetics.client;

import com.f4xizzz.greatcosmetics.config.MainConfig;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Espelho client-side do mainconfig.conf (ver SyncMainConfigPayload) — TODA leitura de
 *  slots/types/config geral no client (Dev Studio, EquippedSlotsWidget) deve vir daqui, nunca de
 *  {@link MainConfig#config} direto: esse é o mapa que só o SERVIDOR carrega do disco de verdade;
 *  no client (numa conexão remota) ele nunca é populado sozinho. */
public class ClientMainConfigCache {

    public static MainConfig.ConfigData config = new MainConfig.ConfigData();

    private static final Gson GSON = new Gson();

    public static void setConfig(String json) {
        try {
            MainConfig.ConfigData parsed = GSON.fromJson(json, MainConfig.ConfigData.class);
            if (parsed != null) config = parsed;
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Falha ao ler mainconfig sincronizado do servidor: " + e.getMessage());
        }
    }

    /** Snapshot em JSON do estado atual, pra permitir desfazer edições não salvas (ver
     *  discardChanges() nas sub-páginas do Dev Studio). */
    public static String snapshotJson() {
        return GSON.toJson(config);
    }

    /** Reverte pro snapshot passado (edição não salva sendo descartada). */
    public static void restoreFromJson(String json) {
        if (json == null) return;
        setConfig(json);
    }

    /** Manda a edição atual pro servidor persistir de verdade (ver SaveMainConfigPayload) — o
     *  servidor rebroadcasta pra todo mundo depois, incluindo quem mandou. */
    public static void sendSave() {
        ClientPlayNetworking.send(new com.f4xizzz.greatcosmetics.network.SaveMainConfigPayload(GSON.toJson(config)));
    }
}
