package com.f4xizzz.greatcosmetics.mixin.client;

import com.f4xizzz.greatcosmetics.client.ClientCosmeticCache;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Devolve AR (stack vazia) quando o toggle "esconder capacete/peitoral/pernas/botas" tá ativo pra
 * esse slot — isso é o que faz o hide funcionar até com armadura de OUTROS MODS que têm modelo 3D
 * próprio (ex: asas, chifres, partes extras): esses mods quase sempre continuam lendo
 * {@code getEquippedStack(slot)} pra decidir o que desenhar, mesmo desenhando com um FeatureRenderer
 * totalmente próprio que o ArmorFeatureRendererMixin (que só intercepta a classe vanilla
 * ArmorFeatureRenderer) nunca teria como interceptar. Mentir aqui, na fonte que QUALQUER renderer
 * (vanilla ou de mod) consulta, esconde universalmente sem precisar conhecer o renderer específico
 * de cada mod.
 *
 * Armadura-cosmético (ver ArmorCosmeticsConfig) NÃO passa mais por aqui — ela desenha o item real
 * como ícone flutuante em ArmorFeatureRendererMixin, igual um cosmético fantasma, em vez de tentar
 * ocupar de mentira o slot de armadura de verdade (isso quebrava com mods como GeckoLib, que
 * interceptam a CHAMADA pro renderArmor em vez do método em si).
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityGetEquippedStackMixin {

    @Inject(method = "getEquippedStack", at = @At("HEAD"), cancellable = true)
    private void greatcosmetics$hideVanillaArmor(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) return;

        PlayerEntity self = (PlayerEntity) (Object) this;
        try {
            ClientCosmeticCache.PlayerSettings settings = ClientCosmeticCache.getSettings(self.getUuid());
            boolean shouldHideVanilla = switch (slot) {
                case HEAD -> settings.hideHelmet();
                case CHEST -> settings.hideChestplate();
                case LEGS -> settings.hideLeggings();
                case FEET -> settings.hideBoots();
                default -> false;
            };
            if (shouldHideVanilla) {
                cir.setReturnValue(ItemStack.EMPTY);
            }
        } catch (Exception ignored) {}
    }
}
