package com.f4xizzz.greatcosmetics.mixin.client;

import com.f4xizzz.greatcosmetics.GreatCosmetics;
import com.f4xizzz.greatcosmetics.client.ArmorCosmeticResolver;
import com.f4xizzz.greatcosmetics.client.ClientCosmeticCache;
import com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager;
import com.f4xizzz.greatcosmetics.config.CosmeticData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin<T extends LivingEntity, M extends BipedEntityModel<T>, A extends BipedEntityModel<T>> extends FeatureRenderer<T, M> {

	public ArmorFeatureRendererMixin(FeatureRendererContext<T, M> context) {
		super(context);
	}

	// Membros PRIVADOS do ArmorFeatureRenderer vanilla que a gente precisa (ver
	// greatcosmetics$renderRealArmor) — o resto do algoritmo de render de armadura de verdade
	// (copyBipedStateTo/setVisible/render do BipedEntityModel, RenderLayer.getArmorCutoutNoCull)
	// é tudo público. "outerModel" serve pra HEAD/CHEST/FEET; "innerModel" (camada fina) é só de
	// LEGS — igual o vanilla (usesInnerModel(slot) == slot==LEGS).
	@Shadow private A outerModel;
	@Shadow private A innerModel;

	// Evita spammar o console com a mesma linha todo frame — ver uso abaixo.
	private static final Set<String> greatcosmetics$loggedMissingIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private static long greatcosmetics$lastPreviewLogMs = 0L;

	/** true no máx 1x/segundo — pra o log de diagnóstico do preview de variante não inundar. */
	private static boolean greatcosmetics$devPreviewLogTick() {
		long now = System.currentTimeMillis();
		if (now - greatcosmetics$lastPreviewLogMs < 1000L) return false;
		greatcosmetics$lastPreviewLogMs = now;
		return true;
	}

	/** Desenha uma armadura-cosmético (ver ArmorCosmeticsConfig) que representa um ArmorItem de
	 *  VERDADE (qualquer slot — HEAD/CHEST/LEGS/FEET) usando o MESMO algoritmo que o Minecraft usa
	 *  pra armadura realmente equipada — modelo 3D biped (peça de verdade, não um ícone chapado) +
	 *  a(s) textura(s) da camada do material — em vez do caminho genérico de ícone
	 *  (itemRenderer.renderItem com ModelTransformationMode.HEAD) que o resto deste mixin usa.
	 *
	 *  Replicado a partir do bytecode real de ArmorFeatureRenderer#renderArmor + #setVisible +
	 *  #usesInnerModel (decompilado do jar vanilla via javap): copyBipedStateTo (copia a pose do
	 *  corpo pro modelo de armadura) -> setVisible(false) + liga só as partes do slot (head/hat pra
	 *  HEAD, body+braços pra CHEST, body+pernas pra LEGS, pernas pra FEET) -> escolhe innerModel só
	 *  pra LEGS -> pra cada Layer do ArmorMaterial resolve a textura (com tint de dye) e desenha
	 *  via RenderLayer.getArmorCutoutNoCull -> brilho de encantamento se tiver. NÃO replica os
	 *  trims (ArmorTrim — precisaria de outro campo privado, e é raro num cosmético).
	 *
	 *  Offset/Rotação/Escala da Part são aplicados no modelo INTEIRO (em model space, ~1.0 = 1
	 *  bloco), ANTES do render — padrão 0/0/0 + 1/1/1 = encaixe exato do vanilla. Na prática só o
	 *  Offset costuma ser útil aqui (rotacionar/escalar um biped inteiro gira em torno dos pés);
	 *  quem quer controle fino de verdade usa um GeckoLib Model ID, que cai no caminho geo. */
	private void greatcosmetics$renderRealArmor(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
			M contextModel, ItemStack stack, ArmorItem armorItem, CosmeticData.CosmeticPart part, boolean sneaking) {
		EquipmentSlot armorSlot = armorItem.getSlotType();
		boolean usesInner = armorSlot == EquipmentSlot.LEGS;
		A armorModel = usesInner ? this.innerModel : this.outerModel;

		contextModel.copyBipedStateTo(armorModel);
		armorModel.setVisible(false);
		// Mostra SÓ o osso da âncora desta Part (BODY = torso, RIGHT_ARM = manga direita, etc). Com
		// o "split into body parts" no Dev Studio, um peitoral vira 3 Parts (BODY/RIGHT_ARM/LEFT_ARM),
		// cada uma posicionável sozinha. Uma Part com âncora que não bate com o modelo da peça
		// (ex: capacete com âncora BODY) simplesmente não desenha nada.
		switch (part.anchor) {
			case HEAD -> { armorModel.head.visible = true; armorModel.hat.visible = true; }
			case BODY -> armorModel.body.visible = true;
			case RIGHT_ARM -> armorModel.rightArm.visible = true;
			case LEFT_ARM -> armorModel.leftArm.visible = true;
			case RIGHT_LEG -> armorModel.rightLeg.visible = true;
			case LEFT_LEG -> armorModel.leftLeg.visible = true;
			default -> { return; }
		}

		// Ajuste fino opcional (padrão = sem efeito). translate -> rotation -> scale, model space.
		matrices.translate(part.offsetX, part.offsetY, part.offsetZ);
		if (part.rotationX != 0.0F) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(part.rotationX));
		if (part.rotationY != 0.0F) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(part.rotationY));
		if (part.rotationZ != 0.0F) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(part.rotationZ));
		if (sneaking) {
			matrices.translate(part.shiftOffsetX, part.shiftOffsetY, part.shiftOffsetZ);
			if (part.shiftRotationX != 0.0F) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(part.shiftRotationX));
			if (part.shiftRotationY != 0.0F) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(part.shiftRotationY));
			if (part.shiftRotationZ != 0.0F) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(part.shiftRotationZ));
		}
		float sX = part.scaleX == 0.0F ? 1.0F : part.scaleX;
		float sY = part.scaleY == 0.0F ? 1.0F : part.scaleY;
		float sZ = part.scaleZ == 0.0F ? 1.0F : part.scaleZ;
		if (sX != 1.0F || sY != 1.0F || sZ != 1.0F) matrices.scale(sX, sY, sZ);

		ArmorMaterial material = armorItem.getMaterial().value();

		// -6265536 = ArmorFeatureRenderer.DEFAULT_LEATHER_COLOR de verdade (vanilla usa esse
		// literal como fallback quando o item é dyeable mas não tem cor customizada setada).
		int dyeColor = stack.isIn(ItemTags.DYEABLE)
				? ColorHelper.Argb.fullAlpha(DyedColorComponent.getColor(stack, -6265536))
				: -1;

		for (ArmorMaterial.Layer layer : material.layers()) {
			int color = layer.isDyeable() ? dyeColor : -1;
			Identifier texture = layer.getTexture(usesInner);
			VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getArmorCutoutNoCull(texture));
			armorModel.render(matrices, vc, light, net.minecraft.client.render.OverlayTexture.DEFAULT_UV, color);
		}

		if (stack.hasGlint()) {
			VertexConsumer glintVc = vertexConsumers.getBuffer(RenderLayer.getArmorEntityGlint());
			armorModel.render(matrices, glintVc, light, net.minecraft.client.render.OverlayTexture.DEFAULT_UV);
		}
	}

	/** Desenha as setas/anéis do gizmo 3D numa origem já resolvida ({@code pos}) — versão compacta
	 *  pro caminho de ARMADURA-COSMÉTICO (o caminho de ícone tem o seu próprio bloco inline com
	 *  toda a compensação de transform "head" do item). {@code entry} só é usado pras normais. */
	private void greatcosmetics$drawPartGizmoAt(VertexConsumerProvider vertexConsumers, MatrixStack.Entry entry, Matrix4f pos) {
		VertexConsumer vc = vertexConsumers.getBuffer(GizmoManager.GIZMO_LINES_NO_DEPTH);
		float size = GizmoManager.AXIS_WORLD_LENGTH;
		if (GizmoManager.currentMode == GizmoManager.Mode.ROTATE) {
			boolean anyDragging = GizmoManager.isDragging;
			greatcosmetics$drawGizmoRing(vc, pos, entry, GizmoManager.Axis.X, size, 255, 60, 60, !anyDragging || GizmoManager.currentAxis == GizmoManager.Axis.X);
			greatcosmetics$drawGizmoRing(vc, pos, entry, GizmoManager.Axis.Y, size, 60, 255, 60, !anyDragging || GizmoManager.currentAxis == GizmoManager.Axis.Y);
			greatcosmetics$drawGizmoRing(vc, pos, entry, GizmoManager.Axis.Z, size, 70, 130, 255, !anyDragging || GizmoManager.currentAxis == GizmoManager.Axis.Z);
		} else {
			vc.vertex(pos, 0f, 0f, 0f).color(255, 0, 0, 255).normal(entry, 1f, 0f, 0f);
			vc.vertex(pos, size, 0f, 0f).color(255, 0, 0, 255).normal(entry, 1f, 0f, 0f);
			vc.vertex(pos, 0f, 0f, 0f).color(0, 255, 0, 255).normal(entry, 0f, 1f, 0f);
			vc.vertex(pos, 0f, size, 0f).color(0, 255, 0, 255).normal(entry, 0f, 1f, 0f);
			vc.vertex(pos, 0f, 0f, 0f).color(0, 0, 255, 255).normal(entry, 0f, 0f, 1f);
			vc.vertex(pos, 0f, 0f, size).color(0, 0, 255, 255).normal(entry, 0f, 0f, 1f);
		}
		Vector3f hoverPoint = GizmoManager.getHoverLocalPoint();
		if (hoverPoint != null) {
			int[] hc = GizmoManager.isDragging ? greatcosmetics$axisColor(GizmoManager.currentAxis) : new int[]{200, 200, 200};
			greatcosmetics$drawHoverDot(vc, pos, entry, hoverPoint, hc[0], hc[1], hc[2]);
		}
		GizmoManager.updateScreenProjection(pos);
	}

	/** true se {@code slot} é um dos 4 slots de armadura vestível (HEAD/CHEST/LEGS/FEET). */
	private static boolean greatcosmetics$isWearableArmorSlot(EquipmentSlot slot) {
		return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST
				|| slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
	}

	@Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
	private void greatcosmetics$onRenderArmor(MatrixStack matrices, VertexConsumerProvider vertexConsumers, T entity, EquipmentSlot slot, int light, A model, CallbackInfo ci) {
		// NÃO precisa mais resetar RenderSystem.setShaderColor aqui — a transparência do corpo do
		// jogador (ver LivingEntityRendererMixin#greatcosmetics$applyBodyAlpha) agora bakeia o
		// alpha direto no argumento "color" da chamada EntityModel.render() do CORPO, sem tocar no
		// shader global nem no VertexConsumerProvider — armadura/cosméticos (aqui) são um caminho
		// de código totalmente separado e nunca são afetados, então não tem nada pra proteger.

		if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) return;

		// Setting local "esconder cosmético dos outros jogadores" (performance) — só o PRÓPRIO
		// jogador continua com cosmético (e o preview do Dev Studio). Ver ClientLocalSettings.
		if (ClientCosmeticCache.hideOtherPlayersCosmetics
				&& !ArmorCosmeticResolver.isDevPreview(entity)
				&& (net.minecraft.client.MinecraftClient.getInstance().player == null
					|| !entity.getUuid().equals(net.minecraft.client.MinecraftClient.getInstance().player.getUuid()))) {
			return;
		}

		ClientCosmeticCache.PlayerSettings settings = ClientCosmeticCache.getSettings(entity.getUuid());

		boolean shouldHideVanilla = switch (slot) {
			case HEAD -> settings.hideHelmet();
			case CHEST -> settings.hideChestplate();
			case LEGS -> settings.hideLeggings();
			case FEET -> settings.hideBoots();
			default -> false;
		};

		boolean isDevPreview = ArmorCosmeticResolver.isDevPreview(entity);

		Set<String> equippedIds;
		if (isDevPreview) {
			String greatcosmetics$prev = com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen.previewCosmeticId;
			String greatcosmetics$prevVar = com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen.previewVariantId;
			if (greatcosmetics$prev == null || greatcosmetics$prev.isEmpty()) {
				equippedIds = Set.of();
			} else if (greatcosmetics$prevVar != null && !greatcosmetics$prevVar.isEmpty()) {
				// Editor de variante: mostra as parts DAQUELA variante (gizmo + preview ao vivo).
				equippedIds = Set.of(greatcosmetics$prev + "#" + greatcosmetics$prevVar);
			} else {
				equippedIds = Set.of(greatcosmetics$prev);
			}
		} else {
			equippedIds = ClientCosmeticCache.getEquipped(entity.getUuid());
		}

		if (shouldHideVanilla) {
			ci.cancel();
		}

		// TUDO desenha num ÚNICO passe fixo — sempre no HEAD, que roda pra todo mundo — em vez de
		// cada cosmético escolher um dos 4 passes (HEAD/CHEST/LEGS/FEET). Isso existia antes como
		// "render" configurável, mas virou sem querer um filtro de EXCLUSIVIDADE: dois cosméticos
		// com o mesmo "render" competiam pelo mesmo passe e só um aparecia. Usando sempre o mesmo
		// passe único, todo mundo desenha exatamente uma vez, sem disputa nenhuma.
		if (slot != EquipmentSlot.HEAD) return;

		// GIZMO 3D — EFFECTS (DevEffectsSubPage): o MESMO gizmo de setas do editor de cosméticos,
		// só que só TRANSLATE (Effect não tem rotação/escala — ver GizmoManager#activeEffect).
		// Roda ANTES do early-return de equippedIds vazio de propósito: editar um Effect não exige
		// nenhum cosmético equipado. "matrices" aqui ainda é a "model space" pristina (origem nos
		// pés, espelhada, já rotacionada pro bodyYaw) recebida do ArmorFeatureRenderer — NÃO é a
		// mesma convenção que EffectData.offsetX/Y/Z usa de verdade (ver
		// DevEffectsSubPage#spawnPreviewParticles/GreatCosmetics#spawnCosmeticParticle, que
		// calculam em espaço de MUNDO). BUG (2026-09): sem compensar esse espelhamento aqui, a
		// seta desenhada e o clique nela ficavam consistentes ENTRE SI, mas invertidos em relação
		// a onde a partícula de verdade aparece. Testado ao vivo pelo usuário: com só X invertido
		// (primeira tentativa), Y e Z continuavam invertidos — ou seja, os TRÊS eixos precisam do
		// sinal trocado aqui, não só X (a hipótese de "só esquerda/direita espelha" estava errada;
		// aparentemente esse espaço inverte os três). A outra metade da correção (o delta do
		// arraste) está em GizmoManager#handleDrag.
		if (isDevPreview && GizmoManager.activeEffect != null) {
			var eff = GizmoManager.activeEffect;
			matrices.push();
			matrices.translate((float) -eff.offsetX, (float) -eff.offsetY, (float) -eff.offsetZ);

			VertexConsumer effVc = vertexConsumers.getBuffer(GizmoManager.GIZMO_LINES_NO_DEPTH);
			MatrixStack.Entry effEntry = matrices.peek();
			Matrix4f effPos = effEntry.getPositionMatrix();
			float effSize = GizmoManager.AXIS_WORLD_LENGTH;

			effVc.vertex(effPos, 0.0f, 0.0f, 0.0f).color(255, 0, 0, 255).normal(effEntry, 1.0f, 0.0f, 0.0f);
			effVc.vertex(effPos, effSize, 0.0f, 0.0f).color(255, 0, 0, 255).normal(effEntry, 1.0f, 0.0f, 0.0f);
			effVc.vertex(effPos, 0.0f, 0.0f, 0.0f).color(0, 255, 0, 255).normal(effEntry, 0.0f, 1.0f, 0.0f);
			effVc.vertex(effPos, 0.0f, effSize, 0.0f).color(0, 255, 0, 255).normal(effEntry, 0.0f, 1.0f, 0.0f);
			effVc.vertex(effPos, 0.0f, 0.0f, 0.0f).color(0, 0, 255, 255).normal(effEntry, 0.0f, 0.0f, 1.0f);
			effVc.vertex(effPos, 0.0f, 0.0f, effSize).color(0, 0, 255, 255).normal(effEntry, 0.0f, 0.0f, 1.0f);

			Vector3f effHover = GizmoManager.getHoverLocalPoint();
			if (effHover != null) {
				int[] hoverColor = GizmoManager.isDragging
						? greatcosmetics$axisColor(GizmoManager.currentAxis)
						: new int[]{200, 200, 200};
				greatcosmetics$drawHoverDot(effVc, effPos, effEntry, effHover, hoverColor[0], hoverColor[1], hoverColor[2]);
			}

			GizmoManager.updateScreenProjection(effPos);
			matrices.pop();
		}

		if (equippedIds == null || equippedIds.isEmpty()) return;

		var itemRenderer = net.minecraft.client.MinecraftClient.getInstance().getItemRenderer();
		M contextModel = this.getContextModel();

		for (String id : equippedIds) {
			String greatcosmetics$variantId = com.f4xizzz.greatcosmetics.util.EquippedCosmeticId.variant(id);
			CosmeticData data = GreatCosmetics.getCosmeticById(id);
			if (data == null) {
				// getCosmeticById() roda TODO FRAME por cosmético equipado — sem o "log só uma vez",
				// habilitar /gc debug com um cosmético fantasma equipado (id que sumiu do catálogo)
				// inundava o console com a mesma linha centenas de vezes por segundo.
				if (greatcosmetics$loggedMissingIds.add(id)) {
					com.f4xizzz.greatcosmetics.GreatCosmeticsClient.debugLog("ArmorFeatureRendererMixin: equipped cosmetic '" + id + "' does NOT exist in the client catalog — will not render.");
				}
				continue;
			}
			// Variante equipada ("baseId#variantId") → desenha as parts DELA, não as base.
			java.util.List<CosmeticData.CosmeticPart> greatcosmetics$partsToDraw = data.parts;
			if (!greatcosmetics$variantId.isEmpty()) {
				CosmeticData.CosmeticVariant greatcosmetics$v = data.findVariant(greatcosmetics$variantId).orElse(null);
				if (greatcosmetics$v != null && greatcosmetics$v.parts != null && !greatcosmetics$v.parts.isEmpty())
					greatcosmetics$partsToDraw = greatcosmetics$v.parts;
				if (isDevPreview && greatcosmetics$devPreviewLogTick()) {
					com.f4xizzz.greatcosmetics.GreatCosmeticsClient.debugLog(
							"[variant preview] id='" + id + "' variantId='" + greatcosmetics$variantId
							+ "' variantFound=" + (greatcosmetics$v != null)
							+ " variantParts=" + (greatcosmetics$v != null && greatcosmetics$v.parts != null ? greatcosmetics$v.parts.size() : -1)
							+ " usingVariantParts=" + (greatcosmetics$partsToDraw != data.parts)
							+ " activePart=" + System.identityHashCode(GizmoManager.activePart)
							+ " part0=" + (greatcosmetics$partsToDraw.isEmpty() ? "none" : System.identityHashCode(greatcosmetics$partsToDraw.get(0)))
							+ " match=" + (!greatcosmetics$partsToDraw.isEmpty() && GizmoManager.activePart == greatcosmetics$partsToDraw.get(0)));
				}
			}
			if (greatcosmetics$partsToDraw == null || greatcosmetics$partsToDraw.isEmpty()) {
				if (greatcosmetics$loggedMissingIds.add(id + "#noparts")) {
					com.f4xizzz.greatcosmetics.GreatCosmeticsClient.debugLog("ArmorFeatureRendererMixin: cosmetic '" + id + "' has no configured Part — nothing to draw.");
				}
				continue;
			}

			if (!isDevPreview && greatcosmetics$isAccessoryHidden(settings, data)) continue;

			// ARMADURA CONVERTIDA EM COSMÉTICO (ver ArmorCosmeticsConfig): em vez de tentar
			// desenhar via ArmorFeatureRenderer/getEquippedStack (que só cabe UM item por slot de
			// verdade, e alguns mods — ex: GeckoLib — interceptam a CHAMADA pro renderArmor, não o
			// método em si, então redesenhar manualmente não funciona pra eles), desenha o ITEM
			// REAL como ícone, exatamente igual um cosmético fantasma, só trocando o carved_pumpkin
			// pelo ItemStack de verdade. Isso funciona sempre — vanilla, GeckoLib, qualquer mod —
			// e ganha de graça offset/rotação/escala configuráveis por parte.
			ItemStack realStack = null;
			if (data.realItemId != null) {
				Identifier realId = Identifier.tryParse(data.realItemId);
				Item realItem = realId != null ? Registries.ITEM.get(realId) : null;
				if (realItem == null || realItem == Items.AIR) {
					if (greatcosmetics$loggedMissingIds.add(id + "#realitem")) {
						com.f4xizzz.greatcosmetics.GreatCosmeticsClient.debugLog("ArmorFeatureRendererMixin: cosmetic '" + id + "' has realItemId='" + data.realItemId + "' invalid/AIR — skipping render.");
					}
					continue;
				}
				realStack = new ItemStack(realItem);
			}

			for (CosmeticData.CosmeticPart part : greatcosmetics$partsToDraw) {
				matrices.push();

				// Resolvido bem no início (antes só vinha depois) — o bloco de prévia do gizmo (mais
				// abaixo) precisa saber se essa Part é GeckoLib, não só na hora de escolher o item
				// pra renderizar de verdade.
				int modelToRender = part.resolvedCmd != 0 ? part.resolvedCmd : data.cmd;
				boolean isGecko = com.f4xizzz.greatcosmetics.geckolib.GeoModelRegistry.has(modelToRender);

				// ARMADURA-COSMÉTICO: desenha o MODELO 3D de armadura vestido (não o ícone chapado),
				// mas agora POR ÂNCORA (BODY = torso, RIGHT_ARM = manga direita, etc — ver
				// applyArmorAnchorLayout no Dev Studio, "split into body parts") e COM o gizmo 3D.
				// GeckoLib continua com prioridade (cai no caminho geo mais abaixo).
				if (!isGecko && realStack != null && realStack.getItem() instanceof ArmorItem realArmorItem) {
					// gizmo (só no preview do Dev Studio) — capturado ANTES do renderRealArmor mexer na matriz.
					if (isDevPreview && GizmoManager.activePart == part) {
						Matrix4f gzBasis = new Matrix4f(matrices.peek().getPositionMatrix());
						matrices.push();
						greatcosmetics$renderRealArmor(matrices, vertexConsumers, light, contextModel, realStack, realArmorItem, part, entity.isInSneakingPose());
						Vector3f gzOrigin = matrices.peek().getPositionMatrix().getTranslation(new Vector3f());
						matrices.pop();
						greatcosmetics$drawPartGizmoAt(vertexConsumers, matrices.peek(), new Matrix4f(gzBasis).setTranslation(gzOrigin));
					} else {
						matrices.push();
						greatcosmetics$renderRealArmor(matrices, vertexConsumers, light, contextModel, realStack, realArmorItem, part, entity.isInSneakingPose());
						matrices.pop();
					}
					matrices.pop();
					continue;
				}

				switch (part.anchor) {
					case HEAD -> contextModel.head.rotate(matrices);
					case BODY -> contextModel.body.rotate(matrices);
					case RIGHT_ARM -> { contextModel.rightArm.rotate(matrices); matrices.translate(0.3125F, -0.125F, 0.0F); }
					case LEFT_ARM -> { contextModel.leftArm.rotate(matrices); matrices.translate(-0.3125F, -0.125F, 0.0F); }
					case RIGHT_LEG -> { contextModel.rightLeg.rotate(matrices); matrices.translate(0.125F, -0.75F, 0.0F); }
					case LEFT_LEG -> { contextModel.leftLeg.rotate(matrices); matrices.translate(-0.125F, -0.75F, 0.0F); }
				}

				matrices.translate(part.offsetX, part.offsetY, part.offsetZ);
				if (part.rotationX != 0.0F) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(part.rotationX));
				if (part.rotationY != 0.0F) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(part.rotationY));
				if (part.rotationZ != 0.0F) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(part.rotationZ));

				if (entity.isInSneakingPose()) {
					matrices.translate(part.shiftOffsetX, part.shiftOffsetY, part.shiftOffsetZ);
					if (part.shiftRotationX != 0.0F) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(part.shiftRotationX));
					if (part.shiftRotationY != 0.0F) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(part.shiftRotationY));
					if (part.shiftRotationZ != 0.0F) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(part.shiftRotationZ));
				}

				// ==========================================
				// PROTEÇÃO ANTI-GSON (Garante que a escala nunca seja ZERO)
				// ==========================================
				float sX = part.scaleX == 0.0F ? 1.0F : part.scaleX;
				float sY = part.scaleY == 0.0F ? 1.0F : part.scaleY;
				float sZ = part.scaleZ == 0.0F ? 1.0F : part.scaleZ;

				ItemStack stackToRender;
				if (com.f4xizzz.greatcosmetics.geckolib.GeoModelRegistry.has(modelToRender)) {
					// Modelo 3D de verdade via GeckoLib (ver BuiltinGeoModels/GeoModelRegistry) —
					// tem prioridade sobre o item real E sobre o ícone chapado: é exatamente pra
					// isso que serve, mostrar a geometria de verdade em vez de aproximação.
					stackToRender = new ItemStack(com.f4xizzz.greatcosmetics.geckolib.GreatCosmeticsItems.GEO_DISPLAY);
					stackToRender.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(modelToRender));
				} else if (realStack != null) {
					stackToRender = realStack;
				} else {
					stackToRender = new ItemStack(Items.CARVED_PUMPKIN);
					stackToRender.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(modelToRender));
				}

				// ==========================================
				// GIZMO 3D: RENDERIZADOR DE SETAS GUIA!
				// ==========================================
				if (isDevPreview && GizmoManager.activePart == part) {
					// O ícone final NÃO fica na posição "lógica" (âncora+offset) — o
					// itemRenderer.renderItem(..., ModelTransformationMode.HEAD, ...) logo abaixo
					// aplica, por dentro, o transform "head" do PRÓPRIO modelo do item (o mesmo
					// mecanismo que faz uma abóbora "sentar" na cabeça — cada modelo tem o seu,
					// definido no JSON dele). Sem replicar isso aqui o gizmo desenhava longe do
					// ícone de verdade. matrices.push()/pop() isola essa "prévia" — não afeta em
					// nada o render de verdade que roda de novo, do zero, logo abaixo.

					// Base de ORIENTAÇÃO do gizmo: a matriz current ANTES da cadeia de espelhamento
					// abaixo (rotationDegrees(180, Y) + scale(0.625,-0.625,-0.625) inverte X e Y
					// líquido — Z é invertido duas vezes, cancela). Usar a matriz JÁ espelhada pra
					// desenhar as setas/testar o clique (como era antes) fazia arrastar uma seta
					// mover o cosmético pro lado OPOSTO de onde ela aponta, sempre — bug reportado.
					// Guardando a orientação de ANTES do espelho e só pegando a ORIGEM (translação)
					// da cadeia espelhada abaixo (pra continuar alinhada com o ícone renderizado),
					// os eixos do gizmo voltam a bater com offsetX/Y/Z de verdade.
					Matrix4f orientationBasis = new Matrix4f(matrices.peek().getPositionMatrix());

					matrices.push();
					if (isGecko) {
						// GeckoLib: ler as matrizes internas do próprio GeoItemRenderer (tentado
						// antes) piorou mais do que ajudou — o pipeline dele (auto-escala pro
						// bounding box do .geo + transforms por osso) não dá pra aproximar direito
						// só com essas duas matrizes. Constatado em jogo: X e Z já ficavam bem
						// alinhados com a posição "lógica" pura (sem NENHUM ajuste) — só o Y ficava
						// baixo demais. Em vez de tentar replicar o pipeline inteiro, um empurrão
						// manual no Y só pra esse caso, ajustável ao vivo (barrinha lateral, ver
						// GizmoDevConfig) em vez de um número fixo no código.
						matrices.translate(0.0F, com.f4xizzz.greatcosmetics.client.GizmoDevConfig.geoGizmoYOffset, 0.0F);
					}

					// translate ANTES do scale(sX,sY,sZ) — não o contrário: esse -0.25 é um ajuste FIXO
					// de onde o ícone "senta" (mesma ideia do offset de cabeça vanilla), não algo que
					// deveria crescer/encolher junto com a escala que o usuário está ajustando. Com
					// scale DEPOIS, escalar Y multiplicava esse -0.25 junto, fazendo o ícone subir/
					// descer sozinho toda vez que só a escala Y mudava — parecia o offsetY mudando
					// sem ninguém mexer nele.
					matrices.translate(0.0F, -0.25F, 0.0F);
					matrices.scale(sX, sY, sZ);
					matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
					matrices.scale(0.625F, -0.625F, -0.625F);

					if (!isGecko) {
						try {
							var bakedModel = itemRenderer.getModel(stackToRender, entity.getWorld(), entity, entity.getId());
							if (bakedModel != null && bakedModel.getTransformation() != null) {
								bakedModel.getTransformation().head.apply(false, matrices);
							}
						} catch (Exception ignored) {
							// Modelo dinâmico pode não ter uma ModelTransformation "head" de verdade —
							// melhor deixar o gizmo na posição lógica de antes do que travar o render.
						}
					}

					// GIZMO_LINES_NO_DEPTH (não RenderLayer.getLines()) — dá pra ver o gizmo através de
					// blocos/paredes, útil quando o offset da part empurra ela pra dentro de algo.
					VertexConsumer vc = vertexConsumers.getBuffer(GizmoManager.GIZMO_LINES_NO_DEPTH);
					MatrixStack.Entry entry = matrices.peek();
					// pos = orientação de ANTES do espelho + origem (translação) DEPOIS do espelho —
					// ver comentário acima. entry continua sendo usado só pras normais (sombreamento
					// de linha, sem efeito nenhum na posição/clique).
					Vector3f gizmoOrigin = entry.getPositionMatrix().getTranslation(new Vector3f());
					Matrix4f pos = new Matrix4f(orientationBasis).setTranslation(gizmoOrigin);

					float size = GizmoManager.AXIS_WORLD_LENGTH;

					if (GizmoManager.currentMode == GizmoManager.Mode.ROTATE) {
						// Modo Girar: anel por eixo em vez da seta reta — o eixo agarrado (ou
						// nenhum, se só passando o mouse) fica no brilho total, os outros dois
						// ficam esmaecidos, pra não confundir qual está sendo arrastado.
						boolean draggingX = GizmoManager.currentAxis == GizmoManager.Axis.X;
						boolean draggingY = GizmoManager.currentAxis == GizmoManager.Axis.Y;
						boolean draggingZ = GizmoManager.currentAxis == GizmoManager.Axis.Z;
						boolean anyDragging = GizmoManager.isDragging;
						greatcosmetics$drawGizmoRing(vc, pos, entry, GizmoManager.Axis.X, size, 255, 60, 60, !anyDragging || draggingX);
						greatcosmetics$drawGizmoRing(vc, pos, entry, GizmoManager.Axis.Y, size, 60, 255, 60, !anyDragging || draggingY);
						greatcosmetics$drawGizmoRing(vc, pos, entry, GizmoManager.Axis.Z, size, 70, 130, 255, !anyDragging || draggingZ);
					} else {
						// Eixo X = Vermelho (Sem o .next())
						vc.vertex(pos, 0.0f, 0.0f, 0.0f).color(255, 0, 0, 255).normal(entry, 1.0f, 0.0f, 0.0f);
						vc.vertex(pos, size, 0.0f, 0.0f).color(255, 0, 0, 255).normal(entry, 1.0f, 0.0f, 0.0f);

						// Eixo Y = Verde
						vc.vertex(pos, 0.0f, 0.0f, 0.0f).color(0, 255, 0, 255).normal(entry, 0.0f, 1.0f, 0.0f);
						vc.vertex(pos, 0.0f, size, 0.0f).color(0, 255, 0, 255).normal(entry, 0.0f, 1.0f, 0.0f);

						// Eixo Z = Azul
						vc.vertex(pos, 0.0f, 0.0f, 0.0f).color(0, 0, 255, 255).normal(entry, 0.0f, 0.0f, 1.0f);
						vc.vertex(pos, 0.0f, 0.0f, size).color(0, 0, 255, 255).normal(entry, 0.0f, 0.0f, 1.0f);
					}

					// BOLINHA DE MIRA: cruzinha 3D pequena onde o mouse está "grudado" no gizmo
					// agora — cinza só passando o mouse, vira a cor do eixo enquanto arrasta (ver
					// GizmoManager#updateHover, chamado todo frame pelo Wardrobe3DScreen).
					org.joml.Vector3f hoverPoint = GizmoManager.getHoverLocalPoint();
					if (hoverPoint != null) {
						int[] hoverColor = GizmoManager.isDragging
								? greatcosmetics$axisColor(GizmoManager.currentAxis)
								: new int[]{200, 200, 200};
						greatcosmetics$drawHoverDot(vc, pos, entry, hoverPoint, hoverColor[0], hoverColor[1], hoverColor[2]);
					}

					// Guarda a projeção 2D dessas mesmas pontas (E dos anéis) pra tela poder testar
					// clique/arraste nelas depois (ver GizmoManager) — mundo e GUI rodam em momentos
					// separados do frame, não tem como a GUI calcular isso sozinha sem essa matriz.
					GizmoManager.updateScreenProjection(pos);
					matrices.pop();
				}

				// Mesma ordem (translate antes do scale) do bloco de prévia do gizmo acima — ver
				// comentário lá. Precisa bater EXATAMENTE, senão o ícone renderizado de verdade aqui
				// fica num lugar diferente de onde o gizmo (e a prévia) mostrava.
				matrices.translate(0.0F, -0.25F, 0.0F);
				matrices.scale(sX, sY, sZ);
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
				matrices.scale(0.625F, -0.625F, -0.625F);

				itemRenderer.renderItem(
						stackToRender,
						ModelTransformationMode.HEAD,
						light,
						net.minecraft.client.render.OverlayTexture.DEFAULT_UV,
						matrices,
						vertexConsumers,
						entity.getWorld(),
						entity.getId()
				);

				matrices.pop();
			}
		}
	}

	private boolean greatcosmetics$isAccessoryHidden(ClientCosmeticCache.PlayerSettings settings, CosmeticData data) {
		return data.id != null && settings.hiddenCosmeticIds().contains(data.id);
	}

	/** Desenha o anel de rotação de UM eixo (32 segmentos, ver GizmoManager#ringPoint — mesma
	 *  fórmula usada pra projetar os pontos de teste de clique, os dois NUNCA podem divergir).
	 *  {@code highlighted} esmaece o anel (alpha baixo) quando não é o eixo sendo arrastado no
	 *  momento, pra ficar óbvio qual dos três está ativo. */
	private static void greatcosmetics$drawGizmoRing(VertexConsumer vc, Matrix4f pos, MatrixStack.Entry entry,
			GizmoManager.Axis axis, float radius, int r, int g, int b, boolean highlighted) {
		int alpha = highlighted ? 255 : 100;
		int segments = 32;
		org.joml.Vector3f prev = GizmoManager.ringPoint(axis, radius, 0f);
		for (int i = 1; i <= segments; i++) {
			float angle = (float) (i * Math.PI * 2.0 / segments);
			org.joml.Vector3f next = GizmoManager.ringPoint(axis, radius, angle);
			float nx = next.x - prev.x, ny = next.y - prev.y, nz = next.z - prev.z;
			float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (len > 0.0001f) { nx /= len; ny /= len; nz /= len; }
			vc.vertex(pos, prev.x, prev.y, prev.z).color(r, g, b, alpha).normal(entry, nx, ny, nz);
			vc.vertex(pos, next.x, next.y, next.z).color(r, g, b, alpha).normal(entry, nx, ny, nz);
			prev = next;
		}
	}

	/** Cruzinha 3D pequena (3 tracinhos perpendiculares cruzando no ponto) — mais barata que montar
	 *  uma esfera de verdade com a RenderLayer de linhas que já temos, e já dá a sensação de
	 *  "bolinha" de longe. */
	private static void greatcosmetics$drawHoverDot(VertexConsumer vc, Matrix4f pos, MatrixStack.Entry entry, org.joml.Vector3f p, int r, int g, int b) {
		float s = 0.045f;
		vc.vertex(pos, p.x - s, p.y, p.z).color(r, g, b, 255).normal(entry, 1f, 0f, 0f);
		vc.vertex(pos, p.x + s, p.y, p.z).color(r, g, b, 255).normal(entry, 1f, 0f, 0f);
		vc.vertex(pos, p.x, p.y - s, p.z).color(r, g, b, 255).normal(entry, 0f, 1f, 0f);
		vc.vertex(pos, p.x, p.y + s, p.z).color(r, g, b, 255).normal(entry, 0f, 1f, 0f);
		vc.vertex(pos, p.x, p.y, p.z - s).color(r, g, b, 255).normal(entry, 0f, 0f, 1f);
		vc.vertex(pos, p.x, p.y, p.z + s).color(r, g, b, 255).normal(entry, 0f, 0f, 1f);
	}

	private static int[] greatcosmetics$axisColor(GizmoManager.Axis axis) {
		boolean rotate = GizmoManager.currentMode == GizmoManager.Mode.ROTATE;
		return switch (axis) {
			case X -> rotate ? new int[]{255, 60, 60} : new int[]{255, 0, 0};
			case Y -> rotate ? new int[]{60, 255, 60} : new int[]{0, 255, 0};
			case Z -> rotate ? new int[]{70, 130, 255} : new int[]{0, 0, 255};
			case NONE -> new int[]{200, 200, 200};
		};
	}
}
