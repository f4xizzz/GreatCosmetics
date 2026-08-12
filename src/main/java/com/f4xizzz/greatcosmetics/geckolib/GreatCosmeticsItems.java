package com.f4xizzz.greatcosmetics.geckolib;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** Registro dos itens próprios do mod (comum aos dois lados — chamado de GreatCosmetics#onInitialize). */
public class GreatCosmeticsItems {

    public static final GreatCosmeticsGeoItem GEO_DISPLAY = new GreatCosmeticsGeoItem(new Item.Settings().maxCount(1));

    public static void register() {
        Registry.register(Registries.ITEM, GreatCosmeticsGeoItem.ID, GEO_DISPLAY);
    }
}
