package com.f4xizzz.greatcosmetics.client;

import com.f4xizzz.greatcosmetics.config.TagData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Espelho client-side do catálogo de tags e do que o jogador local possui/tem equipado. */
public class ClientTagsCache {

    public static Map<String, TagData> allTags = new HashMap<>();
    public static final Set<String> ownedTagIds = new HashSet<>();
    public static String equippedTagId = null;

    public static void setAllTags(Map<String, TagData> tags) {
        allTags = tags;
    }

    public static void setPlayerTags(java.util.List<String> owned, String equipped) {
        ownedTagIds.clear();
        ownedTagIds.addAll(owned);
        equippedTagId = equipped;
    }

    public static boolean hasTag(String id) {
        return ownedTagIds.contains(id);
    }

    public static boolean isEquipped(String id) {
        return id != null && id.equals(equippedTagId);
    }
}
