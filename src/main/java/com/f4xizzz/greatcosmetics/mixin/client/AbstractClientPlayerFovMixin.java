package com.f4xizzz.greatcosmetics.mixin.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.Identifier;
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
 *  original do player", já que esse desvio nem dependia do voo (só o flying=true já era coberto). */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerFovMixin {

    private static final Identifier GROUND_SPEED_MODIFIER_ID = Identifier.of("greatcosmetics", "ground_speed_boost");
    private static final Identifier SWIM_SPEED_MODIFIER_ID = Identifier.of("greatcosmetics", "swim_speed_boost");

    @Inject(method = "getFovMultiplier", at = @At("RETURN"), cancellable = true)
    private void greatcosmetics$disableCosmeticFovBoost(CallbackInfoReturnable<Float> cir) {
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;

        boolean flying = self.getAbilities().flying;

        EntityAttributeInstance speedAttr = self.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        boolean hasCosmeticSpeedBoost = speedAttr != null
                && (speedAttr.hasModifier(GROUND_SPEED_MODIFIER_ID) || speedAttr.hasModifier(SWIM_SPEED_MODIFIER_ID));

        if (flying || hasCosmeticSpeedBoost) {
            cir.setReturnValue(1.0F);
        }
    }
}
