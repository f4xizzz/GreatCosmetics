package com.f4xizzz.greatcosmetics.mixin;

import com.f4xizzz.greatcosmetics.config.ArmorCosmeticsConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Bloqueia o resultado de qualquer receita cujo item final foi registrado no
 * armor_cosmetics.json (ver ItemStackAttributeMixin) — sem precisar tocar nas receitas em si
 * (que podem vir de outro mod), a gente só invalida o "match" pra esses itens específicos, então
 * eles simplesmente não crafteiam mais em nenhuma mesa/inventário que use o RecipeManager.
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void greatcosmetics$blockConvertedArmorCrafting(
            RecipeType<T> type, I input, Level world, CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        try {
            Optional<RecipeHolder<T>> result = cir.getReturnValue();
            if (result == null || result.isEmpty()) return;

            ItemStack output = result.get().value().getResultItem(world.registryAccess());
            if (!output.isEmpty() && ArmorCosmeticsConfig.isConverted(output.getItem())) {
                cir.setReturnValue(Optional.empty());
            }
        } catch (Exception ignored) {}
    }
}
