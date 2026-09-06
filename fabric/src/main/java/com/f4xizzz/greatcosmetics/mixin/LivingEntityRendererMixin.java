package com.f4xizzz.greatcosmetics.mixin;

import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Visibilidade do CORPO do jogador na Dev Studio (botão de 3 estados na barrinha lateral
 *  esquerda, ver Wardrobe3DScreen#renderGizmoSidebar/cycleCharacterAlpha).
 *
 *  HISTÓRICO (2026-09) — quatro tentativas até chegar nesta:
 *  1ª: só RenderSystem.setShaderColor com alpha, sem trocar layer — corpo continuava 100% opaco
 *  no estado "Meio", porque a RenderLayer que a pele do jogador usa (ver ponto 2 abaixo) tem uma
 *  fase própria que decide blend/opacidade — setShaderColor sozinho não é suficiente.
 *  2ª: trocar a RenderLayer da pele via @ModifyVariable no parâmetro VertexConsumerProvider de
 *  PlayerEntityRenderer#render — quebrou de DUAS formas: (a) a premissa estava errada — bytecode
 *  de PlayerEntityModel (via javap no jar vanilla mergeado do Loom) mostra que o construtor JÁ
 *  passa RenderLayer::getEntityTranslucent como layerFactory pro corpo (não getEntityCutoutNoCull
 *  — essa era a layer default de OUTROS bípedes tipo zumbi, não do jogador), então a comparação
 *  "layer == cutoutSkinLayer" nunca batia e o swap nunca fazia nada — daí "50% não funciona"; (b)
 *  o VertexConsumerProvider daquele parâmetro é a MESMA referência que o loop de FeatureRenderer
 *  (armadura + cosméticos) recebe logo depois, dentro do MESMO render() — confirmado no bytecode
 *  de LivingEntityRenderer#render (offsets ~516-525 pro corpo e ~606-629 pro loop de features, os
 *  dois fazem "aload 5" do MESMO local) — qualquer coisa que mexer nessa referência (ou no shader
 *  color global, que é lido no DRAW de verdade, não no momento de adicionar o vértice ao buffer —
 *  layers translúcidas costumam ser desenhadas de forma ADIADA) arrisca vazar pro cosmético.
 *  3ª: @ModifyArg no argumento "color" (int ARGB) da chamada EntityModel.render(...) — CRASHOU no
 *  boot: "InvalidInjectionException ... targets a method with an invalid signature". Causa: o
 *  handler tinha parâmetros extra capturados (entity, matrices, vertexConsumers, etc, pro check de
 *  identidade/estado) além do único argumento "color" sendo modificado — @ModifyArg (SINGULAR) não
 *  aceita esse "capture de argumentos do método de fora" que @Inject/@Redirect aceitam; exige a
 *  assinatura crua T handler(T arg), sem mais nada.
 *  4ª (esta): @Redirect na MESMA chamada EntityModel.render(...) inteira — substitui a chamada
 *  toda (nós decidimos o que chamar de verdade), e @Redirect SIM suporta capturar os parâmetros do
 *  método de fora (LivingEntityRenderer#render) como argumentos extras no final da assinatura do
 *  handler — só captura o "entity" (primeiro parâmetro), que é tudo que precisamos pro check de
 *  identidade. O resto da lógica é igual à 3ª tentativa: cor é um valor BAKEADO no vértice (não um
 *  uniform de shader lido depois, no draw adiado) — não depende de timing de flush, e não toca no
 *  VertexConsumerProvider nem no shader global, então é IMPOSSÍVEL vazar pro loop de FeatureRenderer
 *  (cosméticos/armadura), que nem usa esse argumento "color" — cada FeatureRenderer resolve sua
 *  própria cor por conta. Mesma técnica que o vanilla usa pra desenhar um jogador
 *  invisível-mas-visível-pra-você-mesmo com uma cor semitransparente fixa (0x27FFFFFF, ~15%, ver
 *  LivingEntityRenderer#getRenderLayer) — só que com o alpha configurável pelo usuário. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Redirect(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V")
    )
    private void greatcosmetics$renderBodyWithAlpha(EntityModel model, PoseStack matrices, VertexConsumer vertexConsumer, int light, int overlay, int color, LivingEntity entity) {
        if (entity == Minecraft.getInstance().player && GizmoManager.hasTarget() && Wardrobe3DScreen.characterAlpha < 1.0f) {
            int a = Math.round(Wardrobe3DScreen.characterAlpha * 255f) & 0xFF;
            color = (a << 24) | (color & 0x00FFFFFF);
        }
        model.renderToBuffer(matrices, vertexConsumer, light, overlay, color);
    }
}
