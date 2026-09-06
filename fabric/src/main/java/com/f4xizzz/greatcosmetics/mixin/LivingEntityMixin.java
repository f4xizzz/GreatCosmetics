package com.f4xizzz.greatcosmetics.mixin;

import com.f4xizzz.greatcosmetics.GreatCosmetics;
import com.f4xizzz.greatcosmetics.client.ClientCosmeticCache;
import com.f4xizzz.greatcosmetics.config.CosmeticData;
import com.f4xizzz.greatcosmetics.database.DatabaseManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

	// ==========================================
	// 1. O SEU MÉTODO ORIGINAL
	// ==========================================
	@Inject(method = "getEquipmentSlotForItem", at = @At("HEAD"), cancellable = true)
	private void sascosmetics$onGetPreferredEquipmentSlot(ItemStack stack, CallbackInfoReturnable<EquipmentSlot> cir) {
		if (stack.is(Items.CARVED_PUMPKIN)) {
			CustomModelData cmdComp = stack.get(DataComponents.CUSTOM_MODEL_DATA);
			if (cmdComp != null) {
				CosmeticData data = GreatCosmetics.getCosmeticData(cmdComp.value());

				if (data != null && data.render != null) {
					EquipmentSlot eqSlot = switch (data.render) {
						case HEAD -> EquipmentSlot.HEAD;
						case CHEST -> EquipmentSlot.CHEST;
						case LEGS -> EquipmentSlot.LEGS;
						case FEET -> EquipmentSlot.FEET;
					};

					cir.setReturnValue(eqSlot);
					return;
				}
			}
		}
	}

	// ==========================================
	// 2. O MÉTODO DE PONTOS DE ARMADURA
	// ==========================================
	@Inject(method = "getArmorValue", at = @At("RETURN"), cancellable = true)
	private void greatcosmetics$addCosmeticArmor(CallbackInfoReturnable<Integer> cir) {
		if ((Object) this instanceof Player player) {
			// getArmor() roda dos DOIS lados (client pro HUD, server pro cálculo de dano de
			// verdade) — cada lado só tem uma fonte de equipados de verdade populada: o client só
			// tem ClientCosmeticCache (recebido por rede), o server só tem o banco (fonte da
			// verdade). Usar só ClientCosmeticCache (como era antes) fazia esse bônus de armadura
			// nunca aparecer no lado SERVER — ou seja, o bônus era só visual no HUD, nunca reduzia
			// dano de verdade, exatamente o mesmo bug do Auto-Feed abaixo (ver
			// greatcosmetics$autoFeedTick).
			java.util.Collection<String> equipped = player.level().isClientSide()
					? ClientCosmeticCache.getEquipped(player.getUUID())
					: DatabaseManager.getPlayerEquippedCosmetics(player.getUUID());

			if (equipped != null && !equipped.isEmpty()) {
				int extraArmor = 0;
				for (String id : equipped) {
					CosmeticData data = GreatCosmetics.getCosmeticById(id);
					if (data != null) extraArmor += data.armor;
				}

				if (extraArmor > 0) {
					cir.setReturnValue(cir.getReturnValue() + extraArmor);
				}
			}
		}
	}

	// ==========================================
	// 3. NOVO: SISTEMA DE AUTO-FEED
	// ==========================================
	@Inject(method = "tick", at = @At("TAIL"))
	private void greatcosmetics$autoFeedTick(CallbackInfo ci) {
		// Roda apenas no SERVIDOR para players de verdade!
		if ((Object) this instanceof Player player && !player.level().isClientSide()) {

			// Verifica a cada 20 ticks (1 segundo) para não explodir o desempenho do server
			if (player.tickCount % 20 == 0) {
				// Esse método já roda só no SERVER (ver checagem acima) — ClientCosmeticCache é só
				// populado no client via rede, então aqui ele estava SEMPRE vazio num servidor
				// dedicado de verdade, e hasAutoFeed nunca virava true. DatabaseManager é a fonte
				// de verdade do lado do servidor (mesma usada no tick loop principal em
				// GreatCosmetics.java).
				java.util.List<String> equipped = DatabaseManager.getPlayerEquippedCosmetics(player.getUUID());
				if (equipped == null || equipped.isEmpty()) return;

				boolean hasAutoFeed = false;
				for (String id : equipped) {
					CosmeticData data = GreatCosmetics.getCosmeticById(id);
					if (data != null && data.AutoFeed) {
						hasAutoFeed = true;
						break;
					}
				}

				if (hasAutoFeed) {
					FoodData hunger = player.getFoodData();
					if (hunger.needsFood()) {
						int currentMissing = 20 - hunger.getFoodLevel();

						ItemStack foodToEat = ItemStack.EMPTY;
						int slotToConsume = -1;

						// 1. TENTA A OFFHAND PRIMEIRO (Mão Esquerda)
						ItemStack offhand = player.getOffhandItem();
						FoodProperties offFood = offhand.get(DataComponents.FOOD);
						// Checa se é comida e se não vai desperdiçar saturação à toa
						if (offFood != null && offFood.nutrition() <= currentMissing) {
							foodToEat = offhand;
							slotToConsume = -2; // -2 sinaliza que é a OffHand
						}
						else {
							// 2. PROCURA NO INVENTÁRIO (A que der mais fome, sem desperdiçar)
							int bestNutrition = -1;
							Inventory inv = player.getInventory();
							for (int i = 0; i < inv.items.size(); i++) {
								ItemStack stack = inv.items.get(i);
								FoodProperties food = stack.get(DataComponents.FOOD);

								if (food != null) {
									int nutrition = food.nutrition();
									// Pega a comida que supre mais a fome, sem passar do limite
									if (nutrition <= currentMissing && nutrition > bestNutrition) {
										bestNutrition = nutrition;
										foodToEat = stack;
										slotToConsume = i;
									}
								}
							}
						}

						// 3. COME O ITEM SE ACHOU ALGUM
						if (!foodToEat.isEmpty()) {
							FoodProperties foodComp = foodToEat.get(DataComponents.FOOD);

							// A função eatFood nativa do Minecraft 1.21.1 executa o som, as partículas,
							// aplica a fome, gasta o item e devolve restos (como Tigelas Vazias de sopa)
							ItemStack leftover = player.eat(player.level(), foodToEat, foodComp);

							if (slotToConsume == -2) {
								player.setItemInHand(InteractionHand.OFF_HAND, leftover);
							} else {
								player.getInventory().setItem(slotToConsume, leftover);
							}
						}
					}
				}
			}
		}
	}
}