package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

// Client-only pose layer for the player's own body during jetpack flight, called from the two
// mixins (PlayerRendererMixin / PlayerModelMixin). Two-layer design, matching how vanilla poses
// the elytra glide:
//  - applyBodyRotation runs at the tail of PlayerRenderer.setupRotations and pitches the WHOLE
//    model on the PoseStack (after body yaw, inside the renderer's push/pop, so the nametag,
//    shadow, fire overlay and hitbox stay upright).
//  - applyLimbPose runs at the tail of PlayerModel.setupAnim and adjusts individual limbs, after
//    vanilla has finished writing them.
//
// setupRotations always runs before setupAnim inside the same render() call, so applyBodyRotation
// is also where all guards live and where the frame's blended weights are sampled (it has the
// partial tick; setupAnim does not). The results are stashed on the player's JetpackAnimationState
// for applyLimbPose to read, which keeps the two layers frame-consistent by construction.
public final class JetpackPoseRenderer {

    // Rotation pivot height in blocks above the feet (roughly the belly), so pitching swings the
    // legs back and the head forward instead of hinging at the feet.
    private static final float PIVOT_Y = 1.05F;

    // Weights below this render identically to upright; skip the work.
    private static final float MIN_WEIGHT = 0.005F;

    // How far apart the arms sit at full cruise, in degrees outward from the torso line (a small
    // gap keeps them from clipping the torso, whichever way cruiseArmSweepDegrees points them).
    private static final float CRUISE_ARM_HUG_DEGREES = 4.0F;

    // Knees-together roll at full cruise, in radians (right leg; mirrored for the left).
    private static final float CRUISE_LEG_TOGETHER_ROLL = 0.02F;

    // Loose-leg roll spread at full hover, in radians (right leg; mirrored for the left).
    private static final float HOVER_LEG_ROLL = 0.03F;

    private JetpackPoseRenderer() {
    }

    // Whole-model pitch. PoseStack space here: origin at the feet, Y up, body yaw already applied,
    // so forward pitch is a plain rotation about X — negative degrees pitch face-down, same
    // convention as vanilla's elytra rotation in PlayerRenderer.setupRotations.
    public static void applyBodyRotation(AbstractClientPlayer player, PoseStack poseStack, float partialTick) {
        JetpackAnimationState state = JetpackAnimationController.get(player.getId());
        if (state == null) {
            return;
        }

        // Never fight a vanilla pose: while any of these is active the layer does nothing at all
        // (scratch cleared so the limb pass is skipped too).
        if (!JetpackClientConfig.ENABLE_FLIGHT_POSES.get()
                || player.isFallFlying()
                || player.getSwimAmount(partialTick) > 0.0F // swimming AND crawling
                || player.isSleeping()
                || player.isPassenger()
                || player.isAutoSpinAttack()
                || player.isSpectator()
                || player.deathTime > 0) {
            state.clearRenderPose();
            return;
        }

        float weight = state.poseWeight(partialTick);
        if (weight < MIN_WEIGHT) {
            state.clearRenderPose();
            return;
        }

        // Partition of unity scaled by engagement: upright <-> hover <-> cruise is one continuous
        // 2-D blend. Both terms are positive pitch = forward, so the hover's slight forward tilt is
        // the same attitude the cruise pitch grows out of, not a lean the body has to swing back
        // through on the way to flat. (A negative hoverLeanDegrees restores the back-lean.)
        float cruise = state.cruiseFactor(partialTick);
        float hoverWeight = weight * (1.0F - cruise);
        float cruiseWeight = weight * cruise;
        float pitchDeg = cruiseWeight * (float) (double) JetpackClientConfig.MAX_CRUISE_PITCH_DEGREES.get()
                + hoverWeight * (float) (double) JetpackClientConfig.HOVER_LEAN_DEGREES.get();
        state.setRenderPose(hoverWeight, cruiseWeight, pitchDeg);

        poseStack.translate(0.0F, PIVOT_Y, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitchDeg));
        poseStack.translate(0.0F, -PIVOT_Y, 0.0F);
    }

    // Per-limb offsets on top of whatever vanilla setupAnim produced. Hover is additive (the idle
    // arm bob keeps breathing underneath); cruise lerps limbs toward its fixed targets, which also
    // progressively damps the walk/fall swing as speed rises.
    public static void applyLimbPose(PlayerModel<?> model, LivingEntity entity) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }
        JetpackAnimationState state = JetpackAnimationController.get(player.getId());
        if (state == null) {
            return;
        }

        // PlayerRenderer.renderHand reuses this model's setupAnim for the first-person arm; the
        // pose must never bend it. (setupRotations does not run on that path, so the scratch would
        // be stale there anyway.)
        Minecraft minecraft = Minecraft.getInstance();
        if (player == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }

        float hoverWeight = state.renderHoverWeight();
        float cruiseWeight = state.renderCruiseWeight();
        if (hoverWeight + cruiseWeight < MIN_WEIGHT) {
            return;
        }

        // Hover: arms slightly out and back, legs loose and asymmetric — "balancing on thrust".
        // Sign convention (from vanilla's idle bob / riding poses): arm +zRot = outward on the
        // right and mirrored on the left; +xRot swings arms and legs backward.
        if (hoverWeight > 0.0F) {
            float armOut = hoverWeight * degToRad(JetpackClientConfig.HOVER_ARM_OUT_DEGREES.get());
            float armBack = hoverWeight * degToRad(JetpackClientConfig.HOVER_ARM_BACK_DEGREES.get());
            model.rightArm.zRot += armOut;
            model.leftArm.zRot -= armOut;
            model.rightArm.xRot += armBack;
            model.leftArm.xRot += armBack;

            float legLoose = degToRad(JetpackClientConfig.HOVER_LEG_LOOSE_DEGREES.get());
            model.rightLeg.xRot += hoverWeight * legLoose;
            model.leftLeg.xRot -= hoverWeight * legLoose * 0.5F;
            model.rightLeg.zRot += hoverWeight * HOVER_LEG_ROLL;
            model.leftLeg.zRot -= hoverWeight * HOVER_LEG_ROLL;
        }

        // Cruise: arms driven to cruiseArmSweepDegrees (forward past the head by default), legs
        // together and trailing.
        if (cruiseWeight > 0.0F) {
            float armSweep = degToRad(JetpackClientConfig.CRUISE_ARM_SWEEP_DEGREES.get());
            float armHug = degToRad(CRUISE_ARM_HUG_DEGREES);
            model.rightArm.xRot = Mth.lerp(cruiseWeight, model.rightArm.xRot, armSweep);
            model.leftArm.xRot = Mth.lerp(cruiseWeight, model.leftArm.xRot, armSweep);
            model.rightArm.yRot = Mth.lerp(cruiseWeight, model.rightArm.yRot, 0.0F);
            model.leftArm.yRot = Mth.lerp(cruiseWeight, model.leftArm.yRot, 0.0F);
            model.rightArm.zRot = Mth.lerp(cruiseWeight, model.rightArm.zRot, armHug);
            model.leftArm.zRot = Mth.lerp(cruiseWeight, model.leftArm.zRot, -armHug);

            float legTrail = degToRad(JetpackClientConfig.CRUISE_LEG_TRAIL_DEGREES.get());
            model.rightLeg.xRot = Mth.lerp(cruiseWeight, model.rightLeg.xRot, legTrail);
            model.leftLeg.xRot = Mth.lerp(cruiseWeight, model.leftLeg.xRot, legTrail);
            model.rightLeg.yRot = Mth.lerp(cruiseWeight, model.rightLeg.yRot, 0.0F);
            model.leftLeg.yRot = Mth.lerp(cruiseWeight, model.leftLeg.yRot, 0.0F);
            model.rightLeg.zRot = Mth.lerp(cruiseWeight, model.rightLeg.zRot, CRUISE_LEG_TOGETHER_ROLL);
            model.leftLeg.zRot = Mth.lerp(cruiseWeight, model.leftLeg.zRot, -CRUISE_LEG_TOGETHER_ROLL);
        }

        // Counter-rotate the head against the body pitch (clamped to a plausible neck bend) so the
        // wearer keeps looking where the camera looks while the body flattens out.
        float maxCounter = degToRad(JetpackClientConfig.HEAD_COUNTER_MAX_DEGREES.get());
        model.head.xRot += Mth.clamp(-state.renderBodyPitchDeg() * Mth.DEG_TO_RAD, -maxCounter, maxCounter);

        // Vanilla copies the skin's outer layer from the base parts BEFORE this hook ran; re-copy
        // so hat, sleeves and pants follow the pose. (body is never modified here, so the jacket
        // needs no re-copy; armor and the jetpack model copy their poses after setupAnim returns.)
        model.hat.copyFrom(model.head);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
    }

    private static float degToRad(double degrees) {
        return (float) degrees * Mth.DEG_TO_RAD;
    }
}
