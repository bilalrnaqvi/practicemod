package com.bilal.practicemod;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PracticeMod.MODID)
@PrefixGameTestTemplate(false)
public final class GrappleGameTests {
    @GameTest(template = "empty")
    public void pullBuildsBoundedUpwardMomentum(GameTestHelper helper) {
        Vec3 velocity = Vec3.ZERO;
        Vec3 anchor = new Vec3(20, 12, 0);
        for (int i = 0; i < 80; i++) {
            velocity = GrapplePhysics.pull(velocity, Vec3.ZERO, new GrappleState(true, anchor, BlockPos.ZERO));
            helper.assertTrue(velocity.length() <= GrapplePhysics.MAX_SPEED + 1E-6, "pull speed must stay bounded");
        }
        helper.assertTrue(velocity.x > 1 && velocity.y > 0.5, "pull must build forward and upward launch speed");
        helper.assertTrue(GrapplePhysics.finite(GrapplePhysics.pull(new Vec3(Double.NaN, 0, 0), Vec3.ZERO, new GrappleState(true, anchor, BlockPos.ZERO))),
                "invalid incoming velocity must not poison movement");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void releaseRetainsVelocityAndArmsHandoff(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setOnGround(false);
        Vec3 velocity = new Vec3(1.3, 0.7, 0.2);
        player.setDeltaMovement(velocity);
        player.setData(PracticeMod.GRAPPLE_STATE, new GrappleState(true, new Vec3(10, 10, 0), BlockPos.ZERO));
        GrappleHandler.release(player);
        helper.assertTrue(player.getDeltaMovement().equals(velocity), "detachment must not brake or replace velocity");
        helper.assertFalse(GrappleHandler.active(player), "release must remove the cable");
        helper.assertTrue(player.getData(PracticeMod.GRAPPLE_GLIDE_CAP) > 1.2F, "release must arm excess glide speed");
        player.setOnGround(true);
        GrappleHandler.tick(player);
        helper.assertTrue(player.getData(PracticeMod.GRAPPLE_GLIDE_CAP) == 0, "landing must clear excess speed allowance");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void umbrellaPreservesOnlyGrappleMomentum(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setOnGround(false);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(PracticeMod.UMBRELLA_GLIDER.get()));
        player.setDeltaMovement(1.3, -0.3, 0);
        player.setData(PracticeMod.UMBRELLA_GLIDER_DEPLOYED, true);
        player.setData(PracticeMod.GRAPPLE_GLIDE_CAP, 1.3F);
        UmbrellaGliderHandler.tickGlider(player);
        helper.assertTrue(player.getDeltaMovement().x > 1.2 && player.getDeltaMovement().x < 1.3,
                "opening after grapple must shed speed gradually");
        player.setData(PracticeMod.GRAPPLE_GLIDE_CAP, 0F);
        player.setDeltaMovement(1.3, -0.3, 0);
        UmbrellaGliderHandler.tickGlider(player);
        helper.assertTrue(player.getDeltaMovement().horizontalDistance() <= UmbrellaGliderPhysics.HORIZONTAL_SPEED_CAP + 1E-6,
                "ordinary umbrella must retain its established speed cap");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void anchorRemovalAndItemSwitchCancelPull(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos block = helper.absolutePos(new BlockPos(1, 30, 1));
        player.setPos(Vec3.atCenterOf(block).add(0, 0, -6));
        player.setOnGround(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PracticeMod.GRAPPLING_HOOK.get()));
        player.startUsingItem(InteractionHand.MAIN_HAND);
        helper.getLevel().setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
        GrappleState state = new GrappleState(true, Vec3.atCenterOf(block).add(0, 0, -0.5), block);
        Vec3 aim = state.anchor().subtract(player.getEyePosition());
        player.setYRot(0);
        player.setXRot((float) -Math.toDegrees(Math.atan2(aim.y, aim.horizontalDistance())));
        helper.assertTrue(GrappleHandler.start(player), "use must raycast and acquire a reachable solid anchor");
        player.setData(PracticeMod.GRAPPLE_STATE, state);
        helper.assertTrue(GrappleHandler.anchorValid(player, state), "solid unobstructed anchor must be usable");
        BlockPos obstruction = BlockPos.containing(player.getEyePosition().lerp(state.anchor(), 0.5));
        helper.getLevel().setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
        helper.assertFalse(GrappleHandler.anchorValid(player, state), "a wall between player and anchor must break the cable");
        helper.getLevel().setBlockAndUpdate(obstruction, Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
        GrappleHandler.tick(player);
        helper.assertFalse(GrappleHandler.active(player), "removed anchor must detach");
        helper.getLevel().setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
        player.setData(PracticeMod.GRAPPLE_STATE, state);
        player.stopUsingItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        GrappleHandler.tick(player);
        helper.assertFalse(GrappleHandler.active(player), "changing held item must cancel the pull");
        helper.getLevel().setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void releaseClearsEdgeBeforeOvershoot(GameTestHelper helper) {
        Vec3 anchor = new Vec3(0, 10, 0);
        helper.assertFalse(GrapplePhysics.shouldRelease(new Vec3(0, 9, -1), anchor, new Vec3(0, 0.6, 1)),
                "automatic release must wait for vertical clearance");
        helper.assertTrue(GrapplePhysics.shouldRelease(new Vec3(0, 11, -1), anchor, new Vec3(0, 0.6, 1)),
                "nearby cleared edge must release before the pull reverses");
        double cap = 1.5;
        for (int i = 0; i < 150; i++) cap = GrapplePhysics.glideCap(cap);
        helper.assertTrue(Math.abs(cap - UmbrellaGliderPhysics.HORIZONTAL_SPEED_CAP) < 1E-6,
                "excess speed must settle to the ordinary glide cap");
        helper.succeed();
    }
    private static void aim(net.minecraft.world.entity.player.Player player, Vec3 point) {
        Vec3 direction = point.subtract(player.getEyePosition());
        player.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance())));
    }

    @GameTest(template = "empty")
    public void aimAssistFindsMissedEdgeAndRejectsBlockedExit(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos block = helper.absolutePos(new BlockPos(1, 80, 1));
        helper.getLevel().setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
        player.setPos(Vec3.atCenterOf(block).add(0, -5, -14));
        // Intentionally miss the one-block target by two blocks horizontally.
        aim(player, Vec3.atCenterOf(block).add(2, 0.5, 0));
        var exact = player.level().clip(new net.minecraft.world.level.ClipContext(player.getEyePosition(),
                player.getEyePosition().add(player.getLookAngle().scale(48)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        helper.assertFalse(exact.getBlockPos().equals(block), "fixture must actually miss with exact aim");
        GrappleState target = GrappleTargeting.find(player);
        helper.assertTrue(target.active() && target.block().equals(block), "nearby edge must be selected despite an exact-ray miss");
        helper.assertTrue(Math.abs(target.anchor().y - (block.getY() + 1)) < 1E-6,
                "target circle must be on the top edge, not the face center");
        helper.assertTrue(target.anchor().z == block.getZ(), "near-facing edge must be selected");
        helper.getLevel().setBlockAndUpdate(block.above(2), Blocks.STONE.defaultBlockState());
        helper.assertFalse(GrappleTargeting.clearExit(player, target), "blocked headroom must reject a launch corridor");
        helper.getLevel().setBlockAndUpdate(block.above(2), Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(block, Blocks.STONE_SLAB.defaultBlockState());
        target = GrappleTargeting.find(player);
        helper.assertTrue(target.active() && Math.abs(target.anchor().y - (block.getY() + 0.5)) < 1E-6,
                "slab edge must follow the actual collision-shape height");
        player.setPos(Vec3.atCenterOf(block).add(0, -5, -44));
        aim(player, Vec3.atCenterOf(block).add(6, 0.5, 0));
        helper.assertTrue(GrappleTargeting.find(player).active(),
                "far single-block targets must not disappear between aim-assist samples");
        long started = System.nanoTime();
        for (int i = 0; i < 10; i++) GrappleTargeting.find(player);
        org.slf4j.LoggerFactory.getLogger(GrappleGameTests.class).info("Grapple target search: {} ms average (far slab fixture)",
                (System.nanoTime() - started) / 10_000_000.0);
        aim(player, player.getEyePosition().add(0, 0, -20));
        helper.assertFalse(GrappleTargeting.find(player).active(), "target behind the player must not be acquired");
        helper.getLevel().setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void completedPullsClearRoofAndTreeFromDifferentHeights(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(new BlockPos(1, 140, 1));
        // Test the actual handler and vanilla collision movement, above unrelated fixtures.
        for (boolean leaves : new boolean[]{false, true}) {
            for (int x = -3; x <= 3; x++) for (int z = 0; z <= 5; z++) {
                helper.getLevel().setBlockAndUpdate(base.offset(x, 0, z),
                        (leaves ? Blocks.OAK_LEAVES : Blocks.STONE).defaultBlockState());
            }
            for (Vec3 offset : new Vec3[]{new Vec3(0, -12, -18), new Vec3(0, -2, -9),
                    new Vec3(0, 8, -12), new Vec3(0, -8, -3), new Vec3(7, -8, -12)}) {
                var player = helper.makeMockPlayer(GameType.SURVIVAL);
                player.setPos(Vec3.atCenterOf(base).add(offset));
                player.setOnGround(false);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PracticeMod.GRAPPLING_HOOK.get()));
                player.startUsingItem(InteractionHand.MAIN_HAND);
                player.setDeltaMovement(0, -0.7, 0);
                aim(player, Vec3.atCenterOf(base).add(0, 0.5, -0.5));
                helper.assertTrue(GrappleHandler.start(player), "must acquire roof/tree from " + offset);
                GrappleState target = player.getData(PracticeMod.GRAPPLE_STATE);
                BlockPos internal = base.offset(0, 0, 2);
                helper.assertFalse(GrappleTargeting.clearExit(player, new GrappleState(true,
                        Vec3.atCenterOf(internal).add(0, 0.5, -0.5), internal)),
                        "internal roof/canopy seams must not masquerade as exposed edges");
                int ticks = 0;
                while (GrappleHandler.active(player) && ticks++ < 160) {
                    GrappleHandler.tick(player);
                    if (!GrappleHandler.active(player)) break;
                    Vec3 velocity = player.getDeltaMovement();
                    player.move(net.minecraft.world.entity.MoverType.SELF, velocity);
                    player.setDeltaMovement(velocity.x * 0.91, (velocity.y - 0.08) * 0.98, velocity.z * 0.91);
                }
                helper.assertFalse(GrappleHandler.active(player), "pull must complete within its timeout from " + offset);
                helper.assertTrue(player.getY() >= target.anchor().y + GrapplePhysics.CLEARANCE,
                        "feet must clear roof/tree before detachment from " + offset + " at " + player.position());
                helper.assertTrue(player.getDeltaMovement().y >= GrapplePhysics.LAUNCH_UP - 1E-6,
                        "completion must launch upward, including approaches from above");
                helper.assertTrue(player.getDeltaMovement().dot(target.outward().scale(-1)) >= 0.45,
                        "launch must carry forward over the selected edge");
                helper.assertTrue(player.getData(PracticeMod.GRAPPLE_GLIDE_CAP) >= 0.45,
                        "launch impulse must reach the umbrella handoff allowance");
                helper.assertTrue(player.level().noCollision(player, player.getBoundingBox()), "completed pull must not clip into terrain");
            }
            for (int x = -3; x <= 3; x++) for (int z = 0; z <= 5; z++) {
                helper.getLevel().setBlockAndUpdate(base.offset(x, 0, z), Blocks.AIR.defaultBlockState());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void steepLaunchGivesUmbrellaSwitchWindow(GameTestHelper helper) {
        for (Vec3 incoming : new Vec3[]{Vec3.ZERO, new Vec3(0, -3, 1), new Vec3(2, 2, 0)}) {
            Vec3 launch = GrapplePhysics.launch(incoming, new Vec3(0, 0, -1));
            helper.assertTrue(launch.z >= 0.47, "sideways approach momentum must not erase the across-edge launch");
            helper.assertTrue(Math.toDegrees(Math.atan2(launch.y, launch.horizontalDistance())) >= 50,
                    "launch must be distinctly steeper than forward travel");
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(helper.absoluteVec(new Vec3(1, 210, 1)));
            player.setOnGround(false);
            player.setDeltaMovement(launch);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PracticeMod.GRAPPLING_HOOK.get()));
            player.setData(PracticeMod.GRAPPLE_GLIDE_CAP, (float)launch.horizontalDistance());
            player.setData(PracticeMod.GRAPPLE_COAST_TICKS, 200);
            double releaseY = player.getY(), apex = releaseY;
            int deploymentTick = -1;
            for (int tick = 0; tick < 30; tick++) {
                // Six tenths of a second to switch hotbar items; do not require an offhand umbrella.
                if (tick == 12) player.setItemInHand(InteractionHand.MAIN_HAND,
                        new ItemStack(PracticeMod.UMBRELLA_GLIDER.get()));
                Vec3 velocity = player.getDeltaMovement();
                player.move(net.minecraft.world.entity.MoverType.SELF, velocity);
                apex = Math.max(apex, player.getY());
                player.setDeltaMovement(velocity.x * 0.91, (velocity.y - 0.08) * 0.98, velocity.z * 0.91);
                GrappleHandler.tick(player);
                UmbrellaGliderHandler.tickGlider(player);
                if (UmbrellaGliderHandler.isDeployed(player)) {
                    deploymentTick = tick;
                    break;
                }
            }
            helper.assertTrue(apex - releaseY >= 2 && apex - releaseY <= 3,
                    "free launch arc must rise 2–3 blocks above release, got " + (apex - releaseY));
            helper.assertTrue(deploymentTick >= 12 && deploymentTick < 24,
                    "switching to umbrella after 0.6 seconds must still catch the descent, tick=" + deploymentTick);
            helper.assertTrue(player.getY() > releaseY,
                    "umbrella must open while still above release height, before returning to the ledge");
            helper.assertTrue(player.getDeltaMovement().horizontalDistance() > 0.08,
                    "the launch-to-glide transition must retain forward motion");
        }
        helper.succeed();
    }

}
