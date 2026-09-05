package com.f4xizzz.greatcosmetics.network;

import com.f4xizzz.greatcosmetics.config.CosmeticData;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** {@code allowResourceReload}: só true quando o /gc reload manda esse pacote — o resource pack
 *  (texturas/models) só deve recarregar de verdade nesse momento explícito. Quando o Dev Studio
 *  salva uma edição individual (ver SaveCosmeticPayload), os dados já chegam certos no client
 *  (CosmeticsConfig.cosmeticsMap é sobrescrito direto), mas SEM disparar reloadResources() — que é
 *  pesado e dá um "pisca" perceptível na tela — até o admin pedir /gc reload de propósito. */
public record SyncCosmeticsPayload(Map<String, CosmeticData> configMap, boolean allowResourceReload) implements CustomPayload {

    public static final CustomPayload.Id<SyncCosmeticsPayload> ID = new CustomPayload.Id<>(Identifier.of("greatcosmetics", "sync_config"));

    public static final PacketCodec<PacketByteBuf, SyncCosmeticsPayload> CODEC = PacketCodec.of(
            SyncCosmeticsPayload::write,
            SyncCosmeticsPayload::new
    );

    private SyncCosmeticsPayload(PacketByteBuf buf) {
        this(readMap(buf), buf.readBoolean());
    }

    private static Map<String, CosmeticData> readMap(net.minecraft.network.PacketByteBuf buf) {
        Map<String, CosmeticData> map = new HashMap<>();
        int size = buf.readVarInt();
        for (int i = 0; i < size; i++) {
            String id = buf.readString();

            CosmeticData.RenderSlot render = CosmeticData.RenderSlot.valueOf(buf.readString());
            CosmeticData.VirtualSlot slot = CosmeticData.VirtualSlot.valueOf(buf.readString());
            String type = buf.readString();

            CosmeticData data = new CosmeticData();
            data.id = id;
            data.render = render;
            data.slot = slot;
            data.type = type;

            data.cmd = buf.readInt();
            data.resolvedIconCmd = buf.readInt();
            data.iconId = buf.readString();

            // ==========================================
            // DESEMPACOTANDO OS ATRIBUTOS
            // ==========================================
            data.DisplayName = buf.readString();
            data.permission = buf.readString();
            data.maxDurability = buf.readInt();
            data.armor = buf.readInt();
            data.toughness = buf.readDouble();
            data.isBackpack = buf.readBoolean();
            data.backpackRows = buf.readInt();
            data.backpackDisplayName = buf.readString();
            data.EnableFly = buf.readBoolean();
            data.flySpeedMultiplier = buf.readDouble();
            data.groundSpeedMultiplier = buf.readDouble();
            data.swimSpeedMultiplier = buf.readDouble();

            data.effects = readStringList(buf);
            data.effectVisual = readStringList(buf);
            data.flyParticle = readStringList(buf);

            // --- LENDO OS SONS ---
            data.sounds = new CosmeticData.CosmeticSounds();
            data.sounds.equipSound = buf.readString();
            data.sounds.unequipSound = buf.readString();
            data.sounds.walkSound = buf.readString();
            data.sounds.flySound = buf.readString();
            data.sounds.shiftSound = buf.readString();
            data.sounds.backpackSound = buf.readString();
            data.sounds.idleSound = buf.readString(); // <--- LENDO O IDLE SOUND

            data.sounds.walkVolume = buf.readDouble();
            data.sounds.walkPitch = buf.readDouble();
            data.sounds.flyVolume = buf.readDouble();
            data.sounds.flyPitch = buf.readDouble();
            data.sounds.shiftVolume = buf.readDouble();
            data.sounds.shiftPitch = buf.readDouble();
            data.sounds.backpackVolume = buf.readDouble();
            data.sounds.backpackPitch = buf.readDouble();
            data.sounds.idleVolume = buf.readDouble();
            data.sounds.idlePitch = buf.readDouble();

            // Desempacotando o Sistema de Lure
            boolean hasLure = buf.readBoolean();
            if (hasLure) {
                data.lure = new CosmeticData.LureStats();
                data.lure.enabled = buf.readBoolean();
                data.lure.lureTYPE = buf.readString();
                data.lure.lureShinyMultiplier = buf.readDouble();
                data.lure.lureUltraRAREMultiplier = buf.readDouble();
                data.lure.lureHiddenAbilityMultiplier = buf.readDouble();
                data.lure.lureExpAllMultiplier = buf.readDouble();
                data.lure.lureAmizadeMultiplier = buf.readDouble();
                data.lure.lureIV = buf.readInt();
                data.lure.lureChanceIV = buf.readDouble();
                data.lure.lurePescaShiny = buf.readDouble();
                data.lure.lurePescaUltraRare = buf.readDouble();
                data.lure.lurePescaIvChance = buf.readDouble();
                data.lure.lurePescaIv = buf.readInt();
                data.lure.lurePescaVelocidade = buf.readDouble();
                data.lure.lureEXP = buf.readDouble();
                data.lure.lureEV = buf.readDouble();
                data.lure.lureChanceDeCaptura = buf.readDouble();
                data.lure.lureDePesca = buf.readDouble();
            }

            // Desempacotando Partes 3D
            int partsSize = buf.readVarInt();
            for (int j = 0; j < partsSize; j++) {
                CosmeticData.CosmeticPart part = new CosmeticData.CosmeticPart(CosmeticData.Anchor.valueOf(buf.readString()));
                part.customModelData_or_ID = buf.readString();
                part.geoModelId = buf.readString();
                part.useExactPath = buf.readBoolean();
                part.resolvedCmd = buf.readInt();

                part.offsetX = buf.readFloat();
                part.offsetY = buf.readFloat();
                part.offsetZ = buf.readFloat();
                part.rotationX = buf.readFloat();
                part.rotationY = buf.readFloat();
                part.rotationZ = buf.readFloat();
                part.shiftOffsetX = buf.readFloat();
                part.shiftOffsetY = buf.readFloat();
                part.shiftOffsetZ = buf.readFloat();
                part.shiftRotationX = buf.readFloat();
                part.shiftRotationY = buf.readFloat();
                part.shiftRotationZ = buf.readFloat();
                part.scaleX = buf.readFloat();
                part.scaleY = buf.readFloat();
                part.scaleZ = buf.readFloat();
                data.parts.add(part);
            }
            map.put(id, data);
        }
        return map;
    }

    private void write(PacketByteBuf buf) {
        buf.writeVarInt(configMap.size());
        for (Map.Entry<String, CosmeticData> entry : configMap.entrySet()) {
            buf.writeString(entry.getKey());
            CosmeticData data = entry.getValue();

            buf.writeString(data.render != null ? data.render.name() : "HEAD");
            buf.writeString(data.slot != null ? data.slot.name() : "HEAD");
            buf.writeString(data.type != null ? data.type : "default");

            buf.writeInt(data.cmd);
            buf.writeInt(data.resolvedIconCmd);
            buf.writeString(data.iconId != null ? data.iconId : "");

            // ==========================================
            // EMPACOTANDO OS ATRIBUTOS
            // ==========================================
            buf.writeString(data.DisplayName != null ? data.DisplayName : "");
            buf.writeString(data.permission != null ? data.permission : "");
            buf.writeInt(data.maxDurability);
            buf.writeInt(data.armor);
            buf.writeDouble(data.toughness);
            buf.writeBoolean(data.isBackpack);
            buf.writeInt(data.backpackRows);
            buf.writeString(data.backpackDisplayName != null ? data.backpackDisplayName : "");
            buf.writeBoolean(data.EnableFly);
            buf.writeDouble(data.flySpeedMultiplier);
            buf.writeDouble(data.groundSpeedMultiplier);
            buf.writeDouble(data.swimSpeedMultiplier);

            writeStringList(buf, data.effects);
            writeStringList(buf, data.effectVisual);
            writeStringList(buf, data.flyParticle);

            // --- ESCREVENDO OS SONS ---
            if (data.sounds == null) data.sounds = new CosmeticData.CosmeticSounds();
            buf.writeString(data.sounds.equipSound != null ? data.sounds.equipSound : "");
            buf.writeString(data.sounds.unequipSound != null ? data.sounds.unequipSound : "");
            buf.writeString(data.sounds.walkSound != null ? data.sounds.walkSound : "");
            buf.writeString(data.sounds.flySound != null ? data.sounds.flySound : "");
            buf.writeString(data.sounds.shiftSound != null ? data.sounds.shiftSound : "");
            buf.writeString(data.sounds.backpackSound != null ? data.sounds.backpackSound : "");
            buf.writeString(data.sounds.idleSound != null ? data.sounds.idleSound : ""); // <--- ESCREVENDO O IDLE SOUND

            buf.writeDouble(data.sounds.walkVolume);
            buf.writeDouble(data.sounds.walkPitch);
            buf.writeDouble(data.sounds.flyVolume);
            buf.writeDouble(data.sounds.flyPitch);
            buf.writeDouble(data.sounds.shiftVolume);
            buf.writeDouble(data.sounds.shiftPitch);
            buf.writeDouble(data.sounds.backpackVolume);
            buf.writeDouble(data.sounds.backpackPitch);
            buf.writeDouble(data.sounds.idleVolume);
            buf.writeDouble(data.sounds.idlePitch);

            // Empacotando o Sistema de Lure
            if (data.lure != null) {
                buf.writeBoolean(true);
                buf.writeBoolean(data.lure.enabled);
                buf.writeString(data.lure.lureTYPE != null ? data.lure.lureTYPE : "");
                buf.writeDouble(data.lure.lureShinyMultiplier);
                buf.writeDouble(data.lure.lureUltraRAREMultiplier);
                buf.writeDouble(data.lure.lureHiddenAbilityMultiplier);
                buf.writeDouble(data.lure.lureExpAllMultiplier);
                buf.writeDouble(data.lure.lureAmizadeMultiplier);
                buf.writeInt(data.lure.lureIV);
                buf.writeDouble(data.lure.lureChanceIV);
                buf.writeDouble(data.lure.lurePescaShiny);
                buf.writeDouble(data.lure.lurePescaUltraRare);
                buf.writeDouble(data.lure.lurePescaIvChance);
                buf.writeInt(data.lure.lurePescaIv);
                buf.writeDouble(data.lure.lurePescaVelocidade);
                buf.writeDouble(data.lure.lureEXP);
                buf.writeDouble(data.lure.lureEV);
                buf.writeDouble(data.lure.lureChanceDeCaptura);
                buf.writeDouble(data.lure.lureDePesca);
            } else {
                buf.writeBoolean(false);
            }

            // Empacotando as Partes 3D
            buf.writeVarInt(data.parts.size());
            for (CosmeticData.CosmeticPart part : data.parts) {
                buf.writeString(part.anchor != null ? part.anchor.name() : "HEAD");
                buf.writeString(part.customModelData_or_ID != null ? part.customModelData_or_ID : "");
                buf.writeString(part.geoModelId != null ? part.geoModelId : "");
                buf.writeBoolean(part.useExactPath);
                buf.writeInt(part.resolvedCmd);

                buf.writeFloat(part.offsetX);
                buf.writeFloat(part.offsetY);
                buf.writeFloat(part.offsetZ);
                buf.writeFloat(part.rotationX);
                buf.writeFloat(part.rotationY);
                buf.writeFloat(part.rotationZ);
                buf.writeFloat(part.shiftOffsetX);
                buf.writeFloat(part.shiftOffsetY);
                buf.writeFloat(part.shiftOffsetZ);
                buf.writeFloat(part.shiftRotationX);
                buf.writeFloat(part.shiftRotationY);
                buf.writeFloat(part.shiftRotationZ);
                buf.writeFloat(part.scaleX);
                buf.writeFloat(part.scaleY);
                buf.writeFloat(part.scaleZ);
            }
        }
        buf.writeBoolean(allowResourceReload);
    }

    private static List<String> readStringList(PacketByteBuf buf) {
        int size = buf.readVarInt();
        List<String> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(buf.readString());
        }
        return list;
    }

    private static void writeStringList(PacketByteBuf buf, List<String> list) {
        if (list == null) {
            buf.writeVarInt(0);
        } else {
            buf.writeVarInt(list.size());
            for (String s : list) {
                buf.writeString(s != null ? s : "");
            }
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}