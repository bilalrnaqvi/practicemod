package com.bilal.practicemod;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PracticeMod.MODID)
@PrefixGameTestTemplate(false)
public class MechanicalDolphinGameTests {
    private static MechanicalDolphinEntity pool(GameTestHelper helper) {
        for (int x=0;x<3;x++) for (int z=0;z<3;z++) {
            helper.setBlock(new BlockPos(x,0,z), Blocks.STONE);
            for (int y=1;y<4;y++) helper.setBlock(new BlockPos(x,y,z), Blocks.WATER);
            for (int y=4;y<15;y++) helper.setBlock(new BlockPos(x,y,z), Blocks.AIR);
        }
        var dolphin = new MechanicalDolphinEntity(PracticeMod.MECHANICAL_DOLPHIN_ENTITY.get(), helper.getLevel());
        dolphin.setPos(helper.absoluteVec(new Vec3(1.5, 2.5, 1.5)));
        return dolphin;
    }

    @GameTest(template="empty")
    public void mountAirPersistenceAndPickup(GameTestHelper helper) {
        var dolphin=pool(helper);
        var rider=helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(dolphin.interact(rider,InteractionHand.MAIN_HAND).consumesAction(), "right-click mounts");
        helper.assertFalse(helper.makeMockPlayer(GameType.SURVIVAL).startRiding(dolphin), "single rider");
        helper.assertTrue(dolphin.getControllingPassenger()==null && !dolphin.dismountsUnderwater(), "server moves hull and rider stays submerged");
        rider.setAirSupply(1);
        dolphin.tick(); dolphin.positionRider(rider);
        helper.assertTrue(rider.getAirSupply()==rider.getMaxAirSupply(), "mounted rider has breathing air");
        helper.assertTrue(rider.position().add(0,rider.getEyeHeight(),0).distanceTo(dolphin.position()
                .add(MechanicalDolphinEntity.pilotEyeOffset(dolphin.getYRot(),dolphin.getXRot())))<.001,
                "prone pilot eye anchored inside canopy");
        for (float yaw : new float[]{0,90,180,-90}) for (float pitch : new float[]{-60,0,60}) {
            dolphin.setYRot(yaw); dolphin.setXRot(pitch); dolphin.positionRider(rider);
            helper.assertTrue(rider.getEyePosition().distanceTo(dolphin.position()
                    .add(MechanicalDolphinEntity.pilotEyeOffset(yaw,pitch)))<.001,
                    "camera stays attached during turns, dives and breaches");
        }
        dolphin.setYRot(0); dolphin.setXRot(0); dolphin.positionRider(rider);
        dolphin.hurt(helper.getLevel().damageSources().playerAttack(rider),1);
        helper.assertFalse(dolphin.isRemoved(), "occupied craft cannot be picked up");
        rider.stopRiding();
        CompoundTag tag=new CompoundTag(); dolphin.save(tag);
        helper.assertTrue(EntityType.loadEntityRecursive(tag,helper.getLevel(),e->e) instanceof MechanicalDolphinEntity, "save/load keeps entity type");
        helper.assertTrue(dolphin.getPickResult().is(PracticeMod.MECHANICAL_DOLPHIN.get()), "pick-block item");
        dolphin.hurt(helper.getLevel().damageSources().playerAttack(rider),1);
        helper.assertTrue(dolphin.isRemoved(), "empty craft collected");
        helper.assertItemEntityCountIs(PracticeMod.MECHANICAL_DOLPHIN.get(),new BlockPos(1,3,1),3,1);
        helper.succeed();
    }

    @GameTest(template="empty")
    public void realWaterBreachAndReentry(GameTestHelper helper) {
        var dolphin=pool(helper);
        var rider=helper.makeMockPlayer(GameType.SURVIVAL); rider.startRiding(dolphin);
        Vec3 origin=dolphin.position();
        double surface=dolphin.nearbySurface(), maxY=origin.y;
        boolean leftWater=false, returned=false, nosedDown=false;
        for (int i=0;i<65;i++) {
            dolphin.acceptInput(rider,1,0,0,true);
            dolphin.tick();
            maxY=Math.max(maxY,dolphin.getY());
            if (!dolphin.propulsionInWater() && dolphin.getY()>surface) leftWater=true;
            if (leftWater && dolphin.getXRot()>20) nosedDown=true;
            if (leftWater && dolphin.propulsionInWater()) returned=true;
            // Keep the narrow test column under the craft; all vertical integration is real.
            dolphin.setPos(origin.x,dolphin.getY(),origin.z);
        }
        helper.assertTrue(leftWater && maxY>surface+3, "breach clears actual water surface by several blocks");
        helper.assertTrue(nosedDown && returned, "ballistic nose-down re-entry reconnects with water");
        helper.assertTrue(rider.getVehicle()==dolphin, "rider retained through breach");
        helper.succeed();
    }

    @GameTest(template="empty")
    public void validatedInputAndWallCollision(GameTestHelper helper) {
        var dolphin=pool(helper);
        var rider=helper.makeMockPlayer(GameType.SURVIVAL); rider.startRiding(dolphin);
        dolphin.acceptInput(helper.makeMockPlayer(GameType.SURVIVAL),1,0,0,true); dolphin.tick();
        helper.assertTrue(dolphin.getDeltaMovement().horizontalDistance()<.001, "strangers cannot drive");
        dolphin.acceptInput(rider,Float.NaN,0,0,true); dolphin.tick();
        dolphin.acceptInput(rider,1,Float.POSITIVE_INFINITY,0,true); dolphin.tick();
        helper.assertTrue(dolphin.getDeltaMovement().horizontalDistance()<.001, "invalid packets ignored");
        dolphin.setPos(helper.absoluteVec(new Vec3(1.5,2.5,1.2)));
        dolphin.acceptInput(rider,1,0,0,false); dolphin.tick();
        helper.assertTrue(dolphin.getDeltaMovement().z>.08, "valid intent swims forward");
        helper.setBlock(new BlockPos(1,2,2),Blocks.STONE);
        helper.setBlock(new BlockPos(1,3,2),Blocks.STONE);
        dolphin.setDeltaMovement(0,0,.8); dolphin.tick();
        helper.assertTrue(dolphin.getDeltaMovement().z==0, "wall clears blocked momentum");
        helper.assertTrue(dolphin.getZ()<helper.absoluteVec(new Vec3(1.5,2.5,1.46)).z, "hull cannot cross wall");
        helper.succeed();
    }

    @GameTest(template="empty")
    public void placementAndOverlap(GameTestHelper helper) {
        pool(helper);
        var player=helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(1.5,5,1.5))); player.setXRot(90);
        var item=PracticeMod.MECHANICAL_DOLPHIN.get();
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item,2));
        helper.assertTrue(item.use(helper.getLevel(),player,InteractionHand.MAIN_HAND).getResult().consumesAction(), "place on water");
        helper.assertTrue(player.getMainHandItem().getCount()==1, "consume one item");
        helper.assertFalse(item.use(helper.getLevel(),player,InteractionHand.MAIN_HAND).getResult().consumesAction(), "reject overlapping craft");
        helper.assertTrue(player.getMainHandItem().getCount()==1, "failed placement preserves item");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=30)
    public void staleInputExpires(GameTestHelper helper) {
        var dolphin=pool(helper);
        var rider=helper.makeMockPlayer(GameType.SURVIVAL); rider.startRiding(dolphin);
        dolphin.acceptInput(rider,1,0,0,true);
        helper.runAfterDelay(12,()->{
            dolphin.tick();
            helper.assertTrue(dolphin.getDeltaMovement().horizontalDistance()<.001, "expired input cannot propel craft");
            helper.assertTrue(dolphin.getDeltaMovement().y<=.12, "expired Jump cannot launch craft");
            helper.succeed();
        });
    }
}
