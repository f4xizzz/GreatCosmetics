package com.f4xizzz.greatcosmetics.client;

/** Espelho client-side das permissões de dev calculadas no servidor (ver SyncDevPermissionsPayload).
 *  Todas as GUIs de dev do mod devem ler daqui, nunca chamar hasPermissionLevel()/Permissions.check()
 *  diretamente no client. */
public class ClientPermissionCache {
    public static boolean isOperator = false;
    public static boolean hasGcDev = false;
    public static boolean hasGcPermDevmode = false;
}
