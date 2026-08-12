package com.f4xizzz.greatcosmetics.geckolib;

import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * Item ÚNICO e universal — igual o carved_pumpkin+CustomModelData de hoje serve pra TODOS os
 * cosméticos "ícone chapado", essa é a versão GeckoLib: um item só, e o CustomModelData na stack
 * (mesma convenção já usada em todo o mod) diz qual modelo 3D (geo+textura, ver GeoModelRegistry)
 * desenhar. Cosméticos, armas, ferramentas e escudos com modelo GeckoLib configurado usam esse
 * item; quem não tem continua no caminho antigo (carved_pumpkin).
 *
 * createGeoRenderer() delega pra uma classe client-only (GreatCosmeticsGeoRenderProvider) — essa
 * classe (comum, registrada nos dois lados) nunca pode referenciar direto tipos client-only
 * (GeoItemRenderer, MatrixStack...) no CORPO de um método, senão o dedicated server pode falhar
 * ao carregar essa classe.
 */
public class GreatCosmeticsGeoItem extends Item implements GeoItem {

    public static final Identifier ID = Identifier.of("sascosmetics", "geo_display");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public GreatCosmeticsGeoItem(Settings settings) {
        super(settings);
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        com.f4xizzz.greatcosmetics.geckolib.client.GreatCosmeticsGeoRenderProvider.accept(consumer);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Sem animação — modelo estático (posição/rotação/escala vêm do CosmeticData.CosmeticPart,
        // aplicadas na matriz de fora, não por controlador de animação do GeckoLib).
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object itemStack) {
        return software.bernie.geckolib.util.RenderUtil.getCurrentTick();
    }
}
