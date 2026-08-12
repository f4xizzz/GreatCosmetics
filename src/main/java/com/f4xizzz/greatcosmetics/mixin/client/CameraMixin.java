package com.f4xizzz.greatcosmetics.mixin.client;

import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow protected abstract void setPos(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow private Entity focusedEntity;

    @Shadow private float clipToSpace(float f) { return f; }

    @Inject(method = "update", at = @At("TAIL"))
    private void greatcosmetics$applyWardrobeFreecam(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {

        if (Wardrobe3DScreen.isFreecamActive && this.focusedEntity != null) {

            // 1. Lemos a altura ATUAL animada pela GUI
            double focusYOffset = Wardrobe3DScreen.currentFocusY;

            // 2. Calculamos o centro do jogador no mundo
            double centerX = MathHelper.lerp((double) tickDelta, this.focusedEntity.prevX, this.focusedEntity.getX());
            double centerY = MathHelper.lerp((double) tickDelta, this.focusedEntity.prevY, this.focusedEntity.getY()) + focusYOffset;
            double centerZ = MathHelper.lerp((double) tickDelta, this.focusedEntity.prevZ, this.focusedEntity.getZ());

            // 3. Pegamos a rotação ATUAL animada pela GUI
            float yaw = Wardrobe3DScreen.currentYaw;
            float pitch = Wardrobe3DScreen.currentPitch;

            // 4. O SEGREDO DO "PERSONAGEM NA DIREITA":
            // Lemos o currentPan da GUI em vez de panOffset
            Vec3d panLeftVector = Vec3d.fromPolar(0, yaw - 90f);
            centerX += panLeftVector.x * Wardrobe3DScreen.currentPan;
            centerZ += panLeftVector.z * Wardrobe3DScreen.currentPan;

            // 5. Aplicamos a rotação e a posição central deslocada
            this.setRotation(yaw, pitch);
            this.setPos(centerX, centerY, centerZ);

            // 6. Calculamos o recuo da câmera baseado no Zoom (currentDistance) — sem colisão
            // contra blocos na aba Dev (Studio precisa de liberdade pra zoom/ângulo, ex: entrar
            // dentro de paredes pra ver o gizmo de um ângulo específico); nas outras abas continua
            // clampando normal, igual terceira pessoa vanilla.
            float targetDist = (float) Wardrobe3DScreen.currentDistance;
            float safeDist = Wardrobe3DScreen.isDevTabActive ? targetDist : this.clipToSpace(targetDist);

            Vec3d look = Vec3d.fromPolar(pitch, yaw);
            Vec3d offset = look.multiply(-safeDist);

            // 7. Setamos a posição final da câmera
            this.setPos(centerX + offset.x, centerY + offset.y, centerZ + offset.z);
        }
    }
}