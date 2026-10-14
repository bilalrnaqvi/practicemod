package com.bilal.practicemod;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Shared terrain probe, also exercised against real blocks by server GameTests. */
final class SubmarineCameraCollision {
    private SubmarineCameraCollision() {}
    /** Nearby exposed water surface, including cameras just above its top cell. */
    static double waterCeiling(BlockGetter level, Vec3 position) {
        var pos=net.minecraft.core.BlockPos.containing(position);
        // Only inspect a bounded local column; deep water needs no surface correction.
        for (int offset=-2;offset<=SubmarineDepth.MAX_BLOCKS;offset++) {
            var cell=pos.offset(0,offset,0);
            var fluid=level.getFluidState(cell);
            if (!fluid.is(net.minecraft.tags.FluidTags.WATER)) continue;
            var above=cell.above();
            if (level.getFluidState(above).is(net.minecraft.tags.FluidTags.WATER)
                    || !level.getBlockState(above).isAir()) continue;
            return cell.getY()+fluid.getOwnHeight()-.75;
        }
        return Double.POSITIVE_INFINITY;
    }

    /** Nine swept probes protect the near plane, ignore water, and use visible block shapes. */
    static double clearBoom(BlockGetter level, Entity entity, Vec3 anchor, Vec3 direction, double length) {
        double safe=length;
        for (int i=-1;i<8;i++) {
            Vec3 padding=i<0 ? Vec3.ZERO : new Vec3((i&1)==0?-.2:.2,(i&2)==0?-.2:.2,(i&4)==0?-.2:.2);
            Vec3 start=anchor.add(padding);
            var hit=level.clip(new ClipContext(start,start.add(direction.scale(length)),ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,entity));
            if (hit.getType()!=HitResult.Type.MISS) safe=Math.min(safe,Math.max(0,start.distanceTo(hit.getLocation())-.25));
        }
        return safe;
    }

}
