package com.bilal.practicemod;

/** Tick-driven visual state; no changes to swimming physics or rider/camera transforms. */
public final class MechanicalDolphinAnimation {
    public record Pose(float tail, float flukes, float portPitch, float portRoll,
            float starboardPitch, float starboardRoll, float dorsalYaw, float dorsalRoll) {}
    private double phase, previousPhase;
    private float swim, previousSwim, water, previousWater, turn, previousTurn, dive, previousDive;
    private boolean initialized;

    public void tick(double speed, boolean wet, float yawChange, float pitchChange) {
        if (!initialized) {
            water = wet ? 1 : 0;
            initialized = true;
        }
        previousPhase = phase;
        previousSwim = swim;
        previousWater = water;
        previousTurn = turn;
        previousDive = dive;
        // Ignore retrieval drift; blend over several ticks instead of snapping on packets.
        float target = wet ? clamp((float)(speed - .10) / .72F, 0, 1) : 0;
        swim += (target - swim) * .16F;
        water += ((wet ? 1 : 0) - water) * .20F;
        turn += (clamp(yawChange / 7, -1, 1) - turn) * .22F;
        dive += (clamp(pitchChange / 5, -1, 1) - dive) * .22F;
        // Integrate frequency: changing speed never reinterprets the elapsed age/phase.
        phase += (.075 + .285 * swim) * water;
    }

    public Pose sample(float partial) {
        partial = clamp(partial, 0, 1);
        return cycle(previousPhase + (phase - previousPhase) * partial,
                lerp(previousSwim, swim, partial), lerp(previousWater, water, partial),
                lerp(previousTurn, turn, partial), lerp(previousDive, dive, partial));
    }

    /** Steady cycle used by editable Blockbench clips and runtime-mesh previews. */
    public static Pose steady(float ageTicks, float motion) {
        float swim = clamp(motion, 0, 1);
        return cycle(ageTicks * (.075 + .285 * swim), swim, 1, 0, 0);
    }

    public static Pose cycle(double phase, float swim, float water, float turn, float dive) {
        // Small second harmonic gives the power/recovery stroke different timing.
        float stroke = (sin(phase) + .12F * swim * sin(2 * phase)) / (1 + .12F * swim);
        float tail = water * (2 + 18 * swim) * stroke;
        // The flukes feather against the peduncle, with delayed follow-through.
        float flukes = -water * (1 + 9 * swim) * sin(phase - .65);
        float trim = water * (-2 * swim + 1.1F * sin(phase - .4) + 4 * dive);
        float spread = water * (1.8F + 2.2F * swim);
        float port = -spread - water * (1.2F + .8F * swim) * sin(phase + .35) + water * 4 * turn;
        float starboard = spread + water * (1.2F + .8F * swim) * sin(phase - .35) + water * 4 * turn;
        // Dorsal fin is a stabilizer: restrained flex, not a second propulsive paddle.
        float dorsalRoll = water * ((.35F + .55F * swim) * sin(phase + .6) - 1.5F * turn);
        return new Pose(tail, flukes, trim, port, trim, starboard, water * -2 * turn, dorsalRoll);
    }

    private static float sin(double angle) { return (float)Math.sin(angle); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
