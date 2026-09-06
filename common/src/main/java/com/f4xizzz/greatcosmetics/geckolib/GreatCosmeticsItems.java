package com.f4xizzz.greatcosmetics.geckolib;

import com.f4xizzz.greatcosmetics.GreatCosmeticsCommon;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;

/** Registro do item próprio do mod, via Architectury DeferredRegister (NeoForge não aceita
 *  {@code Registry.register} direto — registro congelado). Chamado de {@code GreatCosmeticsServer#init}. */
public class GreatCosmeticsItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(GreatCosmeticsCommon.MOD_ID, Registries.ITEM);

    // ID = sascosmetics:geo_display (namespace legado — não muda sem quebrar resourcepack/dados).
    public static final RegistrySupplier<Item> GEO_DISPLAY = ITEMS.register(
            GreatCosmeticsGeoItem.ID,
            () -> new GreatCosmeticsGeoItem(new Item.Properties().stacksTo(1)));

    public static void register() {
        ITEMS.register();
    }
}
