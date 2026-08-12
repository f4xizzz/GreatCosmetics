package com.f4xizzz.greatcosmetics.mixin.client;

import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import com.f4xizzz.greatcosmetics.client.gui.pages.PartyPage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void interceptPCState(CallbackInfo ci) {
        if (PartyPage.waitingForPcToOpen || PartyPage.reopenAfterPC) {
            MinecraftClient client = MinecraftClient.getInstance();
            Screen current = client.currentScreen;

            if (PartyPage.waitingForPcToOpen && current != null) {
                // O servidor abriu a tela do PC para nós
                PartyPage.waitingForPcToOpen = false;
                PartyPage.reopenAfterPC = true;

            } else if (PartyPage.reopenAfterPC && current == null) {
                // O jogador apertou ESC e a tela do PC ficou nula. Nós reabrimos o Wardrobe!
                PartyPage.reopenAfterPC = false;

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