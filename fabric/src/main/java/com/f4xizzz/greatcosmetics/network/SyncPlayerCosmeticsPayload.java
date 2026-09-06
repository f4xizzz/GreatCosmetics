package com.f4xizzz.greatcosmetics.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** hiddenCosmeticIds substituiu os 7 booleans hide_acc_* (por SLOT inteiro) — esconder agora é
 *  por COSMÉTICO ESPECÍFICO, então dá pra esconder só um item de um slot com vários equipados ao
 *  mesmo tempo sem esconder os outros junto (ver ToggleCosmeticVisibilityPayload). Os 4 booleans
 *  hideHelmet/hideChestplate/hideLeggings/hideBoots continuam por slot — são pra armadura VANILLA
 *  real, onde só existe UM item físico por slot mesmo. */
public record SyncPlayerCosmeticsPayload(
        UUID playerUuid,
        List<String> equippedCosmetics,
        boolean hideHelmet, boolean hideChestplate, boolean hideLeggings, boolean hideBoots,
        List<String> hiddenCosmeticIds
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncPlayerCosmeticsPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "sync_player_cosmetics"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerCosmeticsPayload> CODEC = StreamCodec.ofMember(
            // ESCREVER (Servidor -> Cliente)
            (payload, buf) -> {
                buf.writeUUID(payload.playerUuid());

                buf.writeBoolean(payload.hideHelmet());
                buf.writeBoolean(payload.hideChestplate());
                buf.writeBoolean(payload.hideLeggings());
                buf.writeBoolean(payload.hideBoots());

                buf.writeInt(payload.equippedCosmetics().size());
                for (String id : payload.equippedCosmetics()) {
                    buf.writeUtf(id);
                }

                buf.writeInt(payload.hiddenCosmeticIds().size());
                for (String id : payload.hiddenCosmeticIds()) {
                    buf.writeUtf(id);
                }
            },
            // LER (Cliente recebe)
            buf -> {
                UUID uuid = buf.readUUID();

                boolean hH = buf.readBoolean();
                boolean hC = buf.readBoolean();
                boolean hL = buf.readBoolean();
                boolean hB = buf.readBoolean();

                int size = buf.readInt();
                List<String> list = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    list.add(buf.readUtf());
                }

                int hiddenSize = buf.readInt();
                List<String> hidden = new ArrayList<>();
                for (int i = 0; i < hiddenSize; i++) {
                    hidden.add(buf.readUtf());
                }

                return new SyncPlayerCosmeticsPayload(uuid, list, hH, hC, hL, hB, hidden);
            }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
}
