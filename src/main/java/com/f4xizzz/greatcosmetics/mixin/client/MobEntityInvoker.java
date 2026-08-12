package com.f4xizzz.greatcosmetics.mixin.client;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.sound.SoundEvent;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MobEntity.class)
public interface MobEntityInvoker {

    /**
     * Interface gerada via Mixin para acessar o método protegido de som ambiente.
     * O Fabric resolve a ofuscação dinamicamente, sem precisar de Reflection!
     */
    @Invoker("getAmbientSound")
    @Nullable
    SoundEvent invokeGetAmbientSound();

}