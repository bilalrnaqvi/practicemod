package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import org.joml.Vector3f;

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

    // Calm neutral the vanilla limb swing is damped toward once ANY flight pose is engaged: arms
    // hanging at the sides, legs quiet with a gentle trail. Feet leave the ground under thrust, so
    // the walk/fall cycle underneath has nothing left to describe.
    private static final float NEUTRAL_ARM_XROT = 0.0F;
    private static final float NEUTRAL_LEG_TRAIL_DEGREES = 4.0F;

    // Below this speed (blocks/tick) the travel direction is meaningless, so the last valid one is
    // held rather than recomputed - otherwise the lean direction spins on floating-point noise as
    // the wearer comes to a stop.
    private static final float DIRECTION_EPSILON = 1.0E-4F;

    // Leans smaller than this render as upright; skip the quaternion.
    private static final float MIN_LEAN_DEGREES = 0.01F;

    // How much of the body roll the head cancels out. A fully levelled head at a steep roll reads
    // as a snapped neck; half-levelling looks intentional.
    private static final float HEAD_COUNTER_ROLL_SCALE = 0.5F;

    private JetpackPoseRenderer() {
    }

    // Whole-model lean. PoseStack space here: origin at the feet, Y up, body yaw already applied,
    // so body-forward is -Z and body-right is +X, and a pure forward pitch is a plain rotation
    // about X by negative degrees (same convention as vanilla's elytra rotation in
    // PlayerRenderer.setupRotations).
    //
    // The lean is the tilt of the thrust vector, which points along the direction of travel, not
    // along body-forward - so it is a 2-D quantity: Lf degrees forward plus Lr degrees rightward.
    // Those are applied as ONE axis-angle rotation about (Lf, 0, Lr), never as composed X and Z
    // Euler rotations: at the 90-degree cruise pitch a composed pair produces a parasitic yaw
    // twist. With Lr = 0 the axis collapses to (1, 0, 0) and the rotation is exactly
    // Axis.XP.rotationDegrees(-Lf), so forward flight and the superman pose are untouched.
    //
    // `yBodyRot` is the interpolated body yaw the caller already applied to the stack; vertical
    // motion is deliberately ignored, since a pure climb has an upright body.
    public static void applyBodyRotation(AbstractClientPlayer player, PoseStack poseStack, float yBodyRot,
            float partialTick) {
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

        // Resolve the smoothed world-space travel vector into body-local forward/right. Minecraft
        // yaw convention: forward = (-sin, cos), right = (-cos, -sin) in world (x, z).
        float yawRad = yBodyRot * Mth.DEG_TO_RAD;
        float sin = Mth.sin(yawRad);
        float cos = Mth.cos(yawRad);
        float vx = state.leanVelocityX(partialTick);
        float vz = state.leanVelocityZ(partialTick);
        float f = vx * -sin + vz * cos;  // blocks/tick along body-forward
        float r = vx * -cos + vz * -sin; // blocks/tick along body-right
        float speed = Mth.sqrt(f * f + r * r);

        // Below the epsilon the direction is noise, so hold the last valid one instead of letting
        // it spin as the wearer settles to a stop.
        float dirF = state.lastLeanDirForward();
        float dirR = state.lastLeanDirRight();
        if (speed > DIRECTION_EPSILON) {
            dirF = f / speed;
            dirR = r / speed;
            state.setLastLeanDir(dirF, dirR);
        }

        // Ramp the hover-speed directional lean in from a standstill up to where cruise takes over.
        // cruiseSpeedThreshold is a FRACTION of the server's horizontal cap (same as in the cruise
        // blend), so the measured speed has to be normalised against that cap before comparing. The
        // cap is only readable while in a world; without it there is nothing to ramp against, so
        // the directional term simply stays out and the pose is exactly today's.
        float cap = JetpackServerConfig.SPEC.isLoaded()
                ? (float) (double) JetpackServerConfig.GLIDE_HORIZONTAL_CAP.get()
                : 0.0F;
        float ramp = 0.0F;
        if (cap > 0.0F) {
            float cruiseStart = Math.max(1.0E-4F, (float) (double) JetpackClientConfig.CRUISE_SPEED_THRESHOLD.get());
            ramp = Mth.clamp(speed / cap / cruiseStart, 0.0F, 1.0F);
            ramp = ramp * ramp * (3.0F - 2.0F * ramp); // smoothstep, same curve as the cruise blend
        }
        float dirLean = hoverWeight * (float) (double) JetpackClientConfig.DIRECTIONAL_LEAN_DEGREES.get() * ramp;

        // Cruise pitch, optionally following travel direction (blend = 1 fully directional, 0 locks
        // the pitch to body-forward the way it used to be).
        float blend = (float) (double) JetpackClientConfig.CRUISE_DIRECTIONAL_BLEND.get();
        float cruiseDeg = cruiseWeight * (float) (double) JetpackClientConfig.MAX_CRUISE_PITCH_DEGREES.get();

        // Climb/descend attitude, folded into the same forward-pitch term as everything else so it
        // composes into the single axis-angle rotation below instead of adding geometry of its own.
        // Carried on hoverWeight because it belongs to the upright mode: the cruise pose already aims
        // the whole body along its travel vector, vertical component included, so layering this on top
        // there would double-count the climb.
        float vertical = state.verticalTrim(partialTick);
        float climbPitchDeg = hoverWeight
                * (float) (double) JetpackClientConfig.HOVER_CLIMB_PITCH_DEGREES.get() * vertical;

        float leanForwardDeg = hoverWeight * (float) (double) JetpackClientConfig.HOVER_LEAN_DEGREES.get()
                + climbPitchDeg
                + dirLean * dirF
                + cruiseDeg * Mth.lerp(blend, 1.0F, dirF);
        float leanRightDeg = dirLean * dirR
                + cruiseDeg * blend * dirR;

        state.setRenderPose(hoverWeight, cruiseWeight, leanForwardDeg, leanRightDeg, vertical);

        float magnitude = Mth.sqrt(leanForwardDeg * leanForwardDeg + leanRightDeg * leanRightDeg);
        if (magnitude < MIN_LEAN_DEGREES) {
            state.clearRenderPose();
            return;
        }

        float axisX = leanForwardDeg / magnitude;
        float axisZ = leanRightDeg / magnitude;
        poseStack.translate(0.0F, PIVOT_Y, 0.0F);
        poseStack.mulPose(Axis.of(new Vector3f(axisX, 0.0F, axisZ)).rotationDegrees(-magnitude));
        poseStack.translate(0.0F, -PIVOT_Y, 0.0F);
    }

    // Per-limb offsets on top of whatever vanilla setupAnim produced. The vanilla limb swing is
    // first damped toward a calm neutral by total engagement (so no flight pose ever has a run
    // cycle running under it); hover is then additive on that base (the idle arm bob keeps
    // breathing underneath), and cruise lerps limbs toward its fixed targets.
    public static void applyLimbPose(PlayerModel<?> model, LivingEntity entity) {
        // Vanilla setupAnim rewrites head.xRot/yRot every frame but never touches head.zRot, so
        // this layer owns the head roll outright and must zero it itself — before ANY early
        // return, or a stale counter-roll from the last engaged frame sticks forever.
        model.head.zRot = 0.0F;
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
        float totalWeight = Mth.clamp(hoverWeight + cruiseWeight, 0.0F, 1.0F);
        if (totalWeight < MIN_WEIGHT) {
            return;
        }

        // Suppress the vanilla walk cycle FIRST, by total engagement rather than by cruise alone.
        // The hover offsets below are additive, so without this the setupAnim limb swing keeps
        // running at full amplitude underneath them and a light boost reads as a mid-air run.
        // Damping here — before the hover offsets and the cruise lerps — means any engaged flight
        // pose is built on a calm base, and the vanilla swing fades back in as the pose blends out.
        float neutralLegTrail = degToRad(NEUTRAL_LEG_TRAIL_DEGREES);
        model.rightArm.xRot = Mth.lerp(totalWeight, model.rightArm.xRot, NEUTRAL_ARM_XROT);
        model.leftArm.xRot = Mth.lerp(totalWeight, model.leftArm.xRot, NEUTRAL_ARM_XROT);
        model.rightLeg.xRot = Mth.lerp(totalWeight, model.rightLeg.xRot, neutralLegTrail);
        model.leftLeg.xRot = Mth.lerp(totalWeight, model.leftLeg.xRot, neutralLegTrail);

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

            // Climb/descend, on the same signed factor driving the body pitch above: legs trail behind
            // on the way up and swing forward on the way down, arms flare wider on the way down only.
            // Additive like the rest of this block, so the splay and the idle bob keep running under
            // it and the whole attitude eases through neutral as the trim reverses.
            float vertical = state.renderVerticalTrim();
            float legSwing = hoverWeight * degToRad(JetpackClientConfig.HOVER_CLIMB_LEG_DEGREES.get()) * vertical;
            model.rightLeg.xRot += legSwing;
            model.leftLeg.xRot += legSwing;

            float descendFlare = hoverWeight * degToRad(JetpackClientConfig.HOVER_DESCEND_ARM_DEGREES.get())
                    * Math.max(0.0F, -vertical);
            model.rightArm.zRot += descendFlare;
            model.leftArm.zRot -= descendFlare;
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

        // Counter-rotate the head against the body lean (clamped to a plausible neck bend) so the
        // wearer keeps looking where the camera looks while the body flattens out. Note the sign
        // asymmetry between the two terms: LivingEntityRenderer applies poseStack.scale(-1, -1, 1)
        // before the parts are posed, which is a 180-degree rotation about Z, so part-space X is
        // flipped relative to PoseStack space while Z is not.
        float maxCounter = degToRad(JetpackClientConfig.HEAD_COUNTER_MAX_DEGREES.get());
        model.head.xRot += Mth.clamp(-state.renderBodyPitchDeg() * Mth.DEG_TO_RAD, -maxCounter, maxCounter);
        model.head.zRot = Mth.clamp(state.renderBodyRollDeg() * Mth.DEG_TO_RAD, -maxCounter, maxCounter)
                * HEAD_COUNTER_ROLL_SCALE;

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
