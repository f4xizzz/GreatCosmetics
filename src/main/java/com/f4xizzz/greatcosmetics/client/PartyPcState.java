package com.f4xizzz.greatcosmetics.client;

/**
 * SOFT-DEP COBBLEMON: dois sinalizadores do fluxo "botão PC da aba Party" (fecha o wardrobe →
 * servidor abre o PC → ao sair do PC o wardrobe reabre na aba Party). Ficavam como estáticos em
 * {@code PartyPage}, mas essa classe toca {@code com.cobblemon.*} e é lida por código
 * sempre-carregado ({@code MinecraftClientMixin}, {@code Wardrobe3DScreen#removed}) — num servidor
 * sem Cobblemon, tocar em {@code PartyPage} explodiria com NoClassDefFoundError. Aqui não há nada
 * de Cobblemon, então pode ser lido de qualquer lugar. Sem Cobblemon esses flags nunca viram
 * {@code true} (só {@code PartyPage} os liga) e todo o fluxo é no-op.
 */
public final class PartyPcState {

    private PartyPcState() {}

    public static boolean waitingForPcToOpen = false;
    public static boolean reopenAfterPC = false;
}
