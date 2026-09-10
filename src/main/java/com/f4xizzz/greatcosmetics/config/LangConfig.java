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

    /** Chaves cujo texto DEFAULT mudou entre versões: se o valor no disco do dono ainda é um
     *  destes valores antigos (nunca foi customizado), o load troca pelo novo default. Customização
     *  de verdade (qualquer outro valor) é respeitada. */
    private static final Map<String, java.util.Set<String>> STALE_DEFAULTS = new java.util.HashMap<>();
    static {
        STALE_DEFAULTS.put("devstudio.lure.title", java.util.Set.of("Lure Config", "Cobblemon Cosmetics"));
        STALE_DEFAULTS.put("devstudio.cosmetic.btn.config_lure", java.util.Set.of(">> Configure LURE", ">> Cobblemon Cosmetics"));
        STALE_DEFAULTS.put("devstudio.cosmetic.divider.lure", java.util.Set.of("=== LURE SYSTEM ===", "=== COBBLEMON COSMETICS ==="));
        STALE_DEFAULTS.put("devstudio.cosmetic.tooltip.config_lure", java.util.Set.of(
                "<gray>Cobblemon-specific perks this cosmetic grants:\n<gray>Lure bonuses (shiny/IV/spawn/xp/fishing) and the\n<gray>IVs Scanner. Hover each field inside for details."));
    }

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
                    String onDiskVal = onDisk.get(key);
                    // Chave RENOMEADA no código: se o valor no disco ainda é um default ANTIGO
                    // (o dono nunca customizou), atualiza pro novo. Se ele customizou pra outra
                    // coisa, respeita. Ver STALE_DEFAULTS.
                    java.util.Set<String> stale = STALE_DEFAULTS.get(key);
                    if (stale != null && stale.contains(onDiskVal)) {
                        merged.put(key, def.getValue());
                        changed = true;
                    } else {
                        merged.put(key, onDiskVal);
                    }
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
        commands.put("commands.extraslot.done", "<green>{player} now has <white>{total}<green> extra slot(s) for <white>{slot}<green>.");
        commands.put("commands.extraslot.bad_slot", "<red>Invalid slot '{slot}'. Use ALL, HEAD, FACE, NECK, CHEST, BACK, WAIST, LEGS, FEET or HAND.");
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
        m.put("messages.cosmetic.variant_conflict", "<red>Unequip the current variant of this cosmetic first.");
        m.put("messages.cosmetic.variant_unknown", "<red>That variant no longer exists.");
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
        m.put("messages.tag.dev_group_added", "<green>[DEV] You were added to group '{id}'.");
        m.put("messages.tag.dev_group_set", "<yellow>[DEV] Your groups were replaced with '{id}' only.");
        m.put("messages.tag.dev_group_fail", "<red>[DEV] Failed to change group '{id}' (LuckPerms missing?).");
        m.put("messages.tag.dev_custom_added", "<green>[DEV] Custom tag '{id}' granted and equipped.");
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
        items.put("items.lure.expall", "<aqua>📉 Exp Share to Party: <green>{value}x");
        items.put("items.lure.friendship", "<light_purple>❤ Friendship: <green>+{value}x");
        items.put("items.lure.ev", "<green>🐍 Battle EV: <green>+{value}x");
        items.put("items.lure.capture", "<red>🎯 Capture Chance: <green>+{value}x");
        items.put("items.lure.iv", "<gold>⭐ Guaranteed Perfect IVs: <green>+{value}");
        items.put("items.lure.iv_chance", "<blue>🎲 Per-IV Perfect Chance: <green>+{value}%");
        items.put("items.lure.ultrarare", "<light_purple>🔮 Ultra Rare Chance: <green>+{value}x");
        items.put("items.lure.hidden_ability", "<dark_aqua>👁 Hidden Ability: <green>+{value}x");
        items.put("items.lure.fishing_header", "<aqua>🎣 <b>Fishing Bonus</b>");
        items.put("items.lure.fishing_shiny", "<gray>  ▪ <yellow>Shiny: <green>+{value}x");
        items.put("items.lure.fishing_iv_chance", "<gray>  ▪ <green>Per-IV Perfect Chance: <green>+{value}%");
        items.put("items.lure.fishing_speed", "<gray>  ▪ <aqua>Speed: <green>+{value}%");
        // ---------------- HUD de cosméticos (LureHudOverlay — canto inferior DIREITO) ----------------
        items.put("hud.cos.section.abilities", "<gold>Abilities");
        items.put("hud.cos.section.effects", "<gold>Effects");
        items.put("hud.cos.section.lure", "<gold>Lure");
        items.put("hud.cos.section.fishing", "<gold>Fishing");
        items.put("hud.cos.flight", "<aqua>Flight");
        items.put("hud.cos.backpack", "<yellow>Backpack — {rows} rows");
        items.put("hud.cos.autofeed", "<green>Auto Feed");
        items.put("hud.cos.particle_trail", "<light_purple>Particle Trail");
        items.put("hud.cos.ivs_scanner", "<aqua>IVs Scanner");
        items.put("hud.cos.scanner.line", "<aqua>{names} Scanner");
        items.put("hud.cos.scanner.ivs", "IVs");
        items.put("hud.cos.scanner.nature", "Nature");
        items.put("hud.cos.scanner.size", "Size");
        items.put("hud.cos.scanner.ability", "Ability");
        items.put("hud.lure.type", "<white>Type: <aqua>{value}");
        items.put("hud.lure.shiny", "<yellow>✨ Shiny <green>+{value}x");
        items.put("hud.lure.ultrarare", "<dark_purple>🔮 Ultra Rare <green>+{value}x");
        items.put("hud.lure.hidden_ability", "<dark_aqua>👁 Hidden Ability <green>+{value}x");
        items.put("hud.lure.iv", "<gold>⭐ Perfect IVs <green>+{value}");
        items.put("hud.lure.iv_chance", "<blue>🎲 Per-IV Perfect <green>+{value}%");
        items.put("hud.lure.expall", "<aqua>⚡ Exp Share <green>{value}x");
        items.put("hud.lure.exp", "<aqua>📘 Exp (Main) <green>+{value}x");
        items.put("hud.lure.ev", "<green>🐍 Battle EV <green>+{value}x");
        items.put("hud.lure.friendship", "<light_purple>❤ Friendship <green>+{value}x");
        items.put("hud.lure.capture", "<red>🎯 Capture <green>+{value}x");
        items.put("hud.lure.fishing_shiny", "<yellow>✨ Shiny <green>+{value}x");
        items.put("hud.lure.fishing_iv", "<gold>⭐ Perfect IVs <green>+{value}");
        items.put("hud.lure.fishing_iv_chance", "<blue>🎲 Per-IV Perfect <green>+{value}%");
        items.put("hud.lure.fishing_speed", "<aqua>💨 Speed <green>+{value}%");

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
        w.put("wardrobe.customize.hide_others", "Others' cosmetics");
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
        a.put("accessories.tooltip.iv_scanner", " <aqua>⚲ <white>IVs Scanner");
        a.put("accessories.tooltip.passive_effects", "<light_purple>Passive Effects:");
        a.put("accessories.tooltip.effect_line", " <dark_purple>- <white>");
        a.put("accessories.variant_picker.default", "Default");
        a.put("accessories.tooltip.lure_header", "<light_purple><bold>✦ <light_purple><bold>Attraction Power (Lure):");
        a.put("accessories.tooltip.lure_type", " <gray>▪ <white>Affected Type: <aqua>{value}");
        a.put("accessories.tooltip.lure_shiny", " <yellow>✨ <white>Shiny: <green>+{value}x");
        a.put("accessories.tooltip.lure_ultrarare", " <dark_purple>🔮 <white>Ultra Rare: <green>+{value}x");
        a.put("accessories.tooltip.lure_hidden_ability", " <dark_aqua>👁 <white>Hidden Ability: <green>+{value}x");
        a.put("accessories.tooltip.lure_iv", " <gold>⭐ <white>Guaranteed Perfect IVs: <green>+{value}");
        a.put("accessories.tooltip.lure_iv_chance", " <blue>🎲 <white>Per-IV Perfect Chance: <green>+{value}%");
        a.put("accessories.tooltip.lure_expall", " <aqua>⚡ <white>Exp Share to Party: <green>{value}x");
        a.put("accessories.tooltip.lure_exp", " <aqua>📘 <white>Exp (Main): <green>+{value}x");
        a.put("accessories.tooltip.lure_ev", " <green>🐍 <white>Battle EV: <green>+{value}x");
        a.put("accessories.tooltip.lure_friendship", " <light_purple>❤ <white>Friendship: <green>+{value}x");
        a.put("accessories.tooltip.lure_capture", " <red>🎯 <white>Capture Chance: <green>+{value}x");
        a.put("accessories.tooltip.lure_fishing_header", " <aqua><bold>🎣 Fishing Bonus:");
        a.put("accessories.tooltip.lure_fishing_shiny", "   <yellow>✨ <white>Shiny: <green>+{value}x");
        a.put("accessories.tooltip.lure_fishing_iv", "   <gold>⭐ <white>Guaranteed IVs: <green>+{value}");
        a.put("accessories.tooltip.lure_fishing_iv_chance", "   <blue>🎲 <white>Per-IV Perfect Chance: <green>+{value}%");
        a.put("accessories.tooltip.lure_fishing_speed", "   <dark_aqua>💨 <white>Speed: <green>+{value}");
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
        t.put("tags.dev.add", "+ Add to me");
        t.put("tags.dev.set", "Set as only");
        t.put("tags.dev.set_confirm_title", "Set as your only group?");
        t.put("tags.dev.set_confirm_body", "You will lose every other LuckPerms group.");
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
        d.put("devstudio.menu.tags", "Chat Tags");
        d.put("devstudio.menu.help", "<gray>Need help? Ask on the mod's\n<gray>support Discord!");
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
        d.put("devstudio.cosmetic.divider.lure", "=== COBBLEMON EFFECTS ===");
        d.put("devstudio.cosmetic.divider.sounds", "=== SOUNDS ===");
        d.put("devstudio.cosmetic.field.real_item", "Real Item");
        d.put("devstudio.cosmetic.tooltip.real_item", "<gray>The real Minecraft item this armor cosmetic\n<gray>represents (players actually wear this item,\n<gray>the mod just changes how it looks in-game).\n<gray>Type the item's ID, e.g. minecraft:diamond_helmet.\n<gray>Chestplate/leggings auto-split into body + limb parts.");
        d.put("devstudio.cosmetic.btn.split_armor_parts", ">> Split into {n} body parts");
        d.put("devstudio.cosmetic.tooltip.split_armor_parts", "<gray>Rebuilds the Part list to match this armor slot:\n<gray>chestplate = torso + right arm + left arm,\n<gray>leggings = torso + right leg + left leg,\n<gray>boots = right leg + left leg. Each part follows\n<gray>its body bone. Keeps the model of Part 0.");
        d.put("devstudio.cosmetic.confirm.split_armor_title", "Split parts?");
        d.put("devstudio.cosmetic.confirm.split_armor_body", "Replaces the current parts with {n} (one per body bone).");
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
        d.put("devstudio.cosmetic.field.granted_permissions", "Granted Permissions");
        d.put("devstudio.cosmetic.tooltip.granted_permissions", "<gray>Permission nodes GRANTED to the player while this\n<gray>cosmetic is equipped (and the Permission gate above\n<gray>passes), removed on unequip. Comma-separated.\n<gray>Needs LuckPerms. Nodes are session-only (transient)\n<gray>— they never touch LuckPerms storage. Don't put\n<gray>this cosmetic's own gate node here.");
        d.put("devstudio.cosmetic.field.minecraft_tags", "Minecraft Tags");
        d.put("devstudio.cosmetic.tooltip.minecraft_tags", "<gray>Vanilla scoreboard tags (/tag) added to the player\n<gray>while this cosmetic is equipped and removed on\n<gray>unequip. Comma-separated. Use them in\n<gray>/execute if entity @s[tag=...] and datapacks.");
        d.put("devstudio.cosmetic.field.tooltip_description", "Tooltip Description");
        d.put("devstudio.cosmetic.tooltip.tooltip_description", "<gray>Free text shown in the accessory tooltip (right\n<gray>under the name) in the Wardrobe. MiniMessage; type\n<gray>\\n for a line break. Empty = nothing shows.");
        d.put("devstudio.cosmetic.divider.auto_unlock", "=== AUTO-UNLOCK ===");
        d.put("devstudio.cosmetic.field.unlock_permission", "Unlock Permission");
        d.put("devstudio.cosmetic.tooltip.unlock_permission", "<gray>Players who HAVE this permission node get this\n<gray>cosmetic automatically — no need to /gc give it.\n<gray>It's dynamic: lose the node (e.g. VIP expired) and\n<gray>the cosmetic is gone (unequips). Nothing is saved\n<gray>to the database. Empty = off. Different from\n<gray>\"Permission\" above, which only gates visibility.");
        d.put("devstudio.cosmetic.field.unlock_tag", "Unlock Tag");
        d.put("devstudio.cosmetic.tooltip.unlock_tag", "<gray>Same as Unlock Permission, but a vanilla scoreboard\n<gray>tag (/tag <player> add <tag>). Player has the tag\n<gray>→ gets the cosmetic (dynamic). Fill EITHER this or\n<gray>Unlock Permission (or both). Empty = off.");
        d.put("devstudio.cosmetic.field.part_model", "Part {i}: Model ID");
        d.put("devstudio.cosmetic.tooltip.part_model", "<gray>The item/model this Part renders — either a\n<gray>Custom Model Data name registered by this mod,\n<gray>or a vanilla item id. Ignored if the GeckoLib\n<gray>Model ID below is filled in.");
        d.put("devstudio.cosmetic.hint.part_model", "e.g. wizard_hat (name) or hats/wizard_hat (Exact Path)");
        d.put("devstudio.cosmetic.field.part_geo", "Part {i}: GeckoLib Model");
        d.put("devstudio.cosmetic.tooltip.part_geo", "<gray>Optional. The id of a GeckoLib 3D model (.geo.json)\n<gray>for this Part, used instead of a flat item icon.\n<gray>Takes priority over Model ID above and over the\n<gray>Real Item, if this is an armor cosmetic.");
        d.put("devstudio.cosmetic.hint.part_geo", "e.g. dragon_wings (name) or item/dragon_wings (Exact Path)");
        d.put("devstudio.cosmetic.field.part_anchor", "Part {i}: Anchor");
        d.put("devstudio.cosmetic.tooltip.part_anchor", "<gray>Which body part this Part is attached to:\n<gray>HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG or\n<gray>LEFT_LEG. Applies to the flat-icon and GeckoLib\n<gray>render paths. With a real 3D armor model the\n<gray>piece always renders in its natural spot.");
        d.put("devstudio.cosmetic.field.part_exact_path", "Part {i}: Exact Path");
        d.put("devstudio.cosmetic.tooltip.part_exact_path", "<gray>OFF (default): the Model ID/GeckoLib fields above\n<gray>are just a file name, found in any folder.\n<gray>ON: they become the full relative path, e.g.\n<gray>\"hats/wizard_hat\" — needed when two files share a\n<gray>name in different folders.");
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
        d.put("devstudio.cosmetic.btn.config_lure", ">> Cobblemon Effects");
        d.put("devstudio.cosmetic.tooltip.config_lure", "<gray>Cobblemon-specific perks this cosmetic grants:\n<gray>Lure bonuses (shiny/IV/EV/spawn/xp/fishing) and the\n<gray>scanners (IVs/Nature/Ability/Size). Hover each field\n<gray>inside for details.");
        // --- Part popup ---
        d.put("devstudio.part.new", "New");
        d.put("devstudio.part.popup_title", "Part {i}: {name}");
        d.put("devstudio.part.divider.gizmo_tool", "3D GIZMO TOOL");
        d.put("devstudio.part.divider.general", "GENERAL SETTINGS");
        d.put("devstudio.part.divider.scale", "SCALE");
        d.put("devstudio.part.divider.normal_values", "NORMAL VALUES");
        d.put("devstudio.part.divider.sneak_values", "SNEAK VALUES");
        // --- Variants ---
        d.put("devstudio.variant.divider", "=== VARIANTS ===");
        d.put("devstudio.variant.field.id", "Variant {i} ID");
        d.put("devstudio.variant.hint.id", "e.g. shoulder");
        d.put("devstudio.variant.tooltip.id", "<gray>Internal id (letters/numbers/underscore).\n<gray>Unique within this cosmetic. Players pick the\n<gray>variant by its display name; this id is what's stored.");
        d.put("devstudio.variant.btn.config", ">> Config Variant {i}");
        d.put("devstudio.variant.tooltip.config", "<gray>Edit this variant's display name, slot, and the\n<gray>anchor / offset / rotation / scale of its model.\n<gray>The model itself is reused from the base cosmetic.");
        d.put("devstudio.variant.btn.remove", "X Remove Variant {i}");
        d.put("devstudio.variant.btn.add", "+ Add Variant");
        d.put("devstudio.variant.tooltip.add", "<gray>A variant reuses this cosmetic's model but with its\n<gray>own slot and placement. When a cosmetic has variants,\n<gray>players get a picker: 'Default' + each variant.\n<gray>Only one variant (or Default) can be worn at a time.");
        d.put("devstudio.variant.confirm.title", "Remove variant?");
        d.put("devstudio.variant.confirm.body", "Deletes variant {i} permanently.");
        d.put("devstudio.variant.popup_title", "Variant {i}");
        d.put("devstudio.variant.field.name", "Display Name");
        d.put("devstudio.variant.tooltip.name", "<gray>Shown in the player's variant picker. MiniMessage ok.");
        d.put("devstudio.variant.field.slot", "Slot (blank = inherit)");
        d.put("devstudio.variant.tooltip.slot", "<gray>Which virtual slot this variant occupies (for slot\n<gray>limits). Blank = same slot as the base cosmetic.");
        d.put("devstudio.variant.field.anchor", "Anchor");
        d.put("devstudio.variant.tooltip.anchor", "<gray>Body part the variant's model attaches to:\n<gray>HEAD / BODY / RIGHT_ARM / LEFT_ARM / RIGHT_LEG / LEFT_LEG.");
        d.put("devstudio.part.gizmo_hint", "<yellow>3D Tool (Hold X, Y or Z and drag outside):");
        d.put("devstudio.part.gizmo_move", "Move");
        d.put("devstudio.part.gizmo_rotate", "Rotate");
        d.put("devstudio.part.gizmo_scale", "Scale");
        // --- Cobblemon Effects popup (ex-Lure) ---
        d.put("devstudio.lure.title", "Cobblemon Effects");
        d.put("devstudio.cobcos.divider.lures", "LURES");
        d.put("devstudio.cobcos.divider.fishing", "FISHING");
        d.put("devstudio.cobcos.divider.scanner", "SCANNER");
        d.put("devstudio.cobcos.field.ivs_scanner", "IVs Scanner");
        d.put("devstudio.cobcos.field.nature_scanner", "Nature Scanner");
        d.put("devstudio.cobcos.field.ability_scanner", "Ability Scanner");
        d.put("devstudio.cobcos.field.size_scanner", "Size Scanner");
        d.put("devstudio.cobcos.tooltip.nature_scanner", "While worn, shows each nearby Pokemon's NATURE above its name (right under the IV block when both scanners are on). Server-computed, like the IVs Scanner.");
        d.put("devstudio.cobcos.tooltip.ability_scanner", "While worn, shows each nearby Pokemon's ABILITY to the RIGHT of its name, in red. Server-computed.");
        d.put("devstudio.cobcos.tooltip.size_scanner", "While worn, shows each nearby Pokemon's SIZE (XS/S/M/L/XL) to the LEFT of its name, in yellow bold. Server-computed.");
        d.put("devstudio.cobcos.tooltip.enabled", "Master switch. Off = none of the bonuses below do anything.\nThe scanners have their own switches and are NOT gated by this.");
        d.put("devstudio.cobcos.tooltip.type", "Type name or id (e.g. fire). While worn, ~95% of the WILD Pokemon spawning around you are of this type (the rest still spawn rarely, so there are no dead spells with nothing spawning). Your party / NPCs are never affected. Blank = off.");
        d.put("devstudio.cobcos.tooltip.shiny_mult", "Chance (0.0-1.0) to re-roll a non-shiny catch into shiny, checked on capture. Stacks additively across all worn Lure cosmetics.");
        d.put("devstudio.cobcos.tooltip.ultrarare_mult", "Chance (0.0-1.0) to upgrade a land spawn to the 'ultra-rare' rarity bucket. Fishing does not use rarity buckets, so this has no effect on fished Pokemon.");
        d.put("devstudio.cobcos.tooltip.hidden_ability_mult", "Chance (0.0-1.0) that a caught Pokemon gets its hidden ability.");
        d.put("devstudio.cobcos.tooltip.iv", "Number of IVs forced to 31 on capture (random stats). 0-6.");
        d.put("devstudio.cobcos.tooltip.iv_chance", "Chance PER IV (0.0-1.0) for each of the 6 IVs to roll 31 on capture, on top of the ones guaranteed by Lure IV. 0.25 = 25% on each stat.");
        d.put("devstudio.cobcos.tooltip.capture_chance", "Chance (0.0-1.0) to turn a FAILED capture into a success on the ball throw.");
        d.put("devstudio.cobcos.tooltip.exp_mult", "Extra experience for the Pokemon that battled. 0.5 = +50%. Applies to the active Pokemon only (unless Exp Share is on).");
        d.put("devstudio.cobcos.tooltip.expall", "On = every other ALIVE party Pokemon also gets the full XP the active Pokemon gained (EXP Multiplier included). Does NOT apply to candy XP.");
        d.put("devstudio.cobcos.tooltip.friendship_mult", "Multiplies positive friendship gains. 0.5 = +50%.");
        d.put("devstudio.cobcos.tooltip.ev_mult", "Extra EVs gained in battle by the player's Pokemon. 0.5 = +50%. 0 = off. (Cobblemon 1.8+ only — earlier versions had no hook.)");
        d.put("devstudio.cobcos.tooltip.fishing_shiny", "Extra shiny chance (0.0-1.0), fishing only. Stacks with Shiny Multiplier.");
        d.put("devstudio.cobcos.tooltip.fishing_iv", "Guaranteed perfect IVs on a fished Pokemon (random stats). 0-6.");
        d.put("devstudio.cobcos.tooltip.fishing_iv_chance", "Per-IV chance (0.0-1.0) for each IV to roll 31 on a fished Pokemon.");
        d.put("devstudio.cobcos.tooltip.fishing_speed", "0.0-1.0. Adds Cobblemon 'Lure' enchant levels to the rod for faster bites (~+1 level per 33%, capped at +10).");
        d.put("devstudio.cobcos.tooltip.ivs_scanner", "While worn, shows the IVs of every Pokemon within ~48 blocks above its name (HP/Atk/Def/SpA/SpD/Spe, colored). Independent from the Lure master switch. The server computes this — no client-side wild IV data exists otherwise.");
        d.put("devstudio.lure.field.enabled", "Lure Enabled");
        d.put("devstudio.lure.field.type", "Lure TYPE");
        d.put("devstudio.lure.field.shiny_mult", "Shiny Multiplier");
        d.put("devstudio.lure.field.ultrarare_mult", "Ultra Rare Multiplier");
        d.put("devstudio.lure.field.hidden_ability_mult", "Hidden Ability Multiplier");
        d.put("devstudio.lure.field.expall_mult", "Exp Share to Party (on/off)");
        d.put("devstudio.lure.field.friendship_mult", "Friendship Multiplier");
        d.put("devstudio.lure.field.iv", "Lure IV (guaranteed 31s)");
        d.put("devstudio.lure.field.iv_chance", "Per-IV Perfect Chance (0.0-1.0)");
        d.put("devstudio.lure.field.fishing_shiny", "Fishing Shiny");
        d.put("devstudio.lure.field.fishing_iv_chance", "Fishing Per-IV Perfect Chance");
        d.put("devstudio.lure.field.fishing_iv", "Fishing IV");
        d.put("devstudio.lure.field.fishing_speed", "Fishing Speed");
        d.put("devstudio.lure.field.exp_mult", "EXP Multiplier");
        d.put("devstudio.lure.field.ev_mult", "EV Multiplier");
        d.put("devstudio.lure.field.capture_chance", "Capture Chance");
        // --- Effects popup ---
        d.put("devstudio.effects.title", "Potion Effects");
        d.put("devstudio.effects.divider", "CLICK TO CYCLE THE LEVEL (OFF -> V)");
        d.put("devstudio.effects.level", "Level {n}");
        d.put("devstudio.effects.off", "OFF (click to enable)");
        // --- Effects subpage ---
        d.put("devstudio.effect.list_title", "<light_purple>Effects");
        d.put("devstudio.effect.search_hint", "Search…");
        d.put("devstudio.effect.filter.all", "All");
        d.put("devstudio.effect.filter.effects", "Effects");
        d.put("devstudio.effect.filter.groups", "Groups");
        d.put("devstudio.effect.new", "+ New Effect");
        d.put("devstudio.effect.new_group", "+ New Group");
        d.put("devstudio.effect.delete", "DELETE EFFECT");
        d.put("devstudio.effect.particles_none", "<gray>(no matches)");
        d.put("devstudio.effect.divider.identification", "=== IDENTIFICATION ===");
        d.put("devstudio.effect.divider.configuration", "=== CONFIGURATION ===");
        d.put("devstudio.effect.divider.color", "=== COLOR ===");
        d.put("devstudio.effect.field.color_enabled", "Custom Color");
        d.put("devstudio.effect.field.color_r", "Red");
        d.put("devstudio.effect.field.color_g", "Green");
        d.put("devstudio.effect.field.color_b", "Blue");
        d.put("devstudio.effect.tooltip.color", "<gray>0-255 each. Leave all three BLANK for no color.\n<gray>Only works on colourable particles:\n<gray>minecraft:dust, minecraft:dust_color_transition\n<gray>and minecraft:entity_effect. Ignored on the rest.");
        // --- Effect groups ---
        d.put("devstudio.effect.group.divider.id", "=== GROUP ID ===");
        d.put("devstudio.effect.group.field.id", "Group ID");
        d.put("devstudio.effect.group.divider.members", "=== EFFECTS IN THIS GROUP ===");
        d.put("devstudio.effect.group.btn.edit_member", "Edit: {id}");
        d.put("devstudio.effect.group.btn.edit_owned", "Edit: {label}");
        d.put("devstudio.effect.group.btn.remove_member", "Remove {id}");
        d.put("devstudio.effect.group.btn.add_existing", "+ Add Existing Effect");
        d.put("devstudio.effect.group.btn.create_new", "+ Create New Effect");
        d.put("devstudio.effect.group.delete", "DELETE GROUP");
        d.put("devstudio.effect.group.pick_title", "=== PICK AN EFFECT TO ADD ===");
        d.put("devstudio.effect.group.pick_cancel", "< Cancel");
        // --- Effect / group: confirmação de delete ---
        d.put("devstudio.effect.confirm.delete_group_title", "Delete group?");
        d.put("devstudio.effect.confirm.delete_effect_title", "Delete effect?");
        d.put("devstudio.effect.confirm.delete_body", "\"{id}\" will be gone for good.");
        d.put("devstudio.effect.confirm.remove_member_title", "Remove from group?");
        d.put("devstudio.effect.confirm.remove_member_body", "\"{id}\" stays in the Effects tab.");
        d.put("devstudio.effect.confirm.remove_owned_title", "Delete this effect?");
        d.put("devstudio.effect.confirm.remove_owned_body", "It only exists inside this group.");
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
        // --- Effect: presets + formas ---
        d.put("devstudio.effect.divider.preset", "=== EFFECT PRESET ===");
        d.put("devstudio.effect.field.preset", "Preset");
        d.put("devstudio.effect.tooltip.preset", "<gray>Pick a ready-made effect (Ring, Helix, Beam, Pulse\n<gray>...) — it fills in every field below and you tweak\n<gray>from there. This box stays empty; it's an action.");
        d.put("devstudio.effect.divider.shape", "=== SHAPE ===");
        d.put("devstudio.effect.field.shape", "Shape");
        d.put("devstudio.effect.tooltip.shape", "<gray>SIMPLE = a burst of particles with spread (the\n<gray>classic behaviour). CIRCLE / HELIX / BEAM / PULSE\n<gray>draw a geometric pattern instead — the fields\n<gray>below change with the shape you pick.");
        d.put("devstudio.effect.divider.shape_params", "=== SHAPE PARAMS ===");
        d.put("devstudio.effect.field.radius", "Radius");
        d.put("devstudio.effect.tooltip.radius", "<gray>Size of the ring/helix (blocks). For BEAM it's the\n<gray>horizontal offset of extra strands.");
        d.put("devstudio.effect.field.points", "Points");
        d.put("devstudio.effect.tooltip.points", "<gray>Particles per full turn / per ring. More = smoother\n<gray>but heavier. Capped around 200.");
        d.put("devstudio.effect.field.strands", "Strands");
        d.put("devstudio.effect.tooltip.strands", "<gray>Parallel copies of the shape, evenly spaced (e.g.\n<gray>2 = double helix). Capped at 12.");
        d.put("devstudio.effect.field.phase", "Phase");
        d.put("devstudio.effect.tooltip.phase", "<gray>Starting angle of the shape, in degrees.");
        d.put("devstudio.effect.field.clockwise", "Clockwise");
        d.put("devstudio.effect.tooltip.clockwise", "<gray>Direction the shape is drawn / spins.");
        d.put("devstudio.effect.field.helix_height", "Helix Height");
        d.put("devstudio.effect.tooltip.helix_height", "<gray>How tall the helix is, in blocks.");
        d.put("devstudio.effect.field.turns", "Turns");
        d.put("devstudio.effect.tooltip.turns", "<gray>How many full revolutions the helix makes over its\n<gray>height.");
        d.put("devstudio.effect.field.reverse", "Reverse");
        d.put("devstudio.effect.tooltip.reverse", "<gray>Draw the helix from the top down instead of bottom\n<gray>up.");
        d.put("devstudio.effect.field.beam_height", "Beam Height");
        d.put("devstudio.effect.tooltip.beam_height", "<gray>Length of the beam, in blocks.");
        d.put("devstudio.effect.field.spacing", "Spacing");
        d.put("devstudio.effect.tooltip.spacing", "<gray>Gap between particles along the beam, in blocks.\n<gray>Smaller = denser beam.");
        d.put("devstudio.effect.field.upwards", "Upwards");
        d.put("devstudio.effect.tooltip.upwards", "<gray>Beam goes up from the anchor (on) or down (off).");
        d.put("devstudio.effect.field.end_radius", "End Radius");
        d.put("devstudio.effect.tooltip.end_radius", "<gray>The radius the pulse grows to (starts at Radius).");
        d.put("devstudio.effect.field.end_points", "End Points");
        d.put("devstudio.effect.tooltip.end_points", "<gray>Particle count of the outermost ring (starts at\n<gray>Points).");
        d.put("devstudio.effect.field.rings", "Rings");
        d.put("devstudio.effect.tooltip.rings", "<gray>How many rings the pulse steps through between\n<gray>Radius and End Radius. Capped at 24.");
        d.put("devstudio.effect.field.outwards", "Outwards");
        d.put("devstudio.effect.tooltip.outwards", "<gray>Pulse expands outward (on) or contracts inward\n<gray>(off).");
        d.put("devstudio.effect.field.rot_x", "Rotate X");
        d.put("devstudio.effect.field.rot_y", "Rotate Y");
        d.put("devstudio.effect.field.rot_z", "Rotate Z");
        d.put("devstudio.effect.tooltip.rot", "<gray>Tilt the whole shape around each axis, in degrees.\n<gray>e.g. Rotate X 30 tilts a flat ring forward.");
        d.put("devstudio.effect.field.anim_ticks", "Anim Ticks");
        d.put("devstudio.effect.tooltip.anim_ticks", "<gray>0 = the whole shape is drawn every Interval ticks\n<gray>(static). >0 = the shape ANIMATES over this many\n<gray>ticks (helix draws, beam rises, pulse expands),\n<gray>then pauses Interval ticks and repeats.");
        d.put("devstudio.effect.group.divider.preset", "=== GROUP PRESET ===");
        d.put("devstudio.effect.group.tooltip.preset", "<gray>Pick a ready-made combo (Smash, Vortex...) — it\n<gray>creates one effect per piece and REPLACES this\n<gray>group's members with them.");
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
        d.put("devstudio.serverconfig.field.effect_block_groups", "Effect-Blocked Groups");
        d.put("devstudio.serverconfig.tooltip.effect_block_groups", "<gray>LuckPerms groups (comma-separated) whose players get\n<gray>NO cosmetic effects at all — particles, potion effects,\n<gray>flight, speed, lure, scanners, granted perms/tags. The\n<gray>cosmetic model still shows. Empty = nobody blocked.");
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
        // --- Chat Tags subpage ---
        d.put("devstudio.tags.list_title", "<green>Chat Tags");
        d.put("devstudio.tags.new", "+ New Tag");
        d.put("devstudio.tags.title_new", "§bNew Tag");
        d.put("devstudio.tags.delete", "DELETE TAG");
        d.put("devstudio.tags.group_locked", "LuckPerms group tag — editable, can't be deleted");
        d.put("devstudio.tags.field.id", "Tag ID");
        d.put("devstudio.tags.tooltip.id", "<gray>Internal name of the tag — used in /gc tags and the\n<gray>config file name. Lowercase letters, numbers and\n<gray>underscore only. Can't be changed after creation.");
        d.put("devstudio.tags.field.display_name", "Display Name");
        d.put("devstudio.tags.tooltip.display_name", "<gray>Name shown in the Tags menu and tooltips. Supports\n<gray>color codes (&a, &d...) and MiniMessage tags.");
        d.put("devstudio.tags.field.description", "Description");
        d.put("devstudio.tags.tooltip.description", "<gray>Optional line shown under the name in the Tags\n<gray>menu tooltip.");
        d.put("devstudio.tags.field.prefix", "Prefix");
        d.put("devstudio.tags.tooltip.prefix", "<gray>The chat prefix applied to the player while this\n<gray>tag is equipped (via LuckPerms, weight 1000).\n<gray>MiniMessage, e.g. <gold>[VIP]</gold>.");
        d.put("devstudio.tags.field.permissions", "Permissions");
        d.put("devstudio.tags.tooltip.permissions", "<gray>Permission nodes granted to the player while this\n<gray>tag is equipped, removed on unequip. Comma-\n<gray>separated. Needs LuckPerms.");
        d.put("devstudio.tags.field.minecraft_tag", "Minecraft Tag");
        d.put("devstudio.tags.tooltip.minecraft_tag", "<gray>Optional vanilla scoreboard tag (/tag) added while\n<gray>this chat tag is equipped and removed on unequip.");
        files.put("devstudio", d);

        return files;
    }
}
