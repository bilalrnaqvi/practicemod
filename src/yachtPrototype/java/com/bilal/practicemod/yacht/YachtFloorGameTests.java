package com.bilal.practicemod.yacht;

import com.bilal.practicemod.PracticeMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PracticeMod.MODID)
@PrefixGameTestTemplate(false)
public final class YachtFloorGameTests {
    @GameTest(template="empty", batch="yacht_floor", timeoutTicks=1000)
    public void movingFloorSupportsUnmountedEntityThroughAccelerationAndStop(GameTestHelper h) {
        runProbe(h,new Vec3(0,100,0));
    }

    @GameTest(template="empty", batch="yacht_precision", timeoutTicks=1000)
    public void movingFloorAtMillionBlockCoordinates(GameTestHelper h) {
        runProbe(h,new Vec3(1_000_000,100,1_000_000));
    }

    @GameTest(template="empty", batch="yacht_turn", timeoutTicks=1000)
    public void movingFloorTurnsAtFarCoordinates(GameTestHelper h) {
        runProbe(h,new Vec3(-1_000_000,100,-1_000_000),true,h::succeed);
    }

    @GameTest(template="empty", batch="yacht_multiregion", timeoutTicks=1000)
    public void simultaneousDistantPlatforms(GameTestHelper h) {
        var completed=new java.util.concurrent.atomic.AtomicInteger();
        Runnable finish=()->{ if(completed.incrementAndGet()==2) h.succeed(); };
        runProbe(h,new Vec3(1_000_000,100,1_000_000),false,finish);
        runProbe(h,new Vec3(-1_000_000,100,-1_000_000),false,finish);
    }

    @GameTest(template="empty", batch="yacht_jump", timeoutTicks=1000)
    public void unmountedContactProbeJumpsAndLandsDuringCruise(GameTestHelper h) {
        runProbe(h,new Vec3(0,100,0),false,h::succeed,true);
    }

    @GameTest(template="empty", batch="yacht_stairs", timeoutTicks=500)
    public void unmountedPlayerClimbsMovingStaircase(GameTestHelper h) {
        for(int x=-1;x<=1;x++) for(int z=-1;z<=2;z++) h.getLevel().getChunk(x,z);
        var ship=YachtFloorPrototype.spawn(h.getLevel(),0,100,0);
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setInvulnerable(true);
        Vec3[] targets={new Vec3(2,0,5.5),new Vec3(2,0,6.5),new Vec3(4.5,0,6.5),
                new Vec3(4.5,0,2.5),new Vec3(6.5,0,2.5)};
        int[] target={0};
        h.runAfterDelay(50,()->player.setPos(YachtFloorPrototype.worldPoint(ship,2,1.05,1.25)));
        for(int tick=51;tick<=430;tick++) h.runAfterDelay(tick,()->{
            player.tick();
            if(target[0]>=targets.length) return;
            var center=ship.getPlot().getCenterBlock();
            var local=ship.logicalPose().transformPositionInverse(player.position())
                .subtract(center.getX(),center.getY(),center.getZ());
            var destination=targets[target[0]];
            var delta=new Vec3(destination.x-local.x,0,destination.z-local.z);
            if(delta.horizontalDistance()<.12) { target[0]++; return; }
            var step=ship.logicalPose().transformNormal(delta.normalize().scale(.08));
            player.move(net.minecraft.world.entity.MoverType.SELF,new Vec3(step.x,-.08,step.z));
        });
        h.runAfterDelay(440,()->{
            try {
                h.assertFalse(player.isPassenger(),"stairs never mount the player");
                h.assertTrue(target[0]==targets.length,"stair route stopped at waypoint "+target[0]+" pos="+player.position());
                double error=Math.abs(player.getY()-YachtFloorPrototype.worldPoint(ship,6.5,5,2.5).y);
                h.assertTrue(error<.15,"player stands on upper landing: "+error);
            } finally { player.discard(); YachtFloorPrototype.dispose(ship); }
            h.succeed();
        });
    }

    private void runProbe(GameTestHelper h, Vec3 p) { runProbe(h,p,false,h::succeed); }

    private void runProbe(GameTestHelper h, Vec3 p, boolean turn, Runnable finish) {
        runProbe(h,p,turn,finish,false);
    }

    private void runProbe(GameTestHelper h, Vec3 p, boolean turn, Runnable finish, boolean jump) {
        // Finish bounded terrain loading before measuring physics/contact, not during motion.
        int cx=net.minecraft.util.Mth.floor(p.x)>>4, cz=net.minecraft.util.Mth.floor(p.z)>>4;
        for (int x=cx-1;x<=cx+1;x++) for(int z=cz-1;z<=cz+2;z++) h.getLevel().getChunk(x,z);
        var ship=YachtFloorPrototype.spawn(h.getLevel(),p.x,p.y,p.z,turn);
        YachtFloorPrototype.hold(ship);
        ArmorStand occupant=EntityType.ARMOR_STAND.create(h.getLevel());
        occupant.setPos(p.x+8.5,p.y+1.05,p.z+2.5);
        h.getLevel().addFreshEntity(occupant);
        final Vec3[] settled = new Vec3[1];
        final int[] stable={0}, start={-1};
        final boolean[] done={false};
        // Register before ticking: mutating GameTestInfo's pending-task map from a
        // callback can invalidate its fastutil iterator.
        for(int tick=1;tick<=960;tick++) {
            final int now=tick;
            h.runAfterDelay(tick,()->{
                if(done[0]) return;
                try {
                    if(start[0]<0) {
                        stable[0]=occupant.onGround()?stable[0]+1:0;
                        h.assertTrue(now<=300,"contact probe did not settle within 300 ticks");
                        if(stable[0]<20) return;
                        settled[0]=ship.logicalPose().transformPositionInverse(occupant.position());
                        start[0]=now;
                        YachtFloorPrototype.start(ship);
                    }
                    int elapsed=now-start[0];
                    h.assertFalse(ship.isRemoved(),"fixture unloaded before motion completed");
                    h.assertFalse(occupant.isPassenger(),"contact probe remains unmounted");
                    double floorError=occupant.getY()-YachtFloorPrototype.worldPoint(ship,8.5,1,2.5).y;
                    if(jump && elapsed==240) occupant.setDeltaMovement(0,.42,0);
                    if(jump && elapsed==245) h.assertTrue(floorError>.4,"probe leaves the moving floor");
                    if(elapsed%10==0 && !(jump && elapsed>=240 && elapsed<=270))
                        h.assertTrue(Math.abs(floorError)<.15,"probe stays supported throughout motion: "+floorError);
                    if(elapsed!=640) return;
                    var pose=YachtFloorPrototype.worldPoint(ship,0,0,0);
                    h.assertTrue(pose.z-p.z>10 && pose.z-p.z<20,"physical travel: "+(pose.z-p.z));
                    h.assertTrue(Math.abs(pose.y-p.y)<.1,"height hold: "+(pose.y-p.y));
                    h.assertTrue(Math.abs(floorError)<.1,"probe finishes on lower deck: "+floorError);
                    var local=ship.logicalPose().transformPositionInverse(occupant.position());
                    double drift=Math.hypot(local.x-settled[0].x,local.z-settled[0].z);
                    // A jump may change local X/Z; grounded probes must stay within .05.
                    h.assertTrue(drift<(jump?1.0:.05),"relative drift: "+drift);
                    System.out.println("YACHT_RESULT origin="+p+" jump="+jump+" settledTick="+start[0]
                            +" travel="+(pose.z-p.z)+" heightError="+(pose.y-p.y)+" drift="+drift);
                    done[0]=true;
                    occupant.discard();
                    YachtFloorPrototype.dispose(ship);
                    finish.run();
                } catch(RuntimeException | AssertionError failure) {
                    done[0]=true;
                    occupant.discard();
                    YachtFloorPrototype.dispose(ship);
                    throw failure;
                }
            });
        }
    }
}
