package com.bilal.practicemod;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PracticeMod.MODID)
@PrefixGameTestTemplate(false)
public class JetskiGameTests {
    private static JetskiEntity floating(GameTestHelper helper) {
        for (int x=0;x<3;x++) for (int z=0;z<3;z++) {
            helper.setBlock(new BlockPos(x,0,z), Blocks.STONE);
            for (int y=1;y<=3;y++) helper.setBlock(new BlockPos(x,y,z), Blocks.WATER);
            for (int y=4;y<=6;y++) helper.setBlock(new BlockPos(x,y,z), Blocks.AIR);
        }
        var ski = new JetskiEntity(PracticeMod.JETSKI_ENTITY.get(), helper.getLevel());
        ski.setPos(helper.absoluteVec(new Vec3(1.5, 3.75, 1.5)));
        return ski;
    }

    @GameTest(template = "empty")
    public void mountingPersistenceAndPickup(GameTestHelper helper) {
        var ski = floating(helper);
        helper.getLevel().addFreshEntity(ski);
        var rider = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(ski.interact(rider, InteractionHand.MAIN_HAND).consumesAction(), "right-click mounts jetski");
        helper.assertFalse(helper.makeMockPlayer(GameType.SURVIVAL).startRiding(ski), "one rider only");
        helper.assertTrue(ski.getControllingPassenger()==null, "client position packets cannot control hull");
        ski.acceptInput(rider, 1, 0, true);
        ski.tick();
        ski.positionRider(rider);
        helper.assertTrue(Math.abs(rider.getY()-ski.getY()
                - (JetskiEntity.SEAT_HEIGHT-JetskiEntity.RIDER_HIP_OFFSET)) < .15,
                "rider is anchored to the enlarged saddle");
        ski.hurt(helper.getLevel().damageSources().playerAttack(rider), 1);
        helper.assertFalse(ski.isRemoved(), "occupied vehicle cannot be picked up");
        rider.stopRiding();
        CompoundTag tag = new CompoundTag(); ski.save(tag);
        var loaded = EntityType.loadEntityRecursive(tag, helper.getLevel(), e -> e);
        helper.assertTrue(loaded instanceof JetskiEntity, "jetski survives save/load");
        helper.assertTrue(Math.abs(((JetskiEntity)loaded).boostCharge()-ski.boostCharge())<.001, "boost charge persists");
        helper.assertTrue(ski.getPickResult().is(PracticeMod.JETSKI.get()), "pick-block returns jetski");
        ski.hurt(helper.getLevel().damageSources().playerAttack(rider), 1);
        helper.assertTrue(ski.isRemoved(), "empty jetski can be collected");
        helper.assertItemEntityCountIs(PracticeMod.JETSKI.get(), new BlockPos(1,4,1), 3, 1);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void steeringPreservesPassengerInterpolation(GameTestHelper helper) {
        var ski = floating(helper);
        var rider = helper.makeMockPlayer(GameType.SURVIVAL);
        rider.startRiding(ski);
        for (float steer : new float[]{1, -1}) {
            ski.setPos(helper.absoluteVec(new Vec3(1.5, 3.75, 1.5)));
            // Cross the yaw wrap in both directions while keeping a free-look offset.
            ski.setYRot(steer > 0 ? -179.9F : 179.9F);
            rider.setYRot(ski.getYRot() + 30);
            rider.setYHeadRot(rider.getYRot());
            float oldHullYaw = ski.getYRot(), oldRiderYaw = rider.getYRot();
            double angle = Math.toRadians(oldHullYaw);
            ski.setDeltaMovement(-Math.sin(angle)*.4, 0, Math.cos(angle)*.4);
            ski.acceptInput(rider, 1, steer, false);
            ski.tick();
            helper.assertTrue(rider.getYRot() == oldRiderYaw,
                    "hull tick must not turn the rider before the history snapshot");
            // Match ClientLevel.tickPassenger + LivingEntity.baseTick history snapshots.
            rider.setOldPosAndRot();
            rider.yHeadRotO = rider.getYHeadRot();
            ski.positionRider(rider);
            float turn = net.minecraft.util.Mth.wrapDegrees(ski.getYRot()-oldHullYaw);
            helper.assertTrue(Math.abs(turn) > .01 && Math.abs(turn) < 5, "bounded turn across yaw wrap");
            helper.assertTrue(Math.abs(rider.getViewYRot(.5F)-(oldRiderYaw+turn*.5F)) < .001,
                    "half-frame camera yaw interpolates half the steering turn");
            helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(rider.getYRot()-ski.getYRot())-30) < .001,
                    "steering preserves mouse free-look offset");
            ski.positionRider(rider);
            helper.assertTrue(Math.abs(rider.getYRot()-(oldRiderYaw+turn)) < .001,
                    "extra passenger positioning cannot double-apply steering");
        }
        helper.assertTrue(Math.abs(ski.getBbWidth()-1.63875F) < .001, "collision width scales with the hull");
        helper.assertTrue(Math.abs(ski.getBbHeight()-.92625F) < .001, "collision height scales with the hull");
        ski.setPos(helper.absoluteVec(new Vec3(1.5, 3.75, 1.5)));
        ski.setXRot(0); ski.setYRot(0);
        // The simulation's bank is small here; check the seat grew rather than leaving hips inside the shell.
        ski.positionRider(rider);
        helper.assertTrue(rider.getY()-ski.getY() > .27, "enlarged saddle raises the rider");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void waterPhysicsAndValidatedInput(GameTestHelper helper) {
        var ski = floating(helper);
        Vec3 origin = ski.position();
        helper.assertTrue(Double.isFinite(ski.waterSurface()), "water surface found");
        for(int i=0;i<50;i++) ski.tick();
        helper.assertTrue(Math.abs(ski.getY()-(ski.waterSurface()-JetskiDynamics.DRAFT))<.02, "idle floats at waterline");
        var rider = helper.makeMockPlayer(GameType.SURVIVAL); rider.startRiding(ski);
        ski.acceptInput(helper.makeMockPlayer(GameType.SURVIVAL), 1, 1, true); ski.tick();
        helper.assertTrue(ski.speed()==0 && ski.boostCharge()==1, "non-riders cannot throttle or boost");
        ski.acceptInput(rider, Float.NaN, 0, true); ski.tick();
        ski.acceptInput(rider, 1, Float.POSITIVE_INFINITY, true); ski.tick();
        helper.assertTrue(ski.speed()==0, "invalid floats cannot poison simulation");
        // Leave room for two forward ticks before the wider hull reaches the fixture edge.
        ski.setPos(origin.add(0, 0, -.4));
        ski.acceptInput(rider, 1, 0, true); ski.tick();
        helper.assertTrue(ski.speed()>.1 && ski.isBoosting(), "valid boost propels the floating hull");
        helper.assertTrue(ski.jetStrength() > .9F, "boost drives full nozzle spray");
        double speed = ski.speed();
        rider.stopRiding(); ski.tick();
        helper.assertFalse(ski.isBoosting(), "dismount clears boost");
        helper.assertTrue(ski.jetStrength() <= .25F, "dismount leaves only a weak coasting wake");
        helper.assertTrue(ski.speed()>speed*.9, "unoccupied hull retains momentum");
        ski.setPos(origin.add(0,0,-.5)); ski.setDeltaMovement(0,0,.9);
        helper.setBlock(new BlockPos(1,3,2), Blocks.STONE);
        helper.setBlock(new BlockPos(1,4,2), Blocks.STONE);
        ski.tick();
        helper.assertTrue(ski.getZ()<=origin.z-.07 && ski.getDeltaMovement().z==0, "wall collision clears blocked momentum");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void nozzleFollowsHullAndNeedsWater(GameTestHelper helper) {
        var ski = floating(helper);
        Vec3 center = ski.position();
        Vec3 mouth = JetskiWaterEffects.nozzlePosition(ski, 1);
        helper.assertTrue(Math.abs(mouth.z-center.z+15.3/16*JetskiEntity.HULL_SCALE)<.001,
                "spray starts at the scaled nozzle mouth");
        ski.setYRot(90);
        Vec3 turned = JetskiWaterEffects.nozzlePosition(ski, 1).subtract(center);
        helper.assertTrue(turned.x > 1.3 && Math.abs(turned.z) < .001,
                "nozzle follows the hull heading");
        ski.setXRot(-20);
        Vec3 pitched = JetskiWaterEffects.nozzlePosition(ski, 1).subtract(center);
        helper.assertTrue(Math.abs(pitched.length()-mouth.subtract(center).length())<.001
                && Math.abs(pitched.y-turned.y)>.1, "nozzle follows hull pitch without changing its offset length");
        ski.setXRot(0); ski.setYRot(0);
        ski.tick();
        helper.assertTrue(ski.jetStrength()==0, "idle hull has no propulsion spray");
        var rider = helper.makeMockPlayer(GameType.SURVIVAL); rider.startRiding(ski);
        ski.setPos(center.add(0, 8, 0));
        ski.acceptInput(rider, 1, 0, true); ski.tick();
        helper.assertTrue(ski.jetStrength()==0, "throttle cannot spray away from water");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void placementConsumesItemAndRejectsOverlap(GameTestHelper helper) {
        floating(helper);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(1.5,5,1.5)));
        player.setXRot(90);
        player.setItemInHand(InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(PracticeMod.JETSKI.get(), 2));
        var item = PracticeMod.JETSKI.get();
        helper.assertTrue(item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult().consumesAction(), "place onto water");
        helper.assertTrue(player.getMainHandItem().getCount()==1, "survival placement consumes exactly one");
        helper.assertFalse(item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult().consumesAction(), "overlapping placement is rejected");
        helper.assertTrue(player.getMainHandItem().getCount()==1, "failed placement does not consume item");
        var skis = helper.getLevel().getEntitiesOfClass(JetskiEntity.class, player.getBoundingBox().inflate(4));
        helper.assertTrue(skis.size()==1, "one hull was spawned");
        skis.getFirst().discard();
        var creative = helper.makeMockPlayer(GameType.CREATIVE);
        // makeMockPlayer overrides isCreative, but does not initialize the ability flags.
        creative.getAbilities().instabuild = true;
        creative.setPos(player.position()); creative.setXRot(90);
        creative.setItemInHand(InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(item));
        helper.assertTrue(item.use(helper.getLevel(), creative, InteractionHand.MAIN_HAND).getResult().consumesAction(), "creative placement succeeds");
        helper.assertTrue(creative.getMainHandItem().getCount()==1, "creative placement preserves item");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public void staleInputExpires(GameTestHelper helper) {
        var ski = floating(helper);
        var rider = helper.makeMockPlayer(GameType.SURVIVAL); rider.startRiding(ski);
        ski.acceptInput(rider, 1, 1, true);
        helper.runAfterDelay(12, () -> {
            ski.tick();
            helper.assertTrue(ski.speed()==0 && !ski.isBoosting(), "missing input clears propulsion after ten ticks");
            helper.succeed();
        });
    }
}
