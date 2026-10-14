package com.bilal.practicemod;

/** Client motion-driven controls, including interpolated movement of remote vehicles. */
final class SubmarineJoystickAnimation {
    private float previousPitch, previousRoll, pitch, roll;

    void tick(double x, double y, double z, float yaw, float yawDelta) {
        previousPitch = pitch;
        previousRoll = roll;
        double turn = Math.IEEEremainder(yawDelta, 360);
        double targetPitch = 0, targetRoll = 0;
        // Ignore teleports/corrections and tiny positional jitter, as with the fans.
        if (x * x + y * y + z * z <= 4 && Math.abs(turn) <= 45) {
            double heading = Math.toRadians(yaw);
            double forward = -Math.sin(heading) * x + Math.cos(heading) * z;
            if (Math.abs(forward) < .001) forward = 0;
            if (Math.abs(y) < .001) y = 0;
            if (Math.abs(turn) < .02) turn = 0;
            // Model forward is -Z: push for forward/dive, pull for reverse/climb.
            targetPitch = -clamp(forward / .35) * 14 + clamp(y / .18) * 12;
            // Positive Minecraft yaw turns left (model +X after the hull yaw transform).
            targetRoll = -clamp(turn / 4) * 18;
            double length = Math.hypot(targetPitch, targetRoll);
            if (length > 22) {
                targetPitch *= 22 / length;
                targetRoll *= 22 / length;
            }
        }
        pitch = ease(pitch, targetPitch);
        roll = ease(roll, targetRoll);
    }

    float pitch(float partialTick) { return previousPitch + (pitch - previousPitch) * partialTick; }
    float roll(float partialTick) { return previousRoll + (roll - previousRoll) * partialTick; }

    private static double clamp(double value) { return Math.max(-1, Math.min(1, value)); }

    private static float ease(float value, double target) {
        float next = (float) (value + (target - value) * .3);
        return target == 0 && Math.abs(next) < .01 ? 0 : next;
    }
}
