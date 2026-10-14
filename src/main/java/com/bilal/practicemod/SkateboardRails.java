package com.bilal.practicemod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Grind targets come from real collision surfaces. */
final class SkateboardRails {
    static final TagKey<Block> GRINDABLE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "skateboard_grindable"));
    record Rail(BlockPos pos, Direction.Axis axis, double top) {
        Vec3 center(Vec3 from) {
            return axis == Direction.Axis.X ? new Vec3(from.x, top, pos.getZ() + 0.5)
                    : new Vec3(pos.getX() + 0.5, top, from.z);
        }
        float yaw(float incoming) {
            if (axis == Direction.Axis.X) return Math.sin(Math.toRadians(incoming)) > 0 ? 90 : -90;
            return Math.cos(Math.toRadians(incoming)) > 0 ? 0 : 180;
        }
    }

    static Rail find(Level level, Vec3 feet, float yaw, double verticalTolerance) {
        Rail best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos origin = BlockPos.containing(feet);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = -2; y <= 0; y++) {
            BlockPos pos = origin.offset(x, y, z);
            var state = level.getBlockState(pos);
            if (!state.is(GRINDABLE)) continue;
            var shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            double top = pos.getY() + shape.max(Direction.Axis.Y);
            if (Math.abs(feet.y - top) > verticalTolerance) continue;
            boolean alongX = level.getBlockState(pos.east()).is(GRINDABLE) || level.getBlockState(pos.west()).is(GRINDABLE);
            boolean alongZ = level.getBlockState(pos.north()).is(GRINDABLE) || level.getBlockState(pos.south()).is(GRINDABLE);
            if (!alongX && !alongZ) continue;
            Direction.Axis axis = alongX && (!alongZ || Math.abs(Math.sin(Math.toRadians(yaw))) > 0.7)
                    ? Direction.Axis.X : Direction.Axis.Z;
            double across = axis == Direction.Axis.X ? feet.z - pos.getZ() - 0.5 : feet.x - pos.getX() - 0.5;
            double along = axis == Direction.Axis.X ? feet.x - pos.getX() - 0.5 : feet.z - pos.getZ() - 0.5;
            if (Math.abs(across) > 0.58 || Math.abs(along) > 0.65) continue;
            double distance = across * across + Math.pow(feet.y - top, 2);
            if (distance < bestDistance) { bestDistance = distance; best = new Rail(pos, axis, top); }
        }
        return best;
    }
    private SkateboardRails() {}
}
