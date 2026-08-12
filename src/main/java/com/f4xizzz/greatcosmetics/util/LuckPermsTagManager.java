package com.f4xizzz.greatcosmetics.util;

import com.f4xizzz.greatcosmetics.config.TagData;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.node.types.PrefixNode;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Ponto único de contato com a API do LuckPerms. Toda chamada é blindada com try/catch: se o
 * LuckPerms não estiver instalado no servidor, o classloader nem consegue resolver
 * {@code LuckPermsProvider} (NoClassDefFoundError — um Error, não uma Exception, por isso
 * {@link #api()} usa {@code catch (Throwable)}), e o resto do mod continua funcionando
 * normalmente — só o sistema de tags fica sem efeito de prefixo/permissão real (loga um aviso
 * uma única vez).
 */
public class LuckPermsTagManager {

    public record GroupSnapshot(String name, String displayName, int weight, String prefix) {}

    private static boolean warnedMissing = false;
    private static boolean rankChangeListenerRegistered = false;

    /**
     * Assina o UserDataRecalculateEvent do LuckPerms — disparado toda vez que os dados de
     * permissão/grupo de um jogador são recalculados, o que inclui trocas de cargo feitas por FORA
     * do nosso mod (ex: /lp user X parent add Y digitado direto por um admin). Sem isso, a GUI de
     * Tags só ficava sabendo de uma tag de grupo nova/perdida quando o jogador relogava ou alguém
     * rodava /gc tags — trocar de cargo com o jogador já conectado não avisava o client. Idempotente
     * (chamar mais de uma vez não duplica a assinatura).
     */
    public static void registerRankChangeListener(net.minecraft.server.MinecraftServer server) {
        if (rankChangeListenerRegistered) return;
        LuckPerms luckPerms = api();
        if (luckPerms == null) return;

        try {
            luckPerms.getEventBus().subscribe(
                    net.luckperms.api.event.user.UserDataRecalculateEvent.class,
                    event -> server.execute(() -> {
                        ServerPlayerEntity player = server.getPlayerManager().getPlayer(event.getUser().getUniqueId());
                        if (player != null) {
                            com.f4xizzz.greatcosmetics.GreatCosmetics.validateEquippedGroupTag(player);
                            com.f4xizzz.greatcosmetics.GreatCosmetics.autoEquipCurrentGroupTag(player);
                            com.f4xizzz.greatcosmetics.GreatCosmetics.syncPlayerTags(player);
                        }
                    })
            );
            rankChangeListenerRegistered = true;
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Falha ao registrar listener de troca de cargo do LuckPerms: " + e.getMessage());
        }
    }

    private static LuckPerms api() {
        try {
            return LuckPermsProvider.get();
        } catch (Throwable e) {
            // Throwable (não só Exception) de propósito: se o jar do LuckPerms nem estiver
            // instalado no servidor, tocar em LuckPermsProvider lança NoClassDefFoundError
            // (um Error, não uma Exception) — sem isso o servidor inteiro crashava no boot.
            if (!warnedMissing) {
                warnedMissing = true;
                System.err.println("[GreatCosmetics] LuckPerms não encontrado — o sistema de Tags vai funcionar só visualmente (sem aplicar prefix/permissions reais). Instale o LuckPerms pra ativar isso.");
            }
            return null;
        }
    }

    /** Prefixo/sufixo calculado (herança de grupo já resolvida) do jogador, pra montar o nametag
     * customizado exibido na aba Party do wardrobe. Retorna strings vazias se o LuckPerms não
     * estiver instalado ou o jogador não tiver prefixo/sufixo definido. */
    public static String[] getPrefixSuffix(ServerPlayerEntity player) {
        LuckPerms luckPerms = api();
        if (luckPerms == null) return new String[]{"", ""};

        try {
            User user = luckPerms.getUserManager().getUser(player.getUuid());
            if (user == null) return new String[]{"", ""};

            String prefix = user.getCachedData().getMetaData().getPrefix();
            String suffix = user.getCachedData().getMetaData().getSuffix();
            return new String[]{prefix != null ? prefix : "", suffix != null ? suffix : ""};
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Falha ao buscar prefix/suffix do LuckPerms: " + e.getMessage());
            return new String[]{"", ""};
        }
    }

    public static List<GroupSnapshot> listGroups() {
        List<GroupSnapshot> result = new ArrayList<>();
        LuckPerms luckPerms = api();
        if (luckPerms == null) return result;

        try {
            for (Group group : luckPerms.getGroupManager().getLoadedGroups()) {
                String prefix = group.getCachedData().getMetaData().getPrefix();
                result.add(new GroupSnapshot(
                        group.getName(),
                        group.getDisplayName() != null ? group.getDisplayName() : group.getName(),
                        group.getWeight().orElse(0),
                        prefix != null ? prefix : ""
                ));
            }
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Falha ao listar grupos do LuckPerms: " + e.getMessage());
        }
        return result;
    }

    public static boolean isInGroup(ServerPlayerEntity player, String groupName) {
        LuckPerms luckPerms = api();
        if (luckPerms == null || groupName == null) return false;

        try {
            User user = luckPerms.getUserManager().getUser(player.getUuid());
            if (user == null) return false;

            for (net.luckperms.api.model.group.Group group : user.getInheritedGroups(user.getQueryOptions())) {
                if (group.getName().equalsIgnoreCase(groupName)) return true;
            }
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Falha ao checar grupo do LuckPerms: " + e.getMessage());
        }
        return false;
    }

    /** Aplica o prefixo (peso 1000) e as permissions da tag diretamente no player. */
    public static void applyTag(ServerPlayerEntity player, TagData data) {
        LuckPerms luckPerms = api();
        if (luckPerms == null || data == null) return;

        try {
            User user = luckPerms.getUserManager().getUser(player.getUuid());
            if (user == null) return;

            for (Node node : buildNodes(data)) {
                user.data().add(node);
            }

            luckPerms.getUserManager().saveUser(user);
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Falha ao aplicar tag via LuckPerms: " + e.getMessage());
        }

        if (data.minecraftTag != null && !data.minecraftTag.isBlank()) {
            player.addCommandTag(data.minecraftTag);
        }
    }

    /** Remove exatamente os nodes que {@link #applyTag} teria adicionado pra essa tag. */
    public static void removeTag(ServerPlayerEntity player, TagData data) {
        LuckPerms luckPerms = api();
        if (luckPerms != null && data != null) {
            try {
                User user = luckPerms.getUserManager().getUser(player.getUuid());
                if (user != null) {
                    for (Node node : buildNodes(data)) {
                        user.data().remove(node);
                    }
                    luckPerms.getUserManager().saveUser(user);
                }
            } catch (Exception e) {
                System.err.println("[GreatCosmetics] Falha ao remover tag via LuckPerms: " + e.getMessage());
            }
        }

        if (data != null && data.minecraftTag != null && !data.minecraftTag.isBlank()) {
            player.removeCommandTag(data.minecraftTag);
        }
    }

    private static List<Node> buildNodes(TagData data) {
        List<Node> nodes = new ArrayList<>();

        if (data.tag != null && !data.tag.isBlank()) {
            nodes.add(PrefixNode.builder(data.tag, 1000).build());
        }

        if (data.permissions != null) {
            for (String permission : data.permissions) {
                if (permission == null || permission.isBlank()) continue;
                nodes.add(PermissionNode.builder(permission.trim()).value(true).build());
            }
        }

        return nodes;
    }
}
