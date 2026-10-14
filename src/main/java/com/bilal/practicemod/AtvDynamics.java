package com.bilal.practicemod;

/** Fixed 20 Hz tire forces and damped chassis springs; independent of rendering and Minecraft. */
public final class AtvDynamics {
    public static final double TOP_SPEED = .62; // 12.4 blocks/second
    public static final double REVERSE_SPEED = .20;
    public double vx, vz, yaw, steer, forwardSpeed;
    public double heave, pitch, roll;
    public static final double WHEEL_DROOP = .14, WHEEL_COMPRESSION = .12;
    public final double[] wheelTravel = new double[4];
    private final double[] wheelVelocity = new double[4];
    private double pitchTarget, rollTarget;
    private double heaveVelocity, pitchVelocity, rollVelocity, previousForward;

    public void drive(boolean grounded, boolean wet, double throttle, double steering) {
        steer += (steering - steer) * .28;
        double angle = Math.toRadians(yaw);
        double forward = -Math.sin(angle) * vx + Math.cos(angle) * vz;
        if (grounded && !wet) {
            // Steering authority builds with speed, reverses when backing up, and tapers at speed.
            yaw -= steer * Math.signum(forward) * Math.min(1, Math.abs(forward) / .16)
                    * (4.5 - Math.min(1, Math.abs(forward) / TOP_SPEED) * 1.7);
            angle = Math.toRadians(yaw);
            forward = -Math.sin(angle) * vx + Math.cos(angle) * vz;
            double lateral = Math.cos(angle) * vx + Math.sin(angle) * vz;
            // Opposing throttle is a brake first; only engage reverse after coming to rest.
            if (throttle * forward < -.008) {
                forward = approach(forward, 0, .046 * Math.abs(throttle));
            } else {
                double limit = throttle < 0 ? REVERSE_SPEED : TOP_SPEED;
                forward += throttle * .034 * Math.max(0, 1 - Math.abs(forward) / limit);
            }
            forward = approach(forward, 0, .0035) * .995;
            lateral *= .66 + Math.min(1, Math.abs(forward) / TOP_SPEED) * .13;
            vx = -Math.sin(angle) * forward + Math.cos(angle) * lateral;
            vz = Math.cos(angle) * forward + Math.sin(angle) * lateral;
        } else {
            // A jump retains momentum; neither steering nor throttle grants air propulsion.
            vx *= wet ? .87 : .997;
            vz *= wet ? .87 : .997;
        }
        forwardSpeed = -Math.sin(angle) * vx + Math.cos(angle) * vz;
    }

    public void suspension(double rise, double impact, double terrainPitch, double terrainRoll, boolean grounded,
                           double travelForward, double travelY) {
        // A step compresses the springs. A landing adds an impulse proportional to impact speed.
        heaveVelocity -= Math.max(0, rise) * .14 + Math.min(.9, impact) * .17;
        heaveVelocity += -heave * .23;
        heaveVelocity *= .74;
        heave = clamp(heave + heaveVelocity, -.18, .16);
        if (Math.abs(heave) >= .159 && heave * heaveVelocity > 0) heaveVelocity *= .35;
        double acceleration = forwardSpeed - previousForward;
        previousForward = forwardSpeed;
        // Keep angular momentum across step-up, takeoff and landing. Collision step-up
        // is instantaneous, so its one-tick travel angle is not a chassis orientation.
        double targetPitch = terrainPitch - acceleration * 70;
        if (!grounded) {
            double trajectory = Math.abs(travelForward) > .025
                    ? -Math.toDegrees(Math.atan2(travelY, Math.abs(travelForward)))
                        * Math.signum(travelForward) : 0;
            targetPitch = clamp(trajectory, -35, 35);
        }
        if (rise > .01 && Math.abs(travelForward) > .025) {
            // A finite axle impulse starts the climb without snapping to its travel angle.
            pitchVelocity -= Math.signum(travelForward) * Math.min(2.2, rise * 2.2);
        }
        pitchTarget += (clamp(targetPitch, -35, 35) - pitchTarget) * (grounded ? .32 : .18);
        rollTarget += ((grounded ? clamp(terrainRoll - steer * forwardSpeed * 15, -18, 18) : 0)
                - rollTarget) * .3;
        pitchVelocity = clamp((pitchVelocity + (pitchTarget - pitch) * (grounded ? .12 : .055))
                * (grounded ? .72 : .86), -4, 4);
        rollVelocity = clamp((rollVelocity + (rollTarget - roll) * .12) * .72, -3, 3);
        pitch = clamp(pitch + pitchVelocity, -40, 40);
        roll = clamp(roll + rollVelocity, -21, 21);
        if (Math.abs(pitch) >= 40 && pitch * pitchVelocity > 0) pitchVelocity = 0;
        if (Math.abs(roll) >= 21 && roll * rollVelocity > 0) rollVelocity = 0;
    }

    /** Contact demand converted into chassis-local suspension travel, with mechanical stops. */
    public void wheels(double[] ground, double[] x, double[] z, double hubHeight, boolean grounded) {
        double p = Math.toRadians(pitch), r = Math.toRadians(roll);
        double up = Math.cos(p) * Math.cos(r);
        for (int i = 0; i < 4; i++) {
            // Matches the renderer's pitch * negative-roll transform.
            double hubY = (-Math.sin(r) * x[i] + Math.cos(r) * hubHeight) * Math.cos(p)
                    - Math.sin(p) * z[i] + heave;
            double target = grounded && Double.isFinite(ground[i])
                    ? clamp((ground[i] + hubHeight - hubY) / up, -WHEEL_DROOP, WHEEL_COMPRESSION)
                    : -WHEEL_DROOP;
            wheelVelocity[i] = (wheelVelocity[i] + (target - wheelTravel[i]) * .38) * .58;
            wheelTravel[i] = clamp(wheelTravel[i] + wheelVelocity[i], -WHEEL_DROOP, WHEEL_COMPRESSION);
            if ((wheelTravel[i] <= -WHEEL_DROOP && wheelVelocity[i] < 0)
                    || (wheelTravel[i] >= WHEEL_COMPRESSION && wheelVelocity[i] > 0)) wheelVelocity[i] = 0;
        }
    }

    public static double bumpLaunch(double rise, double speed) {
        return rise > .09 && speed > .18 ? Math.min(.34, .06 + rise * .16 + speed * .19) : 0;
    }
    private static double approach(double value, double target, double step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }
    private static double clamp(double value, double low, double high) { return Math.max(low, Math.min(high, value)); }
}
