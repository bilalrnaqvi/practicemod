package com.bilal.practicemod.mixin;

import com.bilal.practicemod.JetpackPoseRenderer;
import com.bilal.practicemod.SkateboardPoseRenderer;
import com.bilal.practicemod.UmbrellaGliderPoseRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;

// Per-limb offsets for airborne equipment poses. Limb rotations must be written AFTER vanilla's
// setupAnim (anything set earlier is overwritten by it), and NeoForge 1.21.1 has no event at that
// point, hence the mixin. TAIL of the PlayerModel override also lands after vanilla's outer-layer
// copies, which each pose renderer redoes for the parts it touches.
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin<T extends LivingEntity> {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void practicemod$restoreSkateboardPose(T entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        com.bilal.practicemod.BlenderPlayerPose.restore((PlayerModel<?>) (Object) this);
        com.bilal.practicemod.MechanicalDolphinRiderPose.prepare((PlayerModel<?>) (Object) this, entity);
        SkateboardPoseRenderer.restore((PlayerModel<?>) (Object) this);
        com.bilal.practicemod.SubmarinePilotPose.prepare((PlayerModel<?>) (Object) this, entity);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void practicemod$applyAirborneLimbPose(T entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        JetpackPoseRenderer.applyLimbPose((PlayerModel<?>) (Object) this, entity);
        UmbrellaGliderPoseRenderer.applyLimbPose((PlayerModel<?>) (Object) this, entity);
        SkateboardPoseRenderer.applyLimbPose((PlayerModel<?>) (Object) this, entity, ageInTicks);
        com.bilal.practicemod.SubmarinePilotPose.finish((PlayerModel<?>) (Object) this, entity, ageInTicks);
        com.bilal.practicemod.MechanicalDolphinRiderPose.limbs((PlayerModel<?>) (Object) this, entity);
        com.bilal.practicemod.AtvRiderPose.limbs((PlayerModel<?>) (Object) this, entity);
        com.bilal.practicemod.JetskiRiderPose.limbs((PlayerModel<?>) (Object) this, entity);
        com.bilal.practicemod.BlenderAnimationPreview.apply((PlayerModel<?>) (Object) this, entity, ageInTicks);
    }
}
