package com.f4xizzz.greatcosmetics.mixin.client;

import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntitySneakMixin {

    // Intercepta a pergunta "Esse jogador está agachado?" e força o SIM se o botão estiver ativado
    @Inject(method = "isSneaking", at = @At("HEAD"), cancellable = true)
    private void forceWardrobeSneak(CallbackInfoReturnable<Boolean> cir) {
        if (Wardrobe3DScreen.isDevTabActive && Wardrobe3DScreen.isPreviewSneaking) {
            // Garante que só afeta o SEU jogador, e não os outros mobs na tela
            if ((Object) this == MinecraftClient.getInstance().player) {
                cir.setReturnValue(true);
            }
        }
    }
}