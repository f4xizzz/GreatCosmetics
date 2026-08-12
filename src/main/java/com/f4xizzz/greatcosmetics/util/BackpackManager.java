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

public class BackpackManager {

    // Instância de SimpleInventory da mochila aberta AGORA por jogador — a MESMA instância é
    // reaproveitada entre páginas da MESMA mochila (ver handleSwitchPage/openSpecificBackpackPage):
    // trocar de página só troca o CONTEÚDO dela, nunca fecha/reabre a tela. Fechar+reabrir
    // (player.openHandledScreen) a cada seta clicada era a causa de vários problemas juntos: o
    // mouse recentralizando (vanilla recentraliza o cursor sempre que a tela fecha, mesmo que uma
    // nova abra logo em seguida no mesmo tick), o overlay de página (setas/texto) só sendo
    // registrado nas telas que abrem via AFTER_INIT — se esse reabrir falhasse/atrasasse por
    // qualquer motivo (lag, ordem de pacote), o overlay simplesmente não aparecia — e cliques
    // perdidos por caírem bem na janela de troca de tela. Reaproveitando a MESMA tela/handler já
    // aberto, o vanilla sincroniza o conteúdo novo sozinho (ScreenHandler#sendContentUpdates, o
    // mesmo mecanismo de qualquer baú normal), sem precisar fechar nada.
    private static final Map<UUID, SimpleInventory> openInventories = new HashMap<>();
    // Associa qual jogador está com qual mochila aberta (UUID -> cosmetic_id)
    private static final Map<UUID, String> openSessions = new HashMap<>();
    // Página ATUAL de cada jogador com mochila aberta (UUID -> índice da página, base 0).
    private static final Map<UUID, Integer> openPages = new HashMap<>();

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
        openSpecificBackpackPage(player, cosmeticId, 0);
    }

    /** Abre a mochila-cosmético {@code cosmeticId} na página {@code page} (base 0), OU — se essa
     *  MESMA mochila já está aberta pro jogador agora — só troca o conteúdo pra essa página, sem
     *  fechar/reabrir a tela (ver comentário em {@code openInventories} pro motivo). */
    public static void openSpecificBackpackPage(ServerPlayerEntity player, String cosmeticId, int page) {
        CosmeticData data = GreatCosmetics.getCosmeticById(cosmeticId);
        if (data == null || !data.isBackpack) return;

        // OpenSpecificBackpackPayload chama esse método direto com o ID que o CLIENT mandou —
        // handleOpenRequest() (abertura via keybind) já filtra por "está equipada", mas esse outro
        // caminho não passava por nenhum filtro, então qualquer player conseguia abrir (e usar
        // como armazenamento extra) qualquer mochila-cosmético do catálogo só sabendo o ID, mesmo
        // sem nunca ter desbloqueado/equipado ela — o catálogo inteiro (incluindo trancadas) já é
        // sincronizado pro client só pra listagem visual (ver SyncCosmeticsPayload), então os IDs
        // não são segredo nenhum.
        if (!DatabaseManager.getPlayerEquippedCosmetics(player.getUuid()).contains(cosmeticId)) return;

        UUID playerUuid = player.getUuid();
        int totalPages = Math.max(1, data.backpackPages);
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int rows = data.backpackRows;
        int size = rows * 9;
        RegistryWrapper.WrapperLookup registries = player.server.getRegistryManager();

        boolean sameBackpackAlreadyOpen = cosmeticId.equals(openSessions.get(playerUuid))
                && player.currentScreenHandler instanceof GenericContainerScreenHandler
                && openInventories.containsKey(playerUuid)
                && openInventories.get(playerUuid).size() == size;

        if (sameBackpackAlreadyOpen) {
            int oldPage = openPages.getOrDefault(playerUuid, 0);
            if (oldPage != safePage) {
                SimpleInventory inventory = openInventories.get(playerUuid);
                saveInventory(playerUuid, cosmeticId, oldPage, inventory, registries);
                // Atualiza a página ANTES de carregar o conteúdo novo — o listener de auto-save
                // (ver mais abaixo) lê openPages a cada setStack() pra saber ONDE salvar, então
                // precisa já enxergar a página nova antes do primeiro setStack do loop de carga.
                openPages.put(playerUuid, safePage);
                loadInventoryInto(playerUuid, cosmeticId, safePage, inventory, registries);
            }
            ServerPlayNetworking.send(player, new com.f4xizzz.greatcosmetics.network.BackpackPageInfoPayload(cosmeticId, safePage, totalPages));
            return;
        }

        // Abertura de verdade — primeira vez, ou uma mochila DIFERENTE da que já estava aberta
        // (nesse caso sim precisa fechar a antiga e abrir uma tela nova, possivelmente até de
        // tamanho diferente).
        boolean isReopen = cosmeticId.equals(openSessions.get(playerUuid));
        if (!isReopen && data.sounds != null && data.sounds.backpackSound != null && !data.sounds.backpackSound.isEmpty()) {
            GreatCosmetics.playCosmeticSound(player, data.sounds.backpackSound, (float) data.sounds.backpackVolume, (float) data.sounds.backpackPitch);
        }

        SimpleInventory inventory = new SimpleInventory(size);
        openPages.put(playerUuid, safePage);
        loadInventoryInto(playerUuid, cosmeticId, safePage, inventory, registries);

        // Escuta mudanças pra salvar automaticamente — lê a PÁGINA ATUAL de openPages a cada vez
        // que dispara (não uma capturada fixa no momento da criação), porque trocar de página
        // reaproveita essa MESMA instância/listener pra páginas diferentes ao longo do tempo.
        inventory.addListener(inv -> {
            int currentPage = openPages.getOrDefault(playerUuid, 0);
            saveInventory(playerUuid, cosmeticId, currentPage, (SimpleInventory) inv, registries);
        });

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

        // Manda ANTES de openHandledScreen — não depois! ClientBackpackState#pendingCosmeticId só
        // funciona sem gambiarra de sincronização porque o client SEMPRE recebe isso e atualiza o
        // estado ANTES do pacote vanilla que de fato abre a tela do baú (mesma conexão, ordem de
        // chegada = ordem de envio) — invertido, o ScreenEvents.AFTER_INIT do client rodaria com o
        // cosmeticId/página ainda VELHOS (ou nenhum, na primeira abertura).
        ServerPlayNetworking.send(player, new com.f4xizzz.greatcosmetics.network.BackpackPageInfoPayload(cosmeticId, safePage, totalPages));

        player.openHandledScreen(factory);
        openSessions.put(playerUuid, cosmeticId);
    }

    /** Clique numa seta de página (ver SwitchBackpackPagePayload) — delega pra
     *  openSpecificBackpackPage, que já detecta sozinho que é a MESMA mochila aberta e troca só o
     *  conteúdo, sem fechar a tela (ver comentário em openInventories). */
    public static void handleSwitchPage(ServerPlayerEntity player, String cosmeticId, int newPage) {
        if (!cosmeticId.equals(openSessions.get(player.getUuid()))) return; // só troca a mochila que já está aberta
        openSpecificBackpackPage(player, cosmeticId, newPage);
    }

    /** Chave usada na COLUNA cosmetic_id da tabela player_backpacks — não precisa ser o id de
     *  verdade, só precisa ser única por (jogador, mochila, página). page 0 mantém a MESMA chave
     *  de sempre (sem sufixo) — mochilas que já existiam antes da paginação continuam lendo os
     *  itens salvos sem precisar de nenhuma migração de dados. */
    private static String backpackDbKey(String cosmeticId, int page) {
        return page == 0 ? cosmeticId : cosmeticId + "#p" + page;
    }

    /** Chamado no DISCONNECT — se o jogador cair/crashar com a mochila aberta,
     *  checkClosedBackpacks() nunca detecta o fechamento (currentScreenHandler nunca volta a ser
     *  playerScreenHandler pra alguém que já desconectou), e a entrada em openSessions/
     *  openInventories ficava presa em memória pro resto da vida do servidor. Os itens em si não
     *  se perdem (o listener em openSpecificBackpackPage já salva no banco a CADA mudança, não só
     *  no fechamento), então aqui só precisa descartar o estado em memória. */
    public static void handlePlayerDisconnect(UUID playerUuid) {
        openSessions.remove(playerUuid);
        openPages.remove(playerUuid);
        openInventories.remove(playerUuid);
    }

    // ==========================================
    // LÓGICA DE FECHAMENTO E SALVAMENTO
    // ==========================================
    public static void checkClosedBackpacks(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID playerUuid = player.getUuid();
            if (openSessions.containsKey(playerUuid)) {
                // Se a tela atual do jogador voltou a ser o inventário normal, ele fechou a mochila
                // de verdade — trocar de página não passa mais por aqui, porque não fecha/reabre a
                // tela (ver openSpecificBackpackPage), então currentScreenHandler nunca some só por
                // causa de uma troca de página.
                if (player.currentScreenHandler == player.playerScreenHandler) {
                    String cosmeticId = openSessions.remove(playerUuid);
                    int page = openPages.getOrDefault(playerUuid, 0);
                    openPages.remove(playerUuid);

                    SimpleInventory inv = openInventories.remove(playerUuid);
                    if (inv != null) {
                        saveInventory(playerUuid, cosmeticId, page, inv, server.getRegistryManager());
                    }
                }
            }
        }
    }

    // ==========================================
    // SISTEMA DE CONVERSÃO PARA DATABASE (Base64)
    // ==========================================
    /** Carrega os itens salvos da página {@code page} DENTRO da instância de inventory já
     *  existente (não cria uma nova) — usado tanto na abertura inicial quanto na troca de página,
     *  que precisa reaproveitar a MESMA instância (ver openInventories). */
    private static void loadInventoryInto(UUID playerUuid, String cosmeticId, int page, SimpleInventory inventory, RegistryWrapper.WrapperLookup registries) {
        int size = inventory.size();
        DefaultedList<ItemStack> stacks = DefaultedList.ofSize(size, ItemStack.EMPTY);

        String base64Data = DatabaseManager.getBackpackData(playerUuid, backpackDbKey(cosmeticId, page));
        if (base64Data != null && !base64Data.isEmpty()) {
            try {
                byte[] bytes = Base64.getDecoder().decode(base64Data);
                ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                NbtCompound nbt = NbtIo.readCompressed(bais, NbtSizeTracker.ofUnlimitedBytes());
                Inventories.readNbt(nbt, stacks, registries);
            } catch (Exception e) {
                System.err.println("[SASCosmetics] Erro ao decodificar mochila da database: " + e.getMessage());
            }
        }

        for (int i = 0; i < size; i++) inventory.setStack(i, stacks.get(i));
    }

    private static void saveInventory(UUID playerUuid, String cosmeticId, int page, SimpleInventory inventory, RegistryWrapper.WrapperLookup registries) {
        try {
            NbtCompound nbt = new NbtCompound();
            DefaultedList<ItemStack> stacks = DefaultedList.ofSize(inventory.size(), ItemStack.EMPTY);
            for (int i = 0; i < inventory.size(); i++) stacks.set(i, inventory.getStack(i));
            Inventories.writeNbt(nbt, stacks, registries);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            NbtIo.writeCompressed(nbt, baos);
            String base64Data = Base64.getEncoder().encodeToString(baos.toByteArray());

            DatabaseManager.saveBackpackData(playerUuid, backpackDbKey(cosmeticId, page), base64Data);
        } catch (Exception e) {
            System.err.println("[SASCosmetics] Erro ao encodar mochila para a database: " + e.getMessage());
        }
    }
}
