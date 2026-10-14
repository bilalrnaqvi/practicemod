package com.bilal.practicemod;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Connects the vanilla player rig and the custom glider to one shared grip-space animation.
 *
 * <p>The whole player rotates about the upper hand on the umbrella shaft. The
 * glider layer inherits that transform, removes the body's delayed attitude, and applies the
 * glider's faster attitude at the animated right palm. The gripping hand therefore stays at the pivot while
 * the canopy pitches and banks independently, rather than the glider and player being two unrelated
 * models that merely happen to overlap.</p>
 */
public final class UmbrellaGliderPoseRenderer {
    private static final float PIXEL = 1.0F / 16.0F;

    private static final float MIN_WEIGHT = 0.002F;
    private static final float HANG_ARM_X = -2.94F;
    private static final float HANG_ARM_ROLL = 0.35F;
    private static final float HANG_LEG_SPREAD = 2.0F * Mth.DEG_TO_RAD;

    // Right palm in the settled pose, before the renderer flips model X/Y. Use the same
    // suspension point for body banking and canopy banking; the load stays on one shoulder.
    private static final Vector3f REST_GRIP = new Vector3f(-1.0F, 10.0F, 0.0F)
            .rotate(new Quaternionf().rotationZYX(HANG_ARM_ROLL, 0.0F, HANG_ARM_X))
            .add(-5.0F, 2.0F, 0.0F).mul(PIXEL);

    private UmbrellaGliderPoseRenderer() {
    }

    /** Called from PlayerRendererMixin after vanilla has established body yaw. */
    public static void applyBodyRotation(AbstractClientPlayer player, PoseStack poseStack,
            float partialTick) {
        UmbrellaGliderVisualState state = UmbrellaGliderAnimationController.get(player.getId());
        if (state == null || !state.shouldRender()) {
            return;
        }

        state.sampleRenderPose(partialTick);
        if (hasCompetingVanillaPose(player, partialTick)) {
            state.suppressRenderPlayerPose();
            return;
        }

        float bodyWeight = state.renderBodyWeight();
        if (bodyWeight < MIN_WEIGHT) {
            return;
        }

        // Bob is shared by player and glider. Their attitudes differ, but both rotate around the
        // grip so the gripping palm stays planted while the body behaves like a short, weighted pendulum.
        poseStack.translate(0.0F, state.renderBob(), 0.0F);
        poseStack.translate(-REST_GRIP.x, 1.501F - REST_GRIP.y, REST_GRIP.z);
        poseStack.mulPose(worldAttitude(state.renderBodyPitch(), 0.0F, state.renderBodyRoll()));
        poseStack.translate(REST_GRIP.x, -(1.501F - REST_GRIP.y), -REST_GRIP.z);
        state.markRenderBodyApplied();
    }

    /**
     * Called by UmbrellaGliderLayer in model space. It converts the desired world-space attitude
     * through LivingEntityRenderer's 180-degree Z flip, then replaces the inherited body attitude
     * with the leading glider attitude at the animated right palm.
     */
    static void applyGliderAttitude(PoseStack poseStack, UmbrellaGliderVisualState state,
            PlayerModel<?> playerModel) {
        ModelPart arm = playerModel.rightArm;
        Vector3f grip = palmPosition(arm, -1.0F).mul(PIXEL);
        Quaternionf relative = relativeGliderAttitude(state);
        if (!state.renderBodyApplied()) {
            poseStack.translate(0.0F, -state.renderBob(), 0.0F);
        }

        // Translate to the actual animated palm BEFORE changing canopy attitude. This also
        // keeps contact through the reaching clip and supports both classic and slim skins.
        poseStack.translate(grip.x, grip.y, grip.z);
        poseStack.mulPose(relative);
        poseStack.translate(0.0F, -10.5F * PIXEL, 0.0F);
    }

    /** Called from PlayerModelMixin after vanilla has authored walk/fall/item poses. */
    public static void applyLimbPose(PlayerModel<?> model, LivingEntity entity) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }
        UmbrellaGliderVisualState state = UmbrellaGliderAnimationController.get(player.getId());
        if (state == null || !state.shouldRender()) {
            return;
        }

        // renderHand reuses PlayerModel.setupAnim without PlayerRenderer.setupRotations. Never let
        // a stale third-person suspension sample bend the first-person hand.
        Minecraft minecraft = Minecraft.getInstance();
        if (player == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }

        float armWeight = state.renderArmWeight();
        float bodyWeight = state.renderBodyWeight();
        float legWeight = state.renderLegWeight();
        if (Math.max(armWeight, Math.max(bodyWeight, legWeight)) < MIN_WEIGHT) {
            return;
        }

        // Remove crouch/fall/walk offsets as the torso settles under the grip. Head yaw and pitch
        // remain vanilla-owned, so looking around continues to work in front and back views.
        model.body.xRot = Mth.lerp(bodyWeight, model.body.xRot, 0.0F);
        model.body.yRot = Mth.lerp(bodyWeight, model.body.yRot, 0.0F);
        model.body.zRot = Mth.lerp(bodyWeight, model.body.zRot, 0.0F);
        model.body.y = Mth.lerp(bodyWeight, model.body.y, 0.0F);
        model.body.z = Mth.lerp(bodyWeight, model.body.z, 0.0F);
        model.head.y = Mth.lerp(bodyWeight, model.head.y, 0.0F);
        model.head.z = Mth.lerp(bodyWeight, model.head.z, 0.0F);

        model.rightArm.y = Mth.lerp(armWeight, model.rightArm.y, 2.0F);
        model.leftArm.y = Mth.lerp(armWeight, model.leftArm.y, 2.0F);
        model.rightArm.z = Mth.lerp(armWeight, model.rightArm.z, 0.0F);
        model.leftArm.z = Mth.lerp(armWeight, model.leftArm.z, 0.0F);

        // The gripping arm holds its pose independently of canopy banking. The canopy is
        // attached to the resulting palm, so procedural motion cannot pull the handle away.
        model.rightArm.xRot = Mth.lerp(armWeight, model.rightArm.xRot, HANG_ARM_X);
        model.rightArm.yRot = Mth.lerp(armWeight, model.rightArm.yRot, 0.0F);
        model.rightArm.zRot = Mth.lerp(armWeight, model.rightArm.zRot, HANG_ARM_ROLL);

        // Stack the second hand lower on the SAME shaft. Solve its contact against the
        // actual banked shaft, rather than assigning two unrelated overhead arm angles.
        // Minecraft has rigid arms, so the lower contact slides along the handle just enough
        // to preserve arm length while the body swings beneath the umbrella.
        Vector3f upperGrip = palmPosition(model.rightArm, -1.0F);
        Vector3f shaftDirection = new Vector3f(0.0F, 1.0F, 0.0F)
                .rotate(relativeGliderAttitude(state));
        Vector3f shoulderToGrip = new Vector3f(upperGrip)
                .sub(model.leftArm.x, model.leftArm.y, model.leftArm.z);
        float alongShaft = shoulderToGrip.dot(shaftDirection);
        float discriminant = alongShaft * alongShaft - shoulderToGrip.lengthSquared() + 101.0F;
        float lowerContact = Math.max(0.0F, -alongShaft - (float) Math.sqrt(Math.max(0.0F, discriminant)));
        Vector3f reach = shoulderToGrip.fma(lowerContact, shaftDirection);
        Vector3f angles = new Quaternionf().rotationTo(new Vector3f(1.0F, 10.0F, 0.0F), reach)
                .getEulerAnglesZYX(new Vector3f());
        model.leftArm.xRot = lerpAngle(armWeight, model.leftArm.xRot, angles.x);
        model.leftArm.yRot = lerpAngle(armWeight, model.leftArm.yRot, angles.y);
        model.leftArm.zRot = lerpAngle(armWeight, model.leftArm.zRot, angles.z);

        // Legs remain rigid Minecraft limbs, but lose the falling/running cycle and trail with a
        // tiny opposing idle swing. The asymmetry keeps the lower silhouette from reading as a pole.
        model.rightLeg.y = Mth.lerp(legWeight, model.rightLeg.y, 12.0F);
        model.leftLeg.y = Mth.lerp(legWeight, model.leftLeg.y, 12.0F);
        model.rightLeg.z = Mth.lerp(legWeight, model.rightLeg.z, 0.0F);
        model.leftLeg.z = Mth.lerp(legWeight, model.leftLeg.z, 0.0F);
        float idleLeg = state.renderIdleLegSwing() * Mth.DEG_TO_RAD;
        model.rightLeg.xRot = Mth.lerp(legWeight, model.rightLeg.xRot, 0.12F + idleLeg);
        model.leftLeg.xRot = Mth.lerp(legWeight, model.leftLeg.xRot, 0.24F - idleLeg * 0.75F);
        model.rightLeg.yRot = Mth.lerp(legWeight, model.rightLeg.yRot, 0.0F);
        model.leftLeg.yRot = Mth.lerp(legWeight, model.leftLeg.yRot, 0.0F);
        model.rightLeg.zRot = Mth.lerp(legWeight, model.rightLeg.zRot, HANG_LEG_SPREAD);
        model.leftLeg.zRot = Mth.lerp(legWeight, model.leftLeg.zRot, -HANG_LEG_SPREAD);

        // Partial counter-rotation preserves readable head tracking without creating a rubber neck.
        model.head.xRot -= state.renderBodyPitch() * Mth.DEG_TO_RAD * 0.55F;
        model.head.zRot -= state.renderBodyRoll() * Mth.DEG_TO_RAD * 0.48F;

        // PlayerModel copied the outer skin before this tail injection. Re-copy every part touched
        // here so sleeves, jacket, hat and pants remain welded to the base skin.
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
    }

    private static float lerpAngle(float weight, float from, float to) {
        return from + weight * Mth.wrapDegrees((to - from) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
    }

    private static Vector3f palmPosition(ModelPart arm, float palmX) {
        return new Vector3f(palmX, 10.0F, 0.0F)
                .rotate(new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot))
                .add(arm.x, arm.y, arm.z);
    }

    private static Quaternionf relativeGliderAttitude(UmbrellaGliderVisualState state) {
        Quaternionf glider = modelAttitude(state.renderGliderPitch(), state.renderGliderYaw(),
                state.renderGliderRoll());
        if (!state.renderBodyApplied()) {
            return glider;
        }
        // Two planted hands limit how far the canopy can rotate independently of the body.
        // Keep the shaft within the rigid left arm's reach even during the deployment swing.
        float pitch = state.renderBodyPitch() + Mth.clamp(
                state.renderGliderPitch() - state.renderBodyPitch(), -8.0F, 8.0F);
        float roll = state.renderBodyRoll() + Mth.clamp(
                state.renderGliderRoll() - state.renderBodyRoll(), -8.0F, 8.0F);
        return modelAttitude(state.renderBodyPitch(), 0.0F, state.renderBodyRoll())
                .conjugate().mul(modelAttitude(pitch, state.renderGliderYaw(), roll));
    }

    private static boolean hasCompetingVanillaPose(AbstractClientPlayer player, float partialTick) {
        return player.isFallFlying()
                || player.getSwimAmount(partialTick) > 0.0F
                || player.isSleeping()
                || player.isPassenger()
                || player.isAutoSpinAttack()
                || player.isSpectator()
                || player.deathTime > 0;
    }

    private static Quaternionf worldAttitude(float pitchDegrees, float yawDegrees, float rollDegrees) {
        return new Quaternionf().rotationZYX(rollDegrees * Mth.DEG_TO_RAD,
                yawDegrees * Mth.DEG_TO_RAD, pitchDegrees * Mth.DEG_TO_RAD);
    }

    private static Quaternionf modelAttitude(float pitchDegrees, float yawDegrees, float rollDegrees) {
        // Conjugating through renderer scale(-1,-1,1) flips X/Y axes but leaves Z unchanged.
        return new Quaternionf().rotationZYX(rollDegrees * Mth.DEG_TO_RAD,
                -yawDegrees * Mth.DEG_TO_RAD, -pitchDegrees * Mth.DEG_TO_RAD);
    }
}
