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
            if(target[0]>=targets.length) {
                player.setDeltaMovement(0,player.getDeltaMovement().y,0);
                player.tick();
                return;
            }
            var center=ship.getPlot().getCenterBlock();
            var local=ship.logicalPose().transformPositionInverse(player.position())
                .subtract(center.getX(),center.getY(),center.getZ());
            var destination=targets[target[0]];
            var delta=new Vec3(destination.x-local.x,0,destination.z-local.z);
            if(delta.horizontalDistance()<.12) target[0]++;
            var step=delta.horizontalDistance()<.12?Vec3.ZERO:ship.logicalPose().transformNormal(delta.normalize().scale(.08));
            player.setDeltaMovement(step.x,player.getDeltaMovement().y,step.z);
            player.tick();
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

    @GameTest(template="empty", batch="yacht_stair_return", timeoutTicks=800)
    public void unmountedPlayerDescendsMovingStaircase(GameTestHelper h) {
        for(int x=-1;x<=1;x++) for(int z=-1;z<=2;z++) h.getLevel().getChunk(x,z);
        var ship=YachtFloorPrototype.spawn(h.getLevel(),0,100,0);
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setInvulnerable(true);
        Vec3[] targets={new Vec3(4.5,0,2.5),new Vec3(4.5,0,6.5),
                new Vec3(2,0,6.5),new Vec3(2,0,1.25),new Vec3(8.5,0,1.25)};
        int[] target={0};
        h.runAfterDelay(50,()->player.setPos(YachtFloorPrototype.worldPoint(ship,6.5,5.05,2.5)));
        for(int tick=51;tick<=600;tick++) h.runAfterDelay(tick,()->{
            var center=ship.getPlot().getCenterBlock();
            var local=ship.logicalPose().transformPositionInverse(player.position())
                    .subtract(center.getX(),center.getY(),center.getZ());
            Vec3 step=Vec3.ZERO;
            if(target[0]<targets.length) {
                var delta=targets[target[0]].subtract(local).multiply(1,0,1);
                if(delta.horizontalDistance()<.12) target[0]++;
                else step=ship.logicalPose().transformNormal(delta.normalize().scale(.08));
            }
            player.setDeltaMovement(step.x,player.getDeltaMovement().y,step.z);
            player.tick();
        });
        h.runAfterDelay(610,()->{
            try {
                h.assertFalse(player.isPassenger(),"descending player stays unmounted");
                h.assertTrue(target[0]==targets.length,"descent stopped at waypoint "+target[0]);
                h.assertTrue(Math.abs(player.getY()-YachtFloorPrototype.worldPoint(ship,8.5,1,1.25).y)<.15,
                        "player reaches lower deck after descent");
            } finally { player.discard(); YachtFloorPrototype.dispose(ship); }
            h.succeed();
        });
    }

    @GameTest(template="empty", batch="yacht_dock", timeoutTicks=700)
    public void unmountedPlayerBoardsAndLeavesMovingFloor(GameTestHelper h) {
        runDockProbe(h,true);
    }

    @GameTest(template="empty", batch="yacht_dock_stationary", timeoutTicks=700)
    public void unmountedPlayerBoardsAndLeavesStationaryFloor(GameTestHelper h) {
        runDockProbe(h,false);
    }

    @GameTest(template="empty", batch="yacht_dock_clearance", timeoutTicks=50)
    public void reviewDockRejectsOccupiedClearanceWithoutReplacingTerrain(GameTestHelper h) {
        for(int x=-1;x<=1;x++) for(int z=-1;z<=2;z++) h.getLevel().getChunk(x,z);
        var obstruction=new net.minecraft.core.BlockPos(0,120,0);
        var original=h.getLevel().getBlockState(obstruction);
        h.getLevel().setBlockAndUpdate(obstruction,net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState());
        boolean rejected=false;
        try { YachtFloorPrototype.spawnDockFixture(h.getLevel(),-4,120,0); }
        catch(IllegalArgumentException expected) { rejected=true; }
        try {
            h.assertTrue(rejected,"occupied review corridor must reject placement");
            h.assertTrue(h.getLevel().getBlockState(obstruction).is(net.minecraft.world.level.block.Blocks.GOLD_BLOCK),
                    "placement must preserve obstruction");
            h.assertTrue(h.getLevel().getBlockState(new net.minecraft.core.BlockPos(-2,120,0)).isAir(),
                    "rejected placement must not leave a partial dock");
        } finally { h.getLevel().setBlockAndUpdate(obstruction,original); }
        h.succeed();
    }

    private void runDockProbe(GameTestHelper h, boolean moving) {
        for(int x=-1;x<=1;x++) for(int z=-1;z<=2;z++) h.getLevel().getChunk(x,z);
        var ship=YachtFloorPrototype.spawnDockFixture(h.getLevel(),-4,100,0);
        if(moving) YachtFloorPrototype.start(ship);
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setInvulnerable(true);
        Runnable cleanup=()->{
            player.discard(); YachtFloorPrototype.dispose(ship);
        };
        boolean[] done={false};
        int[] phase={0}, leftAt={-1};
        Vec3[] onDock={null};
        h.runAfterDelay(50,()->player.setPos(-2,101,1.25));
        for(int tick=51;tick<=600;tick++) {
            final int now=tick;
            h.runAfterDelay(tick,()->{
                if(done[0]) return;
                try {
                h.assertFalse(player.isPassenger(),"boarding does not mount player");
                var center=ship.getPlot().getCenterBlock();
                var local=ship.logicalPose().transformPositionInverse(player.position())
                        .subtract(center.getX(),center.getY(),center.getZ());
                Vec3 step=Vec3.ZERO;
                if(phase[0]<2) {
                    double targetX=phase[0]==0?8.5:-2;
                    var delta=new Vec3(targetX-local.x,0,1.25-local.z);
                    if(delta.horizontalDistance()<.12) {
                        phase[0]++;
                        if(phase[0]==1) h.assertTrue(Math.abs(player.getY()-YachtFloorPrototype.worldPoint(ship,8.5,1,1.25).y)<.15,
                                "player boarded lower deck: y="+player.getY()+" local="+local);
                        else {
                            leftAt[0]=now; onDock[0]=player.position();
                            System.out.println("YACHT_DOCK moving="+moving+" disembarkedTick="+now+" world="+onDock[0]);
                        }
                    } else step=ship.logicalPose().transformNormal(delta.normalize().scale(.08));
                }
                player.setDeltaMovement(step.x,player.getDeltaMovement().y,step.z);
                player.tick();
                } catch(RuntimeException | AssertionError failure) {
                    done[0]=true; cleanup.run(); throw failure;
                }
            });
        }
        h.runAfterDelay(610,()->{
            try {
                h.assertTrue(phase[0]==2,"completed dock-to-vessel-to-dock route: "+phase[0]);
                h.assertTrue(leftAt[0]<500,"disembarked early enough to observe released support");
                h.assertTrue(Math.abs(player.getY()-101)<.15,"standing on stationary dock");
                h.assertTrue(player.position().subtract(onDock[0]).horizontalDistance()<.1,
                        "disembarked player is not carried along with vessel");
            } finally {
                done[0]=true; cleanup.run();
            }
            h.assertTrue(h.getLevel().getBlockState(new net.minecraft.core.BlockPos(-2,100,0)).isAir(),
                    "disposal removes the owned review dock");
            h.succeed();
        });
    }

    @GameTest(template="empty", batch="yacht_door", timeoutTicks=500)
    public void movingDoorBlocksPlayerUntilOpened(GameTestHelper h) {
        for(int x=-1;x<=1;x++) for(int z=-1;z<=2;z++) h.getLevel().getChunk(x,z);
        var ship=YachtFloorPrototype.spawn(h.getLevel(),0,100,0);
        var blocks=ship.getPlot().getEmbeddedLevelAccessor();
        var door=net.minecraft.world.level.block.Blocks.OAK_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING,net.minecraft.core.Direction.SOUTH);
        var lower=new net.minecraft.core.BlockPos(8,1,3);
        for(int x=7;x<=9;x++) for(int y=1;y<=3;y++) if(x!=8 || y==3)
            blocks.setBlock(new net.minecraft.core.BlockPos(x,y,3),net.minecraft.world.level.block.Blocks.QUARTZ_BLOCK.defaultBlockState(),3);
        blocks.setBlock(lower,door,3);
        blocks.setBlock(lower.above(),door.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),3);
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setInvulnerable(true);
        boolean[] done={false};
        h.runAfterDelay(50,()->player.setPos(YachtFloorPrototype.worldPoint(ship,8.5,1.05,1.25)));
        for(int tick=51;tick<=430;tick++) {
            final int now=tick;
            h.runAfterDelay(tick,()->{
                if(done[0]) return;
                try {
                    var center=ship.getPlot().getCenterBlock();
                    var local=ship.logicalPose().transformPositionInverse(player.position())
                            .subtract(center.getX(),center.getY(),center.getZ());
                    if(now==150) {
                        h.assertTrue(local.z>2 && local.z<3.2,"closed door blocks walking: "+local);
                        h.assertTrue(Math.abs(local.y-1)<.15,"player remains on floor at closed door");
                    }
                    if(now==200) {
                        blocks.setBlock(lower,blocks.getBlockState(lower).setValue(net.minecraft.world.level.block.DoorBlock.OPEN,true),3);
                        blocks.setBlock(lower.above(),blocks.getBlockState(lower.above()).setValue(net.minecraft.world.level.block.DoorBlock.OPEN,true),3);
                    }
                    var delta=new Vec3(8.5-local.x,0,5.5-local.z);
                    var step=delta.horizontalDistance()<.12?Vec3.ZERO:ship.logicalPose().transformNormal(delta.normalize().scale(.08));
                    player.setDeltaMovement(step.x,player.getDeltaMovement().y,step.z);
                    player.tick();
                    h.assertFalse(player.isPassenger(),"door crossing is unmounted");
                    if(now==430) {
                        h.assertTrue(delta.horizontalDistance()<.15 && Math.abs(local.y-1)<.15,
                                "open door admits player without falling: "+local);
                        done[0]=true; player.discard(); YachtFloorPrototype.dispose(ship); h.succeed();
                    }
                } catch(RuntimeException | AssertionError failure) {
                    done[0]=true; player.discard(); YachtFloorPrototype.dispose(ship); throw failure;
                }
            });
        }
    }

    @GameTest(template="empty", batch="yacht_soak", timeoutTicks=12500)
    public void tenMinuteFarCoordinateContactSoak(GameTestHelper h) {
        runProbe(h,new Vec3(1_000_000,100,1_000_000),false,h::succeed,false,true);
    }

    @GameTest(template="empty", batch="yacht_pool", timeoutTicks=850)
    public void playerEntersAndLeavesPoolOnMovingFloor(GameTestHelper h) {
        for(int x=-1;x<=1;x++) for(int z=-1;z<=2;z++) h.getLevel().getChunk(x,z);
        var ship=YachtFloorPrototype.spawn(h.getLevel(),0,100,0);
        YachtFloorPrototype.addPool(ship);
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setInvulnerable(true);
        Vec3[] targets={new Vec3(7.5,0,6.5),new Vec3(8.5,0,6.5),new Vec3(7.5,0,6.5),new Vec3(7.5,0,2.5)};
        int[] target={0}, wetTicks={0}; boolean[] done={false};
        h.runAfterDelay(60,()->player.setPos(YachtFloorPrototype.worldPoint(ship,7.5,1.05,2.5)));
        for(int tick=61;tick<=780;tick++) {
            final int now=tick;
            h.runAfterDelay(tick,()->{
                if(done[0]) return;
                try {
                    var center=ship.getPlot().getCenterBlock();
                    var local=ship.logicalPose().transformPositionInverse(player.position())
                            .subtract(center.getX(),center.getY(),center.getZ());
                    if(player.isInWater()) wetTicks[0]++;
                    Vec3 step=Vec3.ZERO;
                    if(target[0]<targets.length) {
                        var delta=targets[target[0]].subtract(local).multiply(1,0,1);
                        if(delta.horizontalDistance()<.12) target[0]++;
                        else step=ship.logicalPose().transformNormal(delta.normalize().scale(.08));
                    }
                    player.setDeltaMovement(step.x,player.getDeltaMovement().y,step.z);
                    player.tick();
                    h.assertFalse(player.isPassenger(),"pool use stays unmounted");
                    if(now==780) {
                        h.assertTrue(target[0]==targets.length,"pool route stopped at waypoint "+target[0]+" local="+local);
                        h.assertTrue(wetTicks[0]>10,"pool fluid must be detected by player physics: "+wetTicks[0]);
                        h.assertFalse(player.isInWater(),"player leaves pool into dry deck space");
                        h.assertTrue(Math.abs(local.y-1)<.15,"player returns to main floor: "+local);
                        System.out.println("YACHT_POOL wetTicks="+wetTicks[0]+" final="+local);
                        done[0]=true; player.discard(); YachtFloorPrototype.dispose(ship); h.succeed();
                    }
                } catch(RuntimeException | AssertionError failure) {
                    done[0]=true; player.discard(); YachtFloorPrototype.dispose(ship); throw failure;
                }
            });
        }
    }

    @GameTest(template="empty", batch="yacht_water_ladder", timeoutTicks=1100)
    public void playerLeavesVesselForWaterAndReturnsByMovingLadder(GameTestHelper h) {
        for(int x=-1;x<=1;x++) for(int z=-1;z<=2;z++) h.getLevel().getChunk(x,z);
        var terrain=new java.util.HashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(int x=-5;x<=17;x++) for(int z=-4;z<=34;z++) for(int y=97;y<=100;y++) {
            var pos=new net.minecraft.core.BlockPos(x,y,z);
            terrain.put(pos,h.getLevel().getBlockState(pos));
            boolean shell=y==97 || x==-5 || x==17 || z==-4 || z==34;
            h.getLevel().setBlock(pos,(shell?net.minecraft.world.level.block.Blocks.STONE:net.minecraft.world.level.block.Blocks.WATER).defaultBlockState(),2);
        }
        var ship=YachtFloorPrototype.spawn(h.getLevel(),0,100,0);
        var blocks=ship.getPlot().getEmbeddedLevelAccessor();
        for(int y=-2;y<=0;y++) {
            blocks.setBlock(new net.minecraft.core.BlockPos(11,y,2),net.minecraft.world.level.block.Blocks.QUARTZ_BLOCK.defaultBlockState(),3);
            blocks.setBlock(new net.minecraft.core.BlockPos(12,y,2),net.minecraft.world.level.block.Blocks.LADDER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING,net.minecraft.core.Direction.EAST),3);
        }
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setInvulnerable(true);
        Runnable cleanup=()->{
            player.discard(); YachtFloorPrototype.dispose(ship);
            terrain.forEach((pos,state)->h.getLevel().setBlock(pos,state,2));
        };
        int[] phase={0}, wet={0}, climbing={0}; boolean[] done={false};
        h.runAfterDelay(80,()->player.setPos(YachtFloorPrototype.worldPoint(ship,8.5,1.05,2.5)));
        for(int tick=81;tick<=1000;tick++) {
            final int now=tick;
            h.runAfterDelay(tick,()->{
                if(done[0]) return;
                try {
                    var center=ship.getPlot().getCenterBlock();
                    var local=ship.logicalPose().transformPositionInverse(player.position())
                            .subtract(center.getX(),center.getY(),center.getZ());
                    if(player.isInWater()) wet[0]++;
                    if(player.onClimbable()) climbing[0]++;
                    double targetX=phase[0]==0?14:8.5;
                    var delta=new Vec3(targetX-local.x,0,2.5-local.z);
                    if(delta.horizontalDistance()<.12) phase[0]=Math.min(2,phase[0]+1);
                    var step=phase[0]==2?Vec3.ZERO:ship.logicalPose().transformNormal(delta.normalize().scale(.08));
                    player.setDeltaMovement(step.x,player.getDeltaMovement().y,step.z);
                    player.tick();
                    h.assertFalse(player.isPassenger(),"water transition never mounts the player");
                    if(now==1000) {
                        h.assertTrue(wet[0]>10,"outside water detected");
                        h.assertTrue(climbing[0]>0,"moving ladder detected");
                        h.assertTrue(phase[0]==2 && Math.abs(local.y-1)<.15,"returned via ladder: phase="+phase[0]+" local="+local);
                        h.assertFalse(player.isInWater(),"returned deck is dry");
                        System.out.println("YACHT_WATER wetTicks="+wet[0]+" climbingTicks="+climbing[0]);
                        done[0]=true; cleanup.run(); h.succeed();
                    }
                } catch(RuntimeException | AssertionError failure) {
                    done[0]=true; cleanup.run(); throw failure;
                }
            });
        }
    }

    @GameTest(template="empty", batch="yacht_dry_room", timeoutTicks=800)
    public void submergedMovingRoomStaysDryWithoutDryingOutsideWater(GameTestHelper h) {
        for(int x=-1;x<=1;x++) for(int z=-1;z<=2;z++) h.getLevel().getChunk(x,z);
        var terrain=new java.util.HashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(int x=-5;x<=17;x++) for(int z=-4;z<=34;z++) for(int y=97;y<=103;y++) {
            var pos=new net.minecraft.core.BlockPos(x,y,z);
            terrain.put(pos,h.getLevel().getBlockState(pos));
            boolean shell=y==97 || x==-5 || x==17 || z==-4 || z==34;
            h.getLevel().setBlock(pos,(shell?net.minecraft.world.level.block.Blocks.STONE:net.minecraft.world.level.block.Blocks.WATER).defaultBlockState(),2);
        }
        var ship=YachtFloorPrototype.spawn(h.getLevel(),0,100,0);
        var blocks=ship.getPlot().getEmbeddedLevelAccessor();
        for(int x=0;x<12;x++) for(int z=0;z<9;z++) for(int y=1;y<=4;y++)
            if(x==0 || x==11 || z==0 || z==8 || y==4)
                blocks.setBlock(new net.minecraft.core.BlockPos(x,y,z),net.minecraft.world.level.block.Blocks.QUARTZ_BLOCK.defaultBlockState(),3);
        var masks=dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer.getContainer(h.getLevel());
        int regionsBefore=masks.getRegions().size();
        YachtFloorPrototype.addDryRegion(ship,new net.minecraft.core.BlockPos(1,1,1),new net.minecraft.core.BlockPos(10,3,7));
        var inside=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var outside=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        outside.setInvulnerable(true);
        Runnable cleanup=()->{
            inside.discard(); outside.discard(); YachtFloorPrototype.dispose(ship);
            terrain.forEach((pos,state)->h.getLevel().setBlock(pos,state,2));
        };
        boolean[] done={false};
        h.runAfterDelay(80,()->{
            inside.setPos(YachtFloorPrototype.worldPoint(ship,8.5,1.05,2.5));
            outside.setPos(14,101,2.5);
        });
        for(int tick=81;tick<=700;tick++) {
            final int now=tick;
            h.runAfterDelay(tick,()->{
                if(done[0]) return;
                try {
                    inside.tick(); outside.tick();
                    if(now>=100 && now%20==0) {
                        h.assertFalse(inside.isInWater(),"submerged room remains dry as vessel moves");
                        h.assertFalse(inside.isEyeInFluid(net.minecraft.tags.FluidTags.WATER),"dry room eye position is not underwater");
                        h.assertTrue(inside.getAirSupply()==inside.getMaxAirSupply(),"normal breathing inside submerged room");
                        h.assertTrue(outside.isInWater(),"water outside the hull is still active");
                        h.assertFalse(inside.isPassenger(),"dry room does not require mounting");
                    }
                    if(now==700) {
                        var center=ship.getPlot().getCenterBlock();
                        var local=ship.logicalPose().transformPositionInverse(inside.position()).subtract(center.getX(),center.getY(),center.getZ());
                        h.assertTrue(Math.abs(local.y-1)<.15,"inside occupant remains supported");
                        done[0]=true; cleanup.run();
                        h.assertTrue(masks.getRegions().size()==regionsBefore,"disposal releases dry-region registration");
                        System.out.println("YACHT_DRY_ROOM breathing=normal outsideWater=active cleanup=passed");
                        h.succeed();
                    }
                } catch(RuntimeException | AssertionError failure) {
                    if(!done[0]) { done[0]=true; cleanup.run(); }
                    throw failure;
                }
            });
        }
    }

    private void runProbe(GameTestHelper h, Vec3 p) { runProbe(h,p,false,h::succeed); }

    private void runProbe(GameTestHelper h, Vec3 p, boolean turn, Runnable finish) {
        runProbe(h,p,turn,finish,false);
    }

    private void runProbe(GameTestHelper h, Vec3 p, boolean turn, Runnable finish, boolean jump) {
        runProbe(h,p,turn,finish,jump,false);
    }

    private void runProbe(GameTestHelper h, Vec3 p, boolean turn, Runnable finish, boolean jump, boolean soak) {
        int duration=soak?12000:640;
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
        final double[] lastZ={Double.NaN}, distance={0};
        // Register before ticking: mutating GameTestInfo's pending-task map from a
        // callback can invalidate its fastutil iterator.
        for(int tick=1;tick<=duration+320;tick++) {
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
                        if(soak) YachtFloorPrototype.startSoak(ship); else YachtFloorPrototype.start(ship);
                    }
                    int elapsed=now-start[0];
                    h.assertFalse(ship.isRemoved(),"fixture unloaded before motion completed");
                    h.assertFalse(occupant.isPassenger(),"contact probe remains unmounted");
                    double floorError=occupant.getY()-YachtFloorPrototype.worldPoint(ship,8.5,1,2.5).y;
                    if(jump && elapsed==240) occupant.setDeltaMovement(0,.42,0);
                    if(jump && elapsed==245) h.assertTrue(floorError>.4,"probe leaves the moving floor");
                    if(elapsed%10==0 && !(jump && elapsed>=240 && elapsed<=270))
                        h.assertTrue(Math.abs(floorError)<.15,"probe stays supported throughout motion: "+floorError);
                    var pose=YachtFloorPrototype.worldPoint(ship,0,0,0);
                    if(!Double.isNaN(lastZ[0])) distance[0]+=Math.abs(pose.z-lastZ[0]);
                    lastZ[0]=pose.z;
                    if(elapsed%10==0 && soak) {
                        var sample=ship.logicalPose().transformPositionInverse(occupant.position());
                        h.assertTrue(Math.hypot(sample.x-settled[0].x,sample.z-settled[0].z)<.05,"soak contact drift");
                    }
                    if(elapsed!=duration) return;
                    if(soak) h.assertTrue(distance[0]>290 && distance[0]<320,"soak accumulated physical distance: "+distance[0]);
                    else h.assertTrue(pose.z-p.z>10 && pose.z-p.z<20,"physical travel: "+(pose.z-p.z));
                    h.assertTrue(Math.abs(pose.y-p.y)<.1,"height hold: "+(pose.y-p.y));
                    h.assertTrue(Math.abs(floorError)<.1,"probe finishes on lower deck: "+floorError);
                    var local=ship.logicalPose().transformPositionInverse(occupant.position());
                    double drift=Math.hypot(local.x-settled[0].x,local.z-settled[0].z);
                    // A jump may change local X/Z; grounded probes must stay within .05.
                    h.assertTrue(drift<(jump?1.0:.05),"relative drift: "+drift);
                    System.out.println("YACHT_RESULT origin="+p+" jump="+jump+" soak="+soak+" distance="+distance[0]+" settledTick="+start[0]
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
