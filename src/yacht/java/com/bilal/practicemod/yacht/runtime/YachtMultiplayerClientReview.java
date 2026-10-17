package com.bilal.practicemod.yacht.runtime;

import com.google.gson.*;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.nio.file.*;
import java.util.*;

/** Real client input and packets; the shared file coordinates the test, never player motion. */
@EventBusSubscriber(modid="practicemod",value=Dist.CLIENT)
public final class YachtMultiplayerClientReview {
    static int ticks,target,jumpTicks,seenPlayers,swimTicks;static boolean interacted,requested,done,boarded;static double maxStep,maxJump;static Vec3 previous;
    static final List<Vec3> path=new ArrayList<>();static String capture;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        String output=System.getProperty("practicemod.yachtMultiplayerReview");var mc=Minecraft.getInstance();
        if(output==null || mc.player==null || mc.level==null)return;
        mc.options.pauseOnLostFocus=false;mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        Path statePath=Path.of(output,"state.json");if(!Files.exists(statePath))return;
        JsonObject state;try{state=JsonParser.parseString(Files.readString(statePath)).getAsJsonObject();}catch(Exception ignored){return;}
        if(state.get("ticks").getAsInt()<240)return;
        var container=SubLevelContainer.getContainer(mc.level);if(container==null)return;
        SubLevel ship=null;for(var candidate:container.getAllSubLevels())if(candidate.getUniqueId().toString().equals(state.get("ship").getAsString()))ship=candidate;
        if(ship==null)return;ticks++;
        String name=mc.player.getName().getString();boolean owner=name.equals("YachtOwner");var center=ship.getPlot().getCenterBlock();
        var local=ship.logicalPose().transformPositionInverse(mc.player.position()).subtract(center.getX(),center.getY(),center.getZ());
        if(ticks<100)return;
        seenPlayers=Math.max(seenPlayers,mc.level.players().size()-1);
        if(Boolean.getBoolean("practicemod.yachtReviewReconnect")) {
            if(ticks==160)YachtMultiplayerReview.write(Path.of(output,name+"-reconnected.json"),"{\"local\":\""+local+"\",\"otherPlayers\":"+(mc.level.players().size()-1)+"}");
            if(state.has("boarding") && state.get("boarding").getAsBoolean()) {
                mc.setScreen(null);swimTicks++;
                if(swimTicks==60)capture=name+"-swim-contact";
                if(swimTicks%20==0)YachtMultiplayerReview.write(Path.of(output,name+"-swim.json"),"{\"local\":\""+local+"\",\"water\":"+mc.player.isInWater()+",\"climbing\":"+mc.player.onClimbable()+",\"forward\":"+mc.player.input.forwardImpulse+",\"jump\":"+mc.player.input.jumping+"}");
                if(!boarded && local.y>=-2.1 && local.z> -29.2 && local.z< -28) {
                    boarded=true;capture=name+"-boarded";YachtMultiplayerReview.write(Path.of(output,name+"-boarded.json"),"{\"local\":\""+local+"\"}");
                }
                var direction=ship.logicalPose().transformNormal(new Vec3(0,0,boarded?-1:1));mc.player.setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));mc.player.setXRot(0);
                mc.options.keyUp.setDown(true);mc.options.keyJump.setDown(!boarded);
                if(boarded && local.z< -32 && local.y< -4.5) {
                    mc.options.keyUp.setDown(false);capture=name+"-disembarked";YachtMultiplayerReview.write(Path.of(output,name+"-disembarked.json"),"{\"local\":\""+local+"\",\"water\":"+mc.player.isInWater()+"}");
                }
            }
            return;
        }
        if(previous!=null)maxStep=Math.max(maxStep,local.distanceTo(previous));previous=local;
        if(mc.screen!=null && !(mc.screen instanceof YachtClient.Chart))mc.setScreen(null);
        if(owner) {
            if(!interacted){for(var actor:mc.level.entitiesForRendering())if(actor.getUUID().toString().equals(state.get("captain").getAsString())){mc.gameMode.interact(mc.player,actor,net.minecraft.world.InteractionHand.MAIN_HAND);interacted=true;}}
            if(!requested && mc.screen instanceof YachtClient.Chart && ticks>160){PacketDistributor.sendToServer(new YachtNetwork.Request(UUID.fromString(state.get("ship").getAsString()),1,state.get("destinationX").getAsInt(),state.get("destinationZ").getAsInt()));requested=true;capture=name+"-chart";}
            if(requested && ticks>190)mc.setScreen(null);
        } else if(state.get("movingTicks").getAsInt()>40) {
            if(path.isEmpty())for(var raw:YachtAssets.DATA.getAsJsonObject("layout").getAsJsonArray("routes"))if(raw.getAsJsonObject().get("name").getAsString().equals("A_0_to_4"))for(var rawPoint:raw.getAsJsonObject().getAsJsonArray("feet")){var a=rawPoint.getAsJsonArray();path.add(new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble()));}
            while(target<path.size() && path.get(target).subtract(local).multiply(1,0,1).length()<.25)target++;
            if(target<path.size()) {
                var delta=ship.logicalPose().transformNormal(path.get(target).subtract(local).multiply(1,0,1));mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));mc.player.setXRot(5);mc.options.keyUp.setDown(true);
            } else {mc.options.keyUp.setDown(false);done=true;}
        }
        if((owner && state.get("movingTicks").getAsInt()>100) || (!owner && done)) {
            jumpTicks++;mc.options.keyJump.setDown(jumpTicks<=2);maxJump=Math.max(maxJump,local.y-4);
            if(jumpTicks==60){capture=name+"-landing";YachtMultiplayerReview.write(Path.of(output,name+"-done.json"),"{\"stairComplete\":"+done+",\"maxJump\":"+maxJump+",\"maxLocalStep\":"+maxStep+",\"local\":\""+local+"\",\"otherPlayers\":"+seenPlayers+"}");}
        }
        if(ticks%20==0)YachtMultiplayerReview.write(Path.of(output,name+"-latest.json"),"{\"ticks\":"+ticks+",\"local\":\""+local+"\",\"maxLocalStep\":"+maxStep+",\"waypoint\":"+target+",\"otherPlayers\":"+(mc.level.players().size()-1)+"}");
        if(Files.exists(Path.of(output,"server-result.json"))){mc.options.keyUp.setDown(false);mc.options.keyJump.setDown(false);if(ticks%40==0)mc.stop();}
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event){if(capture==null)return;var name=capture;capture=null;try(var pixels=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){pixels.writeToFile(Path.of(System.getProperty("practicemod.yachtMultiplayerReview"),name+".png"));}catch(Exception e){throw new RuntimeException(e);}}
}
