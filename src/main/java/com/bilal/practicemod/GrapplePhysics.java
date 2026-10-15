package com.bilal.practicemod;

import net.minecraft.world.phys.Vec3;

/** Velocity steering, not position interpolation: release leaves an ordinary ballistic trajectory. */
final class GrapplePhysics {
    static final double RANGE = 48.0;
    static final double MAX_SPEED = 1.65;
    static final double ACCELERATION = 0.16;

    private GrapplePhysics() {}

    static final double CLEARANCE = 0.85;
    // Vanilla gravity/vertical drag give 2.52–2.67 blocks of rise after release.
    static final double LAUNCH_UP = 0.62;
    static final double LAUNCH_UP_MAX = 0.64;
    static final double LAUNCH_FORWARD = 0.48;
    static final double LAUNCH_HORIZONTAL_MAX = 0.50;

    static Vec3 pull(Vec3 velocity, Vec3 feet, GrappleState state) {
        if (!finite(velocity)) velocity = Vec3.ZERO;
        Vec3 staging = GrappleTargeting.staging(state);
        // Stay outside the face until the player's FEET clear the lip. A high starting position
        // is retained instead of diving down into the roof simply to touch the anchor.
        Vec3 target = new Vec3(staging.x, Math.max(staging.y, feet.y), staging.z);
        Vec3 delta = target.subtract(feet);
        double desiredSpeed = Math.min(MAX_SPEED, Math.sqrt(2 * ACCELERATION * delta.length()));
        Vec3 desired = delta.normalize().scale(desiredSpeed);
        Vec3 change = desired.subtract(velocity);
        if (change.length() > ACCELERATION) change = change.normalize().scale(ACCELERATION);
        Vec3 next = velocity.add(change).add(0, 0.075, 0);
        // Arrest a fall when the winch engages rather than dragging the player below the route.
        if (feet.y < state.anchor().y + CLEARANCE) next = new Vec3(next.x, Math.max(0.18, next.y), next.z);
        return next.length() > MAX_SPEED ? next.normalize().scale(MAX_SPEED) : next;
    }

    static boolean shouldRelease(Vec3 feet, Vec3 anchor, Vec3 velocity) {
        return anchor.subtract(feet).horizontalDistance() <= 2.1
                && feet.y >= anchor.y + CLEARANCE;
    }

    static Vec3 launch(Vec3 velocity, Vec3 outward) {
        if (!finite(velocity)) velocity = Vec3.ZERO;
        Vec3 across = outward.scale(-1);
        // A completed grapple always has useful upward/forward speed, including near, level,
        // descending and nearly vertical approaches. The winch supplies this final kick once.
        double forward = Math.max(LAUNCH_FORWARD, Math.min(LAUNCH_HORIZONTAL_MAX, velocity.dot(across)));
        Vec3 tangent = new Vec3(velocity.x, 0, velocity.z).subtract(across.scale(velocity.dot(across))).scale(0.35);
        if (tangent.length() > 0.12) tangent = tangent.normalize().scale(0.12);
        Vec3 horizontal = across.scale(forward).add(tangent);
        if (horizontal.length() > LAUNCH_HORIZONTAL_MAX) horizontal = horizontal.normalize().scale(LAUNCH_HORIZONTAL_MAX);
        return new Vec3(horizontal.x, Math.max(LAUNCH_UP, Math.min(LAUNCH_UP_MAX, velocity.y)), horizontal.z);
    }

    static double glideCap(double previousCap) {
        return Math.max(UmbrellaGliderPhysics.HORIZONTAL_SPEED_CAP, previousCap - 0.012);
    }

    static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
