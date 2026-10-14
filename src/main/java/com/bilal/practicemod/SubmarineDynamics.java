package com.bilal.practicemod;

/** Tuned at 20 ticks/second: damped trim, forward-flow dive planes, and slow ballast control. */
final class SubmarineDynamics {
    record State(float pitch, float pitchRate, double verticalSpeed) { }

    private SubmarineDynamics() { }

    static State step(float pitch, float pitchRate, double verticalSpeed, double forwardSpeed, int command) {
        double flow = Math.min(1, Math.abs(forwardSpeed) / .25);
        // At rest ballast changes depth slowly. Under way, dive planes establish a diagonal path.
        // Reversing the propeller reverses which trim produces upward travel.
        double direction = forwardSpeed < -.025 ? -1 : 1;
        double targetPitch = -command * direction * (8 + 16 * flow);
        float rate = (float) clamp(pitchRate * .76 + (targetPitch - pitch) * .022, -.85, .85);
        float nextPitch = (float) clamp(pitch + rate, -26, 26);
        double planeSpeed = -forwardSpeed * Math.tan(Math.toRadians(nextPitch));
        double targetSpeed = clamp(planeSpeed + command * .035, -.18, .18);
        // Added water mass and drag: no instantaneous rise, stop, or reversal.
        double nextSpeed = verticalSpeed + clamp((targetSpeed - verticalSpeed) * .075, -.004, .004);
        if (command == 0 && Math.abs(nextPitch) < .02 && Math.abs(rate) < .02 && Math.abs(nextSpeed) < .0001) {
            return new State(0, 0, 0);
        }
        return new State(nextPitch, rate, nextSpeed);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
