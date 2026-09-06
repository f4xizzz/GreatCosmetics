package com.f4xizzz.greatcosmetics.mixin.client;

import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow private Entity entity;

    @Shadow private float getMaxZoom(float f) { return f; }

    @Inject(method = "setup", at = @At("TAIL"))
    private void greatcosmetics$applyWardrobeFreecam(BlockGetter area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {

        if (Wardrobe3DScreen.isFreecamActive && this.entity != null) {

            // 1. Lemos a altura ATUAL animada pela GUI
            double focusYOffset = Wardrobe3DScreen.currentFocusY;

            // 2. Calculamos o centro do jogador no mundo
            double centerX = Mth.lerp((double) tickDelta, this.entity.xo, this.entity.getX());
            double centerY = Mth.lerp((double) tickDelta, this.entity.yo, this.entity.getY()) + focusYOffset;
            double centerZ = Mth.lerp((double) tickDelta, this.entity.zo, this.entity.getZ());

            // 3. Pegamos a rotação ATUAL animada pela GUI
            float yaw = Wardrobe3DScreen.currentYaw;
            float pitch = Wardrobe3DScreen.currentPitch;

            // 4. O SEGREDO DO "PERSONAGEM NA DIREITA":
            // Lemos o currentPan da GUI em vez de panOffset
            Vec3 panLeftVector = Vec3.directionFromRotation(0, yaw - 90f);
            centerX += panLeftVector.x * Wardrobe3DScreen.currentPan;
            centerZ += panLeftVector.z * Wardrobe3DScreen.currentPan;

            // 5. Aplicamos a rotação e a posição central deslocada
            this.setRotation(yaw, pitch);
            this.setPosition(centerX, centerY, centerZ);

            // 6. Calculamos o recuo da câmera baseado no Zoom (currentDistance) — sem colisão
            // contra blocos na aba Dev (Studio precisa de liberdade pra zoom/ângulo, ex: entrar
            // dentro de paredes pra ver o gizmo de um ângulo específico); nas outras abas continua
            // clampando normal, igual terceira pessoa vanilla.
            float targetDist = (float) Wardrobe3DScreen.currentDistance;
            float safeDist = Wardrobe3DScreen.isDevTabActive ? targetDist : this.getMaxZoom(targetDist);

            Vec3 look = Vec3.directionFromRotation(pitch, yaw);
            Vec3 offset = look.scale(-safeDist);

            // 7. Setamos a posição final da câmera
            this.setPosition(centerX + offset.x, centerY + offset.y, centerZ + offset.z);
        }
    }
}