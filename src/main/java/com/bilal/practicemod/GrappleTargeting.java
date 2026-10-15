package com.bilal.practicemod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Same bounded aim-assist search on the server and in the owner's targeting preview. */
final class GrappleTargeting {
    static final double CONE_DEGREES = 14;
    private static final double CONE_COS = Math.cos(Math.toRadians(CONE_DEGREES));
    private static final double CONE_TAN = Math.tan(Math.toRadians(CONE_DEGREES));
    private GrappleTargeting() {}

    static GrappleState find(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        List<BlockPos> candidates = coneBlocks(player, eye, look);
        GrappleState best = GrappleState.IDLE;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos block : candidates) {
            if (!player.level().hasChunkAt(block)) continue;
            var shape = player.level().getBlockState(block).getCollisionShape(player.level(), block);
            for (AABB local : shape.toAabbs()) {
                AABB box = local.move(block);
                for (Vec3 outward : new Vec3[]{new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
                        new Vec3(0, 0, 1), new Vec3(0, 0, -1)}) {
                    Vec3 anchor = new Vec3(outward.x == 0 ? Mth.clamp(eye.x, box.minX + 0.05, box.maxX - 0.05)
                                    : outward.x > 0 ? box.maxX : box.minX,
                            box.maxY,
                            outward.z == 0 ? Mth.clamp(eye.z, box.minZ + 0.05, box.maxZ - 0.05)
                                    : outward.z > 0 ? box.maxZ : box.minZ);
                    Vec3 delta = anchor.subtract(eye);
                    double distance = delta.length();
                    if (distance < 3 || distance > GrapplePhysics.RANGE || delta.normalize().dot(look) < CONE_COS) continue;
                    // Only the exposed, player-facing edge; never select an internal seam in a roof.
                    if (eye.subtract(anchor).dot(outward) < 0.35) continue;
                    double score = 1 - delta.normalize().dot(look) + distance * 0.00008;
                    if (score > bestScore || (score == bestScore && block.asLong() >= best.block().asLong())) continue;
                    GrappleState candidate = new GrappleState(true, anchor, block, outward);
                    if (!clearExit(player, candidate) || !visible(player, candidate)) continue;
                    if (!clearApproach(player, candidate)) continue;
                    best = candidate;
                    bestScore = score;
                }
            }
        }
        return best;
    }

    private static List<BlockPos> coneBlocks(Player player, Vec3 eye, Vec3 look) {
        // Rasterize cross-sections perpendicular to the dominant view axis. Unlike sparse rays,
        // this cannot skip a one-block edge between rays at the far end of the aiming cone.
        double[] e = {eye.x, eye.y, eye.z}, d = {look.x, look.y, look.z};
        int axis = Math.abs(d[0]) > Math.abs(d[1]) ? 0 : 1;
        if (Math.abs(d[2]) > Math.abs(d[axis])) axis = 2;
        int a = (axis + 1) % 3, b = (axis + 2) % 3;
        double end = e[axis] + d[axis] * GrapplePhysics.RANGE;
        List<BlockPos> result = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int[] xyz = new int[3];
        for (int plane = Mth.floor(Math.min(e[axis], end)) - 1; plane <= Mth.ceil(Math.max(e[axis], end)) + 1; plane++) {
            double t = (plane + 0.5 - e[axis]) / d[axis];
            if (t < -1 || t > GrapplePhysics.RANGE + 1) continue;
            double radius = (Math.max(0, t) * CONE_TAN + 1.75) / Math.abs(d[axis]);
            double ca = e[a] + d[a] * t, cb = e[b] + d[b] * t;
            xyz[axis] = plane;
            for (int ia = Mth.floor(ca - radius); ia <= Mth.ceil(ca + radius); ia++) {
                xyz[a] = ia;
                for (int ib = Mth.floor(cb - radius); ib <= Mth.ceil(cb + radius); ib++) {
                    xyz[b] = ib;
                    double x = xyz[0] + 0.5 - eye.x, y = xyz[1] + 0.5 - eye.y, z = xyz[2] + 0.5 - eye.z;
                    double along = x * look.x + y * look.y + z * look.z;
                    double length2 = x*x + y*y + z*z;
                    double spread = along * CONE_TAN + 1;
                    if (along < 0 || length2 > (GrapplePhysics.RANGE + 1) * (GrapplePhysics.RANGE + 1)
                            || length2 - along*along > spread*spread) continue;
                    cursor.set(xyz[0], xyz[1], xyz[2]);
                    if (player.level().hasChunkAt(cursor) && !player.level().getBlockState(cursor).isAir()) {
                        result.add(cursor.immutable());
                    }
                }
            }
        }
        return result;
    }

    static Vec3 staging(GrappleState state) {
        return state.anchor().add(state.outward().scale(1.5)).add(0, GrapplePhysics.CLEARANCE + 0.5, 0);
    }

    static boolean clearExit(Player player, GrappleState state) {
        // An edge must expose a side just below its top. Otherwise an internal seam between
        // neighboring roof/canopy blocks can look like an eligible edge when viewed from above.
        Vec3 outsideLip = state.anchor().add(state.outward().scale(0.06)).add(0, -0.06, 0);
        if (!player.level().noCollision(player, new AABB(outsideLip, outsideLip).inflate(0.025))) return false;
        // Require room for the entire player both outside the face and above/inward of the lip.
        Vec3 outer = state.anchor().add(state.outward().scale(1.5));
        if (!player.level().noCollision(player, bodyAt(player, outer.add(0, 0.05, 0)).expandTowards(0, 2.5, 0))) return false;
        Vec3 above = state.anchor().add(0, GrapplePhysics.CLEARANCE, 0);
        return player.level().noCollision(player, bodyAt(player, above)
                .expandTowards(state.outward().scale(-1.5)).expandTowards(0, 2.5, 0));
    }

    static boolean clearApproach(Player player, GrappleState state) {
        Vec3 start = player.position().add(0, 0.05, 0);
        Vec3 end = staging(state);
        int steps = Math.max(1, (int) Math.ceil(start.distanceTo(end) * 2));
        for (int i = 1; i <= steps; i++) {
            if (!player.level().noCollision(player, bodyAt(player, start.lerp(end, (double) i / steps)))) return false;
        }
        return true;
    }

    static AABB bodyAt(Player player, Vec3 feet) {
        double half = player.getBbWidth() / 2.0 + 0.05;
        return new AABB(feet.x - half, feet.y, feet.z - half,
                feet.x + half, feet.y + player.getBbHeight(), feet.z + half);
    }

    static boolean visible(Player player, GrappleState state) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = state.anchor().add(state.outward().scale(0.025)).add(0, 0.025, 0);
        var hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(state.block());
    }
}
