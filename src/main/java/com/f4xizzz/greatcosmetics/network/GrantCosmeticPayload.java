package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C — avisa o client que um cosmético específico acabou de ser liberado (unlockCosmetic no
 *  banco), sem precisar reabrir o wardrobe pra ClientUnlockedCosmetics saber disso. Usado
 *  principalmente por checkAndConvertArmorInventory (armadura virada cosmético removida do
 *  inventário e liberada automaticamente) — sem isso o cosmético some do inventário E continua
 *  invisível na aba Acessórios até o player reabrir o wardrobe. */
public record GrantCosmeticPayload(String cosmeticId) implements CustomPayload {
    public static final CustomPayload.Id<GrantCosmeticPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "grant_cosmetic"));

    public static final PacketCodec<RegistryByteBuf, GrantCosmeticPayload> CODEC = PacketCodec.of(GrantCosmeticPayload::write, GrantCosmeticPayload::read);

    private void write(RegistryByteBuf buf) {
        buf.writeString(this.cosmeticId != null ? this.cosmeticId : "");
    }

    private static GrantCosmeticPayload read(RegistryByteBuf buf) {
        return new GrantCosmeticPayload(buf.readString());
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
