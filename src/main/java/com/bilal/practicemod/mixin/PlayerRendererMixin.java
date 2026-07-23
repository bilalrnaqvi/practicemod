package com.bilal.practicemod.mixin;

import com.bilal.practicemod.JetpackPoseRenderer;
import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

// Whole-body pitch for the jetpack flight poses. TAIL of setupRotations is where vanilla applies
// the elytra glide pitch: body yaw is already on the PoseStack (so pitch is a plain X rotation)
// and the transform stays inside the renderer's push/pop (nametag, shadow, fire and hitbox stay
// upright). There is no NeoForge event at this point in 1.21.1, hence the mixin.
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {

    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("TAIL"))
    private void practicemod$applyJetpackBodyPitch(AbstractClientPlayer entity, PoseStack poseStack,
            float bob, float yBodyRot, float partialTick, float scale, CallbackInfo ci) {
        JetpackPoseRenderer.applyBodyRotation(entity, poseStack, partialTick);
    }
}
