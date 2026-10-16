package com.bilal.practicemod.yacht.runtime;

import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("practicemod")
@PrefixGameTestTemplate(false)
public final class YachtRuntimeGameTests {
    @GameTest(template="empty",batch="white_horizon_full",timeoutTicks=500)
    public void fullCanonicalHullSupportsGuestAndBridgeAtCruise(GameTestHelper h) {
        for(int x=4;x<=9;x++)for(int z=4;z<=10;z++)h.getLevel().getChunk(x,z);
        for(int x=65;x<=145;x++)for(int z=65;z<=165;z++)for(int y=89;y<95;y++)h.getLevel().setBlock(new net.minecraft.core.BlockPos(x,y,z),net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(),2);
        var owner=UUID.randomUUID();var c=YachtRuntime.assemble(h.getLevel(),owner,"Test owner",new Vec3(104,100,104),95);
        var guest=h.makeMockPlayer(GameType.SURVIVAL);var bridge=h.makeMockPlayer(GameType.SURVIVAL);
        guest.setInvulnerable(true);bridge.setInvulnerable(true);
        h.runAfterDelay(80,()->{
            guest.setPos(YachtRuntime.point(c.ship,0,-3.95,-12));
            bridge.setPos(YachtRuntime.point(c.ship,-.5,4.05,17));
        });
        for(int t=81;t<=380;t++)h.runAfterDelay(t,()->{
            guest.setDeltaMovement(0,guest.getDeltaMovement().y,0);guest.tick();
            bridge.setDeltaMovement(0,bridge.getDeltaMovement().y,0);bridge.tick();
            // Bounded test drive, independent of destination authorization and ocean survey.
            c.speed=.4;c.velocity=new Vec3(0,0,.4);
        });
        h.runAfterDelay(381,()->{
            try {
                var g=YachtRuntime.local(c.ship,guest.position());var b=YachtRuntime.local(c.ship,bridge.position());
                h.assertTrue(Math.abs(g.y+4)<.15 && Math.abs(g.z+12)<.5,"Lower corridor contact/drift: "+g);
                h.assertTrue(Math.abs(b.y-4)<.15 && Math.abs(b.z-17)<.5,"Bridge contact/drift: "+b);
                h.assertTrue(c.data.getUUID("owner").equals(owner),"Owner lost");
                var saved=dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer.toData(c.ship,java.util.List.of()).fullTag();
                var restored=dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer.fromData(saved.copy());
                h.assertTrue(restored!=null && restored.fullTag().getCompound("user_data").getUUID("owner").equals(owner),"Ownership serialization failed");
                h.assertTrue(h.getLevel().getEntity(c.data.getUUID("captain"))!=null,"Captain missing");
                h.assertTrue(YachtRuntime.point(c.ship,0,0,0).z>106,"Full hull failed to translate");
                h.assertTrue(Math.abs(YachtRuntime.point(c.ship,0,0,0).y-100)<.1 && Math.abs(YachtRuntime.point(c.ship,0,0,0).x-104)<.1,"Water drift: "+YachtRuntime.point(c.ship,0,0,0));
                h.succeed();
            } finally {guest.discard();bridge.discard();YachtRuntime.dispose(c.ship);}
        });
    }
    @GameTest(template="empty",batch="white_horizon_stairs",timeoutTicks=1000)
    public void allAuthoredStairsWorkOnMovingFullHull(GameTestHelper h) {
        for(int x=14;x<=19;x++)for(int z=14;z<=20;z++)h.getLevel().getChunk(x,z);
        var c=YachtRuntime.assemble(h.getLevel(),UUID.randomUUID(),"Stair test",new Vec3(264,100,264),95);
        var routes=YachtAssets.DATA.getAsJsonObject("layout").getAsJsonArray("routes");
        var players=new java.util.ArrayList<net.minecraft.world.entity.player.Player>();
        var paths=new java.util.ArrayList<java.util.List<Vec3>>();
        for(var raw:routes){var r=raw.getAsJsonObject();if(!r.get("name").getAsString().contains("_to_"))continue;
            var path=new java.util.ArrayList<Vec3>();for(var point:r.getAsJsonArray("feet")){var a=point.getAsJsonArray();path.add(new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble()));}
            paths.add(path);players.add(h.makeMockPlayer(GameType.SURVIVAL));
        }
        int[] target=new int[players.size()];
        h.runAfterDelay(80,()->{for(int i=0;i<players.size();i++){var p=paths.get(i).getFirst();players.get(i).setPos(YachtRuntime.point(c.ship,p.x,p.y+.05,p.z));}});
        for(int t=81;t<=800;t++)h.runAfterDelay(t,()->{
            c.speed=.3;c.velocity=new Vec3(0,0,.3);
            for(int i=0;i<players.size();i++){
                var player=players.get(i);var path=paths.get(i);var local=YachtRuntime.local(c.ship,player.position());
                if(target[i]<path.size() && path.get(target[i]).subtract(local).multiply(1,0,1).length()<.12)target[i]++;
                var delta=target[i]>=path.size()?Vec3.ZERO:path.get(target[i]).subtract(local).multiply(1,0,1).normalize().scale(.08);
                var motion=c.ship.logicalPose().transformNormal(delta);player.setDeltaMovement(motion.x,player.getDeltaMovement().y,motion.z);player.tick();
            }
        });
        h.runAfterDelay(801,()->{try{for(int i=0;i<players.size();i++){
            h.assertTrue(target[i]>=paths.get(i).size(),"Stair route "+i+" blocked at waypoint "+target[i]+" local "+YachtRuntime.local(c.ship,players.get(i).position()));
            h.assertTrue(Math.abs(YachtRuntime.local(c.ship,players.get(i).position()).y-paths.get(i).getLast().y)<.15,"Stair landing elevation "+i);
        }h.succeed();}finally{players.forEach(net.minecraft.world.entity.Entity::discard);YachtRuntime.dispose(c.ship);}});
    }
    @GameTest(template="empty",batch="white_horizon_navigation",timeoutTicks=100)
    public void deepWaterRouteSurveyAndChangedTerrain(GameTestHelper h) {
        var level=h.getLevel();
        for(int x=600;x<=680;x++)for(int z=600;z<=700;z++)for(int y=89;y<95;y++)
            level.setBlock(new net.minecraft.core.BlockPos(x,y,z),net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(),2);
        level.setBlock(new net.minecraft.core.BlockPos(640,95,665),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
        var search=new YachtNavigation.Search(level,new Vec3(640,100,640),95,640,648);
        for(int i=0;i<130 && !search.done;i++)search.tick();
        h.assertTrue(search.done && !search.result.isEmpty(),"Deep water route not found");
        h.assertTrue(search.result.getLast().distanceTo(new Vec3(640,100,648))<.01,"Wrong destination");
        level.setBlock(new net.minecraft.core.BlockPos(640,95,665),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
        h.assertTrue(!YachtNavigation.clear(level,640,648,95),"New obstacle accepted by live survey");h.succeed();
    }
    @GameTest(template="empty",batch="white_horizon_validation",timeoutTicks=20)
    public void terrainAndUnloadedWaterAreRejected(GameTestHelper h) {
        h.assertTrue(!YachtNavigation.clear(h.getLevel(),0,0,100),"Air is not navigable water");
        h.assertTrue(!YachtNavigation.clear(h.getLevel(),29000000,29000000,63),"Unloaded destination accepted");
        h.assertTrue(YachtAssets.DATA.getAsJsonArray("cells").size()>6000,"Full canonical hull not exported");
        long ladders=YachtAssets.DATA.getAsJsonArray("palette").asList().stream().filter(e->e.getAsJsonObject().get("ladder").getAsBoolean()).count();
        h.assertTrue(ladders>0,"Boarding ladders missing");
        h.assertTrue(dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper.getVolume(YachtAssets.HULL.defaultBlockState())==0,"Competing native buoyancy must be disabled");h.succeed();
    }
}
