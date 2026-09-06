package com.f4xizzz.greatcosmetics.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro de itens de ARMADURA (inclusive de outros mods) que devem parar de se comportar como
 * armadura de verdade e virar puramente cosméticos: sem pontos de armadura, toughness,
 * resistência a knockback ou qualquer outro atributo que o item concederia normalmente ao ser
 * equipado, e sem crafting disponível (ver ItemStackAttributeMixin/RecipeManagerMixin).
 *
 * Cada entrada tem seu PRÓPRIO id de cosmético (igual qualquer outro em CosmeticsConfig — dá pra
 * usar em /gc give, /gc npc equip, tags, etc), separado do id do item real que ela representa
 * (CosmeticData.realItemId). O resto dos campos (DisplayName, effects, lure, sons...) é editável
 * pela Dev Page igual um cosmético normal — só os campos de modelo 3D (parts/offsets/rotação) não
 * se aplicam aqui, já que ela renderiza o item real dobrado no corpo, não um ghost carved_pumpkin.
 *
 * Nunca é lida do cosmeticsconfig.conf — sempre carregada daqui (armor_cosmetics.json) e exposta
 * ao resto do mod (GUI de Acessórios, EquipCosmeticPayload, limites de slot/tipo, banco de dados)
 * através de {@link GreatCosmetics#getCosmeticById(String)}, que já cai pra cá como fallback.
 */
public class ArmorCosmeticsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final File DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), "GreatCosmetics");
    private static final File FILE = new File(DIR, "armor_cosmetics.json");

    /** cosmeticId (escolhido pelo admin, igual CosmeticsConfig) -> CosmeticData completo, com
     *  realItemId apontando pro item de verdade que ela representa. */
    public static final Map<String, CosmeticData> armorCosmetics = new ConcurrentHashMap<>();

    /** Índice reverso (itemId real -> cosmeticId) só pra isConverted() ser rápido. */
    private static final Set<String> convertedItemIds = ConcurrentHashMap.newKeySet();

    /** Índice reverso completo (itemId real -> cosmeticId), pra achar QUAL cosmético um item de
     *  inventário representa (ver GreatCosmetics#checkAndConvertArmorInventory). */
    private static final Map<String, String> itemIdToCosmeticId = new ConcurrentHashMap<>();

    /** Forma de JSON: igual CosmeticData, só com um campo "itemId" a mais (não existe na classe
     *  base porque realItemId é transient — nunca deveria persistir pra cosméticos normais). */
    private static class ArmorCosmeticEntry extends CosmeticData {
        String itemId = "";
        String slotLegacy; // suporte ao formato bem antigo, onde só existia "slot" (sem itemId nem resto)
    }

    private static class ConfigData {
        Map<String, ArmorCosmeticEntry> convertedItems = new HashMap<>();
    }

    public static void load() {
        armorCosmetics.clear();
        convertedItemIds.clear();
        itemIdToCosmeticId.clear();
        if (!DIR.exists()) DIR.mkdirs();

        if (!FILE.exists()) {
            ConfigData defaults = new ConfigData();
            ArmorCosmeticEntry entry = new ArmorCosmeticEntry();
            entry.itemId = "minecraft:diamond_helmet";
            entry.slot = CosmeticData.VirtualSlot.HEAD;
            entry.type = "armor_cosmetic";
            // BUG (2026-09): esse entry nunca ganhava uma Part — "parts" começa como ArrayList
            // vazia (ver CosmeticData#parts) e nada aqui preenchia ela, diferente do load() normal
            // (linha ~131 abaixo, "if (entry.parts.isEmpty()) entry.parts.add(...)"). Resultado: no
            // PRIMEIRO boot (arquivo ainda não existe) o exemplo ficava sem NENHUMA parte — o loop
            // de render em ArmorFeatureRendererMixin não desenhava nada. Corrigido preenchendo já
            // aqui. Escala/offset ficam nos valores padrão (1.0/0) de propósito: um ArmorItem de
            // slot HEAD de verdade (como diamond_helmet) agora renderiza pelo capacete 3D real do
            // Minecraft, não um ícone chapado (ver ArmorFeatureRendererMixin#greatcosmetics$
            // renderRealArmor) — encaixa sozinho, sem precisar de nenhum ajuste manual.
            CosmeticData.CosmeticPart defaultPart = new CosmeticData.CosmeticPart(CosmeticData.Anchor.HEAD);
            entry.parts.add(defaultPart);
            defaults.convertedItems.put("diamond_helmet", entry);
            save(defaults);
            System.out.println("[GreatCosmetics] armor_cosmetics.json generated with a default example.");

            // Sem isso, o mapa em memória ficava vazio até o PRÓXIMO load() — o item de exemplo
            // era salvo no disco mas nunca ficava disponível nesse boot.
            applyEntry("diamond_helmet", entry);
            return;
        }

        boolean migrated = false;
        boolean shouldResave = false;

        try (FileReader reader = new FileReader(FILE)) {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            com.google.gson.JsonElement itemsElement = root.get("convertedItems");

            if (itemsElement != null && itemsElement.isJsonArray()) {
                // Formato BEM antigo: lista simples de IDs de item (de antes até do slot existir).
                int i = 0;
                for (com.google.gson.JsonElement el : itemsElement.getAsJsonArray()) {
                    String itemId = el.getAsString().toLowerCase();
                    String cosmeticId = generateUniqueId(itemId);
                    ArmorCosmeticEntry entry = new ArmorCosmeticEntry();
                    entry.itemId = itemId;
                    entry.slot = CosmeticData.VirtualSlot.HEAD;
                    entry.type = "armor_cosmetic";
                    applyEntry(cosmeticId, entry);
                    i++;
                }
                migrated = true;
                System.out.println("[GreatCosmetics] armor_cosmetics.json in a very old format detected — migrating " + i + " item(ns).");
            } else if (itemsElement != null && itemsElement.isJsonObject()) {
                for (Map.Entry<String, com.google.gson.JsonElement> jsonEntry : itemsElement.getAsJsonObject().entrySet()) {
                    String key = jsonEntry.getKey();
                    com.google.gson.JsonObject obj = jsonEntry.getValue().getAsJsonObject();

                    ArmorCosmeticEntry entry;
                    try {
                        entry = GSON.fromJson(obj, ArmorCosmeticEntry.class);
                    } catch (Exception ex) {
                        System.err.println("[GreatCosmetics] armor_cosmetics.json: invalid entry '" + key + "', ignorando.");
                        com.f4xizzz.greatcosmetics.GreatCosmetics.debugLog("ArmorCosmeticsConfig: entrada '" + key + "' invalid — " + ex);
                        continue;
                    }
                    if (entry == null) continue;
                    if (entry.slot == null) entry.slot = CosmeticData.VirtualSlot.HEAD;
                    if (entry.type == null || entry.type.isBlank()) entry.type = "armor_cosmetic";
                    if (entry.parts == null) entry.parts = new java.util.ArrayList<>();
                    // Migração: armadura-cosmético agora desenha o item real através do MESMO
                    // sistema de partes (offset/rotação/escala) dos cosméticos normais, em vez de
                    // tentar ocupar de mentira o slot de armadura vanilla — ver
                    // ArmorFeatureRendererMixin. Entradas antigas (de antes dessa mudança) sempre
                    // tinham "parts": [] de propósito; sem essa parte padrão elas ficariam
                    // invisíveis pra sempre, já que o render agora exige pelo menos uma.
                    if (entry.parts.isEmpty()) entry.parts.add(new CosmeticData.CosmeticPart(CosmeticData.Anchor.HEAD));

                    resolveGeoModels(entry);

                    String cosmeticId = key;
                    // Formato ANTIGO (sem itemId): a chave do mapa ERA o id do item real — migra
                    // pra um cosmeticId novo, gerado a partir do item, guardando o itemId de verdade.
                    if (entry.itemId == null || entry.itemId.isBlank()) {
                        entry.itemId = key.toLowerCase();
                        cosmeticId = generateUniqueId(entry.itemId);
                        migrated = true;
                    }

                    applyEntry(cosmeticId.toLowerCase(), entry);
                }
            }

            System.out.println("[GreatCosmetics] armor_cosmetics.json loaded: " + armorCosmetics.size() + " item(ns) convertido(s) em cosmetic.");

            if (migrated) {
                System.out.println("[GreatCosmetics] armor_cosmetics.json migrated to the new format (with its own cosmetic id) — saving it back.");
            }
            // Só marca pra regravar se "convertedItems" existiu de verdade no arquivo (formato
            // reconhecido) — evita sobrescrever um arquivo estranho/corrompido com um mapa vazio.
            // A gravação em si NUNCA pode acontecer aqui dentro: o FileReader desse try ainda
            // está aberto nesse ponto, e abrir um FileWriter pro MESMO arquivo enquanto o reader
            // ainda não fechou é um conflito de lock clássico no Windows — foi exatamente isso
            // que corrompia/esvaziava o armor_cosmetics.json e fazia TODAS as armaduras
            // convertidas (até o diamond_helmet de exemplo) sumirem depois do primeiro load.
            shouldResave = itemsElement != null;
            com.f4xizzz.greatcosmetics.GreatCosmetics.debugLog("ArmorCosmeticsConfig: load completed — " + armorCosmetics.size() + " item(ns), migrated=" + migrated + ".");
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Error loading armor_cosmetics.json!");
            com.f4xizzz.greatcosmetics.GreatCosmetics.debugLog("ArmorCosmeticsConfig: FAILED to load armor_cosmetics.json — " + e);
            e.printStackTrace();
        }

        // Regrava só DEPOIS do try-with-resources fechar o FileReader — qualquer campo novo do
        // CosmeticData que uma entrada antiga não tinha (ex: flySpeedMultiplier) já foi
        // preenchido com o valor padrão pelo Gson no parse acima; só faltava persistir de volta.
        if (shouldResave) saveAll();
    }

    /**
     * Salva uma edição vinda do Dev Studio (SaveCosmeticPayload). Reconstrói o JSON como
     * ArmorCosmeticEntry (não CosmeticData genérico) ANTES de colocar no mapa — saveAll() só sabe
     * regravar de verdade esse wrapper; qualquer outro tipo perde tudo exceto id/realItemId ao
     * persistir (o fallback dele existe só pra não quebrar em caso de estado inesperado, não pra
     * ser o caminho normal de toda edição).
     */
    public static void saveFromJson(String cosmeticId, String jsonData, String realItemId) {
        ArmorCosmeticEntry entry = GSON.fromJson(jsonData, ArmorCosmeticEntry.class);
        if (entry == null) return;
        entry.itemId = realItemId;
        if (entry.parts == null) entry.parts = new java.util.ArrayList<>();
        if (entry.parts.isEmpty()) entry.parts.add(new CosmeticData.CosmeticPart(CosmeticData.Anchor.HEAD));
        resolveGeoModels(entry);
        applyEntry(cosmeticId.toLowerCase(), entry);
        saveAll();
    }

    /** Apaga uma armadura-cosmético de vez (botão "Apagar" do Dev Studio) — limpa o mapa principal
     *  E os dois índices reversos, senão o item real ficava "preso" como convertido pra sempre
     *  mesmo depois da entrada sumir (isConverted() continuaria true, checkAndConvertArmorInventory
     *  continuaria tentando achar um cosmético que não existe mais). */
    public static void remove(String cosmeticId) {
        CosmeticData removed = armorCosmetics.remove(cosmeticId.toLowerCase());
        if (removed == null) return;

        String realItemId = removed.realItemId;
        if (realItemId != null && !realItemId.isEmpty()) {
            boolean stillUsedElsewhere = armorCosmetics.values().stream()
                    .anyMatch(other -> realItemId.equals(other.realItemId));
            if (!stillUsedElsewhere) {
                convertedItemIds.remove(realItemId);
                itemIdToCosmeticId.remove(realItemId);
            }
        }
        saveAll();
    }

    /** Resolve part.resolvedCmd (transient) a partir de part.geoModelId — roda tanto no load()
     *  completo quanto toda vez que o Dev Studio salva uma edição (ver saveFromJson acima). Sem
     *  isso, resolvedCmd ficava zerado até o próximo /gc reload, fazendo a armadura-cosmético
     *  virar o item real "chapado" (ícone) em vez do modelo GeckoLib só de clicar em Salvar. */
    private static void resolveGeoModels(ArmorCosmeticEntry entry) {
        for (CosmeticData.CosmeticPart part : entry.parts) {
            if (part.geoModelId != null && !part.geoModelId.isBlank()) {
                part.resolvedCmd = com.f4xizzz.greatcosmetics.util.AutoCMDManager.getOrCreateCmd("geo:" + part.geoModelId);
            }
        }
    }

    private static void applyEntry(String cosmeticId, ArmorCosmeticEntry entry) {
        entry.id = cosmeticId;
        entry.realItemId = entry.itemId != null ? entry.itemId.toLowerCase() : "";
        armorCosmetics.put(cosmeticId, entry);
        if (!entry.realItemId.isEmpty()) {
            convertedItemIds.add(entry.realItemId);
            itemIdToCosmeticId.put(entry.realItemId, cosmeticId);
            warnIfItemIdUnresolved(cosmeticId, entry.realItemId);
        }
    }

    /** Item id que não resolve pra nenhum item registrado (mod não instalado, typo, item
     *  removido numa atualização) silenciosamente vira Items.AIR pro resto do mod — sem essa
     *  checagem, o cosmético fica "fantasma" (invisível, some do slot) sem NENHUM aviso no
     *  console, e o único jeito de descobrir era o admin perceber e me pedir pra investigar. */
    private static void warnIfItemIdUnresolved(String cosmeticId, String realItemId) {
        ResourceLocation id = ResourceLocation.tryParse(realItemId);
        Item resolved = id != null ? BuiltInRegistries.ITEM.get(id) : null;
        if (id == null || resolved == null || resolved == net.minecraft.world.item.Items.AIR) {
            System.err.println("[GreatCosmetics] WARNING: a armadura-cosmetic '" + cosmeticId + "' aponta pro item '"
                    + realItemId + "', que não existe (mod não instalado ou itemId com erro de digitação). "
                    + "Ela vai ficar invisível e não vai ocupar slot até o itemId ser corrigido no armor_cosmetics.json.");
        }
    }

    /** Qual cosmético (id) o item real representa, ou null se ele não foi convertido. */
    public static String getCosmeticIdForItem(Item item) {
        if (item == null) return null;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? null : itemIdToCosmeticId.get(id.toString());
    }

    /** Gera um id de cosmético único a partir do id do item (ex: "minecraft:diamond_helmet" ->
     *  "diamond_helmet", ou "diamond_helmet_2" se já existir). */
    private static String generateUniqueId(String itemId) {
        String base = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        base = base.toLowerCase();
        if (!armorCosmetics.containsKey(base)) return base;
        int i = 2;
        while (armorCosmetics.containsKey(base + "_" + i)) i++;
        return base + "_" + i;
    }

    public static void saveAll() {
        ConfigData toSave = new ConfigData();
        for (Map.Entry<String, CosmeticData> entry : armorCosmetics.entrySet()) {
            ArmorCosmeticEntry out;
            if (entry.getValue() instanceof ArmorCosmeticEntry ace) {
                out = ace;
            } else {
                out = new ArmorCosmeticEntry();
                // caso raro: alguém colocou um CosmeticData puro no mapa (ex: criado via GUI) —
                // copia os campos relevantes pro wrapper de serialização.
                out.id = entry.getValue().id;
                out.realItemId = entry.getValue().realItemId;
            }
            out.itemId = out.realItemId;
            toSave.convertedItems.put(entry.getKey(), out);
        }
        save(toSave);
    }

    private static void save(ConfigData data) {
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(data, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean isConverted(Item item) {
        if (item == null || convertedItemIds.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id != null && convertedItemIds.contains(id.toString());
    }

    /**
     * Retorna o CosmeticData da armadura convertida de id {@code cosmeticId} (o id PRÓPRIO dela,
     * não o id do item). Preenche DisplayName de fallback sob demanda. Retorna null se esse id
     * não estiver registrado.
     *
     * NÃO mexe em "render" — esse campo não é mais usado pra decidir nada na renderização (ver
     * ArmorFeatureRendererMixin, que desenha tudo num passe único fixo). Escrever nele aqui fazia
     * o valor "vazar" de volta pro armor_cosmetics.json toda vez que saveAll() rodava, porque esse
     * método sempre mexe no MESMO objeto que está no mapa (não uma cópia).
     */
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

    /** Todas as armaduras convertidas, prontas pro grid de Acessórios. */
    public static java.util.List<CosmeticData> getAllSynthetic() {
        java.util.List<CosmeticData> list = new java.util.ArrayList<>();
        for (String id : armorCosmetics.keySet()) {
            CosmeticData data = getSyntheticCosmetic(id);
            if (data != null) list.add(data);
        }
        return list;
    }
}
