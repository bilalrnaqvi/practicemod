package com.bilal.practicemod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Furniture bounds in the same block-space coordinates as the authored cabin. */
public final class SubmarineInterior {
    private SubmarineInterior() { }
    private static final AABB[] FURNITURE = {
            new AABB(-.72, 1.33, .35, .72, 3.4, 1.1), // shared rear cases
            new AABB(-1.83, 1.33, -3.38, -.89, 2.3, -1), // workbench
            new AABB(.86, 1.33, -3.38, 1.8, 2.2, -1), // bunk
            new AABB(-.75, 1.6, -5.3, .75, 3.8, -3.65) // pilot chair
    };
    public static Vec3 toLocal(SubmarineEntity sub, Vec3 world) {
        return world.subtract(sub.position().add(0, sub.pilotEyeOffset(), 0))
                .yRot((sub.getYRot() - 180) * net.minecraft.util.Mth.DEG_TO_RAD)
                .xRot(-sub.getXRot() * net.minecraft.util.Mth.DEG_TO_RAD).add(SubmarineEntryAnimation.PILOT);
    }
    public static int pick(SubmarineEntity sub, Player player) {
        if (!sub.isParked() || sub.isCabinTransition() || player.isSleeping()) return -1;
        Vec3 from = toLocal(sub, player.getEyePosition());
        Vec3 to = toLocal(sub, player.getEyePosition().add(player.getLookAngle().scale(3)));
        double nearest = Double.MAX_VALUE;
        int target = -1;
        for (int i = 0; i < FURNITURE.length; i++) {
            var hit = FURNITURE[i].clip(from, to);
            if (hit.isPresent() && hit.get().distanceToSqr(from) < nearest) {
                nearest = hit.get().distanceToSqr(from); target = i;
            }
        }
        return target;
    }
    public static void use(SubmarineEntity sub, Player player) {
        if (!(player instanceof ServerPlayer server) || player.getVehicle() != sub) return;
        int target = pick(sub, player);
        if (target == 0) {
            server.openMenu(new SimpleMenuProvider((id, inventory, owner) ->
                    new ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x3, id, inventory, sub.storage, 3) {
                        @Override public boolean stillValid(Player p) { return valid(sub, p); }
                    }, Component.translatable("container.practicemod.submarine_storage")));
        } else if (target == 1) {
            server.openMenu(new SimpleMenuProvider((id, inventory, owner) ->
                    new CraftingMenu(id, inventory, ContainerLevelAccess.create(sub.level(), sub.blockPosition())) {
                        @Override public boolean stillValid(Player p) { return valid(sub, p); }
                    }, Component.translatable("container.crafting")));
        } else if (target == 3) {
            sub.sitInChair(player);
        } else if (target == 2) {
            Player.BedSleepingProblem error = null;
            if (!player.level().dimensionType().natural()) error = Player.BedSleepingProblem.NOT_POSSIBLE_HERE;
            else if (player.level().isDay()) error = Player.BedSleepingProblem.NOT_POSSIBLE_NOW;
            else if (!player.isCreative() && !player.level().getEntitiesOfClass(
                    net.minecraft.world.entity.monster.Monster.class, player.getBoundingBox().inflate(8, 5, 8),
                    monster -> monster.isPreventingPlayerRest(player)).isEmpty()) error = Player.BedSleepingProblem.NOT_SAFE;
            if (error != null) {
                if (error.getMessage() != null) player.displayClientMessage(error.getMessage(), true);
                return;
            }
            // This is a mobile entity bed, never a respawn block in the ocean.
            player.startSleeping(BlockPos.containing(sub.modelToWorld(new Vec3(1.3, 2.2, -2.7), 1)));
            server.serverLevel().updateSleepingPlayerList();
        }
    }
    private static boolean valid(SubmarineEntity sub, Player player) {
        return sub.isAlive() && sub.isParked() && player.getVehicle() == sub && !player.isSleeping();
    }
}
