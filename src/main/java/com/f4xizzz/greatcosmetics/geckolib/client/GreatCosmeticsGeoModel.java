package com.f4xizzz.greatcosmetics.geckolib.client;

import com.f4xizzz.greatcosmetics.geckolib.GeoModelRegistry;
import com.f4xizzz.greatcosmetics.geckolib.GreatCosmeticsGeoItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Resolve QUAL geo/textura/animação desenhar pra stack ATUAL sendo renderizada — diferente do uso
 * comum do GeckoLib (um GeoModel por Item, resource fixo), aqui um ÚNICO GreatCosmeticsGeoItem
 * serve pra TODOS os modelos, então a resposta muda a cada chamada, lida do CustomModelData da
 * stack (ver GeoModelRegistry).
 *
 * getModelResource(T)/getTextureResource(T)/getAnimationResource(T) (1 argumento, abstratos na
 * classe base) só recebem a instância "animatable" — que aqui é sempre a MESMA (o Item é
 * singleton), sem acesso à stack. As versões de 2 argumentos (T, GeoRenderer<T>) é que recebem o
 * renderer, de onde dá pra puxar a ItemStack sendo desenhada agora (GeoItemRenderer#getCurrentItemStack)
 * — por isso são essas que a gente sobrescreve de verdade; as de 1 argumento só existem pra
 * satisfazer o compilador (nunca deveriam ser chamadas na prática).
 */
public class GreatCosmeticsGeoModel extends GeoModel<GreatCosmeticsGeoItem> {

    private static final Identifier FALLBACK_MODEL = Identifier.of("greatcosmetics", "geo/item/fallback.geo.json");
    private static final Identifier FALLBACK_TEXTURE = Identifier.of("greatcosmetics", "textures/item/fallback.png");
    private static final Identifier FALLBACK_ANIMATION = Identifier.of("greatcosmetics", "animations/item/fallback.animation.json");

    @Override
    public Identifier getModelResource(GreatCosmeticsGeoItem animatable, GeoRenderer<GreatCosmeticsGeoItem> renderer) {
        GeoModelRegistry.Entry entry = resolveEntry(renderer);
        return entry != null ? entry.geoModel() : FALLBACK_MODEL;
    }

    @Override
    public Identifier getTextureResource(GreatCosmeticsGeoItem animatable, GeoRenderer<GreatCosmeticsGeoItem> renderer) {
        GeoModelRegistry.Entry entry = resolveEntry(renderer);
        return entry != null ? entry.texture() : FALLBACK_TEXTURE;
    }

    @Override
    public Identifier getModelResource(GreatCosmeticsGeoItem animatable) {
        return FALLBACK_MODEL;
    }

    @Override
    public Identifier getTextureResource(GreatCosmeticsGeoItem animatable) {
        return FALLBACK_TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(GreatCosmeticsGeoItem animatable) {
        GeoModelRegistry.Entry entry = currentEntry;
        return entry != null && entry.animation() != null ? entry.animation() : FALLBACK_ANIMATION;
    }

    // getAnimationResource só existe na versão de 1 argumento (sem acesso ao renderer) — guarda a
    // última entry resolvida pelo getModelResource/getTextureResource (chamados sempre antes, no
    // mesmo frame, pelo pipeline de render do GeckoLib) pra reaproveitar aqui.
    private GeoModelRegistry.Entry currentEntry;

    private GeoModelRegistry.Entry resolveEntry(GeoRenderer<GreatCosmeticsGeoItem> renderer) {
        if (!(renderer instanceof GeoItemRenderer<GreatCosmeticsGeoItem> itemRenderer)) return null;

        ItemStack stack = itemRenderer.getCurrentItemStack();
        if (stack == null) return null;

        CustomModelDataComponent cmd = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (cmd == null) return null;

        GeoModelRegistry.Entry entry = GeoModelRegistry.get((int) cmd.value());
        this.currentEntry = entry;
        return entry;
    }
}
