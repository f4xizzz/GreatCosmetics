package com.f4xizzz.greatcosmetics.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityPreviewMixin {

    @Inject(method = "isCrouching", at = @At("HEAD"), cancellable = true)
    private void greatcosmetics$forceSneakInPreview(CallbackInfoReturnable<Boolean> cir) {
        // Se a aba Dev estiver aberta e o botão de Sneak ativado...
        if (com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen.isDevTabActive && com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen.isPreviewSneaking) {

            // Só forçamos o agachamento no jogador local que está sendo desenhado na tela!
            if ((Object) this == Minecraft.getInstance().player) {
                cir.setReturnValue(true);
            }
        }
    }
}