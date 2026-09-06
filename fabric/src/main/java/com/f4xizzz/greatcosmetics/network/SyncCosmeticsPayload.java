package com.f4xizzz.greatcosmetics.network;

import com.f4xizzz.greatcosmetics.config.CosmeticData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** {@code allowResourceReload}: só true quando o /gc reload manda esse pacote — o resource pack
 *  (texturas/models) só deve recarregar de verdade nesse momento explícito. Quando o Dev Studio
 *  salva uma edição individual (ver SaveCosmeticPayload), os dados já chegam certos no client
 *  (CosmeticsConfig.cosmeticsMap é sobrescrito direto), mas SEM disparar reloadResources() — que é
 *  pesado e dá um "pisca" perceptível na tela — até o admin pedir /gc reload de propósito. */
public record SyncCosmeticsPayload(Map<String, CosmeticData> configMap, boolean allowResourceReload) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncCosmeticsPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("greatcosmetics", "sync_config"));

    public static final StreamCodec<FriendlyByteBuf, SyncCosmeticsPayload> CODEC = StreamCodec.ofMember(
            SyncCosmeticsPayload::write,
            SyncCosmeticsPayload::new
    );

    private SyncCosmeticsPayload(FriendlyByteBuf buf) {
        this(readMap(buf), buf.readBoolean());
    }

    private static Map<String, CosmeticData> readMap(net.minecraft.network.FriendlyByteBuf buf) {
        Map<String, CosmeticData> map = new HashMap<>();
        int size = buf.readVarInt();
        for (int i = 0; i < size; i++) {
            String id = buf.readUtf();

            CosmeticData.RenderSlot render = CosmeticData.RenderSlot.valueOf(buf.readUtf());
            CosmeticData.VirtualSlot slot = CosmeticData.VirtualSlot.valueOf(buf.readUtf());
            String type = buf.readUtf();

            CosmeticData data = new CosmeticData();
            data.id = id;
            data.render = render;
            data.slot = slot;
            data.type = type;

            data.cmd = buf.readInt();
            data.resolvedIconCmd = buf.readInt();
            data.iconId = buf.readUtf();

            // ==========================================
            // DESEMPACOTANDO OS ATRIBUTOS
            // ==========================================
            data.DisplayName = buf.readUtf();
            data.permission = buf.readUtf();
            data.maxDurability = buf.readInt();
            data.armor = buf.readInt();
            data.toughness = buf.readDouble();
            data.isBackpack = buf.readBoolean();
            data.backpackRows = buf.readInt();
            data.backpackDisplayName = buf.readUtf();
            data.EnableFly = buf.readBoolean();
            data.flySpeedMultiplier = buf.readDouble();
            data.groundSpeedMultiplier = buf.readDouble();
            data.swimSpeedMultiplier = buf.readDouble();

            data.effects = readStringList(buf);
            data.effectVisual = readStringList(buf);
            data.flyParticle = readStringList(buf);

            // --- LENDO OS SONS ---
            data.sounds = new CosmeticData.CosmeticSounds();
            data.sounds.equipSound = buf.readUtf();
            data.sounds.unequipSound = buf.readUtf();
            data.sounds.walkSound = buf.readUtf();
            data.sounds.flySound = buf.readUtf();
            data.sounds.shiftSound = buf.readUtf();
            data.sounds.backpackSound = buf.readUtf();
            data.sounds.idleSound = buf.readUtf(); // <--- LENDO O IDLE SOUND

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
                data.lure.lureTYPE = buf.readUtf();
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
                CosmeticData.CosmeticPart part = new CosmeticData.CosmeticPart(CosmeticData.Anchor.valueOf(buf.readUtf()));
                part.customModelData_or_ID = buf.readUtf();
                part.geoModelId = buf.readUtf();
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

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(configMap.size());
        for (Map.Entry<String, CosmeticData> entry : configMap.entrySet()) {
            buf.writeUtf(entry.getKey());
            CosmeticData data = entry.getValue();

            buf.writeUtf(data.render != null ? data.render.name() : "HEAD");
            buf.writeUtf(data.slot != null ? data.slot.name() : "HEAD");
            buf.writeUtf(data.type != null ? data.type : "default");

            buf.writeInt(data.cmd);
            buf.writeInt(data.resolvedIconCmd);
            buf.writeUtf(data.iconId != null ? data.iconId : "");

            // ==========================================
            // EMPACOTANDO OS ATRIBUTOS
            // ==========================================
            buf.writeUtf(data.DisplayName != null ? data.DisplayName : "");
            buf.writeUtf(data.permission != null ? data.permission : "");
            buf.writeInt(data.maxDurability);
            buf.writeInt(data.armor);
            buf.writeDouble(data.toughness);
            buf.writeBoolean(data.isBackpack);
            buf.writeInt(data.backpackRows);
            buf.writeUtf(data.backpackDisplayName != null ? data.backpackDisplayName : "");
            buf.writeBoolean(data.EnableFly);
            buf.writeDouble(data.flySpeedMultiplier);
            buf.writeDouble(data.groundSpeedMultiplier);
            buf.writeDouble(data.swimSpeedMultiplier);

            writeStringList(buf, data.effects);
            writeStringList(buf, data.effectVisual);
            writeStringList(buf, data.flyParticle);

            // --- ESCREVENDO OS SONS ---
            if (data.sounds == null) data.sounds = new CosmeticData.CosmeticSounds();
            buf.writeUtf(data.sounds.equipSound != null ? data.sounds.equipSound : "");
            buf.writeUtf(data.sounds.unequipSound != null ? data.sounds.unequipSound : "");
            buf.writeUtf(data.sounds.walkSound != null ? data.sounds.walkSound : "");
            buf.writeUtf(data.sounds.flySound != null ? data.sounds.flySound : "");
            buf.writeUtf(data.sounds.shiftSound != null ? data.sounds.shiftSound : "");
            buf.writeUtf(data.sounds.backpackSound != null ? data.sounds.backpackSound : "");
            buf.writeUtf(data.sounds.idleSound != null ? data.sounds.idleSound : ""); // <--- ESCREVENDO O IDLE SOUND

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
                buf.writeUtf(data.lure.lureTYPE != null ? data.lure.lureTYPE : "");
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
                buf.writeUtf(part.anchor != null ? part.anchor.name() : "HEAD");
                buf.writeUtf(part.customModelData_or_ID != null ? part.customModelData_or_ID : "");
                buf.writeUtf(part.geoModelId != null ? part.geoModelId : "");
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

    private static List<String> readStringList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(buf.readUtf());
        }
        return list;
    }

    private static void writeStringList(FriendlyByteBuf buf, List<String> list) {
        if (list == null) {
            buf.writeVarInt(0);
        } else {
            buf.writeVarInt(list.size());
            for (String s : list) {
                buf.writeUtf(s != null ? s : "");
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}