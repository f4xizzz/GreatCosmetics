package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** S2C — avisa o client que um cosmético específico acabou de ser liberado (unlockCosmetic no
 *  banco), sem precisar reabrir o wardrobe pra ClientUnlockedCosmetics saber disso. Usado
 *  principalmente por checkAndConvertArmorInventory (armadura virada cosmético removida do
 *  inventário e liberada automaticamente) — sem isso o cosmético some do inventário E continua
 *  invisível na aba Acessórios até o player reabrir o wardrobe. */
public record GrantCosmeticPayload(String cosmeticId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GrantCosmeticPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "grant_cosmetic"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GrantCosmeticPayload> CODEC = StreamCodec.ofMember(GrantCosmeticPayload::write, GrantCosmeticPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(this.cosmeticId != null ? this.cosmeticId : "");
    }

    private static GrantCosmeticPayload read(RegistryFriendlyByteBuf buf) {
        return new GrantCosmeticPayload(buf.readUtf());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
