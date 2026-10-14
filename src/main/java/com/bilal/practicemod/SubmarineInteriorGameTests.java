package com.bilal.practicemod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PracticeMod.MODID)
@PrefixGameTestTemplate(false)
public class SubmarineInteriorGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public void cabinParksWalksAndReturnsOnlyThroughChair(GameTestHelper helper) {
        var sub = new SubmarineEntity(PracticeMod.SUBMARINE_ENTITY.get(), helper.getLevel());
        sub.setPos(helper.absoluteVec(new Vec3(1, 4, 1)));
        helper.getLevel().addFreshEntity(sub);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.startRiding(sub);
        Vec3 anchor = sub.position();
        sub.setDeltaMovement(1, 1, 1);
        sub.toggleCabin(player);
        helper.assertTrue(sub.isParked() && sub.isCabinTransition(), "key starts the parked swivel");
        sub.move(MoverType.SELF, new Vec3(3, 3, 3));
        helper.assertTrue(sub.position().equals(anchor), "parked hull cannot move");
        helper.runAfterDelay(32, () -> {
            helper.assertFalse(sub.isCabinTransition(), "swivel finishes");
            helper.assertTrue(Math.abs(sub.chairAngle(0) - 180) < .01, "empty chair faces backward");
            sub.toggleCabin(player);
            helper.assertTrue(sub.isParked(), "key never returns to chair");
            player.setYRot(sub.getYRot() + 180);
            sub.acceptCabinInput(player, 1, 0);
            Vec3 before = sub.cabinFeet();
            sub.tick();
            helper.assertTrue(sub.cabinFeet().distanceTo(before) > .09, "walking moves passenger inside hull");
            helper.assertTrue(sub.position().equals(anchor), "walking cannot steer hull");
            sub.acceptCabinInput(player, Float.NaN, Float.POSITIVE_INFINITY);
            sub.tick();
            helper.assertTrue(Double.isFinite(sub.cabinFeet().lengthSqr()), "invalid inputs cannot corrupt cabin position");
            sub.sitInChair(player);
            helper.assertFalse(sub.isParked(), "chair returns to boarding approach");
            helper.assertTrue(sub.isBoarding() && sub.entryTime(0) == 70, "chair reuses the final entry animation");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public void furnitureTargetsFollowHullRotation(GameTestHelper helper) {
        var sub = new SubmarineEntity(PracticeMod.SUBMARINE_ENTITY.get(), helper.getLevel());
        sub.setPos(helper.absoluteVec(new Vec3(1, 4, 1)));
        helper.getLevel().addFreshEntity(sub);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.startRiding(sub);
        sub.toggleCabin(player);
        helper.runAfterDelay(32, () -> {
            Vec3[] targets = {new Vec3(0, 2.6, .5), new Vec3(-1.1, 2.22, -2.3),
                    new Vec3(1.1, 2.16, -2.3), new Vec3(0, 3.4, -4.1)};
            for (float yaw : new float[]{0, 45, 180, -125}) {
                sub.setYRot(yaw); sub.setXRot(12);
                sub.positionRider(player);
                for (int target = 0; target < targets.length; target++) {
                    Vec3 look = sub.modelToWorld(targets[target], 1).subtract(player.getEyePosition());
                    player.setYRot((float) Math.toDegrees(Math.atan2(-look.x, look.z)));
                    player.setXRot((float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance())));
                    helper.assertTrue(SubmarineInterior.pick(sub, player) == target,
                            "furniture " + target + " remains usable at hull yaw " + yaw);
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public void storageSurvivesSaveAndBelongsToItsSubmarine(GameTestHelper helper) {
        var sub = new SubmarineEntity(PracticeMod.SUBMARINE_ENTITY.get(), helper.getLevel());
        sub.storage.setItem(4, new ItemStack(Items.DIAMOND, 7));
        CompoundTag saved = new CompoundTag();
        sub.addAdditionalSaveData(saved);
        var loaded = new SubmarineEntity(PracticeMod.SUBMARINE_ENTITY.get(), helper.getLevel());
        loaded.readAdditionalSaveData(saved);
        helper.assertTrue(loaded.storage.getItem(4).is(Items.DIAMOND)
                && loaded.storage.getItem(4).getCount() == 7, "chest contents persist");
        loaded.storage.removeItem(4, 2);
        helper.assertTrue(sub.storage.getItem(4).getCount() == 7, "submarine inventories are independent");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public void bunkUsesVanillaSleepWithoutDismounting(GameTestHelper helper) {
        helper.getLevel().setDayTime(14000);
        var sub = new SubmarineEntity(PracticeMod.SUBMARINE_ENTITY.get(), helper.getLevel());
        sub.setPos(helper.absoluteVec(new Vec3(1, 4, 1)));
        helper.getLevel().addFreshEntity(sub);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.startRiding(sub);
        sub.toggleCabin(player);
        helper.runAfterDelay(32, () -> {
            player.startSleeping(sub.blockPosition());
            helper.assertTrue(player.isSleeping() && player.getVehicle() == sub, "bunk retains cabin attachment");
            // Mock players are not ticked by the world's player list.
            sub.positionRider(player);
            for (int i = 0; i < 101; i++) {
                player.tick();
                helper.assertTrue(player.isSleeping(), "woke on tick=" + i + " timer=" + player.getSleepTimer()
                        + " health=" + player.getHealth() + " damage=" + player.getLastDamageSource() + " pos=" + player.position()
                        + " transition=" + sub.isCabinTransition());
            }
            helper.assertTrue(player.isSleepingLongEnough(), "vanilla sleep timer reaches night-vote threshold: sleeping=" + player.isSleeping() + " timer=" + player.getSleepTimer() + " day=" + helper.getLevel().isDay() + " alive=" + player.isAlive() + " mounted=" + player.isPassenger());
            player.stopSleepInBed(true, true);
            helper.assertFalse(player.isSleeping(), "wake clears sleeping state");
            helper.assertTrue(player.getVehicle() == sub && sub.isParked(), "wake returns to parked cabin");
            helper.succeed();
        });
    }
}
