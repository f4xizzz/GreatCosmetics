package com.f4xizzz.greatcosmetics.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.f4xizzz.greatcosmetics.client.ClientNameTagCache;
import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import com.f4xizzz.greatcosmetics.client.gui.pages.PartyPage;
import com.f4xizzz.greatcosmetics.util.TextUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public class PlayerEntityRendererMixin {

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    private void swapPlayerWithPokemon(AbstractClientPlayer player, float yaw, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, CallbackInfo ci) {
        if (player == Minecraft.getInstance().player && Wardrobe3DScreen.isPartyTabActive) {
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
                    if (poke.tickCount != player.tickCount) {
                        poke.tick();
                        poke.tickCount = player.tickCount;
                    }

                    poke.setPosRaw(player.getX(), player.getY(), player.getZ());
                    poke.xo = player.xo;
                    poke.yo = player.yo;
                    poke.zo = player.zo;

                    poke.setYBodyRot(player.yBodyRot);
                    poke.yBodyRotO = player.yBodyRotO;
                    poke.setYHeadRot(player.yHeadRot);
                    poke.yHeadRotO = player.yHeadRotO;
                    poke.setXRot(player.getXRot());
                    poke.xRotO = player.xRotO;

                    Minecraft.getInstance().getEntityRenderDispatcher().render(poke, 0.0, 0.0, 0.0, yaw, tickDelta, matrices, vertexConsumers, light);
                } catch (Exception t) {
                    System.err.println("[GreatCosmetics] Error rendering Pokémon in the Party preview — closing the wardrobe to avoid crashing the client. " + t);
                    Minecraft.getInstance().setScreen(null);
                }
            }
        }
    }

    // Visibilidade do CORPO do jogador (botão de 3 estados na barrinha lateral esquerda, ver
    // Wardrobe3DScreen#renderGizmoSidebar/cycleCharacterAlpha) — a implementação de verdade foi
    // pro LivingEntityRendererMixin (ver lá o porquê: tentar fazer isso aqui via RenderSystem.
    // setShaderColor/troca de RenderLayer causou DUAS rodadas de bug — corpo continuava opaco no
    // "Meio", e a versão seguinte fazia os cosméticos SUMIREM junto — porque tanto o shader color
    // global quanto o VertexConsumerProvider trocado aqui são compartilhados com o loop de
    // FeatureRenderer (armadura/cosméticos), que usa a MESMA referência local. A técnica certa
    // (bytecode-confirmada) é modificar só o argumento "color" da chamada EntityModel.render(...)
    // dentro de LivingEntityRenderer.render() via @ModifyArg — não toca no VertexConsumerProvider
    // nem no shader global nenhuma vez, então não pode vazar pros cosméticos.

    // Na aba Tags o player renderiza NORMALMENTE (esse mixin não cancela nada aqui, ao contrário
    // da Party) — mas mesmo assim o vanilla nunca mostra o próprio nametag: LivingEntityRenderer.
    // hasLabel() retorna false sempre que a entidade == a câmera atual, e na Wardrobe a câmera é
    // o próprio player (terceira pessoa "fake"). TAIL (não cancela) pra rodar DEPOIS do render de
    // verdade — nesse ponto as matrizes já voltaram pro mesmo estado de origem do HEAD (todo
    // push/pop interno do render() é balanceado), então dá pra reusar a mesma rotina de desenho.
    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("TAIL"))
    private void greatcosmetics$renderNameTagOnTagsPage(AbstractClientPlayer player, float yaw, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, CallbackInfo ci) {
        if (player == Minecraft.getInstance().player && Wardrobe3DScreen.isTagsTabActive) {
            greatcosmetics$renderNameTag(player, matrices, vertexConsumers, light);
        }
    }

    private void greatcosmetics$renderNameTag(AbstractClientPlayer player, PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.font == null) return;

        String prefix;
        String suffix;

        // Preview de Dev na aba Tags: mostra no nametag o prefixo da tag sendo pré-visualizada
        // (100% client-side, igual o resto do preview — nunca aplica de verdade via LuckPerms).
        // TagData não tem campo de suffix próprio (só o prefixo aparece no preview).
        String previewId = com.f4xizzz.greatcosmetics.client.gui.pages.TagsPage.devPreviewEquippedId;
        com.f4xizzz.greatcosmetics.config.TagData previewTag = previewId != null
                ? com.f4xizzz.greatcosmetics.client.ClientTagsCache.allTags.get(previewId)
                : null;

        if (previewTag != null) {
            prefix = previewTag.tag;
            suffix = "";
        } else {
            prefix = ClientNameTagCache.prefix;
            suffix = ClientNameTagCache.suffix;
        }

        String raw = (prefix == null ? "" : prefix) + client.player.getName().getString() + (suffix == null ? "" : suffix);
        Component label = TextUtils.parseToText(raw, client.level.registryAccess());

        // +0.7 (não +0.3): a câmera da Tags page fica bem perto da cabeça (zoom fechado, focado no
        // rosto) — um offset pensado pra distância normal de terceira pessoa ficava pequeno
        // demais de perto e o texto acabava sobrepondo a cabeça do modelo.
        double anchorHeight = player.getEyeHeight() + 0.7;

        matrices.pushPose();
        matrices.translate(0.0, anchorHeight, 0.0);
        matrices.mulPose(client.getEntityRenderDispatcher().cameraOrientation());
        matrices.scale(0.025f, -0.025f, 0.025f);

        Font tr = client.font;
        float bgOpacity = client.options.getBackgroundOpacity(0.25f);
        int bgColor = (int) (bgOpacity * 255.0f) << 24;
        Matrix4f matrix = matrices.last().pose();
        float x = -tr.width(label) / 2f;

        tr.drawInBatch(label, x, 0f, 0x20FFFFFF, false, matrix, vertexConsumers, Font.DisplayMode.SEE_THROUGH, bgColor, light);
        tr.drawInBatch(label, x, 0f, -1, false, matrix, vertexConsumers, Font.DisplayMode.NORMAL, 0, light);

        matrices.popPose();
    }
}