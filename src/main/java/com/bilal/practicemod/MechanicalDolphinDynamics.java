package com.bilal.practicemod;

/** Fixed 20 Hz swimming and ballistic breaching. No client or Minecraft dependencies. */
public final class MechanicalDolphinDynamics {
    public double vx, vy, vz, yaw, pitch;
    public boolean launched, landed;
    private boolean armed = true;
    private int cooldown, submergedTicks, airTicks;

    public void step(double y, double surface, boolean wet, boolean grounded, boolean piloted,
            float throttle, float lookYaw, float lookPitch, boolean rise) {
        launched = landed = false;
        if (cooldown > 0) cooldown--;
        if (wet) {
            landed = airTicks >= 3;
            airTicks = 0;
            submergedTicks++;
            if (!rise && cooldown == 0 && submergedTicks >= 12) armed = true;
            if (piloted) {
                yaw += clamp(wrap(lookYaw - yaw), -7, 7);
                double desiredPitch = rise ? -55 : clamp(lookPitch, -80, 80);
                pitch += clamp(desiredPitch - pitch, -5, 5);
                double power = rise ? 1 : clamp(throttle, -0.4, 1);
                double heading = Math.toRadians(yaw), angle = Math.toRadians(pitch);
                double speed = power * .82;
                // Rudder authority redirects momentum gradually, retaining a smooth dive on entry.
                vx += (-Math.sin(heading) * Math.cos(angle) * speed - vx) * .115;
                vy += (-Math.sin(angle) * speed - vy) * .115;
                vz += (Math.cos(heading) * Math.cos(angle) * speed - vz) * .115;
                if (rise && armed && !grounded && Double.isFinite(surface)
                        && surface - (y + .30) < 1.15 && Math.sqrt(vx*vx + vy*vy + vz*vz) > .30) {
                    // A powered tail stroke happens IN water; air never adds thrust or resets velocity.
                    vy = Math.max(vy, .98);
                    double forward = Math.max(.46, Math.hypot(vx, vz));
                    vx = -Math.sin(heading) * forward;
                    vz = Math.cos(heading) * forward;
                    launched = true;
                    armed = false;
                    submergedTicks = 0;
                    cooldown = 35;
                }
            } else {
                vx *= .92; vz *= .92;
                // Empty craft returns slowly to the surface for retrieval.
                vy = Double.isFinite(surface)
                        ? clamp((surface - .31 - y) * .06 - vy * .25 + vy, -.12, .12)
                        : Math.min(.10, vy + .008);
                pitch *= .88;
            }
        } else {
            airTicks++;
            submergedTicks = 0;
            vx *= grounded ? .65 : .995;
            vz *= grounded ? .65 : .995;
            vy = grounded ? 0 : Math.max(-1.5, vy - .065);
            double trajectory = -Math.toDegrees(Math.atan2(vy, Math.max(.05, Math.hypot(vx, vz))));
            pitch += clamp((grounded ? 0 : trajectory) - pitch, -6, 6);
        }
    }

    private static double clamp(double value, double low, double high) { return Math.max(low, Math.min(high, value)); }
    private static double wrap(double degrees) { return degrees - Math.floor((degrees + 180) / 360) * 360; }
}
