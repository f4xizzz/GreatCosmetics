package com.f4xizzz.greatcosmetics.util;

import net.fabricmc.loader.api.FabricLoader;

/**
 * O GreatCosmetics funciona em QUALQUER servidor Fabric. Cobblemon e LuckPerms são opcionais:
 *
 * <ul>
 *   <li>Sem <b>Cobblemon</b>: some a aba Party, o sistema de Lure/scanners e as skins de Pokémon
 *       (dados nos cosméticos ficam no arquivo, só não fazem nada — ver decisão do usuário).</li>
 *   <li>Sem <b>LuckPerms</b>: some a aba Tags e o "Chat Tags" do Dev Studio (o prefixo/permissão
 *       de tag precisa do LuckPerms; permissões de slot/comando continuam via fabric-permissions).</li>
 * </ul>
 *
 * <p>Regra de ouro pra não crashar: NENHUMA classe sempre-carregada pode referenciar
 * {@code com.cobblemon.*} nem {@code net.luckperms.*} — esse código fica em classes de integração
 * dedicadas (pacote {@code integration}) que só são tocadas quando o mod correspondente existe.
 */
public final class ModCompat {

    private ModCompat() {}

    private static final boolean COBBLEMON = FabricLoader.getInstance().isModLoaded("cobblemon");
    private static final boolean LUCKPERMS = FabricLoader.getInstance().isModLoaded("luckperms");

    public static boolean cobblemon() { return COBBLEMON; }
    public static boolean luckPerms() { return LUCKPERMS; }
}
