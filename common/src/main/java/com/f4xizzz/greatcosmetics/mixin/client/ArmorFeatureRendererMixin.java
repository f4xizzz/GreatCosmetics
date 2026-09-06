package com.f4xizzz.greatcosmetics.mixin.client;

import com.f4xizzz.greatcosmetics.client.ArmorCosmeticResolver;
import com.f4xizzz.greatcosmetics.client.ClientCosmeticCache;
import com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager;
import com.f4xizzz.greatcosmetics.config.CosmeticData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.DyedItemColor;

@Mixin(HumanoidArmorLayer.class)
public abstract class ArmorFeatureRendererMixin<T extends LivingEntity, M extends HumanoidModel<T>, A extends HumanoidModel<T>> extends RenderLayer<T, M> {

	public ArmorFeatureRendererMixin(RenderLayerParent<T, M> context) {
		super(context);
	}

	// Único membro PRIVADO do ArmorFeatureRenderer vanilla que a gente precisa (ver
	// greatcosmetics$renderRealArmor) — o resto do algoritmo de render de armadura de verdade
	// (copyBipedStateTo/setVisible/render do BipedEntityModel, RenderLayer.getArmorCutoutNoCull)
	// é tudo público. "outerModel" serve pra QUALQUER slot exceto LEGS (que usa "innerModel") —
	// como esse mixin inteiro só trata EquipmentSlot.HEAD (ver o early-return logo abaixo), nunca
	// precisamos do innerModel.
	@Shadow private A outerModel;

	// Evita spammar o console com a mesma linha todo frame — ver uso abaixo.
	private static final Set<String> greatcosmetics$loggedMissingIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/** Desenha uma armadura-cosmético (ver ArmorCosmeticsConfig) que representa um ArmorItem de
	 *  slot HEAD de verdade usando o MESMO algoritmo que o Minecraft usa pra armadura realmente
	 *  equipada — modelo 3D biped (capacete de verdade, não um ícone chapado) + a(s) textura(s) da
	 *  camada do material — em vez do caminho genérico de ícone (itemRenderer.renderItem com
	 *  ModelTransformationMode.HEAD) que o resto deste mixin usa pra QUALQUER outro item.
	 *
	 *  Replicado a partir do bytecode real de ArmorFeatureRenderer#renderArmor (decompilado do jar
	 *  vanilla via javap pra confirmar a ordem exata das chamadas — sem isso seria só chute):
	 *  copyBipedStateTo (copia a pose do corpo pro modelo de armadura) -> setVisible(false) +
	 *  liga só head/hat -> pra cada Layer do ArmorMaterial, resolve a textura (com tint de
	 *  dye se aplicável) e desenha via RenderLayer.getArmorCutoutNoCull -> brilho de encantamento
	 *  se o item tiver. NÃO replica os trims (ArmorTrim) — precisaria de outro campo privado
	 *  (armorTrimsAtlas) e é bem mais raro num cosmético de exemplo; item com trim configurado
	 *  simplesmente não mostra o trim (ainda mostra o capacete certo, só sem o overlay decorativo).
	 *
	 *  Ao contrário do caminho de ícone, essa renderização IGNORA offset/rotação/escala da Part de
	 *  propósito — o modelo de armadura de verdade já encaixa perfeitamente na cabeça sozinho
	 *  (é a MESMA geometria que o Minecraft usa quando o item está equipado de verdade no slot),
	 *  então não tem "ajuste fino" nenhum pra fazer; os campos continuam existindo/editáveis no Dev
	 *  Studio (não afetam nada nesse caso) só porque são compartilhados com os outros dois
	 *  caminhos (GeckoLib e ícone chapado). NÃO testado ao vivo (sem client Minecraft neste
	 *  ambiente) — validado só por leitura/compile contra a API pública confirmada via bytecode. */
	private void greatcosmetics$renderRealArmor(PoseStack matrices, MultiBufferSource vertexConsumers, int light, M contextModel, ItemStack stack, ArmorItem armorItem) {
		A armorModel = this.outerModel;
		contextModel.copyPropertiesTo(armorModel);
		armorModel.setAllVisible(false);
		armorModel.head.visible = true;
		armorModel.hat.visible = true;

		ArmorMaterial material = armorItem.getMaterial().value();

		// -6265536 = ArmorFeatureRenderer.DEFAULT_LEATHER_COLOR de verdade (vanilla usa esse
		// literal como fallback quando o item é dyeable mas não tem cor customizada setada).
		int dyeColor = stack.is(ItemTags.DYEABLE)
				? FastColor.ARGB32.opaque(DyedItemColor.getOrDefault(stack, -6265536))
				: -1;

		for (ArmorMaterial.Layer layer : material.layers()) {
			int color = layer.dyeable() ? dyeColor : -1;
			// "false" = não é o innerModel (única situação em que seria true é slot LEGS, que
			// esse mixin nunca trata — ver o early-return "if (slot != EquipmentSlot.HEAD) return").
			ResourceLocation texture = layer.texture(false);
			VertexConsumer vc = vertexConsumers.getBuffer(RenderType.armorCutoutNoCull(texture));
			armorModel.renderToBuffer(matrices, vc, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, color);
		}

		if (stack.hasFoil()) {
			VertexConsumer glintVc = vertexConsumers.getBuffer(RenderType.armorEntityGlint());
			armorModel.renderToBuffer(matrices, glintVc, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
		}
	}

	@Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
	private void greatcosmetics$onRenderArmor(PoseStack matrices, MultiBufferSource vertexConsumers, T entity, EquipmentSlot slot, int light, A model, CallbackInfo ci) {
		// NÃO precisa mais resetar RenderSystem.setShaderColor aqui — a transparência do corpo do
		// jogador (ver LivingEntityRendererMixin#greatcosmetics$applyBodyAlpha) agora bakeia o
		// alpha direto no argumento "color" da chamada EntityModel.render() do CORPO, sem tocar no
		// shader global nem no VertexConsumerProvider — armadura/cosméticos (aqui) são um caminho
		// de código totalmente separado e nunca são afetados, então não tem nada pra proteger.

		if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) return;

		ClientCosmeticCache.PlayerSettings settings = ClientCosmeticCache.getSettings(entity.getUUID());

		boolean shouldHideVanilla = switch (slot) {
			case HEAD -> settings.hideHelmet();
			case CHEST -> settings.hideChestplate();
			case LEGS -> settings.hideLeggings();
			case FEET -> settings.hideBoots();
			default -> false;
		};

		boolean isDevPreview = ArmorCosmeticResolver.isDevPreview(entity);

		Set<String> equippedIds = isDevPreview
				? (com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen.previewCosmeticId == null
						|| com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen.previewCosmeticId.isEmpty()
						? Set.of() : Set.of(com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen.previewCosmeticId))
				: ClientCosmeticCache.getEquipped(entity.getUUID());

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
			matrices.pushPose();
			matrices.translate((float) -eff.offsetX, (float) -eff.offsetY, (float) -eff.offsetZ);

			VertexConsumer effVc = vertexConsumers.getBuffer(GizmoManager.GIZMO_LINES_NO_DEPTH);
			PoseStack.Pose effEntry = matrices.last();
			Matrix4f effPos = effEntry.pose();
			float effSize = GizmoManager.AXIS_WORLD_LENGTH;

			effVc.addVertex(effPos, 0.0f, 0.0f, 0.0f).setColor(255, 0, 0, 255).setNormal(effEntry, 1.0f, 0.0f, 0.0f);
			effVc.addVertex(effPos, effSize, 0.0f, 0.0f).setColor(255, 0, 0, 255).setNormal(effEntry, 1.0f, 0.0f, 0.0f);
			effVc.addVertex(effPos, 0.0f, 0.0f, 0.0f).setColor(0, 255, 0, 255).setNormal(effEntry, 0.0f, 1.0f, 0.0f);
			effVc.addVertex(effPos, 0.0f, effSize, 0.0f).setColor(0, 255, 0, 255).setNormal(effEntry, 0.0f, 1.0f, 0.0f);
			effVc.addVertex(effPos, 0.0f, 0.0f, 0.0f).setColor(0, 0, 255, 255).setNormal(effEntry, 0.0f, 0.0f, 1.0f);
			effVc.addVertex(effPos, 0.0f, 0.0f, effSize).setColor(0, 0, 255, 255).setNormal(effEntry, 0.0f, 0.0f, 1.0f);

			Vector3f effHover = GizmoManager.getHoverLocalPoint();
			if (effHover != null) {
				int[] hoverColor = GizmoManager.isDragging
						? greatcosmetics$axisColor(GizmoManager.currentAxis)
						: new int[]{200, 200, 200};
				greatcosmetics$drawHoverDot(effVc, effPos, effEntry, effHover, hoverColor[0], hoverColor[1], hoverColor[2]);
			}

			GizmoManager.updateScreenProjection(effPos);
			matrices.popPose();
		}

		if (equippedIds == null || equippedIds.isEmpty()) return;

		var itemRenderer = net.minecraft.client.Minecraft.getInstance().getItemRenderer();
		M contextModel = this.getParentModel();

		for (String id : equippedIds) {
			CosmeticData data = com.f4xizzz.greatcosmetics.config.CosmeticsConfig.getCosmeticById(id);
			if (data == null) {
				// getCosmeticById() roda TODO FRAME por cosmético equipado — sem o "log só uma vez",
				// habilitar /gc debug com um cosmético fantasma equipado (id que sumiu do catálogo)
				// inundava o console com a mesma linha centenas de vezes por segundo.
				if (greatcosmetics$loggedMissingIds.add(id)) {
					com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("ArmorFeatureRendererMixin: equipped cosmetic '" + id + "' does NOT exist in the client catalog — will not render.");
				}
				continue;
			}
			if (data.parts == null || data.parts.isEmpty()) {
				if (greatcosmetics$loggedMissingIds.add(id + "#noparts")) {
					com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("ArmorFeatureRendererMixin: cosmetic '" + id + "' has no configured Part — nothing to draw.");
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
				ResourceLocation realId = ResourceLocation.tryParse(data.realItemId);
				Item realItem = realId != null ? BuiltInRegistries.ITEM.get(realId) : null;
				if (realItem == null || realItem == Items.AIR) {
					if (greatcosmetics$loggedMissingIds.add(id + "#realitem")) {
						com.f4xizzz.greatcosmetics.GreatCosmeticsCommon.debugLog("ArmorFeatureRendererMixin: cosmetic '" + id + "' has realItemId='" + data.realItemId + "' invalid/AIR — skipping render.");
					}
					continue;
				}
				realStack = new ItemStack(realItem);
			}

			for (CosmeticData.CosmeticPart part : data.parts) {
				matrices.pushPose();

				// Resolvido bem no início (antes só vinha depois) — o bloco de prévia do gizmo (mais
				// abaixo) precisa saber se essa Part é GeckoLib, não só na hora de escolher o item
				// pra renderizar de verdade.
				int modelToRender = part.resolvedCmd != 0 ? part.resolvedCmd : data.cmd;
				boolean isGecko = com.f4xizzz.greatcosmetics.geckolib.GeoModelRegistry.has(modelToRender);

				// ARMADURA DE VERDADE (capacete real, não ícone) — só quando NADA de custom foi
				// configurado pra essa Part (sem GeckoLib) e o item real é um ArmorItem de slot
				// HEAD de verdade. Usa "matrices" no estado CRU de agora (logo após o push, ainda
				// sem a rotação de âncora abaixo) — é o mesmo espaço que o ArmorFeatureRenderer
				// vanilla usa pra desenhar armadura equipada de verdade (ver
				// greatcosmetics$renderRealArmor). Sai do loop pra essa Part aqui — nada do resto
				// (ícone chapado, transform de âncora, gizmo) se aplica nesse caminho.
				if (!isGecko && part.anchor == CosmeticData.Anchor.HEAD && realStack != null
						&& realStack.getItem() instanceof ArmorItem realArmorItem
						&& realArmorItem.getEquipmentSlot() == EquipmentSlot.HEAD) {
					greatcosmetics$renderRealArmor(matrices, vertexConsumers, light, contextModel, realStack, realArmorItem);
					matrices.popPose();
					continue;
				}

				switch (part.anchor) {
					case HEAD -> contextModel.head.translateAndRotate(matrices);
					case BODY -> contextModel.body.translateAndRotate(matrices);
					case RIGHT_ARM -> { contextModel.rightArm.translateAndRotate(matrices); matrices.translate(0.3125F, -0.125F, 0.0F); }
					case LEFT_ARM -> { contextModel.leftArm.translateAndRotate(matrices); matrices.translate(-0.3125F, -0.125F, 0.0F); }
					case RIGHT_LEG -> { contextModel.rightLeg.translateAndRotate(matrices); matrices.translate(0.125F, -0.75F, 0.0F); }
					case LEFT_LEG -> { contextModel.leftLeg.translateAndRotate(matrices); matrices.translate(-0.125F, -0.75F, 0.0F); }
				}

				matrices.translate(part.offsetX, part.offsetY, part.offsetZ);
				if (part.rotationX != 0.0F) matrices.mulPose(Axis.XP.rotationDegrees(part.rotationX));
				if (part.rotationY != 0.0F) matrices.mulPose(Axis.YP.rotationDegrees(part.rotationY));
				if (part.rotationZ != 0.0F) matrices.mulPose(Axis.ZP.rotationDegrees(part.rotationZ));

				if (entity.isCrouching()) {
					matrices.translate(part.shiftOffsetX, part.shiftOffsetY, part.shiftOffsetZ);
					if (part.shiftRotationX != 0.0F) matrices.mulPose(Axis.XP.rotationDegrees(part.shiftRotationX));
					if (part.shiftRotationY != 0.0F) matrices.mulPose(Axis.YP.rotationDegrees(part.shiftRotationY));
					if (part.shiftRotationZ != 0.0F) matrices.mulPose(Axis.ZP.rotationDegrees(part.shiftRotationZ));
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
					stackToRender = new ItemStack(com.f4xizzz.greatcosmetics.geckolib.GreatCosmeticsItems.GEO_DISPLAY.get());
					stackToRender.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(modelToRender));
				} else if (realStack != null) {
					stackToRender = realStack;
				} else {
					stackToRender = new ItemStack(Items.CARVED_PUMPKIN);
					stackToRender.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(modelToRender));
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
					Matrix4f orientationBasis = new Matrix4f(matrices.last().pose());

					matrices.pushPose();
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
					matrices.mulPose(Axis.YP.rotationDegrees(180.0F));
					matrices.scale(0.625F, -0.625F, -0.625F);

					if (!isGecko) {
						try {
							var bakedModel = itemRenderer.getModel(stackToRender, entity.level(), entity, entity.getId());
							if (bakedModel != null && bakedModel.getTransforms() != null) {
								bakedModel.getTransforms().head.apply(false, matrices);
							}
						} catch (Exception ignored) {
							// Modelo dinâmico pode não ter uma ModelTransformation "head" de verdade —
							// melhor deixar o gizmo na posição lógica de antes do que travar o render.
						}
					}

					// GIZMO_LINES_NO_DEPTH (não RenderLayer.getLines()) — dá pra ver o gizmo através de
					// blocos/paredes, útil quando o offset da part empurra ela pra dentro de algo.
					VertexConsumer vc = vertexConsumers.getBuffer(GizmoManager.GIZMO_LINES_NO_DEPTH);
					PoseStack.Pose entry = matrices.last();
					// pos = orientação de ANTES do espelho + origem (translação) DEPOIS do espelho —
					// ver comentário acima. entry continua sendo usado só pras normais (sombreamento
					// de linha, sem efeito nenhum na posição/clique).
					Vector3f gizmoOrigin = entry.pose().getTranslation(new Vector3f());
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
						vc.addVertex(pos, 0.0f, 0.0f, 0.0f).setColor(255, 0, 0, 255).setNormal(entry, 1.0f, 0.0f, 0.0f);
						vc.addVertex(pos, size, 0.0f, 0.0f).setColor(255, 0, 0, 255).setNormal(entry, 1.0f, 0.0f, 0.0f);

						// Eixo Y = Verde
						vc.addVertex(pos, 0.0f, 0.0f, 0.0f).setColor(0, 255, 0, 255).setNormal(entry, 0.0f, 1.0f, 0.0f);
						vc.addVertex(pos, 0.0f, size, 0.0f).setColor(0, 255, 0, 255).setNormal(entry, 0.0f, 1.0f, 0.0f);

						// Eixo Z = Azul
						vc.addVertex(pos, 0.0f, 0.0f, 0.0f).setColor(0, 0, 255, 255).setNormal(entry, 0.0f, 0.0f, 1.0f);
						vc.addVertex(pos, 0.0f, 0.0f, size).setColor(0, 0, 255, 255).setNormal(entry, 0.0f, 0.0f, 1.0f);
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
					matrices.popPose();
				}

				// Mesma ordem (translate antes do scale) do bloco de prévia do gizmo acima — ver
				// comentário lá. Precisa bater EXATAMENTE, senão o ícone renderizado de verdade aqui
				// fica num lugar diferente de onde o gizmo (e a prévia) mostrava.
				matrices.translate(0.0F, -0.25F, 0.0F);
				matrices.scale(sX, sY, sZ);
				matrices.mulPose(Axis.YP.rotationDegrees(180.0F));
				matrices.scale(0.625F, -0.625F, -0.625F);

				itemRenderer.renderStatic(
						stackToRender,
						ItemDisplayContext.HEAD,
						light,
						net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
						matrices,
						vertexConsumers,
						entity.level(),
						entity.getId()
				);

				matrices.popPose();
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
	private static void greatcosmetics$drawGizmoRing(VertexConsumer vc, Matrix4f pos, PoseStack.Pose entry,
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
			vc.addVertex(pos, prev.x, prev.y, prev.z).setColor(r, g, b, alpha).setNormal(entry, nx, ny, nz);
			vc.addVertex(pos, next.x, next.y, next.z).setColor(r, g, b, alpha).setNormal(entry, nx, ny, nz);
			prev = next;
		}
	}

	/** Cruzinha 3D pequena (3 tracinhos perpendiculares cruzando no ponto) — mais barata que montar
	 *  uma esfera de verdade com a RenderLayer de linhas que já temos, e já dá a sensação de
	 *  "bolinha" de longe. */
	private static void greatcosmetics$drawHoverDot(VertexConsumer vc, Matrix4f pos, PoseStack.Pose entry, org.joml.Vector3f p, int r, int g, int b) {
		float s = 0.045f;
		vc.addVertex(pos, p.x - s, p.y, p.z).setColor(r, g, b, 255).setNormal(entry, 1f, 0f, 0f);
		vc.addVertex(pos, p.x + s, p.y, p.z).setColor(r, g, b, 255).setNormal(entry, 1f, 0f, 0f);
		vc.addVertex(pos, p.x, p.y - s, p.z).setColor(r, g, b, 255).setNormal(entry, 0f, 1f, 0f);
		vc.addVertex(pos, p.x, p.y + s, p.z).setColor(r, g, b, 255).setNormal(entry, 0f, 1f, 0f);
		vc.addVertex(pos, p.x, p.y, p.z - s).setColor(r, g, b, 255).setNormal(entry, 0f, 0f, 1f);
		vc.addVertex(pos, p.x, p.y, p.z + s).setColor(r, g, b, 255).setNormal(entry, 0f, 0f, 1f);
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
