package com.f4xizzz.greatcosmetics.util;

import com.f4xizzz.greatcosmetics.config.CosmeticData;
import com.f4xizzz.greatcosmetics.config.LegacyCosmeticMigrationConfig;
import com.f4xizzz.greatcosmetics.database.DatabaseManager;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

/**
 * Migra automaticamente cosméticos do sistema ANTIGO (carved_pumpkin com custom_model_data
 * específico, de antes do mod ter seu próprio sistema de acessórios) pro cosmético NOVO
 * equivalente: remove o item velho (equipado ou no inventário) e dá/equipa o acessório novo no
 * lugar. O mapeamento fica em legacy_cosmetic_migration.json (ver LegacyCosmeticMigrationConfig).
 * Roda automaticamente no JOIN e periodicamente (mesmo tick de 5s do
 * GreatCosmetics#checkAndConvertArmorInventory) — é idempotente (o item velho já não existe mais
 * depois da primeira vez), então é seguro rodar toda vez sem custo real pra quem já foi migrado.
 */
public class LegacyCosmeticMigrator {

    // Evita spammar o console a cada scan de 5s (ver GreatCosmetics#checkAndConvertArmorInventory)
    // pro MESMO id que já sabemos que está faltando — loga uma vez só por id ausente.
    private static final java.util.Set<String> warnedMissingIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void migratePlayer(ServerPlayer player) {
        if (LegacyCosmeticMigrationConfig.oldCmdToNewCosmeticId.isEmpty()) return;

        Inventory inv = player.getInventory();
        boolean migratedAny = false;

        migratedAny |= scanAndReplace(player, inv.items);
        migratedAny |= scanAndReplace(player, inv.armor);
        migratedAny |= scanAndReplace(player, inv.offhand);

        if (migratedAny) {
            DatabaseManager.broadcastPlayerCosmetics(player);
        }
    }

    private static boolean scanAndReplace(ServerPlayer player, NonNullList<ItemStack> slots) {
        boolean migratedAny = false;

        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i);
            if (stack.isEmpty() || !stack.is(Items.CARVED_PUMPKIN)) continue;

            CustomModelData cmdComponent = stack.get(DataComponents.CUSTOM_MODEL_DATA);
            if (cmdComponent == null) continue;

            String newCosmeticId = LegacyCosmeticMigrationConfig.oldCmdToNewCosmeticId.get(cmdComponent.value());
            if (newCosmeticId == null) continue;

            CosmeticData data = com.f4xizzz.greatcosmetics.config.CosmeticsConfig.getCosmeticById(newCosmeticId);
            if (data == null) {
                // Cosmético novo não configurado ainda — não perde o item velho à toa. Isso ficava
                // 100% silencioso antes: o legacy_cosmetic_migration.json podia estar perfeitamente
                // válido e o mapeamento carregar sem erro nenhum, mas se o id do lado direito (ex:
                // "goomyhat_shiny") não existir em NENHUM cosmético configurado ainda no Dev Studio/
                // cosmeticsconfig.conf, a migração pula esse item pra sempre sem avisar ninguém —
                // parecia "não estar funcionando" quando na verdade só faltava cadastrar o cosmético.
                if (warnedMissingIds.add(newCosmeticId)) {
                    com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.LOGGER.warn("[GreatCosmetics] legacy_cosmetic_migration.json points to id '"
                            + newCosmeticId + "', but no cosmetic with that id exists in the current catalog — "
                            + "this old item will NOT be migrated/removed until that cosmetic is created in the Dev Studio "
                            + "(or the id in the JSON is fixed to one that already exists).");
                }
                continue;
            }

            slots.set(i, ItemStack.EMPTY);

            DatabaseManager.unlockCosmetic(player.getUUID(), newCosmeticId);
            DatabaseManager.equipCosmetic(player.getUUID(), newCosmeticId, data.slot.name(), data.type);

            player.displayClientMessage(com.f4xizzz.greatcosmetics.config.LangConfig.chat(
                    "messages.cosmetic.migrated",
                    player.getServer().overworld().registryAccess(),
                    "name", data.getChatSafeDisplayName()), false);

            migratedAny = true;
        }

        return migratedAny;
    }
}
