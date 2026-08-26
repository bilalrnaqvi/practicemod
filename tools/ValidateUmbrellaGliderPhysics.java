package com.bilal.practicemod;

/** Dependency-free regression checks for the glider's terminal descent and horizontal cap. */
public final class ValidateUmbrellaGliderPhysics {
    private ValidateUmbrellaGliderPhysics() {
    }

    public static void main(String[] args) {
        double velocity = -1.5;
        double previous = velocity;
        for (int tick = 0; tick < 40; tick++) {
            velocity = UmbrellaGliderPhysics.nextVerticalVelocity(velocity);
            require(velocity >= previous, "opening must monotonically arrest a fast fall");
            require(velocity < 0.0, "the glider must never create hover or lift");
            previous = velocity;
        }
        require(closeTo(velocity, UmbrellaGliderPhysics.TARGET_DESCENT),
                "fast falls must converge to terminal descent");

        velocity = -0.001;
        for (int tick = 0; tick < 20; tick++) {
            velocity = UmbrellaGliderPhysics.nextVerticalVelocity(velocity);
            require(velocity < 0.0, "slow falls must remain downward");
        }
        require(closeTo(velocity, UmbrellaGliderPhysics.TARGET_DESCENT),
                "slow falls must settle instead of hovering indefinitely");

        double scale = UmbrellaGliderPhysics.horizontalCapScale(0.8, 0.6);
        require(closeTo(Math.hypot(0.8 * scale, 0.6 * scale),
                UmbrellaGliderPhysics.HORIZONTAL_SPEED_CAP), "horizontal speed must be capped");
        require(UmbrellaGliderPhysics.horizontalCapScale(0.1, 0.1) == 1.0,
                "sub-cap steering must remain unchanged");
        require(UmbrellaGliderPhysics.horizontalCapScale(Double.NaN, 0.0) == 0.0,
                "invalid motion must be scrubbed");
    }

    private static boolean closeTo(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-9;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
