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
            System.err.println("[GreatCosmetics] Failed to register LuckPerms group-change listener: " + e.getMessage());
        }
    }

    /** true se o LuckPerms está instalado e respondendo (pra logs de diagnóstico). */
    public static boolean isAvailable() {
        return api() != null;
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
                System.err.println("[GreatCosmetics] LuckPerms not found — the Tags system will work visually only (without applying real prefix/permissions). Install LuckPerms to enable this.");
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
            System.err.println("[GreatCosmetics] Failed to fetch prefix/suffix from LuckPerms: " + e.getMessage());
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
            System.err.println("[GreatCosmetics] Failed to list LuckPerms groups: " + e.getMessage());
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
            System.err.println("[GreatCosmetics] Failed to check LuckPerms group: " + e.getMessage());
        }
        return false;
    }

    /** ADD: coloca o player NO grupo do LuckPerms (mantém os outros grupos). true se aplicou. */
    public static boolean addToGroup(ServerPlayerEntity player, String groupName) {
        LuckPerms luckPerms = api();
        if (luckPerms == null || groupName == null || groupName.isBlank()) return false;
        try {
            User user = luckPerms.getUserManager().getUser(player.getUuid());
            if (user == null) return false;
            for (Node n : inheritanceNodeList(groupName)) user.data().add(n);
            luckPerms.getUserManager().saveUser(user);
            return true;
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Failed to add player to LuckPerms group: " + e.getMessage());
            return false;
        }
    }

    /** SET: remove TODOS os grupos herdados diretamente do player e deixa SÓ este (mesma ideia de
     *  {@code /lp user X parent set <group>}). true se aplicou. */
    public static boolean setGroupExclusive(ServerPlayerEntity player, String groupName) {
        LuckPerms luckPerms = api();
        if (luckPerms == null || groupName == null || groupName.isBlank()) return false;
        try {
            User user = luckPerms.getUserManager().getUser(player.getUuid());
            if (user == null) return false;
            // Coleta todos os nodes de herança de grupo (chave "group.<x>") e remove.
            java.util.List<Node> toRemove = new ArrayList<>();
            for (Node n : user.getNodes()) {
                if (n.getKey() != null && n.getKey().toLowerCase().startsWith("group.")) toRemove.add(n);
            }
            for (Node n : toRemove) user.data().remove(n);
            for (Node n : inheritanceNodeList(groupName)) user.data().add(n);
            try { user.setPrimaryGroup(groupName); } catch (Throwable ignored) {}
            luckPerms.getUserManager().saveUser(user);
            return true;
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Failed to set LuckPerms group: " + e.getMessage());
            return false;
        }
    }

    /** {@code List<Node>} com um InheritanceNode — mesma técnica de {@link #permissionNodesFor} pra
     *  o verificador não precisar carregar {@code InheritanceNode <: Node}. */
    private static java.util.List<Node> inheritanceNodeList(String groupName) {
        java.util.List<Node> out = new ArrayList<>();
        out.add(net.luckperms.api.node.types.InheritanceNode.builder(groupName.toLowerCase().trim()).build());
        return out;
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
            System.err.println("[GreatCosmetics] Failed to apply tag via LuckPerms: " + e.getMessage());
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
                System.err.println("[GreatCosmetics] Failed to remove tag via LuckPerms: " + e.getMessage());
            }
        }

        if (data != null && data.minecraftTag != null && !data.minecraftTag.isBlank()) {
            player.removeCommandTag(data.minecraftTag);
        }
    }

    /** Concede nodes de permissão TRANSIENT (só a sessão — nunca gravados na storage do LuckPerms,
     *  somem sozinhos no logout / restart do servidor) ao jogador. Usado pelos cosméticos, que
     *  equipam/desequipam com muito mais frequência que as tags: um node preso por crash aqui não
     *  deixa resíduo em disco. Diferente de {@link #applyTag}, que usa {@code user.data()}
     *  persistente. Sem {@code saveUser()} — transient não precisa. */
    public static void addTransientPermissions(ServerPlayerEntity player, java.util.Collection<String> nodes) {
        if (nodes == null || nodes.isEmpty()) return;
        LuckPerms luckPerms = api();
        if (luckPerms == null) return;
        try {
            User user = luckPerms.getUserManager().getUser(player.getUuid());
            if (user == null) return;
            // for (Node n : ...) emite um checkcast (o verificador confia, não carrega net.luckperms.
            // api.node.Node) + add(Node) casa exato — sem forçar o verificador a resolver
            // PermissionNode <: Node, que num client SEM LuckPerms explode com NoClassDefFoundError
            // já na verificação da classe (mesmo motivo de buildNodes montar List<Node> com add()).
            for (Node n : permissionNodesFor(nodes)) user.transientData().add(n);
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Failed to add transient permissions via LuckPerms: " + e.getMessage());
        }
    }

    /** Remove exatamente os nodes transient que {@link #addTransientPermissions} teria adicionado. */
    public static void removeTransientPermissions(ServerPlayerEntity player, java.util.Collection<String> nodes) {
        if (nodes == null || nodes.isEmpty()) return;
        LuckPerms luckPerms = api();
        if (luckPerms == null) return;
        try {
            User user = luckPerms.getUserManager().getUser(player.getUuid());
            if (user == null) return;
            for (Node n : permissionNodesFor(nodes)) user.transientData().remove(n);
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Failed to remove transient permissions via LuckPerms: " + e.getMessage());
        }
    }

    /** Monta os {@link PermissionNode} numa {@code List<Node>} via {@code List.add(Object)} — o
     *  genérico apaga em runtime, então esse método NÃO faz o verificador carregar
     *  {@code net.luckperms.api.node.Node}. Ver o comentário em {@link #addTransientPermissions}. */
    private static List<Node> permissionNodesFor(java.util.Collection<String> perms) {
        List<Node> out = new ArrayList<>();
        for (String raw : perms) {
            if (raw == null || raw.isBlank()) continue;
            String p = raw.trim();
            boolean value = true;
            // Aceita o formato de comando/yml do LuckPerms: "node true" / "node false".
            int sp = p.lastIndexOf(' ');
            if (sp > 0) {
                String tail = p.substring(sp + 1).trim().toLowerCase(java.util.Locale.ROOT);
                if (tail.equals("true") || tail.equals("false")) {
                    value = tail.equals("true");
                    p = p.substring(0, sp).trim();
                }
            }
            // "-node" = negar explicitamente (convenção comum de permissões).
            if (p.startsWith("-")) { value = false; p = p.substring(1).trim(); }
            if (p.isEmpty()) continue;
            out.add(PermissionNode.builder(p).value(value).build());
        }
        return out;
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
