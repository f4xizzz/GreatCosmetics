package com.f4xizzz.greatcosmetics.mixin;

import com.f4xizzz.greatcosmetics.config.ArmorCosmeticsConfig;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;

/**
 * Ponto único e universal por onde QUALQUER item concede seus atributos (pontos de armadura,
 * toughness, resistência a knockback, etc) ao ser equipado — usado tanto pra armadura vanilla
 * quanto por itens de outros mods, já que no 1.21 esses bônus vêm de um DataComponent genérico,
 * não de um sistema exclusivo de ArmorItem. Cancelando aqui pros itens registrados no
 * armor_cosmetics.json, eles nunca concedem NENHUM atributo, independente do que o mod de
 * origem tenha configurado neles — viram puramente visuais, sem precisar reimplementar o item.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackAttributeMixin {

    @Inject(method = "applyAttributeModifiers", at = @At("HEAD"), cancellable = true)
    private void greatcosmetics$stripArmorCosmeticAttributes(
            EquipmentSlot slot, BiConsumer<RegistryEntry<EntityAttribute>, EntityAttributeModifier> consumer, CallbackInfo ci) {
        try {
            ItemStack self = (ItemStack) (Object) this;
            if (ArmorCosmeticsConfig.isConverted(self.getItem())) {
                ci.cancel();
            }
        } catch (Exception ignored) {}
    }
}
