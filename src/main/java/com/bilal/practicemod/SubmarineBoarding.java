package com.bilal.practicemod;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

/** Shared client/server boarding ray against the authored mesh, independent of boat physics. */
public final class SubmarineBoarding {
    private static final Vec3 PILOT = new Vec3(DeepseekerPilot.X, DeepseekerPilot.Y, DeepseekerPilot.Z);
    private static final double SEARCH_RADIUS;
    static {
        double radius = 0;
        for (float[] cube : DeepseekerGeometry.CUBES)
            for (int i = 0; i < cube.length; i += 8)
                radius = Math.max(radius, vertex(cube, i).distanceTo(PILOT));
        SEARCH_RADIUS = radius + 8; // Include the raised pilot pivot relative to the entity origin.
    }

    private SubmarineBoarding() {}

    public static SubmarineEntity pick(Player player) {
        if (!player.isAlive() || player.isSpectator() || player.isPassenger() || player.isSecondaryUseActive()) return null;
        Vec3 start = player.getEyePosition(), end = start.add(player.getLookAngle().scale(player.entityInteractionRange()));
        double nearest = 1;
        var block = player.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS)
            nearest = start.distanceTo(block.getLocation()) / start.distanceTo(end);
        // Respect other pickable entities between the player and the visible hull.
        for (var entity : player.level().getEntities(player, new AABB(start, end).inflate(1),
                e -> e.isPickable() && !e.isSpectator() && !(e instanceof SubmarineEntity))) {
            var box = entity.getBoundingBox().inflate(entity.getPickRadius());
            if (box.contains(start)) nearest = 0;
            else {
                var hit = box.clip(start, end);
                if (hit.isPresent()) nearest = Math.min(nearest, start.distanceTo(hit.get()) / start.distanceTo(end));
            }
        }
        SubmarineEntity target = null;
        for (var sub : player.level().getEntitiesOfClass(SubmarineEntity.class,
                new AABB(start, end).inflate(SEARCH_RADIUS), e -> e.isAlive() && e.isPickable())) {
            double hit = hitFraction(sub, start, end);
            if (hit < nearest) { nearest = hit; target = sub; }
        }
        return target;
    }

    /** Re-run the ray on the authoritative world; an entity id alone never grants boarding. */
    public static void board(Player player, int entityId) {
        if (player.level().isClientSide) return;
        var target = pick(player);
        if (target != null && target.getId() == entityId) target.interact(player, InteractionHand.MAIN_HAND);
    }

    static Vec3 toModel(SubmarineEntity sub, Vec3 world) {
        float hurt = sub.getHurtTime();
        float roll = hurt > 0 ? Mth.sin(hurt) * hurt * Math.max(0, sub.getDamage()) / 10 * sub.getHurtDir() : 0;
        return world.subtract(sub.position()).subtract(0, sub.pilotEyeOffset(), 0)
                .yRot(-(180 - sub.getYRot()) * Mth.DEG_TO_RAD)
                .zRot(roll * Mth.DEG_TO_RAD).xRot(-sub.getXRot() * Mth.DEG_TO_RAD).add(PILOT);
    }

    static double hitFraction(SubmarineEntity sub, Vec3 start, Vec3 end) {
        Vec3 origin = toModel(sub, start), direction = toModel(sub, end).subtract(origin);
        double nearest = Double.POSITIVE_INFINITY;
        // Every exported face has four vertices (position, UV, normal); include glass.
        for (float[] cube : DeepseekerGeometry.CUBES) {
            for (int i = 0; i < cube.length; i += 32) {
                Vec3 a = vertex(cube, i), b = vertex(cube, i + 8), c = vertex(cube, i + 16), d = vertex(cube, i + 24);
                nearest = Math.min(nearest, triangle(origin, direction, a, b, c));
                nearest = Math.min(nearest, triangle(origin, direction, a, c, d));
            }
        }
        return nearest;
    }

    private static Vec3 vertex(float[] cube, int i) { return new Vec3(cube[i], cube[i + 1], cube[i + 2]); }

    private static double triangle(Vec3 origin, Vec3 direction, Vec3 a, Vec3 b, Vec3 c) {
        Vec3 ab = b.subtract(a), ac = c.subtract(a), cross = direction.cross(ac);
        double determinant = ab.dot(cross);
        if (Math.abs(determinant) < 1e-9) return Double.POSITIVE_INFINITY;
        Vec3 offset = origin.subtract(a);
        double u = offset.dot(cross) / determinant;
        if (u < -1e-7 || u > 1 + 1e-7) return Double.POSITIVE_INFINITY;
        Vec3 q = offset.cross(ab);
        double v = direction.dot(q) / determinant;
        if (v < -1e-7 || u + v > 1 + 1e-7) return Double.POSITIVE_INFINITY;
        double t = ac.dot(q) / determinant;
        return t >= 0 && t <= 1 ? t : Double.POSITIVE_INFINITY;
    }
}
