package com.f4xizzz.greatcosmetics.client;

import com.f4xizzz.greatcosmetics.config.CosmeticData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Espelho client-side do catálogo de armaduras convertidas em cosmético (ver ArmorCosmeticsConfig). */
public class ClientArmorCosmeticsCache {

    /** cosmeticId (id próprio, separado do item real) -> CosmeticData completo. */
    public static Map<String, CosmeticData> armorCosmetics = new HashMap<>();

    public static void setArmorCosmetics(Map<String, CosmeticData> map) {
        armorCosmetics = map;
    }

    public static CosmeticData getSyntheticCosmetic(String cosmeticId) {
        if (cosmeticId == null) return null;
        CosmeticData data = armorCosmetics.get(cosmeticId.toLowerCase());
        if (data == null) return null;

        if (data.DisplayName == null || data.DisplayName.isBlank()) {
            ResourceLocation itemIdentifier = ResourceLocation.tryParse(data.realItemId);
            Item item = itemIdentifier != null ? BuiltInRegistries.ITEM.get(itemIdentifier) : null;
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                Component itemName = new ItemStack(item).getHoverName();
                data.DisplayName = itemName.getString();
            }
        }
        return data;
    }

    public static List<CosmeticData> getAllSynthetic() {
        List<CosmeticData> list = new ArrayList<>();
        for (String id : armorCosmetics.keySet()) {
            CosmeticData data = getSyntheticCosmetic(id);
            if (data != null) list.add(data);
        }
        return list;
    }
}
