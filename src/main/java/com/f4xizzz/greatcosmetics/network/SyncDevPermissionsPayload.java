package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C — permissões de dev calculadas no SERVIDOR (única fonte confiável) e sincronizadas pro
 * cliente usar nos gates visuais (botões DEV, aba Dev Studio, etc). O cliente NUNCA calcula isso
 * sozinho: MinecraftClient.player.hasPermissionLevel(N) e Permissions.check() chamados
 * diretamente no client são só o nível de OP sincronizado por pacote vanilla — com mods tipo
 * "Vanilla Permissions" instalados, esse valor podia vir incorreto (true pra qualquer jogador)
 * pra permissões que o mod não gerencia. Calculando tudo no servidor com
 * GreatCosmetics.isRealOperator() + checkPermission() (fallback booleano, nunca hasPermissionLevel)
 * e mandando o resultado pronto, o cliente só precisa confiar no que chegou.
 */
public record SyncDevPermissionsPayload(boolean isOperator, boolean hasGcDev, boolean hasGcPermDevmode) implements CustomPayload {

    public static final CustomPayload.Id<SyncDevPermissionsPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "sync_dev_permissions"));

    public static final PacketCodec<RegistryByteBuf, SyncDevPermissionsPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeBoolean(payload.isOperator);
                buf.writeBoolean(payload.hasGcDev);
                buf.writeBoolean(payload.hasGcPermDevmode);
            },
            buf -> new SyncDevPermissionsPayload(buf.readBoolean(), buf.readBoolean(), buf.readBoolean())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
