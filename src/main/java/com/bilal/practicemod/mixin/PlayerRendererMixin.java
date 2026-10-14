package com.bilal.practicemod.mixin;

import com.bilal.practicemod.JetpackPoseRenderer;
import com.bilal.practicemod.SkateboardPoseRenderer;
import com.bilal.practicemod.UmbrellaGliderPoseRenderer;
import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

// Whole-body pitch/roll for airborne equipment poses. TAIL of setupRotations is where vanilla applies
// the elytra glide pitch: body yaw is already on the PoseStack (so pitch is a plain X rotation)
// and the transform stays inside the renderer's push/pop (nametag, shadow, fire and hitbox stay
// upright). There is no NeoForge event at this point in 1.21.1, hence the mixin.
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {

    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void practicemod$seatedSubmarineAttitude(AbstractClientPlayer player, PoseStack pose,
            float bob, float bodyYaw, float partialTick, float scale, CallbackInfo ci) {
        if (player.getVehicle() instanceof com.bilal.practicemod.MechanicalDolphinEntity) {
            com.bilal.practicemod.MechanicalDolphinRiderPose.body(player, pose, partialTick, scale);
            ci.cancel();
            return;
        }
        if (player.getVehicle() instanceof com.bilal.practicemod.AtvEntity) {
            com.bilal.practicemod.AtvRiderPose.body(player, pose, partialTick, scale);
            ci.cancel();
            return;
        }
        if (player.getVehicle() instanceof com.bilal.practicemod.JetskiEntity) {
            com.bilal.practicemod.JetskiRiderPose.body(player, pose, partialTick, scale);
            ci.cancel();
            return;
        }
        if (com.bilal.practicemod.SubmarinePilotPose.active(player)) {
            com.bilal.practicemod.SubmarinePilotPose.body(player, pose, partialTick, scale);
            ci.cancel();
        }
    }

    @Inject(method = "getRenderOffset(Lnet/minecraft/client/player/AbstractClientPlayer;F)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"), cancellable = true)
    private void practicemod$noCrouchSeatOffset(AbstractClientPlayer player, float partialTick,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.phys.Vec3> cir) {
        if (com.bilal.practicemod.SubmarinePilotPose.active(player)
                || player.getVehicle() instanceof com.bilal.practicemod.MechanicalDolphinEntity) cir.setReturnValue(net.minecraft.world.phys.Vec3.ZERO);
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("TAIL"))
    private void practicemod$applyAirborneBodyAttitude(AbstractClientPlayer entity, PoseStack poseStack,
            float bob, float yBodyRot, float partialTick, float scale, CallbackInfo ci) {
        // yBodyRot is the interpolated body rotation LivingEntityRenderer just used for the
        // Axis.YP.rotationDegrees(180 - yBodyRot) above, so it is exactly the frame of reference
        // the pose layer has to resolve the world-space travel vector against.
        JetpackPoseRenderer.applyBodyRotation(entity, poseStack, yBodyRot, partialTick);
        UmbrellaGliderPoseRenderer.applyBodyRotation(entity, poseStack, partialTick);
        SkateboardPoseRenderer.applyBodyRotation(entity, poseStack, yBodyRot, partialTick);
    }
}
