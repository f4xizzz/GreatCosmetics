package com.f4xizzz.greatcosmetics.mixin.client;

import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enquanto o jogador está dentro do Wardrobe (Wardrobe3DScreen), esconde qualquer entidade viva
 * que não seja ele mesmo — Pokémon selvagens/de outros jogadores, outros jogadores e mobs — pra
 * manter o "estúdio" isolado visualmente, sem quebrar decorações intencionais do studio
 * (NPCs do EasyNPC e Armor Stands continuam visíveis). Checa NPC do EasyNPC/Pokémon do Cobblemon
 * pelo NOME da classe (em vez de instanceof) de propósito — o GreatCosmetics não depende mais do
 * EasyNPC/Cobblemon em tempo de compilação (esses mods viraram o EasyNPCobblemonIntegration
 * separado), mas esse comportamento continua funcionando igual se eles estiverem instalados.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void greatcosmetics$hideEntitiesInWardrobe(
            E entity, double x, double y, double z, float yaw, float tickDelta,
            PoseStack matrices, MultiBufferSource vertexConsumers, int light, CallbackInfo ci) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (!(client.screen instanceof Wardrobe3DScreen)) return;
            if (entity == client.player) return;

            String className = entity.getClass().getName().toLowerCase();
            if (className.contains("easynpc")) return;
            if (entity instanceof ArmorStand) return;

            // Pokémon selvagens (sem dono) e o Pokémon do PRÓPRIO jogador continuam visíveis —
            // só escondemos Pokémon de OUTROS jogadores, pra manter o estúdio isolado de outras
            // pessoas sem esconder a vida selvagem do mundo nem o próprio Pokémon do jogador.
            if (className.contains("pokemonentity")) {
                java.util.UUID ownerUuid = greatcosmetics$tryGetPokemonOwnerUuid(entity);
                if (ownerUuid == null || ownerUuid.equals(client.player.getUUID())) return;
                ci.cancel();
                return;
            }

            if (entity instanceof Player || entity instanceof Mob) {
                ci.cancel();
            }
        } catch (Exception ignored) {}
    }

    /** Reflection pura pra evitar depender do Cobblemon em tempo de compilação: equivale a
     *  {@code ((PokemonEntity) entity).getPokemon().getOwnerUUID()}. */
    private static java.util.UUID greatcosmetics$tryGetPokemonOwnerUuid(Entity entity) {
        try {
            Object pokemon = entity.getClass().getMethod("getPokemon").invoke(entity);
            if (pokemon == null) return null;
            return (java.util.UUID) pokemon.getClass().getMethod("getOwnerUUID").invoke(pokemon);
        } catch (Exception e) {
            return null;
        }
    }
}
