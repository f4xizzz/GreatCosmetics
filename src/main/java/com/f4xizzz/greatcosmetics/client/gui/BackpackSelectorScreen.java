package com.f4xizzz.greatcosmetics.client.gui;

import com.f4xizzz.greatcosmetics.GreatCosmetics;
import com.f4xizzz.greatcosmetics.config.CosmeticData;
import com.f4xizzz.greatcosmetics.network.OpenSpecificBackpackPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;

public class BackpackSelectorScreen extends Screen {
    private final List<String> backpackIds;

    public BackpackSelectorScreen(List<String> backpackIds) {
        super(Text.literal("Selecione a Mochila"));
        this.backpackIds = backpackIds;
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        this.renderBackground(c, mouseX, mouseY, delta);

        int itemsCount = backpackIds.size();
        int boxSize = 60;
        int spacing = 10;
        int totalWidth = (itemsCount * boxSize) + ((itemsCount - 1) * spacing);

        int startX = (this.width - totalWidth) / 2;
        int startY = (this.height - boxSize) / 2;

        var world = net.minecraft.client.MinecraftClient.getInstance().world;
        net.minecraft.registry.RegistryWrapper.WrapperLookup regs = world != null ? world.getRegistryManager() : null;

        c.drawCenteredTextWithShadow(this.textRenderer, "Qual mochila você deseja abrir?", this.width / 2, startY - 25, 0xFFFFAA00);

        for (int i = 0; i < itemsCount; i++) {
            String id = backpackIds.get(i);
            CosmeticData data = GreatCosmetics.getCosmeticById(id);
            if (data == null) continue;

            int itemX = startX + (i * (boxSize + spacing));
            int itemY = startY;

            boolean hovered = mouseX >= itemX && mouseX <= itemX + boxSize && mouseY >= itemY && mouseY <= itemY + boxSize;

            c.fill(itemX, itemY, itemX + boxSize, itemY + boxSize, hovered ? 0x88FFFFFF : 0x88000000);
            c.drawBorder(itemX, itemY, boxSize, boxSize, hovered ? 0xFFFFAA00 : 0xFF555555);

            // Renderiza o item usando o CMD
            if (data.cmd > 0) {
                ItemStack renderStack = new ItemStack(Items.CARVED_PUMPKIN);
                renderStack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(data.cmd));

                c.getMatrices().push();
                c.getMatrices().translate(itemX + (boxSize / 2f) - 16, itemY + (boxSize / 2f) - 20, 0);
                c.getMatrices().scale(2.0f, 2.0f, 1.0f);
                c.drawItem(renderStack, 0, 0);
                c.getMatrices().pop();
            }

            // Nome da mochila embaixo do ícone — precisa passar pelo MiniMessage (TextUtils.
            // parseToText), não só um replace("&", "§") cru, senão tags como <light_purple>/hex do
            // Dev Studio apareciam literalmente na tela em vez de virar cor de verdade.
            Text displayName = (data.backpackDisplayName != null && !data.backpackDisplayName.isEmpty() && regs != null)
                    ? com.f4xizzz.greatcosmetics.util.TextUtils.parseToText(data.backpackDisplayName, regs)
                    : Text.literal("Mochila");

            c.getMatrices().push();
            float scale = 0.8f;
            c.getMatrices().scale(scale, scale, 1.0f);
            c.drawCenteredTextWithShadow(this.textRenderer, displayName, (int)((itemX + (boxSize / 2f)) / scale), (int)((itemY + boxSize - 12) / scale), 0xFFFFFF);
            c.getMatrices().pop();
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
                if (this.client != null && this.client.player != null) {
                    this.client.player.playSound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 0.3F, 1.0F);
                }

                // Manda o ID da mochila pro servidor abrir e fecha o Popup
                ClientPlayNetworking.send(new OpenSpecificBackpackPayload(backpackIds.get(i)));
                this.close();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}