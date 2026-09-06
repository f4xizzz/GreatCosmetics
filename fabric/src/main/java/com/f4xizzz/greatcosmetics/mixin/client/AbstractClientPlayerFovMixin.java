package com.f4xizzz.greatcosmetics.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla aumenta o FOV em +10% sempre que o player está com {@code abilities.flying=true}
 *  (ver AbstractClientPlayerEntity#getFovMultiplier() — é um multiplicador FIXO, não escala com
 *  flySpeed) — isso vale tanto pro fly de verdade (creative/spectator) quanto pro fly concedido
 *  por cosmético (EnableFly, mesmo mecanismo de PlayerAbilities).
 *
 *  O flySpeedMultiplier/groundSpeedMultiplier/swimSpeedMultiplier dos cosméticos (ver
 *  GreatCosmetics#handleSpeedLogic) NÃO devem alterar o FOV do jogador de propósito — só que o
 *  bytecode de verdade de getFovMultiplier() (decompilado do jar do jogo) mostra que o vanilla
 *  TAMBÉM multiplica pelo componente
 *  {@code (getAttributeValue(GENERIC_MOVEMENT_SPEED) / abilities.getWalkSpeed() + 1) / 2}
 *  — ou seja, ele já usa o atributo de movimento DE VERDADE (não só o walkSpeed vestigial) pra
 *  decidir o boost de FOV (é assim que o boost de sprint funciona: sprint sobe
 *  GENERIC_MOVEMENT_SPEED via modifier, sem tocar em walkSpeed, o que desvia essa razão de 1.0).
 *  ground_speed_boost/swim_speed_boost (ver GreatCosmetics#GROUND_SPEED_MODIFIER_ID/
 *  SWIM_SPEED_MODIFIER_ID) mexem exatamente nesse MESMO atributo (GENERIC_MOVEMENT_SPEED) — então
 *  qualquer cosmético com bônus de velocidade no chão/água distorcia o FOV por esse mecanismo, até
 *  ANDANDO NORMAL sem sprintar (a razão já sai de 1.0 só pelo atributo estar alterado), mesmo sem
 *  nenhuma linha nossa escrevendo em getFov() diretamente — daí a sensação de "o FOV não volta pro
 *  original do player", já que esse desvio nem dependia do voo (só o flying=true já era coberto).
 *  Pra chão/água a solução continua sendo zerar o multiplicador (1.0F) por completo — não faz
 *  sentido esses cosméticos mexerem no FOV.
 *
 *  Voando é diferente: o efeito de "FOV sobe com a velocidade" É desejado (dá a sensação de
 *  velocidade do voo), só que sem teto ele passava do razoável com cosméticos de flySpeedMultiplier
 *  alto (o mesmo componente acima, só que agora puxado pelo GENERIC_MOVEMENT_SPEED quando um
 *  cosmético de velocidade no chão/água está equipado JUNTO com o de voo — flySpeed em si é
 *  PlayerAbilities#flySpeed, um campo separado que não entra nessa conta, mas a razão sobe do
 *  mesmo jeito). Por isso, voando a gente DEIXA o multiplicador natural do vanilla passar, só bota
 *  um teto: o FOV final na tela (base do slider de Opções × multiplicador) nunca passa de
 *  MAX_FLYING_FOV_DEGREES, nunca importa o quão rápido o cosmético deixe o jogador. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerFovMixin {

    private static final ResourceLocation GROUND_SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("greatcosmetics", "ground_speed_boost");
    private static final ResourceLocation SWIM_SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("greatcosmetics", "swim_speed_boost");

    /** Teto do FOV EFETIVO (graus, na tela — base do slider de Opções × multiplicador), só
     *  enquanto voando. Pedido do usuário: acima disso o efeito de velocidade no FOV incomoda
     *  mais do que ajuda. */
    private static final float MAX_FLYING_FOV_DEGREES = 90.0F;

    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void greatcosmetics$capCosmeticFovBoost(CallbackInfoReturnable<Float> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;

        boolean flying = self.getAbilities().flying;

        AttributeInstance speedAttr = self.getAttribute(Attributes.MOVEMENT_SPEED);
        boolean hasCosmeticSpeedBoost = speedAttr != null
                && (speedAttr.hasModifier(GROUND_SPEED_MODIFIER_ID) || speedAttr.hasModifier(SWIM_SPEED_MODIFIER_ID));

        if (flying) {
            // Deixa o multiplicador natural do vanilla passar (o FOV sobe com a velocidade, efeito
            // desejado enquanto voa) — só limita o resultado FINAL em graus a
            // MAX_FLYING_FOV_DEGREES, convertendo de volta pra multiplicador em cima do FOV base
            // que o jogador escolheu nas Opções (pode ser diferente de jogador pra jogador).
            int baseFovDegrees = Minecraft.getInstance().options.fov().get();
            if (baseFovDegrees > 0) {
                float maxMultiplier = MAX_FLYING_FOV_DEGREES / baseFovDegrees;
                float vanillaMultiplier = cir.getReturnValue();
                cir.setReturnValue(Math.min(vanillaMultiplier, maxMultiplier));
            }
        } else if (hasCosmeticSpeedBoost) {
            cir.setReturnValue(1.0F);
        }
    }
}
