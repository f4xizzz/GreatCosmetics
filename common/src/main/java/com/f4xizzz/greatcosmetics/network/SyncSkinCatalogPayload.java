package com.f4xizzz.greatcosmetics.network;

import com.f4xizzz.greatcosmetics.config.PokemonSkin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C — catálogo completo de skins de Pokémon (pokeskins.json) + cor de cada grupo (ver
 * SkinGroupConfigManager). SkinConfigManager é lido do disco local nos dois lados (client e
 * server), o que só funciona por acaso em singleplayer/LAN onde os dois compartilham a mesma
 * pasta de config — num servidor de verdade o client nunca tinha como saber de skins novas/
 * editadas, nem com /gc reload. Mandado no join e rebroadcast no reload.
 */
public record SyncSkinCatalogPayload(List<PokemonSkin> skins, Map<String, String> groupColors) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncSkinCatalogPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "sync_skin_catalog"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncSkinCatalogPayload> CODEC = StreamCodec.ofMember(SyncSkinCatalogPayload::write, SyncSkinCatalogPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(this.skins.size());
        for (PokemonSkin skin : this.skins) {
            buf.writeUtf(skin.getId());
            buf.writeUtf(skin.getDisplayName());
            buf.writeUtf(skin.getSpecies());
            buf.writeUtf(skin.getAspect());
            buf.writeInt(skin.getCooldownMinutes());

            List<String> altForms = skin.getAltForms();
            buf.writeInt(altForms.size());
            for (String form : altForms) buf.writeUtf(form);

            buf.writeUtf(skin.getGroup());
        }

        buf.writeInt(this.groupColors.size());
        for (Map.Entry<String, String> entry : this.groupColors.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeUtf(entry.getValue());
        }
    }

    private static SyncSkinCatalogPayload read(RegistryFriendlyByteBuf buf) {
        int size = buf.readInt();
        List<PokemonSkin> skins = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String id = buf.readUtf();
            String displayName = buf.readUtf();
            String species = buf.readUtf();
            String aspect = buf.readUtf();
            int cooldownMinutes = buf.readInt();
            PokemonSkin skin = new PokemonSkin(id, displayName, species, aspect, cooldownMinutes);

            int altFormsSize = buf.readInt();
            List<String> altForms = new ArrayList<>();
            for (int j = 0; j < altFormsSize; j++) altForms.add(buf.readUtf());
            skin.setAltForms(altForms);

            skin.setGroup(buf.readUtf());

            skins.add(skin);
        }

        int colorsSize = buf.readInt();
        Map<String, String> groupColors = new HashMap<>();
        for (int i = 0; i < colorsSize; i++) {
            String key = buf.readUtf();
            String value = buf.readUtf();
            groupColors.put(key, value);
        }

        return new SyncSkinCatalogPayload(skins, groupColors);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
