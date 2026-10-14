package com.bilal.practicemod;

/** Tick-driven animation clock shared by every rendering pass; no render-rate dependence. */
public final class SkateboardAnimation {
    private float previousPhase, phase;
    private float previousPush, push;
    private float previousRide, ride;
    private static final float STROKE_TICKS = 34;
    private boolean stroking;
    private int cruiseTicks;
    private float smoothedSpeed;
    private float previousCrouch, crouch, previousPitch, pitch, previousRoll, roll;

    public void tickAction(float crouchTarget, float pitchTarget, float rollTarget) {
        previousCrouch = crouch; previousPitch = pitch; previousRoll = roll;
        crouch = approach(crouch, crouchTarget, 0.7F);
        pitch = approach(pitch, pitchTarget, 5);
        roll = approach(roll, rollTarget, 6);
    }
    public float crouch(float t) { return previousCrouch + (crouch - previousCrouch) * t; }
    public float pitch(float t) { return previousPitch + (pitch - previousPitch) * t; }
    public float roll(float t) { return previousRoll + (roll - previousRoll) * t; }

    /** speedFraction is actual horizontal travel divided by the board's fast speed. */
    public void tick(boolean mounted, int mode, float speedFraction) {
        previousPhase = phase;
        previousPush = push;
        previousRide = ride;
        ride = approach(ride, mounted ? 1 : 0, 0.16F);
        float speed = Float.isFinite(speedFraction) ? Math.max(0, Math.min(1, speedFraction)) : 0;
        smoothedSpeed += (speed - smoothedSpeed) * 0.2F;
        boolean wantsPush = mounted && mode != 0 && speed > 0;
        if (!wantsPush) {
            stroking = false;
            cruiseTicks = 0;
            push = approach(push, 0, 0.14F);
            // Finish the visible recovery while fading out after stopping/leaving ground.
            phase = Math.min(1, phase + push / STROKE_TICKS);
            if (push == 0) previousPhase = phase = 0;
            if (!mounted) smoothedSpeed = 0;
            return;
        }

        // A completed stroke always brings the foot home before a speed change can
        // affect the next push. Faster travel lengthens the planted-foot cruise,
        // rather than stretching the stroke or speeding up the rider's leg.
        float cruiseDuration = 6 + 94 * smoothedSpeed * smoothedSpeed * smoothedSpeed * smoothedSpeed;
        if (!stroking && phase < 1) {
            // Resume an interrupted stroke from its current pose if input returns quickly.
            stroking = true;
            cruiseTicks = 0;
        } else if (!stroking && ++cruiseTicks >= cruiseDuration) {
            stroking = true;
            cruiseTicks = 0;
            previousPhase = phase = 0; // phase 1 and 0 are the same planted pose
        }
        push = approach(push, stroking ? 1 : 0, 0.12F);
        if (stroking) {
            phase = Math.min(1, phase + 1 / STROKE_TICKS);
            if (phase == 1) stroking = false;
        }
    }

    public Sample sample(float partialTick) {
        float t = Math.max(0, Math.min(1, partialTick));
        float p = previousPhase + (phase - previousPhase) * t;
        return new Sample(p - (float) Math.floor(p),
                previousPush + (push - previousPush) * t,
                previousRide + (ride - previousRide) * t);
    }

    private static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    public record Sample(float phase, float push, float ride) {}

    // Reference: nollieskateboarding.com/en/push (real-skater push_02/03 sequences).
    // phase, rear-foot X, Y below deck, Z, hip drop, arm swing, front-foot load.
    // Load turns the shoulders forward BEFORE lifting the rear foot; it settles
    // back sideways only after that foot is back on the rear bolts.
    private static final float[][] KEYS = {
        {0.00F, 0, 0, 5, 1.3F, 0, 0},
        {0.10F, 0, 0, 5, 2.2F, 0, 0.3F},
        {0.20F, -5.3F, -2, 2, 4.5F, 0.04F, 1},
        {0.32F, -5.3F, 4, -8, 5.8F, -0.10F, 1},
        {0.44F, -5.3F, 4, -1, 5.8F, 0.14F, 1},
        {0.64F, -5.3F, -4, 4, 4.6F, 0.12F, 1},
        {0.83F, 0, -1, 5, 2.6F, 0.02F, 0.35F},
        {0.91F, 0, 0, 5, 1.3F, 0, 0},
        {1.00F, 0, 0, 5, 1.3F, 0, 0}
    };

    public static float[] stroke(float phase) {
        for (int i = 1; i < KEYS.length; i++) {
            if (phase <= KEYS[i][0]) {
                float[] a = KEYS[i - 1], b = KEYS[i];
                float t = Math.max(0, (phase - a[0]) / (b[0] - a[0]));
                float linear = t;
                t = t * t * (3 - 2 * t);
                float[] values = new float[6];
                for (int j = 0; j < values.length; j++) values[j] = a[j + 1] + (b[j + 1] - a[j + 1]) * t;
                // During contact the sole sweeps back without easing to a stop on the ground.
                if (i == 4) values[2] = a[3] + (b[3] - a[3]) * linear;
                return values;
            }
        }
        return stroke(0);
    }
}
