package com.f4xizzz.greatcosmetics.client.gui;

import com.f4xizzz.greatcosmetics.config.CosmeticData;
import com.f4xizzz.greatcosmetics.network.OpenSpecificBackpackPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import java.util.List;

public class BackpackSelectorScreen extends Screen {
    private final List<String> backpackIds;

    public BackpackSelectorScreen(List<String> backpackIds) {
        super(com.f4xizzz.greatcosmetics.config.LangConfig.text("backpack.selector.title"));
        this.backpackIds = backpackIds;
    }

    @Override
    public void render(GuiGraphics c, int mouseX, int mouseY, float delta) {
        this.renderBackground(c, mouseX, mouseY, delta);

        int itemsCount = backpackIds.size();
        int boxSize = 60;
        int spacing = 10;
        int totalWidth = (itemsCount * boxSize) + ((itemsCount - 1) * spacing);

        int startX = (this.width - totalWidth) / 2;
        int startY = (this.height - boxSize) / 2;

        var world = net.minecraft.client.Minecraft.getInstance().level;
        net.minecraft.core.HolderLookup.Provider regs = world != null ? world.registryAccess() : null;

        c.drawCenteredString(this.font, com.f4xizzz.greatcosmetics.config.LangConfig.legacy("backpack.selector.title"), this.width / 2, startY - 25, 0xFFFFAA00);

        for (int i = 0; i < itemsCount; i++) {
            String id = backpackIds.get(i);
            CosmeticData data = com.f4xizzz.greatcosmetics.config.CosmeticsConfig.getCosmeticById(id);
            if (data == null) continue;

            int itemX = startX + (i * (boxSize + spacing));
            int itemY = startY;

            boolean hovered = mouseX >= itemX && mouseX <= itemX + boxSize && mouseY >= itemY && mouseY <= itemY + boxSize;

            c.fill(itemX, itemY, itemX + boxSize, itemY + boxSize, hovered ? 0x88FFFFFF : 0x88000000);
            c.renderOutline(itemX, itemY, boxSize, boxSize, hovered ? 0xFFFFAA00 : 0xFF555555);

            // Renderiza o item usando o CMD
            if (data.cmd > 0) {
                ItemStack renderStack = new ItemStack(Items.CARVED_PUMPKIN);
                renderStack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(data.cmd));

                c.pose().pushPose();
                c.pose().translate(itemX + (boxSize / 2f) - 16, itemY + (boxSize / 2f) - 20, 0);
                c.pose().scale(2.0f, 2.0f, 1.0f);
                c.renderItem(renderStack, 0, 0);
                c.pose().popPose();
            }

            // Nome da mochila embaixo do ícone — precisa passar pelo MiniMessage (TextUtils.
            // parseToText), não só um replace("&", "§") cru, senão tags como <light_purple>/hex do
            // Dev Studio apareciam literalmente na tela em vez de virar cor de verdade.
            Component displayName = (data.backpackDisplayName != null && !data.backpackDisplayName.isEmpty() && regs != null)
                    ? com.f4xizzz.greatcosmetics.util.TextUtils.parseToText(data.backpackDisplayName, regs)
                    : com.f4xizzz.greatcosmetics.config.LangConfig.text("backpack.selector.fallback_name");

            // Encaixa o nome na largura da caixa: parte de 0.8 e vai reduzindo se não couber (piso
            // 0.5 pra continuar legível). Sem isso, nome de mochila comprido vazava pros lados do
            // slot. O scissor na coluna da caixa é a rede de segurança pro caso extremo.
            int maxTextW = boxSize - 4;
            int rawW = this.font.width(displayName);
            float scale = 0.8f;
            if (rawW > 0 && rawW * scale > maxTextW) {
                scale = Math.max(0.5f, (float) maxTextW / rawW);
            }

            c.enableScissor(itemX + 1, itemY, itemX + boxSize - 1, itemY + boxSize);
            c.pose().pushPose();
            c.pose().scale(scale, scale, 1.0f);
            c.drawCenteredString(this.font, displayName, Math.round((itemX + (boxSize / 2f)) / scale), Math.round((itemY + boxSize - 12) / scale), 0xFFFFFF);
            c.pose().popPose();
            c.disableScissor();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int itemsCount = backpackIds.size();
        int boxSize = 60;
        int spacing = 10;
        int totalWidth = (itemsCount * boxSize) + ((itemsCount - 1) * spacing);

        int startX = (this.width - totalWidth) / 2;
        int startY = (this.height - boxSize) / 2;

        for (int i = 0; i < itemsCount; i++) {
            int itemX = startX + (i * (boxSize + spacing));
            int itemY = startY;

            if (mouseX >= itemX && mouseX <= itemX + boxSize && mouseY >= itemY && mouseY <= itemY + boxSize) {
                // Toca som de clique
                if (this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.3F, 1.0F);
                }

                // Manda o ID da mochila pro servidor abrir e fecha o Popup
                com.f4xizzz.greatcosmetics.platform.GcNet.toServer(new OpenSpecificBackpackPayload(backpackIds.get(i)));
                this.onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}