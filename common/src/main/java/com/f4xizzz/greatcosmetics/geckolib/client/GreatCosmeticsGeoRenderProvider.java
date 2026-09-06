package com.f4xizzz.greatcosmetics.geckolib.client;

import com.f4xizzz.greatcosmetics.geckolib.GreatCosmeticsGeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import java.util.function.Consumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;

/**
 * Único ponto do mod que toca em GeoItemRenderer/BuiltinModelItemRenderer (client-only) — chamado
 * só a partir de GreatCosmeticsGeoItem#createGeoRenderer, que só o GeckoLib invoca no client (nunca
 * no dedicated server). Manter essa referência fora da classe do Item em si evita o server tentar
 * resolver classes client-only ao carregar o Item (comum aos dois lados).
 */
public final class GreatCosmeticsGeoRenderProvider {

    private static GeoItemRenderer<GreatCosmeticsGeoItem> RENDERER;

    private GreatCosmeticsGeoRenderProvider() {}

    public static void accept(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            @Override
            public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (RENDERER == null) {
                    RENDERER = new GeoItemRenderer<>(new GreatCosmeticsGeoModel());
                }
                return RENDERER;
            }
        });
    }
}
