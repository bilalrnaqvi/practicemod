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
            velocity = GrapplePhysics.pull(velocity, Vec3.ZERO, anchor);
            helper.assertTrue(velocity.length() <= GrapplePhysics.MAX_SPEED + 1E-6, "pull speed must stay bounded");
        }
        helper.assertTrue(velocity.x > 1 && velocity.y > 0.5, "pull must build forward and upward launch speed");
        helper.assertTrue(GrapplePhysics.finite(GrapplePhysics.pull(new Vec3(Double.NaN, 0, 0), Vec3.ZERO, anchor)),
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
}
