package com.bilal.practicemod;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Read-only shadow rays. Water transmits the searchlight; collision shapes block it. */
final class SubmarineLightField {
    static final float RANGE = 24;
    static final float SHADOW_RANGE = 32;
    static final float INTENSITY = 1.2F;

    private SubmarineLightField() { }

    static double distance(Level level, Entity source, Vec3 origin, Vec3 direction) {
        Vec3 end = origin.add(direction.scale(SHADOW_RANGE));
        var hit = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, source));
        return hit.getType() == HitResult.Type.MISS ? SHADOW_RANGE : hit.getLocation().distanceTo(origin);
    }
}
