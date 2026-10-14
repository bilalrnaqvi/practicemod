package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Low-opacity, additive lamp halos and short shafts, attached to the authored exterior lenses. */
final class SubmarineLampEffects extends RenderType {
    private static final int SIDES = 24, STEPS = 8;
    private static final double LENGTH = 5.5, SPREAD = .68;
    private static final RenderType GLOW = create("submarine_lamp_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES, 16384, false, false,
            CompositeState.builder().setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY).setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private SubmarineLampEffects(String name, VertexFormat format, VertexFormat.Mode mode, int size,
            boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    static void render(SubmarineEntity sub, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer vertices = buffers.getBuffer(GLOW);
        for (float[] lens : DeepseekerGeometry.LAMP_EFFECTS) {
            double scale = lens[3], length = LENGTH * scale;
            Vec3 origin = new Vec3(lens[0], lens[1], lens[2] - .012);
            Vec3 worldOrigin = SubmarineHeadlight.modelPosition(sub, partialTick, origin);
            double[] lengths = new double[SIDES];
            // Also clip the center so the shaft does not extend through a wall between edge rays.
            double center = clearLength(sub, partialTick, worldOrigin, new Vec3(0, 0, -length), length);
            for (int side = 0; side < SIDES; side++) {
                double angle = side * Math.PI * 2 / SIDES;
                Vec3 end = new Vec3(Math.cos(angle) * length * SPREAD,
                        Math.sin(angle) * length * SPREAD, -length);
                lengths[side] = Math.min(center, clearLength(sub, partialTick, worldOrigin, end, length));
            }
            for (int side = 0; side < SIDES; side++) {
                int next = (side + 1) % SIDES;
                for (int step = 0; step < STEPS; step++) {
                    double t0 = (double) step / STEPS, t1 = (double) (step + 1) / STEPS;
                    Vec3 a = point(origin, side, lengths[side], t0, scale);
                    Vec3 b = point(origin, next, lengths[next], t0, scale);
                    Vec3 c = point(origin, next, lengths[next], t1, scale);
                    Vec3 d = point(origin, side, lengths[side], t1, scale);
                    float alpha0 = (float) (.055 * Math.pow(1 - t0, 2));
                    float alpha1 = (float) (.055 * Math.pow(1 - t1, 2));
                    vertex(vertices, pose, a, alpha0); vertex(vertices, pose, b, alpha0); vertex(vertices, pose, c, alpha1);
                    vertex(vertices, pose, a, alpha0); vertex(vertices, pose, c, alpha1); vertex(vertices, pose, d, alpha1);
                }
            }
            // Soft spherical halo reads from oblique views; the small forward core marks the lens.
            halo(vertices, pose, origin, .26 * scale, .22F, 0);
            halo(vertices, pose, origin, .22 * scale, .10F, 1);
            halo(vertices, pose, origin, .22 * scale, .10F, 2);
            halo(vertices, pose, origin.add(0, 0, -.006 * scale), .085 * scale, .8F, 0);
        }
    }

    private static double clearLength(SubmarineEntity sub, float partial, Vec3 origin, Vec3 localEnd, double length) {
        Vec3 delta = SubmarineHeadlight.modelDirection(sub, partial, localEnd);
        var hit = sub.level().clip(new ClipContext(origin, origin.add(delta), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, sub));
        return hit.getType() == HitResult.Type.MISS ? length
                : Math.max(0, length * hit.getLocation().distanceTo(origin) / delta.length() - .04);
    }

    private static Vec3 point(Vec3 origin, int side, double length, double t, double scale) {
        double distance = length * t, angle = side * Math.PI * 2 / SIDES;
        double radius = .075 * scale + distance * SPREAD;
        return origin.add(Math.cos(angle) * radius, Math.sin(angle) * radius, -distance);
    }

    private static void halo(VertexConsumer vertices, PoseStack pose, Vec3 origin, double radius, float alpha, int plane) {
        for (int i = 0; i < SIDES; i++) {
            vertex(vertices, pose, origin, alpha);
            vertex(vertices, pose, origin.add(circle(i, radius, plane)), 0);
            vertex(vertices, pose, origin.add(circle(i + 1, radius, plane)), 0);
        }
    }

    private static Vec3 circle(int i, double radius, int plane) {
        double a = i * Math.PI * 2 / SIDES, x = Math.cos(a) * radius, y = Math.sin(a) * radius;
        return switch (plane) {
            case 1 -> new Vec3(x, 0, y);
            case 2 -> new Vec3(0, x, y);
            default -> new Vec3(x, y, 0);
        };
    }

    private static void vertex(VertexConsumer vertices, PoseStack pose, Vec3 point, float alpha) {
        vertices.addVertex(pose.last().pose(), (float) point.x, (float) point.y, (float) point.z)
                .setColor(.72F, .88F, 1F, alpha);
    }
}
