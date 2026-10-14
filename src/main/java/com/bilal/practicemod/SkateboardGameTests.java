package com.bilal.practicemod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PracticeMod.MODID)
@PrefixGameTestTemplate(false)
public class SkateboardGameTests {
    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            for (int y = 2; y < 6; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
    }

    @GameTest(template = "empty")
    public void placementMountPersistenceAndPickup(GameTestHelper helper) {
        floor(helper);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(1.5, 3, 1.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PracticeMod.SKATEBOARD.get(), 2));
        var hit = new BlockHitResult(helper.absoluteVec(new Vec3(1.5, 2, 1.5)), Direction.UP,
                helper.absolutePos(new BlockPos(1, 1, 1)), false);
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(PracticeMod.SKATEBOARD.get().useOn(context).consumesAction(), "ground placement succeeds");
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "placement consumes one item");
        helper.assertFalse(PracticeMod.SKATEBOARD.get().useOn(context).consumesAction(), "overlapping placement is blocked");
        var board = helper.getLevel().getEntitiesOfClass(SkateboardEntity.class,
                player.getBoundingBox().inflate(3)).getFirst();
        helper.assertTrue(board.interact(player, InteractionHand.MAIN_HAND).consumesAction(), "interaction mounts");
        board.positionRider(player);
        helper.assertTrue(player.getVehicle() == board, "player rides board");
        helper.assertTrue(Math.abs(player.getY() - board.getY() - 0.25) < 0.001, "feet rest on deck");
        helper.assertFalse(board.shouldRiderSit(), "rider stands");
        helper.assertTrue(board.getControllingPassenger() == null, "vanilla client vehicle authority is disabled");
        helper.assertFalse(helper.makeMockPlayer(GameType.SURVIVAL).startRiding(board), "only one rider fits");
        player.stopRiding();
        helper.assertFalse(board.isVehicle(), "standard dismount removes passenger");
        CompoundTag tag = new CompoundTag();
        board.save(tag);
        helper.assertTrue(EntityType.loadEntityRecursive(tag, helper.getLevel(), e -> e) instanceof SkateboardEntity,
                "board survives save/load");
        helper.assertTrue(board.getPickResult().is(PracticeMod.SKATEBOARD.get()), "pick block returns item");
        board.hurt(helper.getLevel().damageSources().playerAttack(player), 1);
        helper.assertTrue(board.isRemoved(), "empty board can be broken");
        helper.assertItemEntityCountIs(PracticeMod.SKATEBOARD.get(), new BlockPos(1, 2, 1), 3, 1);
        helper.succeed();
    }

    private static void input(SkateboardEntity board, net.minecraft.world.entity.player.Player rider, int buttons) {
        board.acceptInput(rider, SkateboardDynamics.ACTIVE | buttons, 0, board.getYRot());
    }
    private static SkateboardEntity board(GameTestHelper helper) {
        floor(helper);
        var board = new SkateboardEntity(PracticeMod.SKATEBOARD_ENTITY.get(), helper.getLevel());
        board.setPos(helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
        board.setOnGround(true);
        return board;
    }

    @GameTest(template = "empty")
    public void accelerationCoastingBrakingAndSteering(GameTestHelper helper) {
        var board = board(helper);
        var origin = board.position();
        var rider = helper.makeMockPlayer(GameType.SURVIVAL);
        rider.startRiding(board);
        for (int i = 0; i < 35; i++) {
            board.tickCount++;
            board.setPos(origin); board.setOnGround(true);
            input(board, rider, SkateboardDynamics.PUSH | SkateboardDynamics.SPRINT); board.tick();
        }
        helper.assertTrue(Math.abs(board.speed() - SkateboardEntity.FAST_SPEED) < 0.001, "acceleration reaches bounded sprint speed");
        board.setPos(origin); input(board, rider, 0); board.tick();
        helper.assertTrue(board.speed() > 0.7, "releasing W preserves rolling momentum");
        board.setPos(origin); input(board, rider, SkateboardDynamics.BRAKE); board.tick();
        helper.assertTrue(board.speed() < 0.66, "brake sheds speed");
        board.setPos(origin); board.acceptInput(rider, SkateboardDynamics.ACTIVE, 0, 90); board.tick();
        helper.assertTrue(board.getYRot() > 0 && board.getYRot() < 10, "steering is responsive but cannot snap ninety degrees in a tick");
        helper.assertTrue(board.getX() < origin.x, "turning changes travel direction");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void inputValidationAndMenuCancellation(GameTestHelper helper) {
        var board = board(helper);
        var origin = board.position();
        var rider = helper.makeMockPlayer(GameType.SURVIVAL); rider.startRiding(board);
        board.acceptInput(helper.makeMockPlayer(GameType.SURVIVAL), SkateboardDynamics.ACTIVE | SkateboardDynamics.PUSH, 0, 0);
        board.tick(); helper.assertTrue(board.speed() == 0, "non-rider input is rejected");
        board.acceptInput(rider, SkateboardDynamics.ACTIVE | SkateboardDynamics.PUSH, Float.NaN, 0);
        board.tick(); helper.assertTrue(board.speed() == 0, "non-finite steering is rejected");
        board.acceptInput(rider, SkateboardDynamics.ACTIVE | SkateboardDynamics.PUSH, 0, Float.POSITIVE_INFINITY);
        board.tick(); helper.assertTrue(board.speed() == 0, "non-finite yaw is rejected");
        input(board, rider, SkateboardDynamics.OLLIE); board.tick();
        board.setPos(origin); board.setOnGround(true);
        board.acceptInput(rider, 0, 0, 0); board.tick();
        helper.assertTrue(board.charge() == 0 && board.onGround(), "opening a menu cancels a charged jump without launching");
        input(board, rider, SkateboardDynamics.PUSH); rider.stopRiding(); board.setDeltaMovement(Vec3.ZERO); board.tick();
        helper.assertTrue(board.speed() == 0 && board.pushMode() == 0, "dismount clears intent");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void chargedOllieAndAirTrickLanding(GameTestHelper helper) {
        var board = board(helper); var origin = board.position();
        var rider = helper.makeMockPlayer(GameType.SURVIVAL); rider.startRiding(board);
        for (int i = 0; i < 16; i++) {
            board.setPos(origin); board.setOnGround(true);
            input(board, rider, SkateboardDynamics.OLLIE); board.tick();
        }
        board.setPos(origin); board.setDeltaMovement(Vec3.ZERO);
        input(board, rider, SkateboardDynamics.FLIP); board.tick();
        helper.assertTrue(!board.onGround() && board.getDeltaMovement().y > 0.6, "releasing a charged ollie launches");
        helper.assertTrue(board.currentTrick() == SkateboardTricks.Trick.KICKFLIP, "flip starts in the air");
        for (int i = 0; i < 40; i++) { input(board, rider, 0); board.tick(); }
        helper.assertTrue(board.onGround() && board.totalScore() >= 100, "completed flip lands and banks score");
        helper.assertTrue(board.bestCombo() == board.totalScore(), "best combo is recorded");
        var tag = new CompoundTag(); board.save(tag);
        var loaded = (SkateboardEntity) EntityType.loadEntityRecursive(tag, helper.getLevel(), e -> e);
        helper.assertTrue(loaded.totalScore() == board.totalScore(), "banked score persists through save/load");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void manualLinksLandingAndCanBail(GameTestHelper helper) {
        var board = board(helper); var origin = board.position();
        var rider = helper.makeMockPlayer(GameType.SURVIVAL); rider.startRiding(board);
        board.setDeltaMovement(0, 0, 0.3);
        input(board, rider, SkateboardDynamics.MANUAL); board.tick();
        helper.assertTrue(board.skateMode() == SkateboardEntity.MANUAL_STATE, "manual engages while rolling");
        for (int i = 0; i < 6; i++) {
            board.setPos(origin); board.setOnGround(true); input(board, rider, 0); board.tick();
        }
        helper.assertTrue(board.multiplier() == 1 && board.totalScore() == 0, "manual sustains one multiplier without banking");
        for (int i = 0; i < 90 && board.skateMode() != SkateboardEntity.BAIL; i++) {
            board.setPos(origin); board.setOnGround(true); input(board, rider, SkateboardDynamics.BRAKE); board.tick();
        }
        helper.assertTrue(board.skateMode() == SkateboardEntity.BAIL, "bad balance causes a bail");
        helper.assertTrue(board.multiplier() == 0 && board.totalScore() == 0, "bail loses unbanked combo");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void grindUsesRailSurfaceAndOlliesOut(GameTestHelper helper) {
        var board = board(helper);
        for (int z = 0; z < 3; z++) helper.setBlock(new BlockPos(1, 2, z), Blocks.OAK_FENCE);
        var origin = helper.absoluteVec(new Vec3(1.5, 3.55, 0.8));
        board.setPos(origin); board.setOnGround(false); board.setDeltaMovement(0, -0.1, 0.2);
        var rider = helper.makeMockPlayer(GameType.SURVIVAL); rider.startRiding(board);
        input(board, rider, SkateboardDynamics.GRIND); board.tick();
        helper.assertTrue(board.skateMode() == SkateboardEntity.GRIND_STATE, "descending onto fence catches a grind");
        helper.assertTrue(Math.abs(board.getY() - origin.y + 0.05) < 0.01, "grind rests on actual fence collision top");
        input(board, rider, SkateboardDynamics.GRIND | SkateboardDynamics.OLLIE); board.tick();
        input(board, rider, SkateboardDynamics.GRIND); board.tick();
        helper.assertTrue(board.skateMode() == SkateboardEntity.AIR && board.getDeltaMovement().y > 0.3, "ollie releases rail and keeps combo");
        helper.assertTrue(board.multiplier() > 0, "grind to air preserves combo");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void gravityStepsAndWalls(GameTestHelper helper) {
        var board = board(helper); var origin = board.position();
        board.setPos(origin.add(0, 1, 0)); board.setOnGround(false);
        for (int i = 0; i < 10; i++) board.tick();
        helper.assertTrue(board.onGround() && Math.abs(board.getY() - origin.y) < 0.001, "gravity lands on terrain");
        helper.setBlock(new BlockPos(1, 2, 2), Blocks.STONE_SLAB);
        var rider = helper.makeMockPlayer(GameType.SURVIVAL); rider.startRiding(board);
        board.setDeltaMovement(0, 0, 0.3); input(board, rider, SkateboardDynamics.PUSH); board.tick();
        helper.assertTrue(Math.abs(board.getY() - origin.y - 0.5) < 0.001, "board steps onto bottom slab");
        helper.setBlock(new BlockPos(1, 2, 2), Blocks.STONE);
        board.setPos(origin); board.setOnGround(true); board.setDeltaMovement(0, 0, 0.55);
        board.tick();
        helper.assertTrue(board.getZ() <= origin.z + 0.101, "full block collision prevents tunneling");
        helper.assertTrue(board.skateMode() == SkateboardEntity.BAIL, "high speed wall hit bails");
        helper.assertTrue(board.pushMode() == 0, "blocked board does not push in place");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void bufferedRevertManualAndLateFlip(GameTestHelper helper) {
        var board = board(helper); var origin = board.position();
        var rider = helper.makeMockPlayer(GameType.SURVIVAL); rider.startRiding(board);
        board.setPos(origin.add(0, 3, 0)); board.setOnGround(false); board.setDeltaMovement(0, 0, 0.13);
        input(board, rider, SkateboardDynamics.FLIP); board.tick();
        for (int i = 0; i < 11; i++) {
            board.setPos(origin.add(0, 3, 0)); board.setOnGround(false);
            board.setDeltaMovement(0, 0, 0.13); input(board, rider, 0); board.tick();
        }
        helper.assertTrue(board.comboBase() == 100, "flip completes before touchdown");
        board.setPos(origin.add(0, 0.08, 0)); board.setOnGround(false); board.setDeltaMovement(0, -0.2, 0.13);
        input(board, rider, SkateboardDynamics.REVERT | SkateboardDynamics.MANUAL); board.tick();
        helper.assertTrue(board.skateMode() == SkateboardEntity.MANUAL_STATE, "manual buffered in air catches landing");
        helper.assertTrue(board.multiplier() == 3 && board.totalScore() == 0, "revert and manual extend the airborne combo on touchdown");
        input(board, rider, 0); board.tick();
        input(board, rider, SkateboardDynamics.MANUAL); board.tick();
        for (int i = 0; i < 12; i++) {
            board.setPos(origin); board.setOnGround(true); input(board, rider, 0); board.tick();
        }
        long banked = board.totalScore();
        helper.assertTrue(banked > 100, "ending manual safely banks the linked combo");
        board.setPos(origin.add(0, 0.08, 0)); board.setOnGround(false); board.setDeltaMovement(0, -0.2, 0);
        input(board, rider, SkateboardDynamics.FLIP); board.tick();
        helper.assertTrue(board.skateMode() == SkateboardEntity.BAIL, "starting a flip just before touchdown bails");
        helper.assertTrue(board.totalScore() == banked, "bail preserves previously banked score");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void sidewaysLandingAndMissingRail(GameTestHelper helper) {
        var board = board(helper); var origin = board.position();
        var rider = helper.makeMockPlayer(GameType.SURVIVAL); rider.startRiding(board);
        for (int i = 0; i < 6; i++) {
            board.setPos(origin.add(0, 2, 0)); board.setOnGround(false); board.setDeltaMovement(Vec3.ZERO);
            board.acceptInput(rider, SkateboardDynamics.ACTIVE, 1, 0); board.tick();
        }
        board.setPos(origin.add(0, 0.05, 0)); board.setOnGround(false); board.setDeltaMovement(0, -0.2, 0);
        input(board, rider, 0); board.tick();
        helper.assertTrue(board.skateMode() == SkateboardEntity.BAIL, "ninety-degree sideways landing bails");
        board = board(helper); rider.stopRiding(); rider.startRiding(board);
        board.setPos(origin.add(0, 1.5, 0)); board.setOnGround(false); board.setDeltaMovement(0, -0.1, 0.2);
        input(board, rider, SkateboardDynamics.GRIND); board.tick();
        helper.assertTrue(board.skateMode() == SkateboardEntity.AIR && board.multiplier() == 0,
                "grind key cannot latch or create points without a real rail");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public void staleInputStops(GameTestHelper helper) {
        var board = board(helper); var origin = board.position();
        var rider = helper.makeMockPlayer(GameType.SURVIVAL); rider.startRiding(board);
        input(board, rider, SkateboardDynamics.PUSH | SkateboardDynamics.OLLIE);
        helper.runAfterDelay(12, () -> {
            board.tick();
            helper.assertTrue(board.position().distanceTo(origin) < 0.001, "expired input cannot propel or jump");
            helper.assertTrue(board.pushMode() == 0 && board.charge() == 0, "expired input resets charge and animation");
            helper.succeed();
        });
    }
}
