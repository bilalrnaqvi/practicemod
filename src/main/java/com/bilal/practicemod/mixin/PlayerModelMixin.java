package com.bilal.practicemod.mixin;

import com.bilal.practicemod.JetpackPoseRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;

// Per-limb offsets for the jetpack flight poses. Limb rotations must be written AFTER vanilla's
// setupAnim (anything set earlier is overwritten by it), and NeoForge 1.21.1 has no event at that
// point, hence the mixin. TAIL of the PlayerModel override also lands after vanilla's outer-layer
// copies, which JetpackPoseRenderer redoes for the parts it touches.
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin<T extends LivingEntity> {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void practicemod$applyJetpackLimbPose(T entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        JetpackPoseRenderer.applyLimbPose((PlayerModel<?>) (Object) this, entity);
    }
}
