package com.bilal.practicemod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Short-lived, client-local foam and spray emitted from the authored rear nozzle. */
final class JetskiWaterEffects {
    private static final Vec3 NOZZLE = new Vec3(0, 2.7 / 16.0, -15.30 / 16.0);

    static Vec3 nozzlePosition(JetskiEntity ski, float partial) {
        return ski.getPosition(partial).add(rotate(ski, NOZZLE.scale(JetskiEntity.HULL_SCALE), partial));
    }

    private static Vec3 rotate(JetskiEntity ski, Vec3 vector, float partial) {
        return vector.zRot(ski.roll(partial) * Mth.DEG_TO_RAD)
                .xRot(-Mth.lerp(partial, ski.xRotO, ski.getXRot()) * Mth.DEG_TO_RAD)
                .yRot(-Mth.rotLerp(partial, ski.yRotO, ski.getYRot()) * Mth.DEG_TO_RAD);
    }

    static void tick(JetskiEntity ski) {
        float strength = ski.jetStrength();
        if (!ski.level().isClientSide || strength <= 0) return;
        var level = ski.level();
        var random = ski.getRandom();
        // At most ten foam sprites and two droplets per tick; no per-particle network packets.
        int count = 2 + Math.round(strength * 8);
        for (int i = 0; i < count; i++) {
            float partial = (i + 1F) / count;
            Vec3 mouth = nozzlePosition(ski, partial);
            Vec3 jitter = rotate(ski, new Vec3((random.nextFloat() - .5) * .16,
                    (random.nextFloat() - .5) * .06, 0), partial);
            Vec3 point = mouth.add(jitter);
            Vec3 flow = rotate(ski, new Vec3((random.nextFloat() - .5) * .06,
                    .025 + random.nextFloat() * .025,
                    -(.16 + strength * .32) * (.85 + random.nextFloat() * .3)), partial);
            boolean submerged = level.getFluidState(BlockPos.containing(point)).is(FluidTags.WATER);
            // Bubble-pop sprites provide a four-tick white foam animation above the surface.
            level.addParticle(submerged ? ParticleTypes.BUBBLE : ParticleTypes.BUBBLE_POP,
                    point.x, point.y, point.z, flow.x, flow.y, flow.z);
            if (i == 0 || i == count - 1) {
                // Vanilla splash only honors directed X/Z velocity when Y is exactly zero.
                level.addParticle(ParticleTypes.SPLASH, point.x, point.y, point.z,
                        flow.x, 0, flow.z);
            }
        }
    }

    private JetskiWaterEffects() {}
}
