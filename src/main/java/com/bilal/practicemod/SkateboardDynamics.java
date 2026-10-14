package com.bilal.practicemod;

/** Arcade tuning in blocks/tick. Kept independent of rendering and networking. */
public final class SkateboardDynamics {
    public static final double CRUISE_SPEED = 0.58;
    public static final double FAST_SPEED = 0.72;
    public static final double SPECIAL_SPEED = 0.86;
    public static final double GRAVITY = 0.055;
    public static final int MAX_CHARGE = 16;
    public static final int INPUT_TIMEOUT = 10;
    public static final int PUSH = 1, BRAKE = 2, SPRINT = 4, OLLIE = 8,
            FLIP = 16, GRAB = 32, GRIND = 64, MANUAL = 128, REVERT = 256, SPECIAL = 512;
    public static final int ACTIVE = 1024;
    public static final int INPUT_MASK = 2047;

    public static double speed(double current, boolean push, boolean brake, boolean sprint, boolean special) {
        if (brake) return Math.max(0, current - 0.065);
        double cap = special ? SPECIAL_SPEED : sprint ? FAST_SPEED : CRUISE_SPEED;
        if (push && current <= cap) return Math.min(cap, current + 0.028);
        return current < 0.012 ? 0 : current * 0.992;
    }

    public static double ollie(int charge, boolean special) {
        return 0.48 + 0.24 * Math.min(MAX_CHARGE, Math.max(0, charge)) / MAX_CHARGE
                + (special ? 0.07 : 0);
    }

    public static float wrap(float degrees) {
        float value = degrees % 360;
        if (value >= 180) value -= 360;
        if (value < -180) value += 360;
        return value;
    }

    public static float steer(float yaw, float target, double speed) {
        float limit = (float) (7.5 - Math.min(1, speed / FAST_SPEED) * 2.5);
        return yaw + Math.max(-limit, Math.min(limit, wrap(target - yaw)));
    }

    public static boolean cleanLanding(float spin) {
        return Math.abs(wrap(spin * 2) / 2) <= 48;
    }

    /** Momentum in the balance needle makes a long hold harder than alternating corrections. */
    public static float balanceVelocity(float velocity, float balance, float correction, int ticks) {
        float drift = (float) Math.sin(ticks * 0.12 + 0.7) * (0.0025F + Math.min(ticks, 600) * 0.000012F);
        return velocity * 0.94F + balance * 0.008F + drift + correction * 0.007F;
    }

    private SkateboardDynamics() {}
}
