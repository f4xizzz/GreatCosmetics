package com.f4xizzz.greatcosmetics.mixin.client;

import com.f4xizzz.greatcosmetics.client.ClientJoinReloadState;
import com.f4xizzz.greatcosmetics.client.PartyPcState;
import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Overlay;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SplashOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    /** Suprime a SplashOverlay (tela vermelha "MOJANG STUDIOS") quando ClientJoinReloadState já
     *  confirmou, via SyncCatalogStatePayload, que o cache local (textura + catálogo de
     *  cosméticos) pra ESSE servidor já bate com o que ele tem agora — ver o receiver desse
     *  payload em GreatCosmeticsClient. Bytecode confirma (javap no jar mergeado do Loom) que
     *  MinecraftClient#reloadResources() já cria o ResourceReload de verdade (o trabalho pesado —
     *  reler packs, reconstruir atlas de textura, bakear models) ANTES de chamar setOverlay(...);
     *  cancelar só a chamada de setOverlay aqui NÃO pula esse trabalho (ele já começou, continua
     *  rodando em background igual), só evita a tela cobrir a tudo por cima.
     *
     *  <p>Trava dura (não heurística), várias camadas: {@code ClientJoinReloadState.isJoinSync()}
     *  — o SERVIDOR diz explicitamente (via joinSync no SyncCatalogStatePayload) se este sync é
     *  uma ENTRADA no servidor ou um /gc reload ao vivo; só suprime na entrada, NUNCA num
     *  /gc reload com gente jogando, onde uma tela sumindo enquanto texturas/models são trocados
     *  por baixo de uma cena 3D ativa seria bem mais arriscado. Mais o próprio sinalizador em
     *  ClientJoinReloadState, consumido uma única vez e com um timestamp de validade. (A tentativa
     *  anterior usava uma "janela de entrada" baseada em ClientPlayConnectionEvents.JOIN + tick
     *  handler; num modpack pesado o tick handler fechava a janela ANTES dos payloads do servidor
     *  serem processados, e a supressão nunca armava.) */
    @Inject(method = "setOverlay", at = @At("HEAD"), cancellable = true)
    private void greatcosmetics$suppressRedundantSplash(Overlay overlay, CallbackInfo ci) {
        if (!(overlay instanceof SplashOverlay)) return;
        long now = System.currentTimeMillis();
        if (!ClientJoinReloadState.isJoinSync(now)) return;
        if (ClientJoinReloadState.consumeSuppressFlag(now)) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void interceptPCState(CallbackInfo ci) {
        if (PartyPcState.waitingForPcToOpen || PartyPcState.reopenAfterPC) {
            MinecraftClient client = MinecraftClient.getInstance();
            Screen current = client.currentScreen;

            if (PartyPcState.waitingForPcToOpen && current != null) {
                // O servidor abriu a tela do PC para nós
                PartyPcState.waitingForPcToOpen = false;
                PartyPcState.reopenAfterPC = true;

            } else if (PartyPcState.reopenAfterPC && current == null) {
                // O jogador apertou ESC e a tela do PC ficou nula. Nós reabrimos o Wardrobe!
                PartyPcState.reopenAfterPC = false;

                client.execute(() -> {
                    Wardrobe3DScreen screen = new Wardrobe3DScreen(true);
                    client.setScreen(screen);

                    // Com o método agora público, forçamos a aba Party a abrir
                    // com os exatos ângulos de câmera que você definiu nela!
                    screen.attemptTabSwitch(Wardrobe3DScreen.Tab.PARTY, 0.5f, 3.5, 0.6f);
                });
            }
        }
    }
}