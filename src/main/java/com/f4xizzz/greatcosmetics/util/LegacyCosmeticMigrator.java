package com.f4xizzz.greatcosmetics.util;

import com.f4xizzz.greatcosmetics.GreatCosmetics;
import com.f4xizzz.greatcosmetics.config.CosmeticData;
import com.f4xizzz.greatcosmetics.config.LegacyCosmeticMigrationConfig;
import com.f4xizzz.greatcosmetics.database.DatabaseManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;

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

    public static void migratePlayer(ServerPlayerEntity player) {
        if (LegacyCosmeticMigrationConfig.oldCmdToNewCosmeticId.isEmpty()) return;

        PlayerInventory inv = player.getInventory();
        boolean migratedAny = false;

        migratedAny |= scanAndReplace(player, inv.main);
        migratedAny |= scanAndReplace(player, inv.armor);
        migratedAny |= scanAndReplace(player, inv.offHand);

        if (migratedAny) {
            DatabaseManager.broadcastPlayerCosmetics(player);
        }
    }

    private static boolean scanAndReplace(ServerPlayerEntity player, DefaultedList<ItemStack> slots) {
        boolean migratedAny = false;

        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i);
            if (stack.isEmpty() || !stack.isOf(Items.CARVED_PUMPKIN)) continue;

            CustomModelDataComponent cmdComponent = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
            if (cmdComponent == null) continue;

            String newCosmeticId = LegacyCosmeticMigrationConfig.oldCmdToNewCosmeticId.get(cmdComponent.value());
            if (newCosmeticId == null) continue;

            CosmeticData data = GreatCosmetics.getCosmeticById(newCosmeticId);
            if (data == null) {
                // Cosmético novo não configurado ainda — não perde o item velho à toa. Isso ficava
                // 100% silencioso antes: o legacy_cosmetic_migration.json podia estar perfeitamente
                // válido e o mapeamento carregar sem erro nenhum, mas se o id do lado direito (ex:
                // "goomyhat_shiny") não existir em NENHUM cosmético configurado ainda no Dev Studio/
                // cosmeticsconfig.conf, a migração pula esse item pra sempre sem avisar ninguém —
                // parecia "não estar funcionando" quando na verdade só faltava cadastrar o cosmético.
                if (warnedMissingIds.add(newCosmeticId)) {
                    GreatCosmetics.LOGGER.warn("[SASCosmetics] legacy_cosmetic_migration.json aponta pro id '"
                            + newCosmeticId + "', mas nenhum cosmético com esse id existe no catálogo atual — "
                            + "esse item antigo NÃO será migrado/removido até esse cosmético ser criado no Dev Studio "
                            + "(ou o id no JSON ser corrigido pra um que já existe).");
                }
                continue;
            }

            slots.set(i, ItemStack.EMPTY);

            DatabaseManager.unlockCosmetic(player.getUuid(), newCosmeticId);
            DatabaseManager.equipCosmetic(player.getUuid(), newCosmeticId, data.slot.name(), data.type);

            player.sendMessage(BackpackManager.parseMiniMessage(
                    "<green>[Cosmetics] Seu cosmético antigo foi migrado automaticamente para: <white>" + data.getChatSafeDisplayName() + "<green>!",
                    player.getServer().getOverworld().getRegistryManager()), false);

            migratedAny = true;
        }

        return migratedAny;
    }
}
