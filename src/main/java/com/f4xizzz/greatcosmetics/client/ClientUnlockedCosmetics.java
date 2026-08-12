package com.f4xizzz.greatcosmetics.client;

import java.util.HashSet;
import java.util.Set;

public class ClientUnlockedCosmetics {
    // Se for true (OP ou Permissão Especial), todos os itens aparecem liberados.
    public static boolean hasAllUnlocked = false;

    // Se for false, a tela checa se o ID do cosmético está nesta lista (veio do DB)
    public static final Set<String> unlockedIds = new HashSet<>();

    public static void clear() {
        hasAllUnlocked = false;
        unlockedIds.clear();
    }
}