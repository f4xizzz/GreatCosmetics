package com.f4xizzz.greatcosmetics.client;

import com.f4xizzz.greatcosmetics.config.CosmeticData;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            Identifier itemIdentifier = Identifier.tryParse(data.realItemId);
            Item item = itemIdentifier != null ? Registries.ITEM.get(itemIdentifier) : null;
            if (item != null && item != net.minecraft.item.Items.AIR) {
                Text itemName = new ItemStack(item).getName();
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
