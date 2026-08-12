package com.f4xizzz.greatcosmetics.mixin.client;

import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
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
            MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (!(client.currentScreen instanceof Wardrobe3DScreen)) return;
            if (entity == client.player) return;

            String className = entity.getClass().getName().toLowerCase();
            if (className.contains("easynpc")) return;
            if (entity instanceof ArmorStandEntity) return;

            // Pokémon selvagens (sem dono) e o Pokémon do PRÓPRIO jogador continuam visíveis —
            // só escondemos Pokémon de OUTROS jogadores, pra manter o estúdio isolado de outras
            // pessoas sem esconder a vida selvagem do mundo nem o próprio Pokémon do jogador.
            if (className.contains("pokemonentity")) {
                java.util.UUID ownerUuid = greatcosmetics$tryGetPokemonOwnerUuid(entity);
                if (ownerUuid == null || ownerUuid.equals(client.player.getUuid())) return;
                ci.cancel();
                return;
            }

            if (entity instanceof PlayerEntity || entity instanceof MobEntity) {
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
