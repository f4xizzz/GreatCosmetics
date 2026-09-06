package com.f4xizzz.greatcosmetics.client;

import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/** Detecta se uma entidade está sendo desenhada em modo de preview do Dev Studio (aba Dev aberta,
 *  olhando o próprio jogador) — usado por ArmorFeatureRendererMixin pra saber se deve mostrar só o
 *  cosmético em edição (Wardrobe3DScreen.previewCosmeticId) em vez dos equipados de verdade. */
public class ArmorCosmeticResolver {

    public static boolean isDevPreview(LivingEntity entity) {
        return Wardrobe3DScreen.isDevTabActive && entity == Minecraft.getInstance().player;
    }
}
