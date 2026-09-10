package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * S2C — dados de scanner (IVs / nature / ability / size) dos Pokémon num raio do jogador, mandado
 * periodicamente pelo servidor SÓ quando o jogador veste um cosmético com algum scanner ligado.
 * O cliente não tem esses dados de Pokémon selvagem de outro jeito (não vêm no spawn packet nem
 * no DataTracker). Ver {@code ClientPokemonScanCache} + {@code PokemonRendererMixin}.
 *
 * <p>Chave = {@code entity.getId()} (int local do cliente). Cada campo só vem preenchido se o
 * scanner correspondente estiver ativo. O SIZE vem como {@code scaleModifier} cru (float) —
 * a CATEGORIA (XS/S/M/L/XL) é calculada no CLIENT pelo mesmo método do Cobblemon
 * ({@code PokemonSizeCategory.Companion.fromScale}), pra bater com a Pokédex (o cálculo depende
 * de ServerSettings que só existe de verdade no client).
 */
public record SyncPokemonScanPayload(Map<Integer, Entry> byEntityId) implements CustomPayload {

    /** {@code ivs} = null OU length 6 (HP/Atk/Def/SpA/SpD/Spe). Strings "" = campo não escaneado.
     *  {@code hasSize} false = size não escaneado; senão {@code scaleModifier} vale. */
    public record Entry(int[] ivs, String nature, String ability, boolean hasSize, float scaleModifier) {}

    public static final Id<SyncPokemonScanPayload> ID = new Id<>(Identifier.of("greatcosmetics", "sync_pokemon_scan"));

    public static final PacketCodec<RegistryByteBuf, SyncPokemonScanPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeVarInt(payload.byEntityId.size());
                for (Map.Entry<Integer, Entry> e : payload.byEntityId.entrySet()) {
                    buf.writeVarInt(e.getKey());
                    Entry en = e.getValue();
                    int[] iv = en.ivs();
                    boolean hasIvs = iv != null && iv.length >= 6;
                    buf.writeBoolean(hasIvs);
                    if (hasIvs) for (int i = 0; i < 6; i++) buf.writeByte(iv[i]);
                    buf.writeString(en.nature() != null ? en.nature() : "");
                    buf.writeString(en.ability() != null ? en.ability() : "");
                    buf.writeBoolean(en.hasSize());
                    if (en.hasSize()) buf.writeFloat(en.scaleModifier());
                }
            },
            buf -> {
                int n = buf.readVarInt();
                Map<Integer, Entry> map = new HashMap<>(Math.max(4, n * 2));
                for (int k = 0; k < n; k++) {
                    int id = buf.readVarInt();
                    int[] iv = null;
                    if (buf.readBoolean()) {
                        iv = new int[6];
                        for (int i = 0; i < 6; i++) iv[i] = buf.readByte() & 0xFF;
                    }
                    String nature = buf.readString();
                    String ability = buf.readString();
                    boolean hasSize = buf.readBoolean();
                    float scale = hasSize ? buf.readFloat() : 0f;
                    map.put(id, new Entry(iv, nature, ability, hasSize, scale));
                }
                return new SyncPokemonScanPayload(map);
            }
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
