package com.f4xizzz.greatcosmetics.mixin;

import com.f4xizzz.greatcosmetics.config.ArmorCosmeticsConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.RecipeInput;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Bloqueia o resultado de qualquer receita cujo item final foi registrado no
 * armor_cosmetics.json (ver ItemStackAttributeMixin) — sem precisar tocar nas receitas em si
 * (que podem vir de outro mod), a gente só invalida o "match" pra esses itens específicos, então
 * eles simplesmente não crafteiam mais em nenhuma mesa/inventário que use o RecipeManager.
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @Inject(method = "getFirstMatch(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/recipe/input/RecipeInput;Lnet/minecraft/world/World;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void greatcosmetics$blockConvertedArmorCrafting(
            RecipeType<T> type, I input, World world, CallbackInfoReturnable<Optional<RecipeEntry<T>>> cir) {
        try {
            Optional<RecipeEntry<T>> result = cir.getReturnValue();
            if (result == null || result.isEmpty()) return;

            ItemStack output = result.get().value().getResult(world.getRegistryManager());
            if (!output.isEmpty() && ArmorCosmeticsConfig.isConverted(output.getItem())) {
                cir.setReturnValue(Optional.empty());
            }
        } catch (Exception ignored) {}
    }
}
