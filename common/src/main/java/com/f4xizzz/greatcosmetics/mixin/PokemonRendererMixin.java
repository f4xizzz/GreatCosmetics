package com.f4xizzz.greatcosmetics.mixin;

import com.cobblemon.mod.common.client.render.pokemon.PokemonRenderer;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import com.f4xizzz.greatcosmetics.client.gui.pages.PartyPage;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PokemonRenderer.class)
public class    PokemonRendererMixin {

    // Injeta nossa matemática de encolhimento no final (RETURN) do método original "scale" do Cobblemon
    @Inject(method = "scale", at = @At("RETURN"))
    private void applyWardrobeShrink(PokemonEntity pEntity, PoseStack pPoseStack, float pPartialTickTime, CallbackInfo ci) {
        if (Wardrobe3DScreen.isPartyTabActive) {
            // Garante que só vamos encolher o Pokémon que está no centro da tela
            if (pEntity == PartyPage.getCurrentPokemonEntity()) {
                float maxHeight = 1.2f;
                float trueHeight = pEntity.getBbHeight();

                if (trueHeight > maxHeight && trueHeight > 0) {
                    float shrink = maxHeight / trueHeight;
                    // Multiplica a matriz nativa deles pela nossa redução!
                    pPoseStack.scale(shrink, shrink, shrink);
                }
            }
        }
    }
}