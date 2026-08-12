package com.f4xizzz.greatcosmetics.mixin.client;

import com.f4xizzz.greatcosmetics.GreatCosmetics;
import com.f4xizzz.greatcosmetics.client.ArmorCosmeticResolver;
import com.f4xizzz.greatcosmetics.client.ClientCosmeticCache;
import com.f4xizzz.greatcosmetics.client.gui.pages.utils.GizmoManager;
import com.f4xizzz.greatcosmetics.config.CosmeticData;
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
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin<T extends LivingEntity, M extends BipedEntityModel<T>, A extends BipedEntityModel<T>> extends FeatureRenderer<T, M> {

	public ArmorFeatureRendererMixin(FeatureRendererContext<T, M> context) {
		super(context);
	}

	@Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
	private void greatcosmetics$onRenderArmor(MatrixStack matrices, VertexConsumerProvider vertexConsumers, T entity, EquipmentSlot slot, int light, A model, CallbackInfo ci) {
		// Reset incondicional (sem checar nada) — garante que armadura/cosméticos SEMPRE desenham
		// sólidos, mesmo com a transparência do corpo do jogador ativa (ver
		// PlayerEntityRendererMixin#greatcosmetics$startBodyAlpha / Wardrobe3DScreen#characterAlpha).
		// Fora do Dev Studio isso é só um no-op (a cor já está em 1,1,1,1 de qualquer forma).
		com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

		if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) return;

		ClientCosmeticCache.PlayerSettings settings = ClientCosmeticCache.getSettings(entity.getUuid());

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
				: ClientCosmeticCache.getEquipped(entity.getUuid());

		if (shouldHideVanilla) {
			ci.cancel();
		}

		// TUDO desenha num ÚNICO passe fixo — sempre no HEAD, que roda pra todo mundo — em vez de
		// cada cosmético escolher um dos 4 passes (HEAD/CHEST/LEGS/FEET). Isso existia antes como
		// "render" configurável, mas virou sem querer um filtro de EXCLUSIVIDADE: dois cosméticos
		// com o mesmo "render" competiam pelo mesmo passe e só um aparecia. Usando sempre o mesmo
		// passe único, todo mundo desenha exatamente uma vez, sem disputa nenhuma.
		if (slot != EquipmentSlot.HEAD) return;
		if (equippedIds == null || equippedIds.isEmpty()) return;

		var itemRenderer = net.minecraft.client.MinecraftClient.getInstance().getItemRenderer();
		M contextModel = this.getContextModel();

		for (String id : equippedIds) {
			CosmeticData data = GreatCosmetics.getCosmeticById(id);
			if (data == null || data.parts == null || data.parts.isEmpty()) continue;

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
				if (realItem == null || realItem == Items.AIR) continue;
				realStack = new ItemStack(realItem);
			}

			for (CosmeticData.CosmeticPart part : data.parts) {
				matrices.push();

				// Resolvido bem no início (antes só vinha depois) — o bloco de prévia do gizmo (mais
				// abaixo) precisa saber se essa Part é GeckoLib, não só na hora de escolher o item
				// pra renderizar de verdade.
				int modelToRender = part.resolvedCmd != 0 ? part.resolvedCmd : data.cmd;
				boolean isGecko = com.f4xizzz.greatcosmetics.geckolib.GeoModelRegistry.has(modelToRender);

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
