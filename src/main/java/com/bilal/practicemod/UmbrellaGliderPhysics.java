package com.bilal.practicemod;

/** Pure, deterministic movement helpers shared by gameplay and the standalone validation tool. */
final class UmbrellaGliderPhysics {
    static final double TARGET_DESCENT = -0.12;
    static final double OPENING_BRAKE_PER_TICK = 0.10;
    static final double SETTLING_DESCENT_PER_TICK = 0.015;
    static final double HORIZONTAL_SPEED_CAP = 0.45;

    private UmbrellaGliderPhysics() {
    }

    /**
     * Moves downward velocity toward a terminal descent without ever producing lift. A fast fall is
     * arrested over several ticks so opening feels smooth, while a very slow fall settles toward
     * the same negative terminal speed instead of becoming an indefinite hover.
     */
    static double nextVerticalVelocity(double current) {
        if (!Double.isFinite(current)) {
            return TARGET_DESCENT;
        }
        if (current < TARGET_DESCENT) {
            return Math.min(TARGET_DESCENT, current + OPENING_BRAKE_PER_TICK);
        }
        return Math.max(TARGET_DESCENT, current - SETTLING_DESCENT_PER_TICK);
    }

    /** Returns the multiplier that brings an X/Z vector under the glider's speed cap. */
    static double horizontalCapScale(double x, double z) {
        double speed = Math.hypot(x, z);
        if (!Double.isFinite(speed)) {
            return 0.0;
        }
        return speed > HORIZONTAL_SPEED_CAP && speed > 1.0E-6
                ? HORIZONTAL_SPEED_CAP / speed
                : 1.0;
    }
}
