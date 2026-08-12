package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S — pedido de um admin (Dev Studio) pra persistir a criação/edição de um cosmético (normal ou
 *  armadura convertida). {@code jsonData} é o CosmeticData inteiro serializado, MENOS os campos
 *  transient (id/realItemId/resolvedIconCmd — o Gson pula transient por padrão), por isso eles vêm
 *  como campos próprios do payload. {@code oldId} != {@code newId} só é usado (e só é permitido) em
 *  cosméticos normais — armadura nunca é renomeável (ver DevCosmeticsSubPage). O servidor valida
 *  permissão, aplica, salva no disco DELE e rebroadcasta o catálogo pra todo mundo. */
public record SaveCosmeticPayload(String oldId, String newId, String realItemId, boolean isArmor, String jsonData) implements CustomPayload {
    public static final CustomPayload.Id<SaveCosmeticPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "save_cosmetic"));

    public static final PacketCodec<RegistryByteBuf, SaveCosmeticPayload> CODEC = PacketCodec.of(SaveCosmeticPayload::write, SaveCosmeticPayload::read);

    private void write(RegistryByteBuf buf) {
        buf.writeString(this.oldId != null ? this.oldId : "");
        buf.writeString(this.newId != null ? this.newId : "");
        buf.writeString(this.realItemId != null ? this.realItemId : "");
        buf.writeBoolean(this.isArmor);
        buf.writeString(this.jsonData != null ? this.jsonData : "{}");
    }

    private static SaveCosmeticPayload read(RegistryByteBuf buf) {
        String oldId = buf.readString();
        String newId = buf.readString();
        String realItemId = buf.readString();
        boolean isArmor = buf.readBoolean();
        String jsonData = buf.readString();
        return new SaveCosmeticPayload(oldId, newId, realItemId, isArmor, jsonData);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
