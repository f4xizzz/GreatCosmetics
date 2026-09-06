package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S — pedido de um admin (Dev Studio) pra persistir a criação/edição de um cosmético (normal ou
 *  armadura convertida). {@code jsonData} é o CosmeticData inteiro serializado, MENOS os campos
 *  transient (id/realItemId/resolvedIconCmd — o Gson pula transient por padrão), por isso eles vêm
 *  como campos próprios do payload. {@code oldId} != {@code newId} só é usado (e só é permitido) em
 *  cosméticos normais — armadura nunca é renomeável (ver DevCosmeticsSubPage). O servidor valida
 *  permissão, aplica, salva no disco DELE e rebroadcasta o catálogo pra todo mundo. */
public record SaveCosmeticPayload(String oldId, String newId, String realItemId, boolean isArmor, String jsonData) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SaveCosmeticPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "save_cosmetic"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveCosmeticPayload> CODEC = StreamCodec.ofMember(SaveCosmeticPayload::write, SaveCosmeticPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(this.oldId != null ? this.oldId : "");
        buf.writeUtf(this.newId != null ? this.newId : "");
        buf.writeUtf(this.realItemId != null ? this.realItemId : "");
        buf.writeBoolean(this.isArmor);
        buf.writeUtf(this.jsonData != null ? this.jsonData : "{}");
    }

    private static SaveCosmeticPayload read(RegistryFriendlyByteBuf buf) {
        String oldId = buf.readUtf();
        String newId = buf.readUtf();
        String realItemId = buf.readUtf();
        boolean isArmor = buf.readBoolean();
        String jsonData = buf.readUtf();
        return new SaveCosmeticPayload(oldId, newId, realItemId, isArmor, jsonData);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
