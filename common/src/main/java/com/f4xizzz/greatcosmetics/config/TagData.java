package com.f4xizzz.greatcosmetics.config;

import java.util.ArrayList;
import java.util.List;

public class TagData {
    /** Preenchido automaticamente com a chave do mapa no load — nunca serializado com valor divergente. */
    public transient String id;

    public String displayName = "";
    public String description = "";

    /** O prefixo (MiniMessage) que fica no player enquanto a tag está equipada. */
    public String tag = "";

    /** Permissions que ficam true (node direto no player) enquanto a tag está equipada. */
    public List<String> permissions = new ArrayList<>();

    /** Tag de scoreboard vanilla aplicada/removida junto com a tag. */
    public String minecraftTag = "";

    /** true = importada de um grupo do LuckPerms (não pode ser deletada, ver TagsConfig). */
    public boolean isGroupTag = false;

    /** Peso pra ordenação — só relevante pra tags de grupo (espelha o weight do grupo no LuckPerms). */
    public int weight = 0;

    public TagData() {}
}
