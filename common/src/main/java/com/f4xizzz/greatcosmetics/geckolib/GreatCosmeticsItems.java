package com.f4xizzz.greatcosmetics.geckolib;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/** Registro dos itens próprios do mod (comum aos dois lados — chamado de GreatCosmetics#onInitialize). */
public class GreatCosmeticsItems {

    public static final GreatCosmeticsGeoItem GEO_DISPLAY = new GreatCosmeticsGeoItem(new Item.Properties().stacksTo(1));

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM, GreatCosmeticsGeoItem.ID, GEO_DISPLAY);
    }
}
