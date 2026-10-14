package com.bilal.practicemod;

import net.minecraft.world.phys.Vec3;

/** Velocity steering, not position interpolation: release leaves an ordinary ballistic trajectory. */
final class GrapplePhysics {
    static final double RANGE = 48.0;
    static final double MAX_SPEED = 1.65;
    static final double ACCELERATION = 0.16;

    private GrapplePhysics() {}

    static Vec3 pull(Vec3 velocity, Vec3 eye, Vec3 anchor) {
        if (!finite(velocity)) velocity = Vec3.ZERO;
        // Aim slightly above the contact to give the player's feet clearance at a rooftop edge.
        Vec3 direction = anchor.add(0, 1.8, 0).subtract(eye).normalize();
        Vec3 next = velocity.scale(0.985).add(direction.scale(ACCELERATION)).add(0, 0.075, 0);
        double speed = next.length();
        return speed > MAX_SPEED ? next.scale(MAX_SPEED / speed) : next;
    }

    static boolean shouldRelease(Vec3 eye, Vec3 anchor, Vec3 velocity) {
        Vec3 delta = anchor.subtract(eye);
        // Release before overshoot can turn the pull around and brake the launch.
        return delta.horizontalDistance() < Math.max(1.8, velocity.horizontalDistance() * 2.0)
                && eye.y > anchor.y + 0.4;
    }

    static double glideCap(double previousCap) {
        return Math.max(UmbrellaGliderPhysics.HORIZONTAL_SPEED_CAP, previousCap - 0.012);
    }

    static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
