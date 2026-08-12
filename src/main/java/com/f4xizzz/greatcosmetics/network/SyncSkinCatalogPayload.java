package com.f4xizzz.greatcosmetics.network;

import com.f4xizzz.greatcosmetics.config.PokemonSkin;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * S2C — catálogo completo de skins de Pokémon (pokeskins.json) + cor de cada grupo (ver
 * SkinGroupConfigManager). SkinConfigManager é lido do disco local nos dois lados (client e
 * server), o que só funciona por acaso em singleplayer/LAN onde os dois compartilham a mesma
 * pasta de config — num servidor de verdade o client nunca tinha como saber de skins novas/
 * editadas, nem com /gc reload. Mandado no join e rebroadcast no reload.
 */
public record SyncSkinCatalogPayload(List<PokemonSkin> skins, Map<String, String> groupColors) implements CustomPayload {
    public static final CustomPayload.Id<SyncSkinCatalogPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "sync_skin_catalog"));

    public static final PacketCodec<RegistryByteBuf, SyncSkinCatalogPayload> CODEC = PacketCodec.of(SyncSkinCatalogPayload::write, SyncSkinCatalogPayload::read);

    private void write(RegistryByteBuf buf) {
        buf.writeInt(this.skins.size());
        for (PokemonSkin skin : this.skins) {
            buf.writeString(skin.getId());
            buf.writeString(skin.getDisplayName());
            buf.writeString(skin.getSpecies());
            buf.writeString(skin.getAspect());
            buf.writeInt(skin.getCooldownMinutes());

            List<String> altForms = skin.getAltForms();
            buf.writeInt(altForms.size());
            for (String form : altForms) buf.writeString(form);

            buf.writeString(skin.getGroup());
        }

        buf.writeInt(this.groupColors.size());
        for (Map.Entry<String, String> entry : this.groupColors.entrySet()) {
            buf.writeString(entry.getKey());
            buf.writeString(entry.getValue());
        }
    }

    private static SyncSkinCatalogPayload read(RegistryByteBuf buf) {
        int size = buf.readInt();
        List<PokemonSkin> skins = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String id = buf.readString();
            String displayName = buf.readString();
            String species = buf.readString();
            String aspect = buf.readString();
            int cooldownMinutes = buf.readInt();
            PokemonSkin skin = new PokemonSkin(id, displayName, species, aspect, cooldownMinutes);

            int altFormsSize = buf.readInt();
            List<String> altForms = new ArrayList<>();
            for (int j = 0; j < altFormsSize; j++) altForms.add(buf.readString());
            skin.setAltForms(altForms);

            skin.setGroup(buf.readString());

            skins.add(skin);
        }

        int colorsSize = buf.readInt();
        Map<String, String> groupColors = new HashMap<>();
        for (int i = 0; i < colorsSize; i++) {
            String key = buf.readString();
            String value = buf.readString();
            groupColors.put(key, value);
        }

        return new SyncSkinCatalogPayload(skins, groupColors);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
