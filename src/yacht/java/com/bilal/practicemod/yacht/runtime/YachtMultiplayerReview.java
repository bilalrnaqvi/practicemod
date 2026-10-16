package com.bilal.practicemod.yacht.runtime;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;

/** Opt-in, loopback-only dedicated-server fixture. Never runs during normal gameplay. */
@EventBusSubscriber(modid="practicemod")
public final class YachtMultiplayerReview {
    static YachtRuntime.Controller yacht;
    static int ticks, movingTicks, arrivals,boardingTick;
    static boolean finished;
    static long started;
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        String output=System.getProperty("practicemod.yachtMultiplayerReview");
        if(output==null || event.getServer().isSingleplayer() || finished)return;
        if(started==0)started=System.currentTimeMillis();
        var server=event.getServer();var owner=server.getPlayerList().getPlayerByName("YachtOwner");var guest=server.getPlayerList().getPlayerByName("YachtGuest");
        if(yacht==null) {
            if(owner==null || guest==null)return;
            var level=server.overworld();level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);
            for(int x=960;x<=1040;x++)for(int z=950;z<=1090;z++)for(int y=174;y<180;y++)
                level.setBlock(new net.minecraft.core.BlockPos(x,y,z),net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(),2);
            yacht=YachtRuntime.assemble(level,owner.getUUID(),"Multiplayer review",new Vec3(1000,185,1000),180);
            place(owner,0,4.05,14);place(guest,-5,.05,-11);
        }
        ticks++;
        // Let both clients receive the new sublevel before positioning the review actors.
        // This is fixture setup only; no player correction is applied during travel.
        if(ticks==200 && owner!=null && guest!=null){place(owner,0,4.05,14);place(guest,-5,.05,-11);}
        if(yacht.speed>.2)movingTicks++;
        if(ticks%20==0) {
            var data=new JsonObject();data.addProperty("ticks",ticks);data.addProperty("ship",yacht.ship.getUniqueId().toString());data.addProperty("status",yacht.status);
            data.addProperty("movingTicks",movingTicks);data.addProperty("captain",yacht.data.getUUID("captain").toString());
            data.addProperty("boarding",boardingTick>0);
            data.addProperty("destinationX",1000);data.addProperty("destinationZ",1016);
            data.addProperty("owner",owner==null?"disconnected":YachtRuntime.local(yacht.ship,owner.position()).toString());
            data.addProperty("guest",guest==null?"disconnected":YachtRuntime.local(yacht.ship,guest.position()).toString());
            write(Path.of(output,"state.json"),data.toString());
        }
        if(yacht.status.equals("ARRIVED"))arrivals++;
        boolean reconnectReady=!Boolean.getBoolean("practicemod.yachtReviewRequireReconnect") || Files.exists(Path.of(output,"YachtGuest-reconnected.json"));
        if(arrivals>100 && guest!=null && reconnectReady && boardingTick==0) {
            // A second bounded scenario starts in the water. All subsequent motion is client input.
            place(guest,-1.5,-4.5,-30.4);boardingTick=ticks;
            yacht.route=new java.util.ArrayList<>(java.util.List.of(YachtRuntime.point(yacht.ship,0,0,8)));yacht.status="SAILING — boarding review";
        }
        if(boardingTick>0 && owner!=null && guest!=null && Files.exists(Path.of(output,"YachtGuest-disembarked.json"))) {
            var o=YachtRuntime.local(yacht.ship,owner.position());var g=YachtRuntime.local(yacht.ship,guest.position());
            boolean valid=movingTicks>200 && Math.abs(o.y-4)<.2 && g.y< -4.5 && g.z< -31 && guest.isInWater() && yacht.speed<.01 && owner.getVehicle()==null && guest.getVehicle()==null;
            write(Path.of(output,"server-result.json"),"{\"passed\":"+valid+",\"movingTicks\":"+movingTicks+",\"status\":\""+yacht.status+"\",\"ownerLocal\":\""+o+"\",\"guestLocal\":\""+g+"\"}");
            finished=true;
        }
        if(ticks>4000 && !finished){write(Path.of(output,"server-result.json"),"{\"passed\":false,\"reason\":\"timeout\",\"status\":\""+yacht.status+"\"}");finished=true;}
    }
    static void place(ServerPlayer p,double x,double y,double z){p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);var at=YachtRuntime.point(yacht.ship,x,y,z);p.connection.teleport(at.x,at.y,at.z,0,0);}
    static void write(Path path,String value){try{Files.createDirectories(path.getParent());var tmp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(tmp,value);Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){throw new RuntimeException(e);}}
}
