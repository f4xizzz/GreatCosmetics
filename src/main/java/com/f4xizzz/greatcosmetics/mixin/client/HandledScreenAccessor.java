package com.f4xizzz.greatcosmetics.mixin.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Expõe x/y/backgroundWidth/backgroundHeight (protected em HandledScreen) — precisa disso pra
 *  desenhar as setas/texto de página da mochila (ver BackpackPageOverlay) coladas na borda do baú
 *  vanilla, em qualquer resolução/GUI Scale, sem duplicar a conta de centralização que o próprio
 *  HandledScreen já faz no init(). */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x")
    int greatcosmetics$getX();

    @Accessor("y")
    int greatcosmetics$getY();

    @Accessor("backgroundWidth")
    int greatcosmetics$getBackgroundWidth();

    @Accessor("backgroundHeight")
    int greatcosmetics$getBackgroundHeight();
}
