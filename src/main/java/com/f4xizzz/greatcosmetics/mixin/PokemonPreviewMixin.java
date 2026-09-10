package com.f4xizzz.greatcosmetics.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import com.f4xizzz.greatcosmetics.client.gui.pages.PartyPage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SOFT-DEP COBBLEMON: troca o modelo do jogador local pelo Pokémon selecionado enquanto a aba
 * Party do wardrobe está aberta. Toca {@code com.cobblemon.*}, então é um mixin separado que o
 * {@link com.f4xizzz.greatcosmetics.security.GreatCosmeticsMixinPlugin} NÃO aplica quando o
 * Cobblemon está ausente (junto de {@code PokemonRendererMixin}). A parte de nametag da aba Tags
 * ficou em {@link PlayerEntityRendererMixin} (sem Cobblemon).
 */
@Mixin(PlayerEntityRenderer.class)
public class PokemonPreviewMixin {

    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"), cancellable = true)
    private void swapPlayerWithPokemon(AbstractClientPlayerEntity player, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (player == MinecraftClient.getInstance().player && Wardrobe3DScreen.isPartyTabActive) {
            ci.cancel();

            PokemonEntity poke = PartyPage.getCurrentPokemonEntity();

            if (poke != null) {
                // Esse render substitui o player inteiro na tela (ci.cancel() já rodou acima) — uma
                // exceção sem captura aqui dentro de um @Inject de Mixin sobe direto pro loop
                // principal de render do jogo e derruba o client inteiro. Algumas combinações de
                // species+aspect (ver pose/aspect resolvidos em PokemonClientDelegate) fazem o
                // Cobblemon lançar durante poke.tick()/render() em vez de cair no fallback
                // "Substitute" — captura aqui pra, na pior das hipóteses, só fechar a wardrobe em
                // vez de crashar o jogo do player.
                try {
                    if (poke.age != player.age) {
                        poke.tick();
                        poke.age = player.age;
                    }

                    poke.setPos(player.getX(), player.getY(), player.getZ());
                    poke.prevX = player.prevX;
                    poke.prevY = player.prevY;
                    poke.prevZ = player.prevZ;

                    poke.setBodyYaw(player.bodyYaw);
                    poke.prevBodyYaw = player.prevBodyYaw;
                    poke.setHeadYaw(player.headYaw);
                    poke.prevHeadYaw = player.prevHeadYaw;
                    poke.setPitch(player.getPitch());
                    poke.prevPitch = player.prevPitch;

                    MinecraftClient.getInstance().getEntityRenderDispatcher().render(poke, 0.0, 0.0, 0.0, yaw, tickDelta, matrices, vertexConsumers, light);
                } catch (Exception t) {
                    System.err.println("[GreatCosmetics] Error rendering Pokémon in the Party preview — closing the wardrobe to avoid crashing the client. " + t);
                    MinecraftClient.getInstance().setScreen(null);
                }
            }
        }
    }
}
