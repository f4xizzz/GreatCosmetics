package com.f4xizzz.greatcosmetics.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;

/** Chave estável pra "em qual servidor eu estou agora", usada por ClientForcedTextureCache pra
 *  amarrar o cache de textura+catálogo a UM servidor específico (trocar de servidor = chave
 *  diferente = cache não bate, ver o pedido original do usuário). Prioriza
 *  MinecraftClient#getCurrentServerEntry() (o endereço que o jogador digitou/selecionou — igual
 *  pra Direct Connect e servidor salvo na lista), com fallback pro endereço de transporte de
 *  verdade da conexão (ClientConnection#getAddress()) se por algum motivo o ServerInfo não
 *  existir. Em singleplayer/LAN os dois são null — a feature inteira vira no-op sozinha nesse
 *  caso, sem precisar de nenhum caso especial em quem chama isso. */
public final class ClientServerIdentity {

    private ClientServerIdentity() {}

    public static String currentServerKey() {
        String raw = currentServerAddressRaw();
        if (raw == null) return null;
        return sha256Hex(raw);
    }

    /** Endereço cru (não hasheado) do servidor atual — só pra guardar como referência legível em
     *  ClientForcedTextureCache.Entry#serverAddress (debug), nunca usado como chave de verdade. */
    public static String currentServerAddressRaw() {
        Minecraft client = Minecraft.getInstance();

        ServerData info = client.getCurrentServer();
        if (info != null && info.ip != null && !info.ip.isBlank()) {
            return info.ip;
        }
        ClientPacketListener handler = client.getConnection();
        if (handler != null && handler.getConnection() != null && handler.getConnection().getRemoteAddress() != null) {
            return handler.getConnection().getRemoteAddress().toString();
        }
        return null;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
