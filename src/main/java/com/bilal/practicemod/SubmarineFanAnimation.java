package com.bilal.practicemod;

/** Per-vehicle, tick-driven rotor phase; rendering only interpolates this state. */
final class SubmarineFanAnimation {
    private float angle;
    private float step;

    void tick(double x, double y, double z, float yaw) {
        angle = (angle + step) % 360F;
        double speed = Math.sqrt(x * x + y * y + z * z);
        // Ignore teleport corrections and sub-millimetre network jitter.
        if (speed > 2 || speed < .001) speed = 0;
        double heading = Math.toRadians(yaw);
        double forward = -Math.sin(heading) * x + Math.cos(heading) * z;
        float target = (float) (Math.min(speed / .35, 1) * 40);
        if (forward < -.001) target = -target;
        step += (target - step) * .25F;
        if (target == 0 && Math.abs(step) < .01F) step = 0;
    }

    float angle(float partialTick) {
        return angle + step * partialTick;
    }
}
