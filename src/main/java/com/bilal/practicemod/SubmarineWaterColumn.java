package com.bilal.practicemod;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;

/** Only accesses an already loaded, single X/Z column. Safe on either logical side. */
final class SubmarineWaterColumn implements SubmarineDepth.Column {
    private final Level level;
    private final int x, z;
    SubmarineWaterColumn(Level level, int x, int z) { this.level = level; this.x = x; this.z = z; }
    @Override
    public SubmarineDepth.Cell at(int y) {
        BlockPos pos = new BlockPos(x, y, z);
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos))
            return new SubmarineDepth.Cell(false, false, true, false, 0);
        var state = level.getBlockState(pos);
        var fluid = state.getFluidState();
        // Own height avoids an implicit neighbor query (and possible chunk access) in getHeight.
        // The algorithm only uses it at the terminal cell, where the cell above is known non-water.
        return new SubmarineDepth.Cell(true, fluid.is(FluidTags.WATER), fluid.isEmpty(),
                !state.getCollisionShape(level, pos).isEmpty(), fluid.getOwnHeight());
    }
}
