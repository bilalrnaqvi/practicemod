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
public class AtvGameTests {
    private static AtvEntity grounded(GameTestHelper helper) {
        for (int x=-2; x<5; x++) for (int z=-2; z<5; z++) {
            helper.setBlock(new BlockPos(x,1,z), Blocks.STONE);
            for (int y=2; y<7; y++) helper.setBlock(new BlockPos(x,y,z), Blocks.AIR);
        }
        var atv = new AtvEntity(PracticeMod.ATV_ENTITY.get(), helper.getLevel());
        // Keep the front collision face 0.3 blocks from the obstacle at z=2.
        atv.setPos(helper.absoluteVec(new Vec3(1.5,2,2-atv.getBbWidth()*.5-.3)));
        atv.setOnGround(true);
        return atv;
    }

    @GameTest(template="empty")
    public void placementMountSaveAndPickup(GameTestHelper helper) {
        grounded(helper);
        var player=helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(1.5,3,1.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(PracticeMod.ATV.get(),2));
        var hit=new BlockHitResult(helper.absoluteVec(new Vec3(1.5,2,1.5)),Direction.UP,helper.absolutePos(new BlockPos(1,1,1)),false);
        var context=new UseOnContext(player,InteractionHand.MAIN_HAND,hit);
        helper.assertTrue(PracticeMod.ATV.get().useOn(context).consumesAction(),"solid ground placement");
        helper.assertTrue(player.getMainHandItem().getCount()==1,"consume one in survival");
        helper.assertFalse(PracticeMod.ATV.get().useOn(context).consumesAction(),"reject overlap");
        var atv=helper.getLevel().getEntitiesOfClass(AtvEntity.class,player.getBoundingBox().inflate(3)).getFirst();
        helper.assertTrue(atv.interact(player,InteractionHand.MAIN_HAND).consumesAction(),"right-click mounts");
        var passenger=helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(passenger.startRiding(atv),"second seat");
        helper.assertFalse(helper.makeMockPlayer(GameType.SURVIVAL).startRiding(atv),"reject third rider");
        passenger.stopRiding();
        helper.assertTrue(atv.getControllingPassenger()==null,"server retains vehicle authority");
        atv.positionRider(player);
        helper.assertTrue(Math.abs(player.getY()-atv.getY()-(AtvEntity.SEAT_HEIGHT-AtvEntity.HIP_OFFSET))<.001,"saddle attachment");
        atv.hurt(helper.getLevel().damageSources().playerAttack(player),1);
        helper.assertFalse(atv.isRemoved(),"occupied quad protected from pickup");
        player.stopRiding();
        CompoundTag tag=new CompoundTag(); atv.save(tag);
        helper.assertTrue(EntityType.loadEntityRecursive(tag,helper.getLevel(),e->e) instanceof AtvEntity,"save/load");
        helper.assertTrue(atv.getPickResult().is(PracticeMod.ATV.get()),"pick block");
        atv.hurt(helper.getLevel().damageSources().playerAttack(player),1);
        helper.assertTrue(atv.isRemoved(),"empty quad collected");
        helper.assertItemEntityCountIs(PracticeMod.ATV.get(),new BlockPos(1,2,1),3,1);
        helper.succeed();
    }

    @GameTest(template="empty")
    public void stepsHopsWallsAndLanding(GameTestHelper helper) {
        var atv=grounded(helper); Vec3 origin=atv.position();
        helper.setBlock(new BlockPos(1,2,2),Blocks.STONE);
        atv.setDeltaMovement(0,0,.48); atv.tick();
        helper.assertTrue(Math.abs(atv.getY()-origin.y-1)<.001,"climbs one full block");
        helper.assertTrue(atv.getXRot() < -.5 && atv.getXRot() >= -4, "full-block hop starts a smooth nose lift");
        helper.assertTrue(atv.getDeltaMovement().y>.25,"fast obstacle climb produces a physical hop");
        helper.assertTrue(atv.visual(0,1)<-.05,"step compresses chassis spring");
        atv.tick();
        helper.assertTrue(atv.getY()>origin.y+1.1,"hop leaves ground physically");
        atv=grounded(helper); origin=atv.position();
        helper.setBlock(new BlockPos(1,2,2),Blocks.STONE);
        helper.setBlock(new BlockPos(1,3,2),Blocks.STONE);
        atv.setDeltaMovement(0,0,.48); atv.tick();
        helper.assertTrue(atv.getZ()<=origin.z+.301 && atv.getDeltaMovement().z==0,"tall wall clears blocked momentum");
        atv=grounded(helper); origin=atv.position(); atv.setPos(origin.add(0,2,0)); atv.setOnGround(false);
        boolean compressed=false;
        for (int i=0;i<24;i++) { atv.tick(); compressed |= atv.visual(0,1)<-.05; }
        helper.assertTrue(atv.onGround() && Math.abs(atv.getY()-origin.y)<.001,"lands on floor");
        helper.assertTrue(compressed,"landing compresses suspension");
        helper.succeed();
    }

    @GameTest(template="empty")
    public void slabsAndSlowCrawling(GameTestHelper helper) {
        for (var block : new net.minecraft.world.level.block.Block[]{Blocks.STONE_SLAB,Blocks.STONE_STAIRS,Blocks.STONE}) {
            var atv=grounded(helper); Vec3 origin=atv.position();
            helper.setBlock(new BlockPos(1,2,2),block);
            // Begin just short of the obstacle with the current collision width.
            atv.setPos(helper.absoluteVec(new Vec3(1.5,2,2-atv.getBbWidth()*.5-.05)));
            atv.setDeltaMovement(0,0,.1); atv.tick();
            helper.assertTrue(atv.getY()>origin.y+.4,"crawls over slab, stair or block");
            helper.assertTrue(atv.getXRot() < -.2 && atv.getXRot() >= -4, "slow climb starts pitching without a pose snap");
            helper.assertTrue(atv.getDeltaMovement().y<=0,"slow obstacle climb does not launch");
        }
        helper.succeed();
    }

    @GameTest(template="empty")
    public void continuousTerrainSuspension(GameTestHelper helper) {
        var atv = grounded(helper);
        for (int x=-2; x<5; x++) for (int z=-2; z<18; z++) {
            helper.setBlock(new BlockPos(x,1,z), Blocks.STONE);
            for (int y=2; y<7; y++) helper.setBlock(new BlockPos(x,y,z), Blocks.AIR);
            if (z>=2 && z<=4) helper.setBlock(new BlockPos(x,2,z), Blocks.STONE);
        }
        double lowestPitch = 0;
        for (int tick=0; tick<160; tick++) {
            float pitch = atv.getXRot(), roll = atv.visual(1,1);
            if (tick<48) atv.setDeltaMovement(0,atv.getDeltaMovement().y,.16);
            else atv.setDeltaMovement(0,atv.getDeltaMovement().y,0);
            atv.tick();
            lowestPitch = Math.min(lowestPitch, atv.getXRot());
            helper.assertTrue(Math.abs(atv.getXRot()-pitch)<=4.001,"pitch remains continuous across block and ledge");
            helper.assertTrue(Math.abs(atv.visual(1,1)-roll)<=3.001,"roll remains continuous across contacts");
            helper.assertTrue(Math.abs(atv.visual(7,1)-atv.getXRot())<.001,"pitch shares the suspension sync state");
            for (int wheel=0; wheel<4; wheel++) {
                float travel = atv.visual(wheel+3,1);
                helper.assertTrue(Float.isFinite(travel) && travel>=-AtvDynamics.WHEEL_DROOP-.0001
                        && travel<=AtvDynamics.WHEEL_COMPRESSION+.0001,"wheel remains attached through terrain transitions");
            }
        }
        helper.assertTrue(lowestPitch < -10,"sustained climb builds meaningful terrain attitude");
        helper.assertTrue(atv.onGround() && Math.abs(atv.getXRot())<.01,"chassis settles after the ledge");
        for (int wheel=0; wheel<4; wheel++) helper.assertTrue(Math.abs(atv.visual(wheel+3,1))<.001,"wheels settle on level terrain");
        helper.succeed();
    }

    @GameTest(template="empty")
    public void fullSizeCockpitAndClearance(GameTestHelper helper) {
        var atv=grounded(helper);
        // The empty template is only three blocks wide; provide clear landing pads
        // beyond both edges for the full player width beside the wider vehicle.
        for (int x : new int[]{-1,3}) for (int z=0;z<3;z++) {
            helper.setBlock(new BlockPos(x,1,z),Blocks.STONE);
            for (int y=2;y<7;y++) helper.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        }
        helper.assertTrue(Math.abs(atv.getBbWidth()-2.2)<.001 && Math.abs(atv.getBbHeight()-2.4)<.001,
                "full-size tires and cage represented by collision dimensions");
        var rider=helper.makeMockPlayer(GameType.SURVIVAL); rider.startRiding(atv);
        atv.positionRider(rider);
        helper.assertTrue(Math.abs(rider.getY()+AtvEntity.HIP_OFFSET-atv.getY()-.99)<.001,
                "rider hips align with raised bucket seat");
        helper.assertTrue(Math.abs(rider.getZ()-atv.getZ()+.40)<.001,"rider aligned with seat depth");
        var dismount=atv.getDismountLocationForPassenger(rider);
        helper.assertTrue(Math.abs(dismount.x-atv.getX())>atv.getBbWidth()*.5+rider.getBbWidth()*.5,
                "dismount clears tires and collision body: "+dismount+" from "+atv.position());
        helper.setBlock(new BlockPos(1,4,1),Blocks.STONE);
        helper.assertFalse(helper.getLevel().noCollision(atv,atv.getBoundingBox()),"cage cannot fit under a two-block ceiling");
        helper.succeed();
    }

    @GameTest(template="empty")
    public void validatedInputAndAirborneMomentum(GameTestHelper helper) {
        var atv=grounded(helper); var rider=helper.makeMockPlayer(GameType.SURVIVAL); rider.startRiding(atv);
        atv.acceptInput(helper.makeMockPlayer(GameType.SURVIVAL),1,1); atv.tick();
        atv.acceptInput(rider,Float.NaN,1); atv.tick();
        atv.acceptInput(rider,1,Float.POSITIVE_INFINITY); atv.tick();
        helper.assertTrue(atv.speed()==0,"strangers and invalid floats cannot drive");
        atv.acceptInput(rider,1,0); atv.tick();
        helper.assertTrue(atv.speed()>.02,"rider accelerates");
        rider.stopRiding(); double speed=atv.speed(); atv.tick();
        helper.assertTrue(atv.speed()<speed && atv.speed()>0,"dismount clears throttle and coasts");
        atv.setPos(atv.position().add(0,2,0)); atv.setOnGround(false); atv.setDeltaMovement(0,0,.2);
        rider.startRiding(atv); atv.acceptInput(rider,1,1); float yaw=atv.getYRot(); atv.tick();
        helper.assertTrue(atv.getYRot()==yaw && atv.getDeltaMovement().z<.2 && atv.getDeltaMovement().y<0,"ballistic in air");
        helper.succeed();
    }

    @GameTest(template="empty")
    public void cameraTurnPreservesFreeLook(GameTestHelper helper) {
        var atv=grounded(helper); var rider=helper.makeMockPlayer(GameType.SURVIVAL); rider.startRiding(atv);
        atv.setYRot(-179.9F); rider.setYRot(-149.9F); rider.setYHeadRot(-149.9F);
        float before=rider.getYRot(), oldYaw=atv.getYRot();
        atv.setDeltaMovement(0,0,-.3); atv.acceptInput(rider,1,1); atv.tick();
        helper.assertTrue(rider.getYRot()==before,"turn deferred until rider history snapshot");
        rider.setOldPosAndRot(); rider.yHeadRotO=rider.getYHeadRot(); atv.positionRider(rider);
        float turn=net.minecraft.util.Mth.wrapDegrees(atv.getYRot()-oldYaw);
        helper.assertTrue(Math.abs(turn)>.01 && Math.abs(turn)<5,"bounded wraparound turn");
        helper.assertTrue(Math.abs(rider.getViewYRot(.5F)-(before+turn*.5))<.001,"smooth half-frame camera yaw");
        helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(rider.getYRot()-atv.getYRot())-30)<.001,"free-look retained");
        atv.positionRider(rider);
        helper.assertTrue(Math.abs(rider.getYRot()-(before+turn))<.001,"turn applied once");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=30)
    public void twoRidersAndDriverHandoff(GameTestHelper helper) {
        var atv=grounded(helper);
        var driver=helper.makeMockPlayer(GameType.SURVIVAL);
        var passenger=helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(atv.interact(driver,InteractionHand.MAIN_HAND).consumesAction(),"driver boards");
        helper.assertTrue(atv.interact(passenger,InteractionHand.MAIN_HAND).consumesAction(),"passenger boards occupied buggy");
        helper.assertFalse(atv.interact(helper.makeMockPlayer(GameType.SURVIVAL),InteractionHand.MAIN_HAND).consumesAction(),"full buggy rejects third rider");
        atv.positionRider(driver); atv.positionRider(passenger);
        helper.assertTrue(Math.abs(passenger.getX()-driver.getX()-1.12)<.001,"separate side-by-side seat anchors");
        helper.assertFalse(driver.getBoundingBox().intersects(passenger.getBoundingBox()),"riders do not overlap");
        atv.acceptInput(passenger,1,1); atv.tick();
        helper.assertTrue(atv.speed()==0,"passenger cannot drive");
        atv.setDeltaMovement(0,0,.3); atv.acceptInput(driver,1,1);
        float yaw=atv.getYRot(); atv.tick();
        float turn=net.minecraft.util.Mth.wrapDegrees(atv.getYRot()-yaw);
        atv.positionRider(driver); atv.positionRider(passenger);
        helper.assertTrue(Math.abs(driver.getYRot()-turn)<.001 && Math.abs(passenger.getYRot()-turn)<.001,
                "both riders receive the same camera turn");
        atv.positionRider(passenger);
        helper.assertTrue(Math.abs(passenger.getYRot()-turn)<.001,"passenger turn applied once");
        passenger.stopRiding();
        double speed=atv.speed(); atv.tick();
        helper.assertTrue(atv.speed()>speed,"passenger dismount preserves driver throttle");
        passenger.startRiding(atv); driver.stopRiding();
        helper.assertTrue(atv.isDriver(passenger),"remaining occupant becomes driver");
        atv.setYRot(0); atv.setOnGround(true); atv.setDeltaMovement(Vec3.ZERO);
        atv.acceptInput(driver,1,1); atv.tick();
        helper.assertTrue(atv.speed()==0,"handoff clears old driver input and rejects departed driver");
        atv.positionRider(passenger);
        helper.assertTrue(passenger.getX()<atv.getX()-.4,"promoted rider moves to driver side while suspension rolls");
        atv.acceptInput(passenger,1,0); atv.tick();
        helper.assertTrue(atv.speed()>0,"promoted driver controls buggy with fresh input");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=30)
    public void staleInputExpires(GameTestHelper helper) {
        var atv=grounded(helper); var rider=helper.makeMockPlayer(GameType.SURVIVAL); rider.startRiding(atv);
        atv.acceptInput(rider,1,1);
        helper.runAfterDelay(12,()->{
            atv.tick(); helper.assertTrue(atv.speed()==0,"expired input grants no propulsion"); helper.succeed();
        });
    }
}
