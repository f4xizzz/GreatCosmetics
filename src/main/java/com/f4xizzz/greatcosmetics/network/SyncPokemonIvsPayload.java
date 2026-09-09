package com.f4xizzz.greatcosmetics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * S2C — IVs (6 stats, ordem HP/Atk/Def/SpA/SpD/Spe) dos Pokémon num raio do jogador, mandado
 * periodicamente pelo servidor SÓ quando o jogador veste um cosmético com {@code ivScanner}.
 * O cliente não tem os IVs de Pokémon selvagem de outro jeito (não vêm no spawn packet nem no
 * DataTracker). Ver {@code ClientPokemonIvCache} + {@code PokemonNameTagMixin}.
 *
 * <p>Chave = {@code entity.getId()} (int local do cliente). IVs 0-31 cabem num byte.
 */
public record SyncPokemonIvsPayload(Map<Integer, int[]> ivsByEntityId) implements CustomPayload {

    public static final Id<SyncPokemonIvsPayload> ID = new Id<>(Identifier.of("greatcosmetics", "sync_pokemon_ivs"));

    public static final PacketCodec<RegistryByteBuf, SyncPokemonIvsPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeVarInt(payload.ivsByEntityId.size());
                for (Map.Entry<Integer, int[]> e : payload.ivsByEntityId.entrySet()) {
                    buf.writeVarInt(e.getKey());
                    int[] iv = e.getValue();
                    for (int i = 0; i < 6; i++) buf.writeByte(i < iv.length ? iv[i] : 0);
                }
            },
            buf -> {
                int n = buf.readVarInt();
                Map<Integer, int[]> map = new HashMap<>(Math.max(4, n * 2));
                for (int k = 0; k < n; k++) {
                    int id = buf.readVarInt();
                    int[] iv = new int[6];
                    for (int i = 0; i < 6; i++) iv[i] = buf.readByte() & 0xFF;
                    map.put(id, iv);
                }
                return new SyncPokemonIvsPayload(map);
            }
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
