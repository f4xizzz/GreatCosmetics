package com.f4xizzz.greatcosmetics.mixin.client; // Ajuste para o pacote do EAsas

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TextRenderer.class)
public class MimikeyaPriorityMixin {

    // O caractere que você definiu no JSON
    private static final char MIMIKEYA_CHAR = '\uE052';

    @Inject(method = "drawInternal(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;IIZ)I", at = @At("HEAD"))
    private void applyPriorityLayer(String text, float x, float y, int color, boolean shadow, Matrix4f matrix, VertexConsumerProvider vertexConsumers, TextRenderer.TextLayerType layerType, int backgroundColor, int light, boolean mirror, CallbackInfoReturnable<Integer> cir) {
        if (text.contains(String.valueOf(MIMIKEYA_CHAR))) {
            // Aqui a gente "empurra" a matriz de renderização para frente no eixo Z
            // Isso garante que ele fique no topo da pilha de desenho atual
            matrix.translate(0, 0, 0.01f);
        }
    }
}