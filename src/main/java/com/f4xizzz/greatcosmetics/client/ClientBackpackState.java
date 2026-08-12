package com.f4xizzz.greatcosmetics.client;

/** Estado client-side da mochila-cosmético paginada atualmente aberta (ver
 *  BackpackPageInfoPayload/BackpackPageOverlay/GreatCosmeticsClient). GenericContainerScreenHandler
 *  é 100% genérico (não carrega cosmeticId/página nenhum sozinho), então esse é o único jeito do
 *  overlay saber que mochila/página está sendo mostrada. */
public class ClientBackpackState {

    // Setado pelo receiver de BackpackPageInfoPayload, CONSUMIDO (volta a null) assim que
    // ScreenEvents.AFTER_INIT vir a tela do baú de verdade abrir (ver GreatCosmeticsClient). Nunca
    // fica "pendurado" tempo suficiente pra grudar sem querer num baú de verdade aberto depois —
    // o servidor manda esse payload SEMPRE antes de abrir a tela (nunca depois), então o próximo
    // AFTER_INIT depois de setar isso é garantido ser a tela certa.
    public static String pendingCosmeticId = null;
    public static int pendingPage = 0;
    public static int pendingTotalPages = 1;

    // Timestamp (System.currentTimeMillis()) de quando pendingCosmeticId foi setado — ver a
    // checagem de "idade" em GreatCosmeticsClient#AFTER_INIT. O comentário acima assumia que o
    // PRÓXIMO GenericContainerScreen a abrir depois disso é sempre a mochila de verdade, mas
    // GenericContainerScreenHandler é o mesmo handler genérico usado por baú de verdade E por
    // menus de OUTROS mods/plugins (ex: um menu tipo /flan) — se QUALQUER uma dessas telas abrisse
    // antes da mochila (ou a mochila nunca reabrisse por algum motivo), esse pending ficava
    // "armado" indefinidamente e a próxima tela genérica qualquer, minutos depois, herdava as
    // setas/texto de página da mochila por engano.
    public static long pendingSetAtMillis = 0L;

    // Estado da mochila-cosmético ATUALMENTE mostrada — só significa algo enquanto a tela dela
    // ainda está aberta (os hooks de render/clique em BackpackPageOverlay são por INSTÂNCIA de
    // tela e o próprio Fabric já os desliga sozinho quando ela fecha/troca, então não precisa reset
    // manual daqui pra evitar vazar pra outra tela).
    public static String currentCosmeticId = null;
    public static int currentPage = 0;
    public static int totalPages = 1;
}
