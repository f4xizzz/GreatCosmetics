package com.f4xizzz.greatcosmetics.config;

import com.f4xizzz.greatcosmetics.util.TextUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Todo texto voltado ao jogador (GUIs e comandos) do mod. Cada string fica num JSON editável em
 * {@code config/GreatCosmetics/lang/<area>.json} — o dono do servidor troca idioma, cor,
 * formatação (tudo via MiniMessage: {@code <red>}, {@code <gradient>}, {@code <hover>},
 * {@code <#rrggbb>}, e ainda os códigos legados {@code &}/{@code §}) sem recompilar nada.
 *
 * <p>NÃO há sync por rede: cliente e servidor leem, cada um, a própria pasta
 * {@code config/GreatCosmetics/lang/} do disco. Isso é intencional — o modpack distribui a
 * config junto. Se as duas divergirem, cada lado mostra a sua (as GUIs usam o do cliente, o chat
 * usa o do servidor).
 *
 * <p>Mensagens de console ({@code debugLog}/{@code LOGGER}/{@code System.err}) NÃO passam por
 * aqui — ficam fixas em inglês no código, com a tag {@code [GreatCosmetics]}.
 */
public class LangConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final File LANG_DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), "GreatCosmetics/lang");
    private static final File LEGACY_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "GreatCosmetics/lang.json");
    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, String>>() {}.getType();

    /** Todas as chaves resolvidas, mescladas de todos os arquivos. */
    private static final Map<String, String> messages = new ConcurrentHashMap<>();

    public static void load() {
        messages.clear();
        if (!LANG_DIR.exists()) LANG_DIR.mkdirs();

        Map<String, String> legacyCustom = readLegacySingleFile();

        for (Map.Entry<String, LinkedHashMap<String, String>> file : buildDefaults().entrySet()) {
            File f = new File(LANG_DIR, file.getKey() + ".json");
            LinkedHashMap<String, String> onDisk = new LinkedHashMap<>();
            if (f.exists()) {
                try (FileReader reader = new FileReader(f)) {
                    LinkedHashMap<String, String> parsed = GSON.fromJson(reader, MAP_TYPE);
                    if (parsed != null) onDisk = parsed;
                } catch (Exception e) {
                    System.err.println("[GreatCosmetics] Failed to read lang file " + f.getName() + " — recreating from defaults. " + e);
                }
            }

            LinkedHashMap<String, String> merged = new LinkedHashMap<>();
            boolean changed = false;
            for (Map.Entry<String, String> def : file.getValue().entrySet()) {
                String key = def.getKey();
                if (onDisk.containsKey(key)) {
                    merged.put(key, onDisk.get(key));
                } else if (legacyCustom.containsKey(key)) {
                    merged.put(key, legacyCustom.get(key));
                    changed = true;
                } else {
                    merged.put(key, def.getValue());
                    changed = true;
                }
            }
            // Preserva chaves extras que o dono do servidor adicionou à mão.
            for (Map.Entry<String, String> extra : onDisk.entrySet()) {
                if (!merged.containsKey(extra.getKey())) merged.put(extra.getKey(), extra.getValue());
            }

            if (!f.exists() || changed) {
                try (FileWriter writer = new FileWriter(f)) {
                    GSON.toJson(merged, writer);
                } catch (Exception e) {
                    System.err.println("[GreatCosmetics] Failed to write lang file " + f.getName() + " — " + e);
                }
            }

            messages.putAll(merged);
        }

        // Aposenta o lang.json antigo (chave única) uma vez que os valores customizados já foram
        // migrados pros arquivos novos — deixa um .migrated pra não perder nada.
        if (LEGACY_FILE.exists()) {
            File dst = new File(LEGACY_FILE.getParentFile(), "lang.json.migrated");
            if (dst.exists()) dst.delete();
            if (!LEGACY_FILE.renameTo(dst)) LEGACY_FILE.delete();
        }
    }

    /** Compat com chamadas antigas. */
    public static void loadLang() {
        load();
    }

    // Remapeia as ~13 chaves do lang.json antigo (chave única, plana) pras chaves novas —
    // valores que o jogador já customizou são importados uma vez; a chave "prefix" é descartada
    // (o mod não usa mais prefixo em mensagem de jogador).
    private static Map<String, String> readLegacySingleFile() {
        Map<String, String> out = new LinkedHashMap<>();
        if (!LEGACY_FILE.exists()) return out;

        Map<String, String> old;
        try (FileReader reader = new FileReader(LEGACY_FILE)) {
            old = GSON.fromJson(reader, MAP_TYPE);
        } catch (Exception e) {
            return out;
        }
        if (old == null) return out;

        Map<String, String> remap = new LinkedHashMap<>();
        remap.put("wardrobe_title", "wardrobe.title");
        remap.put("wardrobe_unconfigured_name", "items.unconfigured_name");
        remap.put("wardrobe_unconfigured_lore", "items.unconfigured_lore");
        remap.put("wardrobe_label_id", "items.label_id");
        remap.put("wardrobe_label_slot", "items.label_slot");
        remap.put("wardrobe_label_permission", "items.label_permission");
        remap.put("wardrobe_backpack_on", "items.backpack_on");
        remap.put("wardrobe_backpack_off", "items.backpack_off");
        remap.put("wardrobe_fly_on", "items.fly_on");
        remap.put("wardrobe_fly_off", "items.fly_off");
        remap.put("wardrobe_click_to_get", "items.click_to_get");
        remap.put("fly_enabled", "messages.fly.enabled");
        remap.put("fly_disabled", "messages.fly.disabled");
        remap.put("receive_item", "commands.give.received");
        remap.put("lore_slot", "items.lore.slot");

        for (Map.Entry<String, String> e : remap.entrySet()) {
            if (old.containsKey(e.getKey())) out.put(e.getValue(), old.get(e.getKey()));
        }
        return out;
    }

    // ==========================================
    // RESOLUÇÃO
    // ==========================================

    /** String MiniMessage crua da chave, com placeholders {@code {nome}} substituídos.
     *  {@code ph} são pares alternados (nome, valor): {@code raw("x", "item", stack, "slot", 3)}. */
    public static String raw(String key, Object... ph) {
        String value = messages.getOrDefault(key, key);
        for (int i = 0; i + 1 < ph.length; i += 2) {
            value = value.replace("{" + ph[i] + "}", String.valueOf(ph[i + 1]));
        }
        return value;
    }

    /** String legada ({@code §}) pronta pro {@code DrawContext.drawText*} das GUIs (sem registries). */
    public static String legacy(String key, Object... ph) {
        return TextUtils.parseToString(raw(key, ph));
    }

    /** {@link Text} pra tooltip/placeholder de GUI — resolvido via legado {@code §}, sem registries. */
    public static MutableText text(String key, Object... ph) {
        return Text.literal(legacy(key, ph));
    }

    /** {@link Text} rico (com {@code <hover>}/{@code <click>} de verdade) pro chat — precisa de registries. */
    public static Text chat(String key, RegistryWrapper.WrapperLookup registries, Object... ph) {
        return TextUtils.parseToText(raw(key, ph), registries);
    }

    // ---- Compat com os 3 call sites antigos ----
    public static String getRaw(String key) {
        return messages.getOrDefault(key, "Missing: " + key);
    }

    public static String get(String key) {
        return messages.getOrDefault(key, key);
    }

    // ==========================================
    // DEFAULTS (inglês) — um bloco por arquivo em config/GreatCosmetics/lang/
    // ==========================================
    private static LinkedHashMap<String, LinkedHashMap<String, String>> buildDefaults() {
        LinkedHashMap<String, LinkedHashMap<String, String>> files = new LinkedHashMap<>();

        // ---------------- general.json ----------------
        LinkedHashMap<String, String> general = new LinkedHashMap<>();
        general.put("general.error.cosmetic_not_found", "<red>Cosmetic '{id}' not found!");
        general.put("general.error.skin_not_found", "<red>Pokémon skin '{id}' not found in greatcosmetics_skins.json!");
        general.put("general.error.tag_not_found", "<red>Tag '{id}' not found!");
        general.put("general.error.no_permission", "<red>[!] You don't have permission to use this command.");
        general.put("general.reload.start", "<yellow>Starting reload...");
        general.put("general.reload.done", "<green>Reload complete! Cosmetics and skins synced with {count} players.");
        general.put("general.reload.fail", "<red>Reload failed! Check the console.");
        general.put("general.reload.texture_hash_fail", "<red>Failed to recompute the forced resource pack SHA1: {error}");
        general.put("general.debug.enabled", "<green>Debug mode <green>ENABLED<green>. Messages will appear in the server console.");
        general.put("general.debug.disabled", "<yellow>Debug mode <red>DISABLED<yellow>.");
        general.put("general.license.locked", "<red>This server has no valid GreatCosmetics license. Cosmetics are disabled.");
        general.put("general.license.locked_cmd_1", "<red>This server doesn't have a valid GreatCosmetics license!");
        general.put("general.license.locked_cmd_2", "<red>Commands and menus are locked. Purchase a license to unlock.");
        general.put("general.license.locked_cmd_3", "<red>Use: <green>/gc activation <your_key>");
        general.put("general.license.activating", "<yellow>Contacting the activation server...");
        general.put("general.license.activate_success", "<green>Activated! GreatCosmetics is now unlocked for this server IP.");
        general.put("general.license.activate_fail", "<red>Activation failed. Invalid key, or the key is bound to another IP.");
        general.put("general.license.singleplayer_only", "<red>This command only works on dedicated servers (singleplayer is already active).");
        files.put("general", general);

        // ---------------- commands.json ----------------
        LinkedHashMap<String, String> commands = new LinkedHashMap<>();
        commands.put("commands.uuid.no_target", "<red>Look at an entity or NPC (max 5 blocks) to get its UUID!");
        commands.put("commands.uuid.result", "<green>Entity: <white>{name} <gray>| <green>UUID: <aqua><click:copy_to_clipboard:'{uuid}'><hover:show_text:'<yellow>Click to copy!'><underlined>{uuid}</underlined></hover></click>");
        commands.put("commands.give.received", "<green>You received the cosmetic: <white>{item}");
        commands.put("commands.give.success_other", "<green>Cosmetic '{id}' unlocked for {player}!");
        commands.put("commands.give.already_has", "<yellow>{player} already has the cosmetic '{id}'!");
        commands.put("commands.forceequip.success", "<green>Cosmetic '{id}' force-equipped on {player} (ignoring slot limit)!");
        commands.put("commands.forceunequip.success", "<green>Cosmetic '{id}' unequipped from {player}!");
        commands.put("commands.forceunequip.not_equipped", "<yellow>{player} didn't have that cosmetic equipped.");
        commands.put("commands.tag.given", "<green>Tag '{id}' granted to {player}!");
        commands.put("commands.tag.received", "<green>You received the tag: <white>{name}");
        commands.put("commands.tag.already_has", "<yellow>{player} already has the tag '{id}'!");
        commands.put("commands.tag.removed", "<green>Tag '{id}' removed from {player}!");
        commands.put("commands.tag.not_has", "<yellow>{player} doesn't have the tag '{id}'!");
        commands.put("commands.tag.group_cannot_give", "<red>Group tags can't be given by command — ownership comes from being in the group in LuckPerms.");
        commands.put("commands.tag.group_cannot_remove", "<red>Group tags can't be removed by command.");
        commands.put("commands.giveitem.inv_full", "<yellow>Inventory full! The item '{id}' dropped on the ground at {player}.");
        commands.put("commands.giveitem.success", "<green>You gave the physical cosmetic item '{id}' to {player}!");
        commands.put("commands.remove.success", "<green>Cosmetic '{id}' removed from {player}!");
        commands.put("commands.remove.not_has", "<yellow>{player} doesn't have the cosmetic '{id}'!");
        commands.put("commands.giveskin.received", "<green>You unlocked the Pokémon skin: <white>{name}<green>!");
        commands.put("commands.giveskin.success_other", "<green>Skin '{id}' unlocked for {player}!");
        commands.put("commands.giveskin.already_has", "<yellow>{player} already has the skin '{id}'!");
        commands.put("commands.removeskin.success", "<green>Pokémon skin '{id}' removed from {player}!");
        commands.put("commands.removeskin.not_has", "<yellow>{player} doesn't have the Pokémon skin '{id}'!");
        commands.put("commands.npc.not_found", "<red>Cosmetic not found!");
        commands.put("commands.npc.equip_no_target", "<red>Look at an NPC or Armor Stand (max 5 blocks) to equip!");
        commands.put("commands.npc.remove_no_target", "<red>Look at an NPC or Armor Stand (max 5 blocks) to remove!");
        commands.put("commands.npc.invalid_slot", "<red>Invalid slot! Use: head, face, neck, chest, back, waist, legs, feet or hand.");
        commands.put("commands.npc.equipped", "<green>Cosmetic successfully equipped on the target!");
        commands.put("commands.npc.slot_cleared", "<green>The target's {slot} slot was cleared!");
        commands.put("commands.display.not_found", "<red>Cosmetic '{id}' not found!");
        commands.put("commands.display.spawned", "<green>Display spawned wearing '{id}'. Aim at it and use <white>/gc display remove</white> to delete it.");
        commands.put("commands.display.spawn_failed", "<red>Couldn't spawn the display here.");
        commands.put("commands.display.removed", "<green>Display removed.");
        commands.put("commands.display.remove_no_target", "<red>Look at a display (max 5 blocks) to remove it.");
        commands.put("commands.display.cleared", "<green>{count} display(s) removed.");
        commands.put("commands.wardrobe.background_set", "<green>Background '{name}' set and saved to disk!");
        commands.put("commands.wardrobe.dimension_not_found", "<red>[ERROR] The studio dimension '{dimension}' was not found!");
        files.put("commands", commands);

        // ---------------- messages.json ----------------
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("messages.cosmetic.equipped", "<green>Accessory equipped!");
        m.put("messages.cosmetic.unequipped", "<red>Accessory unequipped!");
        m.put("messages.cosmetic.not_owned", "<red>[!] You don't own this cosmetic!");
        m.put("messages.cosmetic.slot_limit", "<red>[!] You've reached the item limit for the {slot} slot (Limit: {limit}).");
        m.put("messages.cosmetic.type_limit", "<red>[!] You've reached the limit for the accessory type: {type} (Limit: {limit}).");
        m.put("messages.cosmetic.all_removed", "<green>All accessories were removed!");
        m.put("messages.cosmetic.test_removed", "<red>One or more test cosmetics were removed because you no longer have OP/Dev Mode access and never truly unlocked them.");
        m.put("messages.cosmetic.converted_wardrobe", "<green>Your {name} became a cosmetic! It was removed from your inventory and unlocked in your wardrobe (/wardrobe, or visit the clothing shop!).");
        m.put("messages.cosmetic.converted_already", "<yellow>Your {name} became a cosmetic and was removed from your inventory (you already had it unlocked in the wardrobe).");
        m.put("messages.cosmetic.migrated", "<green>Your old cosmetic was automatically migrated to: <white>{name}<green>!");
        m.put("messages.fly.enabled", "<green>✈ Flight enabled!");
        m.put("messages.fly.disabled", "<red>✈ Flight disabled!");
        m.put("messages.tag.equipped", "<green>Tag equipped!");
        m.put("messages.tag.removed", "<red>Tag removed!");
        m.put("messages.tag.not_owned", "<red>[!] You don't own this tag!");
        m.put("messages.tag.removed_role_fallback", "<green>Tag removed! Reverted to your current role's tag.");
        m.put("messages.tag.cannot_remove_role", "<red>[!] You can't remove your current role's tag!");
        m.put("messages.tag.group_lost", "<red>You lost the tag '{name}<red>' because you're no longer in the group '{id}'.");
        m.put("messages.tag.no_perm_edit", "<red>[!] You don't have permission to edit tags.");
        m.put("messages.tag.id_empty", "<red>[!] Tag ID can't be empty.");
        m.put("messages.tag.saved", "<green>Tag '{id}' saved successfully!");
        m.put("messages.tag.no_perm_delete", "<red>[!] You don't have permission to delete tags.");
        m.put("messages.tag.group_cannot_delete", "<red>[!] Group tags can't be deleted.");
        m.put("messages.tag.deleted", "<green>Tag '{id}' deleted.");
        m.put("messages.effect.no_perm_edit", "<red>[!] You don't have permission to edit effects.");
        m.put("messages.effect.id_empty", "<red>[!] Effect ID can't be empty.");
        m.put("messages.effect.saved", "<green>Effect '{id}' saved successfully!");
        m.put("messages.effect.no_perm_delete", "<red>[!] You don't have permission to delete effects.");
        m.put("messages.effect.deleted", "<green>Effect '{id}' deleted.");
        m.put("messages.skin.removed_success", "<green>Skins removed successfully!");
        m.put("messages.skin.none_detected", "<yellow>No skin detected on this Pokémon.");
        m.put("messages.skin.incompatible", "<red>This Pokémon is not compatible with this skin!");
        m.put("messages.skin.on_cooldown", "<red>This skin is on cooldown!");
        m.put("messages.skin.applied", "<green>Skin applied successfully!");
        m.put("messages.devstudio.save_config_fail", "<red>Failed to save config: {error}");
        m.put("messages.devstudio.save_cosmetic_fail", "<red>Failed to save cosmetic: {error}");
        m.put("messages.devstudio.delete_cosmetic_fail", "<red>Failed to delete cosmetic: {error}");
        m.put("messages.devstudio.cosmetic_deleted", "<green>Cosmetic '{id}' deleted.");
        m.put("messages.devstudio.no_perm_delete_cosmetic", "<red>[!] You don't have permission to delete cosmetics.");
        m.put("messages.devstudio.no_perm_clear_skins", "<red>[!] You don't have DEV permission to clear skins.");
        m.put("messages.join.synced", "<aqua>Synced: {count} items loaded.");
        m.put("messages.join.mod_missing", "<red><bold>GreatCosmetics\n\n<gray>You don't have the mod installed or your version is outdated.\n<yellow>Update the modpack and try again.");
        files.put("messages", m);

        // ---------------- items.json ----------------
        LinkedHashMap<String, String> items = new LinkedHashMap<>();
        items.put("items.name_fallback", "<light_purple>Cosmetic");
        items.put("items.backpack_name_fallback", "<dark_gray>Traveler's Backpack");
        items.put("items.lore.slot", "<dark_gray>▪ <gray>Slot: <white>{slot}");
        items.put("items.lure.header", "<gray>This cosmetic's attributes:");
        items.put("items.lure.divider", "<dark_gray><st>                                        </st>");
        items.put("items.lure.type", "<gray> ▪ Affected Type: <white>{type}");
        items.put("items.lure.shiny", "<yellow>✨ Shiny Rate: <green>+{value}x");
        items.put("items.lure.expall", "<aqua>📉 Exp.All: <green>+{value}x");
        items.put("items.lure.friendship", "<light_purple>❤ Friendship: <green>+{value}x");
        items.put("items.lure.ev", "<green>🐍 Battle EV: <green>+{value}x");
        items.put("items.lure.capture", "<red>🎯 Capture Chance: <green>+{value}x");
        items.put("items.lure.iv", "<gold>⭐ Guaranteed Perfect IVs: <green>+{value}");
        items.put("items.lure.iv_chance", "<blue>🎲 IV Chance: <green>+{value}%");
        items.put("items.lure.ultrarare", "<light_purple>🔮 Ultra Rare Chance: <green>+{value}x");
        items.put("items.lure.hidden_ability", "<dark_aqua>👁 Hidden Ability: <green>+{value}x");
        items.put("items.lure.fishing_header", "<aqua>🎣 <b>Fishing Bonus</b>");
        items.put("items.lure.fishing_shiny", "<gray>  ▪ <yellow>Shiny: <green>+{value}x");
        items.put("items.lure.fishing_iv_chance", "<gray>  ▪ <green>IV Chance: <green>+{value}%");
        items.put("items.lure.fishing_speed", "<gray>  ▪ <aqua>Speed: <green>+{value}%");
        // ---------------- HUD de Lure (LureHudOverlay — coluna à direita da hotbar) ----------------
        items.put("hud.lure.header", "<light_purple>✦ Lure Bonus");
        items.put("hud.lure.type", "<white>▪ Type: <aqua>{value}");
        items.put("hud.lure.shiny", "<yellow>✨ Shiny <green>+{value}x");
        items.put("hud.lure.ultrarare", "<dark_purple>🔮 Ultra Rare <green>+{value}x");
        items.put("hud.lure.hidden_ability", "<dark_aqua>👁 Hidden Ability <green>+{value}x");
        items.put("hud.lure.iv", "<gold>⭐ Perfect IVs <green>+{value}");
        items.put("hud.lure.iv_chance", "<blue>🎲 IV Chance <green>+{value}%");
        items.put("hud.lure.expall", "<aqua>⚡ Exp (Team) <green>+{value}x");
        items.put("hud.lure.exp", "<aqua>📘 Exp (Main) <green>+{value}x");
        items.put("hud.lure.ev", "<green>🐍 Battle EV <green>+{value}x");
        items.put("hud.lure.friendship", "<light_purple>❤ Friendship <green>+{value}x");
        items.put("hud.lure.capture", "<red>🎯 Capture <green>+{value}x");
        items.put("hud.lure.fishing_header", "<aqua>🎣 Fishing");
        items.put("hud.lure.fishing_shiny", "<gray> <yellow>✨ Shiny <green>+{value}x");
        items.put("hud.lure.fishing_ultrarare", "<gray> <dark_purple>🔮 Ultra Rare <green>+{value}x");
        items.put("hud.lure.fishing_iv", "<gray> <gold>⭐ Perfect IVs <green>+{value}");
        items.put("hud.lure.fishing_iv_chance", "<gray> <blue>🎲 IV Chance <green>+{value}%");
        items.put("hud.lure.fishing_speed", "<gray> <aqua>💨 Speed <green>+{value}%");
        items.put("hud.lure.fishing_power", "<gray> <aqua>🐟 Fishing Power <green>+{value}x");

        items.put("items.scanner.divider", "&8&m                                     ");
        items.put("items.scanner.line", "&e[Scanner] &fCurrent ID: &c{cmd}");
        // chaves migradas do lang.json antigo (wardrobe item ghost/unconfigured)
        items.put("items.unconfigured_name", "<gray>Unconfigured cosmetic");
        items.put("items.unconfigured_lore", "<gray>This item isn't configured in the mod.\n<yellow>Click to grab just the model!");
        items.put("items.label_id", "<dark_gray>▪ <gray>ID (CMD): <white>{id}");
        items.put("items.label_slot", "<dark_gray>▪ <gray>Slot: <green>{slot}");
        items.put("items.label_permission", "<dark_gray>▪ <red>Permission: <white>{perm}");
        items.put("items.backpack_on", "<dark_gray>▪ <yellow>Backpack: <green>Active <gray>({rows} rows)");
        items.put("items.backpack_off", "<dark_gray>▪ <yellow>Backpack: <red>Disabled");
        items.put("items.fly_on", "<dark_gray>▪ <aqua>Flight: <green>Active");
        items.put("items.fly_off", "<dark_gray>▪ <aqua>Flight: <red>Disabled");
        items.put("items.click_to_get", "\n<yellow>Click to grab 1x of this item!");
        files.put("items", items);

        // ---------------- wardrobe.json ----------------
        LinkedHashMap<String, String> w = new LinkedHashMap<>();
        w.put("wardrobe.title", "Avatar Studio");
        w.put("wardrobe.tab.accessories", "Accessories");
        w.put("wardrobe.tab.customize", "Preview");
        w.put("wardrobe.tab.party", "Party");
        w.put("wardrobe.tab.tags", "Tags");
        w.put("wardrobe.tab.dev", "Dev Studio");
        w.put("wardrobe.panel.title", "// {tab}");
        w.put("wardrobe.equipped_slots.dev_on", "<yellow>Dev: ON");
        w.put("wardrobe.equipped_slots.dev_off", "<gray>Dev: OFF");
        w.put("wardrobe.customize.header", "> Hide Armor");
        w.put("wardrobe.customize.helmet", "Helmet");
        w.put("wardrobe.customize.chestplate", "Chestplate");
        w.put("wardrobe.customize.leggings", "Leggings");
        w.put("wardrobe.customize.boots", "Boots");
        w.put("wardrobe.customize.hidden", "HIDDEN");
        w.put("wardrobe.customize.visible", "VISIBLE");
        files.put("wardrobe", w);

        // ---------------- accessories.json ----------------
        LinkedHashMap<String, String> a = new LinkedHashMap<>();
        a.put("accessories.category.all", "All");
        a.put("accessories.category.head", "Head");
        a.put("accessories.category.face", "Face");
        a.put("accessories.category.neck", "Neck");
        a.put("accessories.category.chest", "Chest");
        a.put("accessories.category.back", "Back");
        a.put("accessories.category.waist", "Waist");
        a.put("accessories.category.legs", "Legs");
        a.put("accessories.category.feet", "Feet");
        a.put("accessories.category.hand", "Hand");
        a.put("accessories.search_placeholder", "<gray>🔍 Search cosmetic...");
        a.put("accessories.clear_all.tooltip_title", "<red>Remove All Cosmetics");
        a.put("accessories.clear_all.tooltip_1", "<gray>Clears every cosmetic currently");
        a.put("accessories.clear_all.tooltip_2", "<gray>equipped on you!");
        a.put("accessories.tooltip.attributes", "<gray>Attributes:");
        a.put("accessories.tooltip.armor", " <blue>🛡 <white>+{value} <gray>Armor");
        a.put("accessories.tooltip.toughness", " <dark_gray>❈ <white>+{value} <gray>Toughness");
        a.put("accessories.tooltip.abilities", "<gray>Abilities:");
        a.put("accessories.tooltip.fly", " <yellow>✦ <white>Allows Flight");
        a.put("accessories.tooltip.backpack", " <gold>🎒 <white>Backpack <gray>({info})");
        a.put("accessories.tooltip.backpack_rows", "{rows} Rows");
        a.put("accessories.tooltip.autofeed", " <green>🍖 <white>Auto Feed");
        a.put("accessories.tooltip.passive_effects", "<light_purple>Passive Effects:");
        a.put("accessories.tooltip.effect_line", " <dark_purple>- <white>");
        a.put("accessories.tooltip.lure_header", "<light_purple><bold>✦ <light_purple><bold>Attraction Power (Lure):");
        a.put("accessories.tooltip.lure_type", " <gray>▪ <white>Affected Type: <aqua>{value}");
        a.put("accessories.tooltip.lure_shiny", " <yellow>✨ <white>Shiny: <green>+{value}x");
        a.put("accessories.tooltip.lure_ultrarare", " <dark_purple>🔮 <white>Ultra Rare: <green>+{value}x");
        a.put("accessories.tooltip.lure_hidden_ability", " <dark_aqua>👁 <white>Hidden Ability: <green>+{value}x");
        a.put("accessories.tooltip.lure_iv", " <gold>⭐ <white>Guaranteed Perfect IVs: <green>+{value}");
        a.put("accessories.tooltip.lure_iv_chance", " <blue>🎲 <white>IV Chance: <green>+{value}");
        a.put("accessories.tooltip.lure_expall", " <aqua>⚡ <white>Exp (Whole Team): <green>+{value}x");
        a.put("accessories.tooltip.lure_exp", " <aqua>📘 <white>Exp (Main): <green>+{value}x");
        a.put("accessories.tooltip.lure_ev", " <green>🐍 <white>Battle EV: <green>+{value}x");
        a.put("accessories.tooltip.lure_friendship", " <light_purple>❤ <white>Friendship: <green>+{value}x");
        a.put("accessories.tooltip.lure_capture", " <red>🎯 <white>Capture Chance: <green>+{value}x");
        a.put("accessories.tooltip.lure_fishing_header", " <aqua><bold>🎣 Fishing Bonus:");
        a.put("accessories.tooltip.lure_fishing_shiny", "   <yellow>✨ <white>Shiny: <green>+{value}x");
        a.put("accessories.tooltip.lure_fishing_ultrarare", "   <dark_purple>🔮 <white>Ultra Rare: <green>+{value}x");
        a.put("accessories.tooltip.lure_fishing_iv", "   <gold>⭐ <white>Guaranteed IVs: <green>+{value}");
        a.put("accessories.tooltip.lure_fishing_iv_chance", "   <blue>🎲 <white>IV Chance: <green>+{value}");
        a.put("accessories.tooltip.lure_fishing_speed", "   <dark_aqua>💨 <white>Speed: <green>+{value}");
        a.put("accessories.tooltip.lure_fishing_power", "   <aqua>🐟 <white>Fishing Power: <green>+{value}x");
        files.put("accessories", a);

        // ---------------- party.json ----------------
        LinkedHashMap<String, String> p = new LinkedHashMap<>();
        p.put("party.form.default", "Default");
        p.put("party.preview.pose_battle", "Battle");
        p.put("party.skins.header", "<yellow>Available Skins");
        p.put("party.skins.none", "<gray>No skins configured.");
        p.put("party.skins.clear_current", "Clear Current");
        p.put("party.skins.clear_party", "Clear Party");
        p.put("party.skins.status.not_owned", "<red>You don't have this skin!");
        p.put("party.skins.status.incompatible", "<red>Incompatible");
        p.put("party.skins.status.cooldown", "<red>Cooldown: {time}");
        p.put("party.skins.status.click_apply", "<green>> Click to apply");
        p.put("party.skins.status.ready", "<gray>Ready to use");
        p.put("party.preview.title", "<gold><bold>⚠ PREVIEW MODE ⚠");
        p.put("party.preview.pose", "<white>🔃 {label}");
        p.put("party.preview.form", "<white>🔄 {label}");
        p.put("party.preview.shiny_on", "<yellow>✨ Shiny: ON");
        p.put("party.preview.shiny_off", "<gray>✨ Shiny: OFF");
        p.put("party.slot.empty", "<gray>Empty Slot");
        p.put("party.pc_button", "<aqua>PC");
        files.put("party", p);

        // ---------------- tags.json ----------------
        LinkedHashMap<String, String> t = new LinkedHashMap<>();
        t.put("tags.header", "<yellow>Your Tags");
        t.put("tags.create_new", "+ Create New Tag");
        t.put("tags.tooltip.click_equip", "<green>Click to equip");
        t.put("tags.tooltip.click_remove", "<red>Click to remove");
        t.put("tags.tooltip.not_owned", "<red>You don't have this TAG!");
        t.put("tags.editor.title_new", "<green>New Tag");
        t.put("tags.editor.title_edit", "<green>{id}");
        t.put("tags.editor.field.id", "ID");
        t.put("tags.editor.field.display_name", "Display Name");
        t.put("tags.editor.field.description", "Description");
        t.put("tags.editor.field.tag", "TAG (prefix)");
        t.put("tags.editor.field.permissions", "Permissions (comma-separated)");
        t.put("tags.editor.field.minecraft_tag", "Minecraft Tag");
        t.put("tags.editor.save", "Save");
        t.put("tags.editor.delete", "DELETE TAG");
        files.put("tags", t);

        // ---------------- backpack.json ----------------
        LinkedHashMap<String, String> b = new LinkedHashMap<>();
        b.put("backpack.selector.title", "Which backpack do you want to open?");
        b.put("backpack.selector.fallback_name", "Backpack");
        files.put("backpack", b);

        // ---------------- devstudio.json ----------------
        LinkedHashMap<String, String> d = new LinkedHashMap<>();
        d.put("devstudio.header", "<gold><bold>// DEV STUDIO");
        d.put("devstudio.menu.subtitle", "Choose what to edit:");
        d.put("devstudio.menu.cosmetics", "Cosmetics");
        d.put("devstudio.menu.effects", "Effects");
        d.put("devstudio.menu.types", "Cosmetics Types");
        d.put("devstudio.menu.server_config", "Server Config");
        d.put("devstudio.menu.slots", "Slots");
        d.put("devstudio.menu.help", "<gray>Need help? Join <yellow>SaSDEV<gray> on Discord\nand ask!");
        d.put("devstudio.unsaved.title", "<yellow><bold>UNSAVED CHANGES!");
        d.put("devstudio.unsaved.question", "Do you want to save the current edits?");
        d.put("devstudio.unsaved.save", "Save");
        d.put("devstudio.unsaved.dont_save", "Don't Save");
        d.put("devstudio.unsaved.continue", "Continue");
        d.put("devstudio.common.back", "< Back");
        d.put("devstudio.common.back_arrow", "<");
        d.put("devstudio.common.save_short", "S");
        d.put("devstudio.common.on", "<green>ON");
        d.put("devstudio.common.off", "<red>OFF");
        d.put("devstudio.common.all", "All");
        d.put("devstudio.common.confirm", "Confirm");
        d.put("devstudio.common.cancel", "Cancel");
        // --- Cosmetics editor ---
        d.put("devstudio.cosmetic.list_title", "<gold>Cosmetics");
        d.put("devstudio.cosmetic.search_placeholder", "<gray>🔍 Search cosmetic...");
        d.put("devstudio.cosmetic.btn.new_cosmetic", "+ Cosmetic");
        d.put("devstudio.cosmetic.btn.new_armor", "+ Armor");
        d.put("devstudio.cosmetic.divider.identification", "=== IDENTIFICATION ===");
        d.put("devstudio.cosmetic.divider.identification_armor", "=== IDENTIFICATION (ID: {id}) ===");
        d.put("devstudio.cosmetic.divider.models3d", "=== 3D MODELS ===");
        d.put("devstudio.cosmetic.divider.status_combat", "=== STATUS & COMBAT ===");
        d.put("devstudio.cosmetic.divider.backpack", "=== BACKPACK ===");
        d.put("devstudio.cosmetic.divider.special_effects", "=== SPECIAL EFFECTS ===");
        d.put("devstudio.cosmetic.divider.lure", "=== LURE SYSTEM ===");
        d.put("devstudio.cosmetic.divider.sounds", "=== SOUNDS ===");
        d.put("devstudio.cosmetic.field.real_item", "Real Item");
        d.put("devstudio.cosmetic.tooltip.real_item", "<gray>The real Minecraft item this armor cosmetic\n<gray>represents (players actually wear this item,\n<gray>the mod just changes how it looks in-game).\n<gray>Type the item's ID, e.g. minecraft:diamond_helmet.");
        d.put("devstudio.cosmetic.field.main_id", "Main ID");
        d.put("devstudio.cosmetic.tooltip.main_id", "<gray>The internal name of this cosmetic — used in\n<gray>commands (/gc give, /gc cosmetics equip),\n<gray>permissions and the config file name. Players\n<gray>never see this, only the Display Name below.\n<gray>Letters, numbers and underscore only.");
        d.put("devstudio.cosmetic.field.icon_name", "Icon Name");
        d.put("devstudio.cosmetic.tooltip.icon_name", "<gray>Optional. The file name (without .png) of the\n<gray>icon shown in the Wardrobe menu, inside\n<gray>textures/icons/ in your resource pack. Leave\n<gray>empty to reuse the Main ID as the icon name —\n<gray>useful when several cosmetics share one icon.");
        d.put("devstudio.cosmetic.hint.icon_name", "empty = uses \"{id}\"");
        d.put("devstudio.cosmetic.field.display_name", "Display Name");
        d.put("devstudio.cosmetic.tooltip.display_name", "<gray>The name players see in the Wardrobe menu and\n<gray>in chat/tooltips. Supports color codes (&a, &d...)\n<gray>and MiniMessage tags (<gold>, <gradient>...).");
        d.put("devstudio.cosmetic.field.slot", "Slot");
        d.put("devstudio.cosmetic.tooltip.slot", "<gray>Which Wardrobe slot this cosmetic goes in.\n<gray>Options: HEAD, FACE, NECK, CHEST, BACK, WAIST,\n<gray>LEGS, FEET or HAND. Only one cosmetic per slot\n<gray>can be equipped at a time.");
        d.put("devstudio.cosmetic.field.type", "Type");
        d.put("devstudio.cosmetic.tooltip.type", "<gray>Free-text category/tag for this cosmetic (e.g.\n<gray>\"halloween\", \"event\"). Used only to filter the\n<gray>list in this Dev Studio and in slot limits — it\n<gray>doesn't change how the cosmetic behaves.");
        d.put("devstudio.cosmetic.field.permission", "Permission");
        d.put("devstudio.cosmetic.tooltip.permission", "<gray>Optional permission node a player needs to equip\n<gray>this cosmetic (checked via your permissions\n<gray>plugin, e.g. LuckPerms). Leave empty for\n<gray>everyone to be able to use it.");
        d.put("devstudio.cosmetic.field.part_model", "Part {i}: Model ID");
        d.put("devstudio.cosmetic.tooltip.part_model", "<gray>The item/model this Part renders — either a\n<gray>Custom Model Data name registered by this mod,\n<gray>or a vanilla item id. Ignored if the GeckoLib\n<gray>Model ID below is filled in.");
        d.put("devstudio.cosmetic.hint.part_model", "e.g. examplehat (name) or sas/cigar (Exact Path)");
        d.put("devstudio.cosmetic.field.part_geo", "Part {i}: GeckoLib Model");
        d.put("devstudio.cosmetic.tooltip.part_geo", "<gray>Optional. The id of a GeckoLib 3D model (.geo.json)\n<gray>for this Part, used instead of a flat item icon.\n<gray>Takes priority over Model ID above and over the\n<gray>Real Item, if this is an armor cosmetic.");
        d.put("devstudio.cosmetic.hint.part_geo", "e.g. faxihat (name) or item/faxihat (Exact Path)");
        d.put("devstudio.cosmetic.field.part_exact_path", "Part {i}: Exact Path");
        d.put("devstudio.cosmetic.tooltip.part_exact_path", "<gray>OFF (default): the Model ID/GeckoLib fields above\n<gray>are just a file name, found in any folder.\n<gray>ON: they become the full relative path, e.g.\n<gray>\"sas/cigar\" — needed when two files share a name\n<gray>in different folders.");
        d.put("devstudio.cosmetic.btn.config_part", ">> Config Part {i}");
        d.put("devstudio.cosmetic.tooltip.config_part", "<gray>Opens Offset, Rotation, Scale and Anchor for this\n<gray>Part — fine-tune with the 3D arrows or by typing\n<gray>exact numbers.");
        d.put("devstudio.cosmetic.tooltip.config_part_disabled_armor", "<gray>Disabled for this Part.\n<gray>This cosmetic uses the real 3D armor model\n<gray>(it's a head item like a helmet), which already\n<gray>fits the player automatically. Offset, Rotation\n<gray>and Scale here would be ignored, so there's\n<gray>nothing to configure.");
        d.put("devstudio.cosmetic.btn.remove_part", "X Remove Part {i}");
        d.put("devstudio.cosmetic.btn.add_part", "+ Add Part");
        d.put("devstudio.cosmetic.tooltip.add_part", "<gray>Adds another 3D Part to this cosmetic — useful\n<gray>when one cosmetic needs more than one model at\n<gray>once (e.g. a hat + glasses combo).");
        d.put("devstudio.cosmetic.confirm.remove_part_title", "REMOVE PART?");
        d.put("devstudio.cosmetic.confirm.remove_part_body", "Deletes Part {i} permanently.");
        d.put("devstudio.cosmetic.confirm.delete_title", "DELETE COSMETIC?");
        d.put("devstudio.cosmetic.confirm.delete_body", "This action cannot be undone.");
        d.put("devstudio.cosmetic.field.armor", "Armor Points");
        d.put("devstudio.cosmetic.tooltip.armor", "<gray>Extra armor points this cosmetic gives while\n<gray>equipped, on top of any real armor worn (same\n<gray>scale as vanilla armor: a diamond helmet gives 3).");
        d.put("devstudio.cosmetic.field.toughness", "Toughness");
        d.put("devstudio.cosmetic.tooltip.toughness", "<gray>Extra armor toughness this cosmetic gives while\n<gray>equipped (reduces damage from strong hits, same\n<gray>stat as vanilla diamond/netherite armor).");
        d.put("devstudio.cosmetic.field.max_durability", "Max Durability");
        d.put("devstudio.cosmetic.tooltip.max_durability", "<gray>If greater than 0, the cosmetic item has a\n<gray>durability bar and breaks after this many uses.\n<gray>0 = never breaks.");
        d.put("devstudio.cosmetic.field.auto_feed", "Auto-Feed");
        d.put("devstudio.cosmetic.tooltip.auto_feed", "<gray>While equipped, slowly refills the player's\n<gray>hunger bar over time on its own.");
        d.put("devstudio.cosmetic.field.is_backpack", "Is Backpack?");
        d.put("devstudio.cosmetic.tooltip.is_backpack", "<gray>Turns this cosmetic into a wearable backpack with\n<gray>its own storage inventory, opened by the player\n<gray>while it's equipped. Configure its size below.");
        d.put("devstudio.cosmetic.field.backpack_rows", "Backpack Rows");
        d.put("devstudio.cosmetic.tooltip.backpack_rows", "<gray>How many inventory rows (1 to 6, like a chest).\n<gray>Only used if \"Is Backpack?\" is ON.");
        d.put("devstudio.cosmetic.field.backpack_name", "Backpack Name");
        d.put("devstudio.cosmetic.tooltip.backpack_name", "<gray>Title shown at the top of the backpack's\n<gray>inventory screen when the player opens it.");
        d.put("devstudio.cosmetic.field.enable_fly", "Allows Flight?");
        d.put("devstudio.cosmetic.tooltip.enable_fly", "<gray>Lets the player fly (like Creative mode) while\n<gray>this cosmetic is equipped, even in Survival.");
        d.put("devstudio.cosmetic.field.fly_speed", "Fly Speed");
        d.put("devstudio.cosmetic.tooltip.fly_speed", "<gray>Flight speed multiplier while flying with this\n<gray>cosmetic. 1.0 = normal speed, 2.0 = twice as\n<gray>fast. Only matters if \"Allows Flight?\" is ON.");
        d.put("devstudio.cosmetic.field.ground_speed", "Ground Speed");
        d.put("devstudio.cosmetic.tooltip.ground_speed", "<gray>Walking speed multiplier while this cosmetic is\n<gray>equipped. 1.0 = normal speed, 1.5 = 50% faster.");
        d.put("devstudio.cosmetic.field.swim_speed", "Swim Speed");
        d.put("devstudio.cosmetic.tooltip.swim_speed", "<gray>Swimming speed multiplier while this cosmetic is\n<gray>equipped. 1.0 = normal speed, 1.5 = 50% faster.");
        d.put("devstudio.cosmetic.btn.select_effects", ">> Select Effects ({count} active)");
        d.put("devstudio.cosmetic.tooltip.select_effects", "<gray>Opens the list of potion effects (Speed, Night\n<gray>Vision, etc.) applied to the player automatically\n<gray>while this cosmetic is equipped, with their level.");
        d.put("devstudio.cosmetic.field.effect_visual", "Effect Visual");
        d.put("devstudio.cosmetic.tooltip.effect_visual", "<gray>Comma-separated list of particle effect ids that\n<gray>constantly play around the player while this\n<gray>cosmetic is equipped (e.g. minecraft:heart).");
        d.put("devstudio.cosmetic.field.fly_particle", "Fly Particle");
        d.put("devstudio.cosmetic.tooltip.fly_particle", "<gray>Comma-separated list of particle effect ids shown\n<gray>only while the player is flying with this\n<gray>cosmetic equipped (needs \"Allows Flight?\" ON).");
        d.put("devstudio.cosmetic.btn.config_lure", ">> Configure LURE");
        // --- Part popup ---
        d.put("devstudio.part.new", "New");
        d.put("devstudio.part.popup_title", "Part {i}: {name}");
        d.put("devstudio.part.divider.gizmo_tool", "3D GIZMO TOOL");
        d.put("devstudio.part.divider.general", "GENERAL SETTINGS");
        d.put("devstudio.part.divider.scale", "SCALE");
        d.put("devstudio.part.divider.normal_values", "NORMAL VALUES");
        d.put("devstudio.part.divider.sneak_values", "SNEAK VALUES");
        d.put("devstudio.part.gizmo_hint", "<yellow>3D Tool (Hold X, Y or Z and drag outside):");
        d.put("devstudio.part.gizmo_move", "Move");
        d.put("devstudio.part.gizmo_rotate", "Rotate");
        d.put("devstudio.part.gizmo_scale", "Scale");
        // --- Lure popup ---
        d.put("devstudio.lure.title", "Lure Config");
        d.put("devstudio.lure.field.enabled", "Lure Enabled");
        d.put("devstudio.lure.field.type", "Lure TYPE");
        d.put("devstudio.lure.field.shiny_mult", "Shiny Multiplier");
        d.put("devstudio.lure.field.ultrarare_mult", "Ultra Rare Multiplier");
        d.put("devstudio.lure.field.hidden_ability_mult", "Hidden Ability Multiplier");
        d.put("devstudio.lure.field.expall_mult", "Exp All Multiplier");
        d.put("devstudio.lure.field.friendship_mult", "Friendship Multiplier");
        d.put("devstudio.lure.field.iv", "Lure IV");
        d.put("devstudio.lure.field.iv_chance", "IV Chance");
        d.put("devstudio.lure.field.fishing_shiny", "Fishing Shiny");
        d.put("devstudio.lure.field.fishing_ultrarare", "Fishing Ultra Rare");
        d.put("devstudio.lure.field.fishing_iv_chance", "Fishing IV Chance");
        d.put("devstudio.lure.field.fishing_iv", "Fishing IV");
        d.put("devstudio.lure.field.fishing_speed", "Fishing Speed");
        d.put("devstudio.lure.field.exp_mult", "EXP Multiplier");
        d.put("devstudio.lure.field.ev_mult", "EV Multiplier");
        d.put("devstudio.lure.field.capture_chance", "Capture Chance");
        d.put("devstudio.lure.field.fishing_lure", "Fishing Lure");
        // --- Effects popup ---
        d.put("devstudio.effects.title", "Potion Effects");
        d.put("devstudio.effects.divider", "CLICK TO CYCLE THE LEVEL (OFF -> V)");
        d.put("devstudio.effects.level", "Level {n}");
        d.put("devstudio.effects.off", "OFF (click to enable)");
        // --- Effects subpage ---
        d.put("devstudio.effect.list_title", "<light_purple>Effects");
        d.put("devstudio.effect.new", "+ New Effect");
        d.put("devstudio.effect.delete", "DELETE EFFECT");
        d.put("devstudio.effect.particles_none", "<gray>(no particles found)");
        d.put("devstudio.effect.divider.identification", "=== IDENTIFICATION ===");
        d.put("devstudio.effect.divider.configuration", "=== CONFIGURATION ===");
        d.put("devstudio.effect.divider.tool_3d", "=== 3D TOOL ===");
        d.put("devstudio.effect.divider.offsets", "=== OFFSETS (POSITION) ===");
        d.put("devstudio.effect.divider.spread", "=== SPREAD ===");
        d.put("devstudio.effect.divider.danger", "=== DANGER ===");
        d.put("devstudio.effect.field.effect_id", "Effect ID");
        d.put("devstudio.effect.tooltip.effect_id", "<gray>The internal name of this effect — used as the\n<gray>config file name and to attach it to cosmetics\n<gray>in \"Select Effects\". Letters, numbers and\n<gray>underscore only.");
        d.put("devstudio.effect.field.particle_id", "Particle ID");
        d.put("devstudio.effect.tooltip.particle_id", "<gray>Which particle is spawned, e.g. minecraft:flame\n<gray>or minecraft:heart. Start typing to see a dropdown\n<gray>with every particle registered (vanilla + mods).");
        d.put("devstudio.effect.field.count", "Count");
        d.put("devstudio.effect.tooltip.count", "<gray>How many particles spawn each time this effect\n<gray>triggers (every Interval ticks below).");
        d.put("devstudio.effect.field.tick_interval", "Interval");
        d.put("devstudio.effect.tooltip.tick_interval", "<gray>How often the particles spawn, in ticks (20 ticks\n<gray>= 1 second). Lower = more frequent/denser effect.");
        d.put("devstudio.effect.gizmo_info", "Drag the red/green/blue arrows on the model to move it! Hold Shift to move all axes together.");
        d.put("devstudio.effect.field.offset_x", "Offset X");
        d.put("devstudio.effect.tooltip.offset_x", "<gray>Moves the particles left/right relative to the\n<gray>player. Drag the RED arrow on the 3D model, or\n<gray>type an exact number here.");
        d.put("devstudio.effect.field.offset_y", "Offset Y");
        d.put("devstudio.effect.tooltip.offset_y", "<gray>Moves the particles up/down relative to the\n<gray>player. Drag the GREEN arrow on the 3D model, or\n<gray>type an exact number here.");
        d.put("devstudio.effect.field.offset_z", "Offset Z");
        d.put("devstudio.effect.tooltip.offset_z", "<gray>Moves the particles forward/backward relative to\n<gray>the player. Drag the BLUE arrow on the 3D model,\n<gray>or type an exact number here.");
        d.put("devstudio.effect.field.spread_x", "Spread X");
        d.put("devstudio.effect.tooltip.spread_x", "<gray>Random left/right variation applied to each\n<gray>particle's spawn position. 0 = always the exact\n<gray>same spot.");
        d.put("devstudio.effect.field.spread_y", "Spread Y");
        d.put("devstudio.effect.tooltip.spread_y", "<gray>Random up/down variation applied to each\n<gray>particle's spawn position. 0 = always the exact\n<gray>same spot.");
        d.put("devstudio.effect.field.spread_z", "Spread Z");
        d.put("devstudio.effect.tooltip.spread_z", "<gray>Random forward/backward variation applied to each\n<gray>particle's spawn position. 0 = always the exact\n<gray>same spot.");
        d.put("devstudio.effect.field.speed", "Speed");
        d.put("devstudio.effect.tooltip.speed", "<gray>The particle's own motion speed (how fast it\n<gray>travels after spawning). 0 = stays still.");
        // --- Types subpage ---
        d.put("devstudio.type.list_title", "<aqua>Types");
        d.put("devstudio.type.new", "+ New Type");
        d.put("devstudio.type.delete", "DELETE TYPE");
        d.put("devstudio.type.field.limit_per_player", "Limit Per Player");
        d.put("devstudio.type.tooltip.limit_per_player", "<gray>How many cosmetics of this Type a single player\n<gray>can own/equip at once, regardless of slot.");
        d.put("devstudio.type.field.permission", "Permission");
        d.put("devstudio.type.tooltip.permission", "<gray>Optional permission PREFIX for VIP/rank bonuses on\n<gray>this Type's limit. If set to e.g. \"vip.types\", giving\n<gray>a player \"vip.types.5\" raises their limit for this\n<gray>Type to 5, and \"vip.types.bypass\" removes it. Leave\n<gray>empty to just use Limit Per Player for everyone.");
        // --- Slots subpage ---
        d.put("devstudio.slot.list_title", "<green>Slots");
        d.put("devstudio.slot.new", "+ New Slot");
        d.put("devstudio.slot.delete", "DELETE SLOT");
        d.put("devstudio.slot.field.extra_permission", "Extra Permission");
        d.put("devstudio.slot.tooltip.extra_permission", "<gray>Optional permission PREFIX for VIP/rank slot bonuses.\n<gray>If set to e.g. \"vip.slots\", giving a player\n<gray>\"vip.slots.5\" raises their limit in this slot to 5,\n<gray>and \"vip.slots.bypass\" removes the limit entirely.\n<gray>Leave empty to just use Default Limit for everyone.");
        // --- Server config subpage ---
        d.put("devstudio.serverconfig.title", "<green>Server Config");
        d.put("devstudio.serverconfig.field.auto_detect_models", "Auto Detect Models");
        d.put("devstudio.serverconfig.tooltip.auto_detect_models", "<gray>When ON, the mod automatically scans your resource\n<gray>pack for new 3D models on startup. Turn OFF only\n<gray>if you want full manual control over model IDs.");
        d.put("devstudio.serverconfig.field.lure_hud", "Lure HUD");
        d.put("devstudio.serverconfig.tooltip.lure_hud", "<gray>When ON, players wearing a cosmetic that grants Lure\n<gray>see the combined Lure bonuses in a column to the\n<gray>right of their hotbar. It hides when no Lure is active.");
        d.put("devstudio.serverconfig.field.use_mysql", "Use MySQL");
        d.put("devstudio.serverconfig.tooltip.use_mysql", "<gray>ON: player cosmetic data is stored in a MySQL\n<gray>database (needed for server networks/BungeeCord).\n<gray>OFF (default): stored in local files, simpler for\n<gray>a single server.");
        d.put("devstudio.serverconfig.field.mysql_host", "MySQL Host");
        d.put("devstudio.serverconfig.tooltip.mysql_host", "<gray>Address of your MySQL server, e.g. localhost or an\n<gray>IP address. Only used if \"Use MySQL\" is ON.");
        d.put("devstudio.serverconfig.field.mysql_port", "MySQL Port");
        d.put("devstudio.serverconfig.tooltip.mysql_port", "<gray>Port your MySQL server listens on. The default\n<gray>MySQL port is 3306. Only used if \"Use MySQL\" is ON.");
        d.put("devstudio.serverconfig.field.mysql_database", "MySQL Database");
        d.put("devstudio.serverconfig.tooltip.mysql_database", "<gray>Name of the database (schema) this mod should use\n<gray>on your MySQL server. Only used if \"Use MySQL\" is ON.");
        d.put("devstudio.serverconfig.field.mysql_user", "MySQL User");
        d.put("devstudio.serverconfig.tooltip.mysql_user", "<gray>Username used to log in to your MySQL server.\n<gray>Only used if \"Use MySQL\" is ON.");
        d.put("devstudio.serverconfig.field.mysql_password", "MySQL Password");
        d.put("devstudio.serverconfig.tooltip.mysql_password", "<gray>Password used to log in to your MySQL server.\n<gray>Only used if \"Use MySQL\" is ON. Keep this config\n<gray>file private — it's stored as plain text.");
        d.put("devstudio.serverconfig.field.force_resource_pack", "Force Resource Pack");
        d.put("devstudio.serverconfig.tooltip.force_resource_pack", "<gray>When ON, the mod pushes the Texture URL below to\n<gray>every player as a required server resource pack\n<gray>on join, so custom cosmetic textures always show.");
        d.put("devstudio.serverconfig.field.texture_url", "Texture URL");
        d.put("devstudio.serverconfig.tooltip.texture_url", "<gray>Direct download link to the resource pack .zip\n<gray>players are prompted to install. Only used if\n<gray>\"Force Resource Pack\" is ON.");
        d.put("devstudio.serverconfig.field.texture_id", "Texture ID");
        d.put("devstudio.serverconfig.tooltip.texture_id", "<gray>Any free-text label for this resource pack version\n<gray>— purely internal bookkeeping, doesn't need to\n<gray>match anything. Change it if you just re-uploaded\n<gray>a new pack build.");
        d.put("devstudio.serverconfig.field.texture_sha1", "Texture SHA1");
        d.put("devstudio.serverconfig.tooltip.texture_sha1", "<gray>Optional SHA-1 checksum of the resource pack .zip,\n<gray>used by Minecraft to verify the download wasn't\n<gray>corrupted. Leave empty to skip verification.");
        d.put("devstudio.serverconfig.field.boot_command", "Boot Command #{n}");
        d.put("devstudio.serverconfig.tooltip.boot_command", "<gray>A console command run automatically every time the\n<gray>server starts. Type it WITHOUT the leading slash\n<gray>(e.g. \"say Server started\", not \"/say ...\").");
        d.put("devstudio.serverconfig.btn.remove_command", "Remove Command #{n}");
        d.put("devstudio.serverconfig.btn.add_command", "+ Add Boot Command");
        d.put("devstudio.serverconfig.tooltip.add_command", "<gray>Adds another command to run automatically on every\n<gray>server startup.");
        d.put("devstudio.serverconfig.btn.remove", "Remove");
        // --- Slots subpage extra ---
        d.put("devstudio.slot.field.name", "Name");
        d.put("devstudio.slot.tooltip.name", "<gray>Must match one of the cosmetic Slot values exactly:\n<gray>HEAD, NECK, CHEST, BACK, WAIST, LEGS or FEET. This\n<gray>links the limit below to that Wardrobe slot.");
        d.put("devstudio.slot.field.default_limit", "Default Limit");
        d.put("devstudio.slot.tooltip.default_limit", "<gray>How many cosmetics a player can have equipped in\n<gray>this slot at once by default (before any VIP/rank\n<gray>bonus is applied). Most slots only make sense\n<gray>with a limit of 1.");
        // --- Types subpage extra ---
        d.put("devstudio.type.field.type_id", "Type ID");
        d.put("devstudio.type.tooltip.type_id", "<gray>A unique name for this Type — used internally and\n<gray>in each cosmetic's own \"Type\" field to link it here.");
        d.put("devstudio.type.field.base_slot", "Base Slot");
        d.put("devstudio.type.tooltip.base_slot", "<gray>Which Wardrobe slot this Type's limit applies to,\n<gray>e.g. NECK. Should match one of your configured\n<gray>Slots.");
        files.put("devstudio", d);

        return files;
    }
}
