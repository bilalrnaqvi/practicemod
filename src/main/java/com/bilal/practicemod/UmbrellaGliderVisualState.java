package com.bilal.practicemod;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Per-player, client-only animation state for the umbrella rig.
 *
 * <p>The synchronized deployed attachment is deliberately the only input from gameplay. Everything
 * else is reconstructed from the player's observed position and body rotation, which makes this
 * state work for the owning player and remote players without adding cosmetic network traffic.
 * The glider channels respond quickly while the hanging-body channels use a softer spring; that
 * small difference is what gives direction changes weight without allowing the two silhouettes to
 * separate.</p>
 */
final class UmbrellaGliderVisualState {
    static final float DEPLOY_TICKS = 10.0F;
    static final float CLOSE_TICKS = 6.0F;

    enum Phase {
        INACTIVE,
        DEPLOYING,
        GLIDING,
        CLOSING
    }

    // Authored canopy poses expressed against normalized deployment progress. The small scale and
    // rotation overshoots make the mechanical sectors lock open crisply instead of simply scaling.
    private static final float[] CANOPY_TIMES = {0.0F, 0.08F, 0.2F, 0.34F, 0.62F, 0.82F, 1.0F};
    private static final float[] CANOPY_ROTATION = {-72.0F, -69.0F, -62.0F, -52.0F, -18.0F, 5.0F, 0.0F};
    private static final float[] CANOPY_SCALE = {0.28F, 0.34F, 0.78F, 1.04F, 1.03F, 1.02F, 1.0F};

    private static final float MAX_FORWARD_PITCH = 9.0F;
    private static final float MAX_BANK = 10.0F;
    private static final float MAX_YAW_LEAD = 4.0F;
    // A restrained canopy turn: 12 degrees/second at Minecraft's 20 ticks/second.
    private static final float CANOPY_SPIN_PER_TICK = 0.6F;

    private float previousCanopySpin;
    private float canopySpin;

    private float previousDeployment;
    private float deployment;
    private float previousGliderPitch;
    private float gliderPitch;
    private float previousGliderRoll;
    private float gliderRoll;
    private float previousGliderYaw;
    private float gliderYaw;
    private float previousBodyPitch;
    private float bodyPitch;
    private float previousBodyRoll;
    private float bodyRoll;
    private float previousAge;
    private float age;

    private float lastVelocityX;
    private float lastVelocityZ;
    private float lastBodyYaw;
    private boolean hasMotionSample;
    private Phase phase = Phase.INACTIVE;

    // Per-render scratch. setupRotations has partialTick while PlayerModel.setupAnim does not, so
    // the whole-body pass samples once and the limb/layer passes consume these exact same values.
    private boolean renderBodyApplied;
    private float renderArmWeight;
    private float renderBodyWeight;
    private float renderLegWeight;
    private float renderBodyPitch;
    private float renderBodyRoll;
    private float renderGliderPitch;
    private float renderGliderRoll;
    private float renderGliderYaw;
    private float renderBob;
    private float renderIdleLegSwing;

    void tick(Player player, boolean deployed) {
        capturePreviousValues();

        if (deployed) {
            this.deployment = Math.min(1.0F, this.deployment + 1.0F / DEPLOY_TICKS);
            this.phase = this.deployment >= 1.0F ? Phase.GLIDING : Phase.DEPLOYING;
        } else {
            this.deployment = Math.max(0.0F, this.deployment - 1.0F / CLOSE_TICKS);
            this.phase = this.deployment <= 0.0F ? Phase.INACTIVE : Phase.CLOSING;
        }

        this.age += 1.0F;
        advanceCanopySpin();
        updateMotion(player, deployed);
    }

    /** Clip-only driver used by the deterministic GameTest. */
    void tick(boolean deployed) {
        capturePreviousValues();
        if (deployed) {
            this.deployment = Math.min(1.0F, this.deployment + 1.0F / DEPLOY_TICKS);
            this.phase = this.deployment >= 1.0F ? Phase.GLIDING : Phase.DEPLOYING;
        } else {
            this.deployment = Math.max(0.0F, this.deployment - 1.0F / CLOSE_TICKS);
            this.phase = this.deployment <= 0.0F ? Phase.INACTIVE : Phase.CLOSING;
        }
        this.age += 1.0F;
        advanceCanopySpin();
        settleMotionChannels();
    }

    private void advanceCanopySpin() {
        // Integrate speed, rather than multiplying the angle by deployment, so closing
        // slows the canopy without unwinding it. Keep both samples on the same turn.
        this.canopySpin += CANOPY_SPIN_PER_TICK * smoothstep(0.5F, 1.0F, this.deployment);
        if (this.canopySpin >= 360.0F) {
            this.canopySpin -= 360.0F;
            this.previousCanopySpin -= 360.0F;
        }
    }

    private void capturePreviousValues() {
        this.previousCanopySpin = this.canopySpin;
        this.previousDeployment = this.deployment;
        this.previousGliderPitch = this.gliderPitch;
        this.previousGliderRoll = this.gliderRoll;
        this.previousGliderYaw = this.gliderYaw;
        this.previousBodyPitch = this.bodyPitch;
        this.previousBodyRoll = this.bodyRoll;
        this.previousAge = this.age;
    }

    private void updateMotion(Player player, boolean deployed) {
        float velocityX = finite((float) (player.getX() - player.xo));
        float velocityZ = finite((float) (player.getZ() - player.zo));
        float bodyYaw = Float.isFinite(player.yBodyRot) ? player.yBodyRot
                : (this.hasMotionSample ? this.lastBodyYaw : 0.0F);

        if (!this.hasMotionSample) {
            this.lastVelocityX = velocityX;
            this.lastVelocityZ = velocityZ;
            this.lastBodyYaw = bodyYaw;
            this.hasMotionSample = true;
        }

        float yawRadians = bodyYaw * Mth.DEG_TO_RAD;
        float sin = Mth.sin(yawRadians);
        float cos = Mth.cos(yawRadians);
        // Body-local frame used by LivingEntityRenderer: forward is -Z and right is +X after yaw.
        float forwardSpeed = velocityX * -sin + velocityZ * cos;
        float rightSpeed = velocityX * -cos + velocityZ * -sin;
        float accelerationX = velocityX - this.lastVelocityX;
        float accelerationZ = velocityZ - this.lastVelocityZ;
        float rightAcceleration = accelerationX * -cos + accelerationZ * -sin;
        float yawDelta = Mth.wrapDegrees(bodyYaw - this.lastBodyYaw);

        this.lastVelocityX = velocityX;
        this.lastVelocityZ = velocityZ;
        this.lastBodyYaw = bodyYaw;

        float active = deployed ? 1.0F : 0.0F;
        float forward = Mth.clamp(forwardSpeed / (float) UmbrellaGliderPhysics.HORIZONTAL_SPEED_CAP,
                -0.25F, 1.0F);
        float right = Mth.clamp(rightSpeed / (float) UmbrellaGliderPhysics.HORIZONTAL_SPEED_CAP,
                -1.0F, 1.0F);
        float turn = Mth.clamp(yawDelta / 12.0F + rightAcceleration / 0.08F * 0.45F
                + right * 0.18F, -1.0F, 1.0F);

        // Negative X pitch moves the canopy forward while letting the body hang behind the shared
        // grip. Glider channels deliberately lead the more heavily damped body channels.
        float targetGliderPitch = -MAX_FORWARD_PITCH * Math.max(0.0F, forward) * active;
        float targetGliderRoll = MAX_BANK * turn * active;
        float targetGliderYaw = MAX_YAW_LEAD * Mth.clamp(yawDelta / 10.0F, -1.0F, 1.0F) * active;
        this.gliderPitch = approach(this.gliderPitch, targetGliderPitch, 0.34F);
        this.gliderRoll = approach(this.gliderRoll, targetGliderRoll, 0.38F);
        this.gliderYaw = approach(this.gliderYaw, targetGliderYaw, 0.3F);

        // The body shares the grip pivot but reaches only part of the glider attitude and arrives a
        // few ticks later. Acceleration adds a restrained pendulum kick on abrupt direction changes.
        float forwardAcceleration = accelerationX * -sin + accelerationZ * cos;
        float targetBodyPitch = (targetGliderPitch * 0.42F + forwardAcceleration * 22.0F) * active;
        float targetBodyRoll = (targetGliderRoll * 0.4F + rightAcceleration * 30.0F) * active;
        this.bodyPitch = approach(this.bodyPitch, Mth.clamp(targetBodyPitch, -5.0F, 5.0F), 0.12F);
        this.bodyRoll = approach(this.bodyRoll, Mth.clamp(targetBodyRoll, -4.5F, 4.5F), 0.11F);
    }

    private void settleMotionChannels() {
        this.gliderPitch = approach(this.gliderPitch, 0.0F, 0.34F);
        this.gliderRoll = approach(this.gliderRoll, 0.0F, 0.38F);
        this.gliderYaw = approach(this.gliderYaw, 0.0F, 0.3F);
        this.bodyPitch = approach(this.bodyPitch, 0.0F, 0.12F);
        this.bodyRoll = approach(this.bodyRoll, 0.0F, 0.11F);
    }

    Phase phase() {
        return this.phase;
    }

    boolean shouldRender() {
        return this.deployment > 0.0F || this.previousDeployment > 0.0F;
    }

    float deployment(float partialTick) {
        return Mth.lerp(clampPartialTick(partialTick), this.previousDeployment, this.deployment);
    }

    float sectorRotationDegrees(float partialTick) {
        return sample(CANOPY_TIMES, CANOPY_ROTATION, deployment(partialTick));
    }

    float sectorScale(float partialTick) {
        return sample(CANOPY_TIMES, CANOPY_SCALE, deployment(partialTick));
    }

    float canopySpinDegrees(float partialTick) {
        return Mth.lerp(clampPartialTick(partialTick), this.previousCanopySpin, this.canopySpin);
    }

    void sampleRenderPose(float partialTick) {
        float frame = clampPartialTick(partialTick);
        float progress = Mth.lerp(frame, this.previousDeployment, this.deployment);
        float ageTicks = Mth.lerp(frame, this.previousAge, this.age);
        float attitudeWeight = smoothstep(0.5F, 0.92F, progress);

        this.renderArmWeight = smoothstep(0.0F, 0.48F, progress);
        this.renderBodyWeight = smoothstep(0.30F, 0.86F, progress);
        this.renderLegWeight = smoothstep(0.42F, 0.96F, progress);

        float gliderPitchValue = Mth.lerp(frame, this.previousGliderPitch, this.gliderPitch);
        float gliderRollValue = Mth.lerp(frame, this.previousGliderRoll, this.gliderRoll);
        float bodyPitchValue = Mth.lerp(frame, this.previousBodyPitch, this.bodyPitch);
        float bodyRollValue = Mth.lerp(frame, this.previousBodyRoll, this.bodyRoll);
        float calm = smoothstep(0.72F, 1.0F, progress);
        float idleA = Mth.sin(ageTicks * 0.09F);
        float idleB = Mth.sin(ageTicks * 0.061F + 1.7F);

        this.renderGliderPitch = gliderPitchValue * attitudeWeight + idleB * 0.45F * calm;
        this.renderGliderRoll = gliderRollValue * attitudeWeight + idleA * 0.65F * calm;
        this.renderGliderYaw = Mth.lerp(frame, this.previousGliderYaw, this.gliderYaw) * attitudeWeight;
        // Reach first, then catch the canopy load and settle into a permanent one-sided hang.
        float catchPulse = Mth.sin(smoothstep(0.38F, 1.0F, progress) * Mth.PI);
        this.renderBodyPitch = (bodyPitchValue - 4.0F - catchPulse * 7.0F)
                * this.renderBodyWeight + idleB * 0.22F * calm;
        this.renderBodyRoll = (bodyRollValue - 12.0F - catchPulse * 4.0F)
                * this.renderBodyWeight + idleA * 0.28F * calm;
        this.renderBob = idleA * 0.018F * calm;
        this.renderIdleLegSwing = idleB * 1.45F * calm;
        this.renderBodyApplied = false;
    }

    void markRenderBodyApplied() {
        this.renderBodyApplied = true;
    }

    void suppressRenderPlayerPose() {
        this.renderBodyApplied = false;
        this.renderArmWeight = 0.0F;
        this.renderBodyWeight = 0.0F;
        this.renderLegWeight = 0.0F;
        this.renderBodyPitch = 0.0F;
        this.renderBodyRoll = 0.0F;
    }

    boolean renderBodyApplied() {
        return this.renderBodyApplied;
    }

    float renderArmWeight() {
        return this.renderArmWeight;
    }

    float renderBodyWeight() {
        return this.renderBodyWeight;
    }

    float renderLegWeight() {
        return this.renderLegWeight;
    }

    float renderBodyPitch() {
        return this.renderBodyPitch;
    }

    float renderBodyRoll() {
        return this.renderBodyRoll;
    }

    float renderGliderPitch() {
        return this.renderGliderPitch;
    }

    float renderGliderRoll() {
        return this.renderGliderRoll;
    }

    float renderGliderYaw() {
        return this.renderGliderYaw;
    }

    float renderBob() {
        return this.renderBob;
    }

    float renderIdleLegSwing() {
        return this.renderIdleLegSwing;
    }

    private static float approach(float current, float target, float response) {
        return Mth.lerp(response, current, target);
    }

    private static float smoothstep(float start, float end, float value) {
        float normalized = Mth.clamp((value - start) / (end - start), 0.0F, 1.0F);
        return normalized * normalized * (3.0F - 2.0F * normalized);
    }

    private static float sample(float[] times, float[] values, float time) {
        if (time <= times[0]) {
            return values[0];
        }
        for (int index = 1; index < times.length; index++) {
            if (time <= times[index]) {
                float weight = (time - times[index - 1]) / (times[index] - times[index - 1]);
                return Mth.lerp(weight, values[index - 1], values[index]);
            }
        }
        return values[values.length - 1];
    }

    private static float clampPartialTick(float partialTick) {
        return Mth.clamp(partialTick, 0.0F, 1.0F);
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }
}
