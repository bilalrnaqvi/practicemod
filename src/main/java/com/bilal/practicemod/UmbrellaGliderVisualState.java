package com.bilal.practicemod;

import net.minecraft.util.Mth;

/**
 * Client-only readout of the synchronized gameplay flag. One scalar tracks the authored clip time,
 * so an open/close reversal resumes from the current rig pose instead of snapping or restarting.
 */
final class UmbrellaGliderVisualState {
    static final float TRANSITION_TICKS = 16.0F;

    enum Phase {
        CLOSED,
        OPENING,
        OPEN,
        CLOSING
    }

    private static final float[] OPEN_TIMES = {0.0F, 0.1F, 0.2F, 0.28F, 0.52F, 0.68F, 0.8F};
    private static final float[] OPEN_ROTATION = {-72.0F, -69.0F, -62.0F, -52.0F, -18.0F, 5.0F, 0.0F};
    private static final float[] OPEN_SCALE = {0.28F, 0.34F, 0.78F, 1.04F, 1.03F, 1.02F, 1.0F};

    private static final float[] CLOSE_TIMES = {0.0F, 0.12F, 0.28F, 0.52F, 0.6F, 0.7F, 0.8F};
    private static final float[] CLOSE_ROTATION = {0.0F, 5.0F, -18.0F, -52.0F, -62.0F, -69.0F, -72.0F};
    private static final float[] CLOSE_SCALE = {1.0F, 1.02F, 1.03F, 1.04F, 0.78F, 0.34F, 0.28F};

    private float previousTicks;
    private float ticks;
    private boolean openingDirection;
    private Phase phase = Phase.CLOSED;

    void tick(boolean deployed) {
        this.previousTicks = this.ticks;
        this.openingDirection = deployed;
        if (deployed) {
            this.ticks = Math.min(TRANSITION_TICKS, this.ticks + 1.0F);
            this.phase = this.ticks >= TRANSITION_TICKS ? Phase.OPEN : Phase.OPENING;
        } else {
            this.ticks = Math.max(0.0F, this.ticks - 1.0F);
            this.phase = this.ticks <= 0.0F ? Phase.CLOSED : Phase.CLOSING;
        }
    }

    Phase phase() {
        return this.phase;
    }

    boolean shouldRender() {
        return this.ticks > 0.0F || this.previousTicks > 0.0F;
    }

    float sectorRotationDegrees(float partialTick) {
        return samplePose(partialTick, OPEN_ROTATION, CLOSE_ROTATION);
    }

    float sectorScale(float partialTick) {
        return samplePose(partialTick, OPEN_SCALE, CLOSE_SCALE);
    }

    private float samplePose(float partialTick, float[] openingValues, float[] closingValues) {
        float deploymentTicks = Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), this.previousTicks, this.ticks);
        if (this.openingDirection) {
            return sample(OPEN_TIMES, openingValues, deploymentTicks / 20.0F);
        }
        float closingTime = (TRANSITION_TICKS - deploymentTicks) / 20.0F;
        return sample(CLOSE_TIMES, closingValues, closingTime);
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
}
