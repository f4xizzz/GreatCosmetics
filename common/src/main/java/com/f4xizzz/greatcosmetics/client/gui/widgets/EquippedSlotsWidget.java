package com.f4xizzz.greatcosmetics.client.gui.widgets;

import com.f4xizzz.greatcosmetics.client.ClientCosmeticCache;
import com.f4xizzz.greatcosmetics.client.ClientMainConfigCache;
import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import com.f4xizzz.greatcosmetics.config.CosmeticData;
import com.f4xizzz.greatcosmetics.config.MainConfig;
import com.f4xizzz.greatcosmetics.network.EquipCosmeticPayload;
import com.f4xizzz.greatcosmetics.network.ToggleVisibilityPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import java.util.*;

public class EquippedSlotsWidget {

    // Ícone de cada um dos 9 slots fixos (ver CosmeticData.VirtualSlot) — mostrado nos boxes vazios
    // da gaveta no lugar do nome abreviado (ex: "HEA", "CHE"). Slot custom (nome que não bate com
    // nenhum dos 9) continua caindo no fallback de texto abaixo.
    private static final Map<String, ResourceLocation> SLOT_ICONS = Map.of(
            "HEAD", ResourceLocation.fromNamespaceAndPath("sascosmetics", "textures/gui/slots/head.png"),
            "FACE", ResourceLocation.fromNamespaceAndPath("sascosmetics", "textures/gui/slots/face.png"),
            "NECK", ResourceLocation.fromNamespaceAndPath("sascosmetics", "textures/gui/slots/neck.png"),
            "CHEST", ResourceLocation.fromNamespaceAndPath("sascosmetics", "textures/gui/slots/chest.png"),
            "BACK", ResourceLocation.fromNamespaceAndPath("sascosmetics", "textures/gui/slots/back.png"),
            "WAIST", ResourceLocation.fromNamespaceAndPath("sascosmetics", "textures/gui/slots/waist.png"),
            "LEGS", ResourceLocation.fromNamespaceAndPath("sascosmetics", "textures/gui/slots/legs.png"),
            "FEET", ResourceLocation.fromNamespaceAndPath("sascosmetics", "textures/gui/slots/feet.png"),
            "HAND", ResourceLocation.fromNamespaceAndPath("sascosmetics", "textures/gui/slots/hand.png")
    );

    private final Wardrobe3DScreen parent;

    // Estados de Animação e Interação
    private boolean isOpen = false;
    private boolean checkedInitialState = false; // <-- NOVO: Controla a primeira abertura
    private float animProgress = 0.0f;
    private float scrollX = 0f;
    private float maxScrollX = 0f;
    private boolean isDraggingDrawer = false;

    // Configurações Minimalistas
    private final int boxSize = 26;
    private final int boxSpacing = 4;
    private final int padding = 8;

    public EquippedSlotsWidget(Wardrobe3DScreen parent) {
        this.parent = parent;
    }

    // Lê do ClientPermissionCache (calculado no servidor) em vez de hasPermissionLevel()/
    // Permissions.check() direto no client — ver SyncDevPermissionsPayload.
    private boolean hasDevPermission(net.minecraft.client.player.LocalPlayer player) {
        return com.f4xizzz.greatcosmetics.client.ClientPermissionCache.isOperator
                || com.f4xizzz.greatcosmetics.client.ClientPermissionCache.hasGcPermDevmode;
    }

    private List<String> getActiveSlots(Set<String> equippedIds) {
        List<String> order = List.of("HEAD", "FACE", "NECK", "CHEST", "BACK", "WAIST", "LEGS", "FEET", "HAND");
        List<String> allSlots = new ArrayList<>(ClientMainConfigCache.config.slots.keySet());
        allSlots.sort((a, b) -> {
            int ia = order.indexOf(a); int ib = order.indexOf(b);
            if (ia != -1 && ib != -1) return Integer.compare(ia, ib);
            if (ia != -1) return -1;
            if (ib != -1) return 1;
            return a.compareTo(b);
        });

        List<String> active = new ArrayList<>();
        for (String s : allSlots) {
            MainConfig.SlotLimit sl = ClientMainConfigCache.config.slots.get(s);
            if (sl == null) continue;
            int eqCount = 0;
            if (equippedIds != null) {
                for (String id : equippedIds) {
                    CosmeticData d = com.f4xizzz.greatcosmetics.config.CosmeticsConfig.getCosmeticById(id);
                    if (d != null && d.slot != null && d.slot.name().equals(s)) eqCount++;
                }
            }
            if (ClientCosmeticCache.isDevModeActive || Math.max(sl.defaultLimit, eqCount) > 0) active.add(s);
        }
        return active;
    }

    private int getMaxColumns(Set<String> equippedIds, List<String> activeSlots) {
        int maxCols = 1;
        for (String slotName : activeSlots) {
            MainConfig.SlotLimit sl = ClientMainConfigCache.config.slots.get(slotName);
            if (sl == null) continue;
            int eqCount = 0;
            if (equippedIds != null) {
                for (String id : equippedIds) {
                    CosmeticData d = com.f4xizzz.greatcosmetics.config.CosmeticsConfig.getCosmeticById(id);
                    if (d != null && d.slot != null && d.slot.name().equals(slotName)) eqCount++;
                }
            }
            int boxes = ClientCosmeticCache.isDevModeActive ? eqCount + 1 : Math.max(sl.defaultLimit, eqCount);
            if (boxes > maxCols) maxCols = boxes;
        }
        return maxCols;
    }

    public void render(GuiGraphics c, int vMouseX, int vMouseY, int vWidth, int vHeight, boolean isDevTabActive) {
        if (isDevTabActive || Minecraft.getInstance().player == null) {
            animProgress = 0.0f;
            isOpen = false;
            return;
        }

        // Abre a gaveta automaticamente na primeira vez pra QUALQUER player — o dev mode em si
        // (bypass de limite de slot) continua exigindo permissão, só a gaveta abrir mostrando
        // os slots é liberado geral.
        if (!checkedInitialState) {
            isOpen = true;
            if (hasDevPermission(Minecraft.getInstance().player)) {
                ClientCosmeticCache.isDevModeActive = true;
            }
            checkedInitialState = true;
        }

        UUID uuid = Minecraft.getInstance().player.getUUID();
        Set<String> equippedIds = ClientCosmeticCache.getEquipped(uuid);
        ClientCosmeticCache.PlayerSettings settings = ClientCosmeticCache.getSettings(uuid);
        Font textRenderer = parent.getTextRenderer();

        List<String> activeSlots = getActiveSlots(equippedIds);
        if (activeSlots.isEmpty()) return;

        int maxColumns = getMaxColumns(equippedIds, activeSlots);
        int visibleColumns = Math.min(3, maxColumns);
        int dynamicDrawerWidth = (visibleColumns * boxSize) + (Math.max(0, visibleColumns - 1) * boxSpacing) + (padding * 2);

        int rowHeight = boxSize + boxSpacing;
        int drawerHeight = (activeSlots.size() * rowHeight) - boxSpacing + (padding * 2);
        int drawerY = Math.max(10, (vHeight - drawerHeight) / 2);

        float animSpeed = 0.2f;
        animProgress += ((isOpen ? 1.0f : 0.0f) - animProgress) * animSpeed;

        int currentX = vWidth - (int)(animProgress * dynamicDrawerWidth);

        if (animProgress < 0.01f && !isOpen) {
            drawHandle(c, vMouseX, vMouseY, currentX, drawerY, drawerHeight);
            return;
        }

        drawHandle(c, vMouseX, vMouseY, currentX, drawerY, drawerHeight);

        c.fill(currentX, drawerY, vWidth, drawerY + drawerHeight, 0xDD0A0A0A);
        c.fill(currentX, drawerY, currentX + 1, drawerY + drawerHeight, 0x33FFFFFF);

        parent.enablePerfectScissor(c, currentX + 1, drawerY, dynamicDrawerWidth - 1, drawerHeight);

        for (int i = 0; i < activeSlots.size(); i++) {
            String slotName = activeSlots.get(i);
            MainConfig.SlotLimit sl = ClientMainConfigCache.config.slots.get(slotName);

            List<CosmeticData> eqInSlot = new ArrayList<>();
            if (equippedIds != null) {
                for (String id : equippedIds) {
                    CosmeticData d = com.f4xizzz.greatcosmetics.config.CosmeticsConfig.getCosmeticById(id);
                    if (d != null && d.slot != null && d.slot.name().equals(slotName)) eqInSlot.add(d);
                }
            }

            int boxesToDraw = ClientCosmeticCache.isDevModeActive ? eqInSlot.size() + 1 : Math.max(sl.defaultLimit, eqInSlot.size());
            int rowY = drawerY + padding + (i * rowHeight);

            for (int j = 0; j < boxesToDraw; j++) {
                int boxX = currentX + dynamicDrawerWidth - padding - boxSize - (j * (boxSize + boxSpacing)) + (int)scrollX;

                if (boxX + boxSize > currentX && boxX < vWidth) {
                    c.fill(boxX, rowY, boxX + boxSize, rowY + boxSize, 0x66000000);

                    if (j < eqInSlot.size()) {
                        CosmeticData equippedData = eqInSlot.get(j);
                        boolean isHidden = getVisibility(equippedData.id, settings);

                        ItemStack renderStack;
                        if (equippedData.realItemId != null) {
                            // Armadura convertida: ícone é o item de verdade, não o ghost carved_pumpkin.
                            net.minecraft.resources.ResourceLocation realId = net.minecraft.resources.ResourceLocation.tryParse(equippedData.realItemId);
                            net.minecraft.world.item.Item realItem = realId != null ? net.minecraft.core.registries.BuiltInRegistries.ITEM.get(realId) : Items.CARVED_PUMPKIN;
                            renderStack = new ItemStack(realItem);
                        } else {
                            renderStack = new ItemStack(Items.CARVED_PUMPKIN);
                            renderStack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(equippedData.cmd));
                        }

                        c.pose().pushPose();
                        c.pose().translate(boxX + 3.5f, rowY + 3.5f, 0);
                        c.pose().scale(1.2f, 1.2f, 1.0f);
                        c.renderItem(renderStack, 0, 0);
                        c.pose().popPose();

                        int eyeColor = isHidden ? 0xAAFF3333 : 0xAA33FF33;
                        c.fill(boxX + 2, rowY + boxSize - 3, boxX + boxSize - 2, rowY + boxSize - 1, eyeColor);

                        if (over(vMouseX, vMouseY, boxX, rowY, boxSize, boxSize)) {
                            c.renderOutline(boxX, rowY, boxSize, boxSize, 0x44FFFFFF);
                        }
                    } else {
                        c.renderOutline(boxX, rowY, boxSize, boxSize, ClientCosmeticCache.isDevModeActive ? 0x44FFAA00 : 0x22FFFFFF);
                        ResourceLocation icon = SLOT_ICONS.get(slotName);
                        if (icon != null) {
                            int iconSize = 16;
                            c.pose().pushPose();
                            c.pose().translate(boxX + (boxSize - iconSize) / 2f, rowY + (boxSize - iconSize) / 2f, 0);
                            c.blit(icon, 0, 0, 0, 0, iconSize, iconSize, iconSize, iconSize);
                            c.pose().popPose();
                        } else {
                            String shortName = slotName.length() >= 3 ? slotName.substring(0, 3) : slotName;
                            c.drawCenteredString(textRenderer, shortName, boxX + (boxSize/2), rowY + (boxSize/2) - 4, ClientCosmeticCache.isDevModeActive ? 0xFFFFAA00 : 0xFF444444);
                        }
                    }
                }
            }
        }
        RenderSystem.disableScissor();

        int contentW = (maxColumns * boxSize) + (Math.max(0, maxColumns - 1) * boxSpacing) + (padding * 2);
        this.maxScrollX = Math.max(0, contentW - dynamicDrawerWidth);

        if (this.maxScrollX > 0) {
            int scrollBarY = drawerY + drawerHeight - 3;
            c.fill(currentX, scrollBarY, vWidth, scrollBarY + 3, 0x44000000);
            int barW = Math.max(20, (int) (dynamicDrawerWidth * ((float) dynamicDrawerWidth / contentW)));
            int barX = currentX + dynamicDrawerWidth - barW - (int) ((dynamicDrawerWidth - barW) * (scrollX / maxScrollX));
            c.fill(barX, scrollBarY, barX + barW, scrollBarY + 3, 0x66FFFFFF);
        }

        // ==========================================
        // BOTÃO MINIMALISTA DE DEV MODE
        // ==========================================
        if (hasDevPermission(Minecraft.getInstance().player)) { // <-- Alterado para o novo método
            int btnW = 60;
            int btnH = 12;
            int btnX = currentX + (dynamicDrawerWidth - btnW) / 2;
            int btnY = drawerY + drawerHeight + 6; // Fica flutuando logo abaixo da gaveta

            boolean hovBtn = over(vMouseX, vMouseY, btnX, btnY, btnW, btnH);
            c.fill(btnX, btnY, btnX + btnW, btnY + btnH, hovBtn ? 0xEE222222 : 0xAA0A0A0A);
            c.fill(btnX, btnY, btnX + 1, btnY + btnH, ClientCosmeticCache.isDevModeActive ? 0xFFFFAA00 : 0xFF555555);

            String devText = com.f4xizzz.greatcosmetics.config.LangConfig.legacy(ClientCosmeticCache.isDevModeActive ? "wardrobe.equipped_slots.dev_on" : "wardrobe.equipped_slots.dev_off");
            c.pose().pushPose();
            c.pose().translate(btnX + btnW / 2.0f, btnY + 2, 0);
            c.pose().scale(0.8f, 0.8f, 1.0f); // Texto um pouquinho menor para ficar clean
            c.drawCenteredString(textRenderer, devText, 0, 0, 0xFFFFFF);
            c.pose().popPose();
        }
    }

    private void drawHandle(GuiGraphics c, int vMouseX, int vMouseY, int currentX, int drawerY, int drawerHeight) {
        int handleW = 14;
        int handleH = 40;
        int handleX = currentX - handleW;
        int handleY = drawerY + (drawerHeight - handleH) / 2;

        boolean hovered = over(vMouseX, vMouseY, handleX, handleY, handleW, handleH);
        c.fill(handleX, handleY, handleX + handleW, handleY + handleH, hovered ? 0xEE222222 : 0xAA0A0A0A);
        c.fill(handleX, handleY, handleX + 1, handleY + handleH, 0x33FFFFFF);

        c.drawCenteredString(parent.getTextRenderer(), isOpen ? ">" : "<", handleX + handleW/2, handleY + (handleH/2) - 4, hovered ? 0xFFFFAA00 : 0xFFAAAAAA);
    }

    public boolean mouseClicked(int vMouseX, int vMouseY, int vWidth, int vHeight, boolean isDevTabActive) {
        if (isDevTabActive || Minecraft.getInstance().player == null) return false;

        UUID uuid = Minecraft.getInstance().player.getUUID();
        Set<String> equippedIds = ClientCosmeticCache.getEquipped(uuid);
        List<String> activeSlots = getActiveSlots(equippedIds);
        if (activeSlots.isEmpty()) return false;

        int maxColumns = getMaxColumns(equippedIds, activeSlots);
        int visibleColumns = Math.min(3, maxColumns);
        int dynamicDrawerWidth = (visibleColumns * boxSize) + (Math.max(0, visibleColumns - 1) * boxSpacing) + (padding * 2);

        int rowHeight = boxSize + boxSpacing;
        int drawerHeight = (activeSlots.size() * rowHeight) - boxSpacing + (padding * 2);
        int drawerY = Math.max(10, (vHeight - drawerHeight) / 2);
        int currentX = vWidth - (isOpen ? dynamicDrawerWidth : 0);

        int handleW = 14;
        int handleH = 40;
        int handleX = currentX - handleW;
        int handleY = drawerY + (drawerHeight - handleH) / 2;

        if (over(vMouseX, vMouseY, handleX, handleY, handleW, handleH)) {
            playClick();
            isOpen = !isOpen;
            return true;
        }

        if (!isOpen) return false;

        // Clique no Botão Dev Mode
        if (hasDevPermission(Minecraft.getInstance().player)) { // <-- Alterado para o novo método
            int btnW = 60;
            int btnH = 12;
            int btnX = currentX + (dynamicDrawerWidth - btnW) / 2;
            int btnY = drawerY + drawerHeight + 6;

            if (over(vMouseX, vMouseY, btnX, btnY, btnW, btnH)) {
                playClick();
                ClientCosmeticCache.isDevModeActive = !ClientCosmeticCache.isDevModeActive;
                return true;
            }
        }

        if (over(vMouseX, vMouseY, currentX, drawerY, dynamicDrawerWidth, drawerHeight)) {
            isDraggingDrawer = true;
            ClientCosmeticCache.PlayerSettings settings = ClientCosmeticCache.getSettings(uuid);

            for (int i = 0; i < activeSlots.size(); i++) {
                String slotName = activeSlots.get(i);
                MainConfig.SlotLimit sl = ClientMainConfigCache.config.slots.get(slotName);

                List<CosmeticData> eqInSlot = new ArrayList<>();
                if (equippedIds != null) {
                    for (String id : equippedIds) {
                        CosmeticData d = com.f4xizzz.greatcosmetics.config.CosmeticsConfig.getCosmeticById(id);
                        if (d != null && d.slot != null && d.slot.name().equals(slotName)) eqInSlot.add(d);
                    }
                }

                int boxesToDraw = ClientCosmeticCache.isDevModeActive ? eqInSlot.size() + 1 : Math.max(sl.defaultLimit, eqInSlot.size());
                int rowY = drawerY + padding + (i * rowHeight);

                for (int j = 0; j < boxesToDraw; j++) {
                    int boxX = currentX + dynamicDrawerWidth - padding - boxSize - (j * (boxSize + boxSpacing)) + (int)scrollX;

                    if (over(vMouseX, vMouseY, boxX, rowY, boxSize, boxSize)) {
                        if (j < eqInSlot.size()) {
                            CosmeticData equippedData = eqInSlot.get(j);
                            if (over(vMouseX, vMouseY, boxX, rowY + boxSize - 8, boxSize, 8)) {
                                toggleAccessoryVisibility(equippedData.id, settings, uuid);
                            } else {
                                com.f4xizzz.greatcosmetics.platform.GcNet.toServer(new EquipCosmeticPayload(equippedData.id, ClientCosmeticCache.isDevModeActive));
                            }
                            playClick();
                        }
                        return true;
                    }
                }
            }
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double vMx, double vMy, int button, double vDeltaX, double vDeltaY) {
        if (isOpen && isDraggingDrawer && maxScrollX > 0) {
            scrollX += vDeltaX;
            if (scrollX < 0) scrollX = 0;
            if (scrollX > maxScrollX) scrollX = maxScrollX;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double vMx, double vMy, double hAmount, double vAmount) {
        if (isOpen && maxScrollX > 0) {
            UUID uuid = Minecraft.getInstance().player.getUUID();
            Set<String> equippedIds = ClientCosmeticCache.getEquipped(uuid);
            List<String> activeSlots = getActiveSlots(equippedIds);

            int maxColumns = getMaxColumns(equippedIds, activeSlots);
            int visibleColumns = Math.min(3, maxColumns);
            int dynamicDrawerWidth = (visibleColumns * boxSize) + (Math.max(0, visibleColumns - 1) * boxSpacing) + (padding * 2);

            int currentX = Minecraft.getInstance().getWindow().getGuiScaledWidth() - dynamicDrawerWidth;
            if (vMx >= currentX) {
                scrollX += (float) (vAmount * 15f);
                if (scrollX < 0) scrollX = 0;
                if (scrollX > maxScrollX) scrollX = maxScrollX;
                return true;
            }
        }
        return false;
    }

    public void onMouseReleased() {
        isDraggingDrawer = false;
    }

    private boolean getVisibility(String cosmeticId, ClientCosmeticCache.PlayerSettings settings) {
        return cosmeticId != null && settings.hiddenCosmeticIds().contains(cosmeticId);
    }

    private void toggleAccessoryVisibility(String cosmeticId, ClientCosmeticCache.PlayerSettings settings, UUID uuid) {
        boolean newState = !getVisibility(cosmeticId, settings);
        com.f4xizzz.greatcosmetics.platform.GcNet.toServer(new com.f4xizzz.greatcosmetics.network.ToggleCosmeticVisibilityPayload(cosmeticId, newState));

        java.util.Set<String> newHidden = new java.util.HashSet<>(settings.hiddenCosmeticIds());
        if (newState) newHidden.add(cosmeticId); else newHidden.remove(cosmeticId);

        ClientCosmeticCache.setSettings(uuid,
                settings.hideHelmet(), settings.hideChestplate(), settings.hideLeggings(), settings.hideBoots(),
                newHidden
        );
    }

    private boolean over(double mx, double my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    private void playClick() {
        try { Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F)); } catch (Exception ignored) {}
    }
}