package com.f4xizzz.greatcosmetics.client;

/** Espelho client-side das permissões de dev calculadas no servidor (ver SyncDevPermissionsPayload).
 *  Todas as GUIs de dev do mod devem ler daqui, nunca chamar hasPermissionLevel()/Permissions.check()
 *  diretamente no client. */
public class ClientPermissionCache {
    public static boolean isOperator = false;
    public static boolean hasGcDev = false;
    public static boolean hasGcPermDevmode = false;

    // SOFT-DEP: o servidor diz (via SyncDevPermissionsPayload, no join) se ELE tem Cobblemon /
    // LuckPerms. A aba Party só aparece se o servidor E o client tiverem Cobblemon (o client não
    // consegue nem instanciar a PartyPage sem — ver Wardrobe3DScreen.init). A aba Tags e o
    // "Chat Tags" do Dev Studio só aparecem se o SERVIDOR tiver LuckPerms (o client nunca precisa
    // dele — a aplicação de tag é 100% server-side). Default false = escondido até o join sincronizar.
    public static boolean serverHasCobblemon = false;
    public static boolean serverHasLuckPerms = false;
}
