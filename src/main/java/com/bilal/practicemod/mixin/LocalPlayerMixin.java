package com.bilal.practicemod.mixin;

import com.bilal.practicemod.PracticeMod;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

// The client half of hover mode's crouch suppression. JetpackFlightHandler already forces
// Pose.STANDING while hovering, which covers the hitbox, the eye height and every remote viewer - but
// NOT the wearer's own client, because LocalPlayer overrides isCrouching() to return its private
// `crouching` field rather than the pose. Without this the player would still see themselves bent over
// while descending on sneak.
//
// isCrouching is the right seam for both symptoms at once. LocalPlayer.aiStep computes `crouching` and
// then immediately calls input.tick(isMovingSlowly(), SNEAKING_SPEED), and isMovingSlowly() is just
// isCrouching() || isVisuallyCrawling() - so KeyboardInput scales WASD by 0.3 off this same answer.
// Returning false here restores full steering authority while descending, which is most of what made
// hover feel sluggish, and does it without touching the input pipeline itself.
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @Inject(method = "isCrouching", at = @At("HEAD"), cancellable = true)
    private void practicemod$suppressHoverCrouch(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        // Deferring to an actual CROUCHING pose keeps this in step with the forced-pose layer: when
        // hovering somewhere too short to stand up in, that layer hands the pose back to vanilla, and
        // this must let the crouch through rather than insisting the player is upright.
        if (Boolean.TRUE.equals(self.getData(PracticeMod.JETPACK_HOVERING))
                && self.getPose() != Pose.CROUCHING) {
            cir.setReturnValue(false);
        }
    }
}
