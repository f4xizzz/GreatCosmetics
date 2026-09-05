package com.f4xizzz.greatcosmetics.util;

import com.f4xizzz.greatcosmetics.GreatCosmetics;
import com.f4xizzz.greatcosmetics.config.CosmeticData;
import com.f4xizzz.greatcosmetics.database.DatabaseManager;
import com.f4xizzz.greatcosmetics.network.ShowBackpackSelectorPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

/** Mochila-cosmético: um único {@link GenericContainerScreenHandler} de {@code backpackRows}
 *  fileiras, salvo no banco (base64 de NBT) a cada mudança. O sistema de PÁGINAS foi removido —
 *  não funcionava bem (setas que não passavam de página, botão que sumia com 2+ mochilas, handler
 *  "fantasma"). Uma mochila = um baú, ponto. */
public class BackpackManager {

    // Instância de SimpleInventory da mochila aberta AGORA por jogador (pra saber quando fechou e
    // salvar o estado final — o listener já salva a cada mudança, mas o save no fechamento é a
    // rede de segurança).
    private static final Map<UUID, SimpleInventory> openInventories = new HashMap<>();
    // Associa qual jogador está com qual mochila aberta (UUID -> cosmetic_id)
    private static final Map<UUID, String> openSessions = new HashMap<>();

    public static Text parseMiniMessage(String input, RegistryWrapper.WrapperLookup registries) {
        if (input == null || input.isEmpty()) return Text.empty();

        String miniMessageInput = input.replace("&", "§")
                .replace("§0", "<black>").replace("§1", "<dark_blue>").replace("§2", "<dark_green>")
                .replace("§3", "<dark_aqua>").replace("§4", "<dark_red>").replace("§5", "<dark_purple>")
                .replace("§6", "<gold>").replace("§7", "<gray>").replace("§8", "<dark_gray>")
                .replace("§9", "<blue>").replace("§a", "<green>").replace("§b", "<aqua>")
                .replace("§c", "<red>").replace("§d", "<light_purple>").replace("§e", "<yellow>")
                .replace("§f", "<white>").replace("§l", "<bold>").replace("§m", "<strikethrough>")
                .replace("§n", "<underlined>").replace("§o", "<italic>").replace("§r", "<reset>");

        var adventureComponent = MiniMessage.miniMessage().deserialize(miniMessageInput);
        String json = GsonComponentSerializer.gson().serialize(adventureComponent);
        return Text.Serialization.fromJson(json, registries);
    }

    // ==========================================
    // LÓGICA DE ABERTURA (Recebida do Cliente)
    // ==========================================
    public static void handleOpenRequest(ServerPlayerEntity player) {
        // Se uma mochila rastreada já está aberta e o jogador aperta a tecla de novo, fecha ela
        // ANTES de decidir o que abrir agora — sem isso, o caminho do seletor (que troca a tela do
        // CLIENT direto, sem avisar o servidor) deixava o player.currentScreenHandler preso no
        // GenericContainerScreenHandler antigo (handler "fantasma"). Fechando de verdade primeiro,
        // o CloseScreenS2CPacket vai ANTES do ShowBackpackSelectorPayload (mesma conexão, mesma
        // ordem), e os dois lados repartem do zero.
        if (openSessions.containsKey(player.getUuid()) && player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            GreatCosmetics.debugLog("BackpackManager: fechando mochila '" + openSessions.get(player.getUuid()) + "' rastreada antes de reabrir/mostrar seletor pra " + player.getName().getString());
            player.closeHandledScreen();
        }

        List<String> equipped = DatabaseManager.getPlayerEquippedCosmetics(player.getUuid());
        List<String> backpackIds = new ArrayList<>();

        // Filtra apenas os cosméticos equipados que são do tipo mochila
        for (String id : equipped) {
            CosmeticData data = GreatCosmetics.getCosmeticById(id);
            if (data != null && data.isBackpack) {
                backpackIds.add(id);
            }
        }

        if (backpackIds.isEmpty()) return; // Não tem mochila equipada

        if (backpackIds.size() == 1) {
            // Se tem só uma, abre ela direto!
            openSpecificBackpack(player, backpackIds.get(0));
        } else {
            // Se tem várias, manda o pacote pro cliente abrir a tela do Mini-GUI
            ServerPlayNetworking.send(player, new ShowBackpackSelectorPayload(backpackIds));
        }
    }

    public static void openSpecificBackpack(ServerPlayerEntity player, String cosmeticId) {
        CosmeticData data = GreatCosmetics.getCosmeticById(cosmeticId);
        if (data == null || !data.isBackpack) return;

        // OpenSpecificBackpackPayload chama esse método direto com o ID que o CLIENT mandou —
        // handleOpenRequest() (abertura via keybind) já filtra por "está equipada", mas esse outro
        // caminho não passava por nenhum filtro, então qualquer player conseguia abrir (e usar como
        // armazenamento extra) qualquer mochila-cosmético do catálogo só sabendo o ID.
        if (!DatabaseManager.getPlayerEquippedCosmetics(player.getUuid()).contains(cosmeticId)) return;

        UUID playerUuid = player.getUuid();
        int rows = data.backpackRows;
        int size = rows * 9;
        RegistryWrapper.WrapperLookup registries = player.server.getRegistryManager();

        GreatCosmetics.debugLog("BackpackManager: openSpecificBackpack(" + cosmeticId + ") player=" + player.getName().getString());

        boolean isReopen = cosmeticId.equals(openSessions.get(playerUuid));
        if (!isReopen && data.sounds != null && data.sounds.backpackSound != null && !data.sounds.backpackSound.isEmpty()) {
            GreatCosmetics.playCosmeticSound(player, data.sounds.backpackSound, (float) data.sounds.backpackVolume, (float) data.sounds.backpackPitch);
        }

        SimpleInventory inventory = new SimpleInventory(size);
        loadInventoryInto(playerUuid, cosmeticId, inventory, registries);
        inventory.addListener(inv -> saveInventory(playerUuid, cosmeticId, (SimpleInventory) inv, registries));

        openInventories.put(playerUuid, inventory);
        Text titleText = parseMiniMessage(data.backpackDisplayName, registries);

        SimpleNamedScreenHandlerFactory factory = new SimpleNamedScreenHandlerFactory((syncId, playerInv, p) -> {
            ScreenHandlerType<?> type = switch (rows) {
                case 1 -> ScreenHandlerType.GENERIC_9X1;
                case 2 -> ScreenHandlerType.GENERIC_9X2;
                case 4 -> ScreenHandlerType.GENERIC_9X4;
                case 5 -> ScreenHandlerType.GENERIC_9X5;
                case 6 -> ScreenHandlerType.GENERIC_9X6;
                default -> ScreenHandlerType.GENERIC_9X3;
            };
            int safeRows = (rows >= 1 && rows <= 6) ? rows : 3;
            return new GenericContainerScreenHandler(type, syncId, playerInv, inventory, safeRows);
        }, titleText);

        player.openHandledScreen(factory);
        openSessions.put(playerUuid, cosmeticId);
    }

    /** Chamado no DISCONNECT — os itens em si não se perdem (o listener já salva a CADA mudança),
     *  aqui só descarta o estado em memória. */
    public static void handlePlayerDisconnect(UUID playerUuid) {
        openSessions.remove(playerUuid);
        openInventories.remove(playerUuid);
    }

    // ==========================================
    // LÓGICA DE FECHAMENTO E SALVAMENTO
    // ==========================================
    public static void checkClosedBackpacks(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID playerUuid = player.getUuid();
            if (openSessions.containsKey(playerUuid)) {
                if (player.currentScreenHandler == player.playerScreenHandler) {
                    String cosmeticId = openSessions.remove(playerUuid);
                    SimpleInventory inv = openInventories.remove(playerUuid);
                    if (inv != null) {
                        saveInventory(playerUuid, cosmeticId, inv, server.getRegistryManager());
                    }
                }
            }
        }
    }

    // ==========================================
    // SISTEMA DE CONVERSÃO PARA DATABASE (Base64)
    // ==========================================
    private static void loadInventoryInto(UUID playerUuid, String cosmeticId, SimpleInventory inventory, RegistryWrapper.WrapperLookup registries) {
        int size = inventory.size();
        DefaultedList<ItemStack> stacks = DefaultedList.ofSize(size, ItemStack.EMPTY);

        String base64Data = DatabaseManager.getBackpackData(playerUuid, cosmeticId);
        if (base64Data != null && !base64Data.isEmpty()) {
            try {
                byte[] bytes = Base64.getDecoder().decode(base64Data);
                ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                NbtCompound nbt = NbtIo.readCompressed(bais, NbtSizeTracker.ofUnlimitedBytes());
                Inventories.readNbt(nbt, stacks, registries);
            } catch (Exception e) {
                System.err.println("[GreatCosmetics] Error decoding backpack from database: " + e.getMessage());
            }
        }

        for (int i = 0; i < size; i++) inventory.setStack(i, stacks.get(i));
    }

    private static void saveInventory(UUID playerUuid, String cosmeticId, SimpleInventory inventory, RegistryWrapper.WrapperLookup registries) {
        try {
            NbtCompound nbt = new NbtCompound();
            DefaultedList<ItemStack> stacks = DefaultedList.ofSize(inventory.size(), ItemStack.EMPTY);
            for (int i = 0; i < inventory.size(); i++) stacks.set(i, inventory.getStack(i));
            Inventories.writeNbt(nbt, stacks, registries);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            NbtIo.writeCompressed(nbt, baos);
            String base64Data = Base64.getEncoder().encodeToString(baos.toByteArray());

            DatabaseManager.saveBackpackData(playerUuid, cosmeticId, base64Data);
        } catch (Exception e) {
            System.err.println("[GreatCosmetics] Error encoding backpack to database: " + e.getMessage());
        }
    }
}
