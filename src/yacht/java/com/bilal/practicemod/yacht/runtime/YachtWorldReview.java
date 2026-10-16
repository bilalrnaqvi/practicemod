package com.bilal.practicemod.yacht.runtime;

import net.minecraft.client.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.nio.file.*;

/** Explicit disposable-world review; never enabled by the normal yacht profile. */
@EventBusSubscriber(modid="practicemod",value=Dist.CLIENT)
public final class YachtWorldReview {
    static volatile YachtRuntime.Controller ship;static volatile boolean requested;static boolean finished;static int ticks,phase,waypoint,waiting;static String capture;static boolean lockerOpened,doorWasClosed;
    static final String[] names={"exterior-world","salon-world","lower-corridor-world","bridge-world","pool-world"};
    static final double[][] views={{40,25,-50,38,18},{0,0,-3,0,0},{0,-4,-16,0,0},{0,4,14,0,0},{5,0,-23,90,10}};
    static final java.util.List<Vec3> path=new java.util.ArrayList<>();
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var output=System.getProperty("practicemod.yachtWorldReview");var mc=Minecraft.getInstance();
        if(output==null||finished||mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        mc.options.pauseOnLostFocus=false;mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        if(ship==null && ++waiting>600){report(output,"FAIL: saved yacht did not become available within 30 seconds.\n");finished=true;mc.stop();return;}
        if(!requested){requested=true;var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
            var server=mc.getSingleplayerServer();var player=server.getPlayerList().getPlayer(id);var level=player.serverLevel();
            if(Boolean.getBoolean("practicemod.yachtReloadReview")) {
                ship=YachtRuntime.ACTIVE.values().stream().filter(c->c.ship.getName()!=null && c.ship.getName().contains("World review")).findFirst().orElse(null);
                if(ship==null){requested=false;return;}
                if(!ship.data.getUUID("owner").equals(id)||!ship.status.startsWith("STOPPED"))throw new IllegalStateException("Reload ownership or stopped-state failure");
                doorWasClosed=ship.data.getBoolean("door_guest_port_34_door");place(4);return;
            }
            for(var old:java.util.List.copyOf(YachtRuntime.ACTIVE.values()))if(old.ship.getName()!=null && old.ship.getName().contains("World review"))YachtRuntime.dispose(old.ship);
            level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);
            for(int x=960;x<=1040;x++)for(int z=950;z<=1070;z++)for(int y=174;y<180;y++)level.setBlock(new net.minecraft.core.BlockPos(x,y,z),net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(),2);
            ship=YachtRuntime.assemble(level,id,"World review",new Vec3(1000,185,1000),180);
            place(0);
        });return;}
        if(ship==null)return;
        if(mc.screen!=null && !(mc.screen instanceof YachtClient.Chart) && !(mc.screen instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen))mc.setScreen(null);
        ticks++;
        if(Boolean.getBoolean("practicemod.yachtReloadReview")) {
            if(ticks==40)mc.getSingleplayerServer().execute(()->place(4));
            if(ticks==160)capture="pool-reloaded-world";
            if(ticks==180)mc.getSingleplayerServer().execute(()->place(3));
            if(ticks==220)for(var actor:mc.level.entitiesForRendering())if(actor.getUUID().equals(ship.data.getUUID("captain")))mc.gameMode.interact(mc.player,actor,net.minecraft.world.InteractionHand.MAIN_HAND);
            if(ticks==260)capture="captain-reloaded-world";
            if(ticks==280)report(output,"Reloaded existing hull UUID="+ship.ship.getUniqueId()+", owner="+ship.data.getUUID("owner")+", status="+ship.status+", origin="+YachtRuntime.point(ship.ship,0,0,0)+", captain chart="+(mc.screen instanceof YachtClient.Chart)+".\n");
            if(ticks==300)mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var at=YachtRuntime.point(ship.ship,0,-4,14.5);p.connection.teleport(at.x,at.y,at.z,90,20);
            });
            if(ticks==340 && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)mc.gameMode.useItemOn(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
            if(ticks==350){lockerOpened=mc.screen instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen;capture="locker-reloaded-world";}
            if(ticks==360){mc.player.closeContainer();mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var at=YachtRuntime.point(ship.ship,0,-4,5.1);p.connection.teleport(at.x,at.y,at.z,90,3);
            });}
            if(ticks==400 && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)mc.gameMode.useItemOn(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
            if(ticks==420){capture="door-reloaded-world";report(output,"Reloaded hull="+ship.ship.getUniqueId()+", owner="+ship.data.getUUID("owner")+", status="+ship.status+", origin="+YachtRuntime.point(ship.ship,0,0,0)+", locker menu="+lockerOpened+", cabin door toggled="+(doorWasClosed!=ship.data.getBoolean("door_guest_port_34_door"))+".\n");}
            if(ticks>440){finished=true;mc.stop();}return;
        }
        if(phase<5 && ticks==160)capture=names[phase];
        if(phase<5 && ticks==180){phase++;ticks=0;if(phase<5)mc.getSingleplayerServer().execute(()->place(phase));else {
            for(var raw:YachtAssets.DATA.getAsJsonObject("layout").getAsJsonArray("routes"))if(raw.getAsJsonObject().get("name").getAsString().equals("A_0_to_4"))for(var rawPoint:raw.getAsJsonObject().getAsJsonArray("feet")){var a=rawPoint.getAsJsonArray();path.add(new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble()));}
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.getAbilities().flying=false;p.onUpdateAbilities();var at=YachtRuntime.point(ship.ship,-5,.05,-11);p.teleportTo(at.x,at.y,at.z);
                ship.route=new java.util.ArrayList<>(java.util.List.of(YachtRuntime.point(ship.ship,0,0,8)));ship.status="SAILING";
            });
        }}
        if(phase==5 && ticks>40){
            var local=YachtRuntime.local(ship.ship,mc.player.position());
            while(waypoint<path.size() && path.get(waypoint).subtract(local).multiply(1,0,1).length()<.25)waypoint++;
            if(waypoint<path.size()){
                var delta=ship.ship.logicalPose().transformNormal(path.get(waypoint).subtract(local).multiply(1,0,1));
                mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));mc.player.setXRot(5);mc.options.keyUp.setDown(true);
                if(ticks%60==0)capture="stairs-"+ticks;
            } else {mc.options.keyUp.setDown(false);capture="upper-landing-world";phase=6;ticks=0;
                report(output,"Client stair input completed; local feet="+local+". Ship status="+ship.status+", origin="+YachtRuntime.point(ship.ship,0,0,0)+". Single integrated client only.\n");
            }
            if(ticks>900){mc.options.keyUp.setDown(false);report(output,"FAIL: client stair route stalled at waypoint "+waypoint+", local feet="+local+"\n");capture="stairs-stalled";phase=6;ticks=0;}
        }
        if(phase==6 && ticks==40)mc.getSingleplayerServer().execute(()->place(3));
        if(phase==6 && ticks==60) {
            for(var actor:mc.level.entitiesForRendering())if(actor.getUUID().equals(ship.data.getUUID("captain")))mc.gameMode.interact(mc.player,actor,net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        if(phase==6 && ticks==80)capture="captain-chart-world";
        if(phase==6 && ticks==450) {
            var at=YachtRuntime.point(ship.ship,0,0,0);
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new YachtNetwork.Request(ship.ship.getUniqueId(),1,(int)Math.round(at.x),(int)Math.round(at.z+8)));
        }
        if(phase==6 && ticks==550)capture="captain-sailing-world";
        if(phase==6 && ticks==1000){capture="captain-arrival-world";report(output,"Client stair input completed. Final voyage status="+ship.status+", origin="+YachtRuntime.point(ship.ship,0,0,0)+". Captain interaction and destination used client packets. Single integrated client only.\n");}
        if(phase==6 && ticks>1020){finished=true;mc.options.keyUp.setDown(false);mc.stop();}
    }
    static void place(int index){var mc=Minecraft.getInstance();var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var v=views[index];var at=YachtRuntime.point(ship.ship,v[0],v[1],v[2]);
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.connection.teleport(at.x,at.y,at.z,(float)v[3],(float)v[4]);
    }
    static void report(String output,String text){try{Files.createDirectories(Path.of(output));Files.writeString(Path.of(output,"result.txt"),text);}catch(Exception e){throw new RuntimeException(e);}}
    @SubscribeEvent public static void render(RenderFrameEvent.Post event){if(capture==null)return;var name=capture;capture=null;try{var directory=Path.of(System.getProperty("practicemod.yachtWorldReview"));Files.createDirectories(directory);try(var pixels=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){pixels.writeToFile(directory.resolve(name+".png"));}}catch(Exception e){throw new RuntimeException(e);}}
}
