package com.bilal.practicemod;

/** Fixed 20 Hz hull simulation. Units are blocks/tick; kept independent of the game for tuning. */
public final class JetskiDynamics {
    public static final double CRUISE_SPEED = 0.78;
    public static final double BOOST_SPEED = 1.42;
    public static final double DRAFT = 0.14;
    public double vx, vy, vz, yaw, turnRate, pitch, roll;
    public double charge = 1;
    public boolean boosting, exhausted, landed;
    private boolean wasBoosting, wasAirborne;
    private double skipDistance;

    public void step(double y, double surface, boolean grounded, float throttle, float steering, boolean boost) {
        boolean water = Double.isFinite(surface);
        double depth = water ? surface - DRAFT - y : -10;
        boolean contact = water && depth > -0.10;
        double speed = Math.hypot(vx, vz);
        landed = contact && wasAirborne && vy < -0.09;
        wasAirborne = water && !contact;

        if (exhausted && charge >= 0.35 && !boost) exhausted = false;
        // Keep the burst alive across short skips, but never propel a beached hull.
        boosting = boost && throttle > 0 && water && depth > -1.5 && !grounded && !exhausted && charge > 0;
        if (boosting) {
            charge = Math.max(0, charge - 1.0 / 60);
            if (charge == 0) exhausted = true;
        } else charge = Math.min(1, charge + 1.0 / 100);

        double turnTarget = -steering * Math.min(1, speed / 0.30) * (boosting ? 2.7 : 4.1);
        double radians = Math.toRadians(yaw);
        double forwardSpeed = -Math.sin(radians) * vx + Math.cos(radians) * vz;
        if (forwardSpeed < -0.04) turnTarget = -turnTarget;
        turnRate += (turnTarget - turnRate) * (contact ? 0.23 : 0.06);
        yaw = Math.IEEEremainder(yaw + turnRate, 360);
        radians = Math.toRadians(yaw);
        double fx = -Math.sin(radians), fz = Math.cos(radians);
        forwardSpeed = vx * fx + vz * fz;
        double lateral = vx * fz - vz * fx;

        double drag = grounded ? 0.72 : contact ? 0.989 : 0.997;
        forwardSpeed *= drag;
        // A turning hull does not instantly rotate its velocity. Boost loosens the stern further.
        lateral *= grounded ? 0.65 : contact ? (boosting ? 0.95 : 0.88) : 0.998;
        if (contact && !grounded) {
            if (throttle > 0) {
                double target = boosting ? BOOST_SPEED : CRUISE_SPEED;
                forwardSpeed += Math.max(0, target - forwardSpeed) * (boosting ? 0.19 : 0.085) * throttle;
                if (boosting && !wasBoosting) forwardSpeed += 0.12;
            } else if (throttle < 0) {
                // S first brakes, then backs up slowly; never reverses full-speed momentum in one tick.
                forwardSpeed += (-0.20 - forwardSpeed) * 0.085 * -throttle;
            }
        }
        vx = fx * forwardSpeed + fz * lateral;
        vz = fz * forwardSpeed - fx * lateral;
        speed = Math.hypot(vx, vz);
        // Defensive terminal bound; cruise release otherwise retains all earned boost momentum.
        if (speed > 1.6) { vx *= 1.6 / speed; vz *= 1.6 / speed; }

        if (contact) {
            vy += clamp(depth * 0.24 - vy * 0.34, -0.10, 0.16);
            skipDistance += speed;
            double planing = clamp((speed - 0.40) / 0.90, 0, 1);
            if (planing > 0 && skipDistance >= 4.0 + 2.0 * (1 - planing)
                    && depth > -0.06 && depth < 0.24 && vy < 0.06 && !grounded) {
                // Actual ballistic skips, followed by spring compression and splash on re-entry.
                vy = 0.12 + 0.20 * planing;
                skipDistance = 0;
            }
            if (speed < 0.35) skipDistance = 0;
        } else vy = Math.max(-1.5, vy - 0.055);
        if (grounded && vy < 0) vy = 0;

        double pitchTarget = water ? clamp(-speed * 5.0 - vy * 48.0 - (boosting ? 3 : 0), -24, 16) : 6;
        pitch += (pitchTarget - pitch) * 0.24;
        roll += (clamp(-turnRate * speed * 4.4, -20, 20) - roll) * 0.20;
        wasBoosting = boosting;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
