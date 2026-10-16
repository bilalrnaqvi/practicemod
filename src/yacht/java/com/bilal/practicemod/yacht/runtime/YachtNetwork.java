package com.bilal.practicemod.yacht.runtime;

import com.google.gson.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.*;

@EventBusSubscriber(modid="practicemod",bus=EventBusSubscriber.Bus.MOD)
public final class YachtNetwork {
    public record Request(UUID ship,int action,int x,int z) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("practicemod","yacht_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.ship);b.writeVarInt(p.action);b.writeInt(p.x);b.writeInt(p.z);},b->new Request(b.readUUID(),b.readVarInt(),b.readInt(),b.readInt()));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("practicemod","yacht_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,32767),b->new Snapshot(b.readUtf(32767)));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    static final Map<UUID,Long> LAST=new HashMap<>();
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("white-horizon-1");
        registrar.playToServer(Request.TYPE,Request.CODEC,(packet,context)->{
            if(!(context.player() instanceof ServerPlayer player))return;
            var c=YachtRuntime.ACTIVE.get(packet.ship);if(c==null || c.ship.getLevel()!=player.level())return;
            long now=player.level().getGameTime();if(now-LAST.getOrDefault(player.getUUID(),-100L)<5)return;LAST.put(player.getUUID(),now);
            if(packet.action==2 && YachtRuntime.aboard(c,player)) {c.stop("STOPPING — passenger request");send(c,player,false,true);return;}
            if(player.position().distanceToSqr(YachtRuntime.point(c.ship,-2.5,4,17))>64)return;
            if(packet.action==0){send(c,player,true,true);return;}
            if(packet.action!=1 || !c.owns(player) || c.age<100 || c.speed>.01)return;
            var origin=YachtRuntime.point(c.ship,0,0,0);
            if(Math.hypot(packet.x-origin.x,packet.z-origin.z)>YachtNavigation.RANGE || !YachtNavigation.clear(player.serverLevel(),packet.x,packet.z,c.waterY)) {
                c.status="REJECTED — destination needs surveyed deep open water";send(c,player,false,true);return;
            }
            c.stop("PREPARING — checking route");c.destination=new Vec3(packet.x,origin.y,packet.z);c.search=new YachtNavigation.Search(player.serverLevel(),origin,c.waterY,packet.x,packet.z);
            send(c,player,false,true);
        });
        registrar.playToClient(Snapshot.TYPE,Snapshot.CODEC,(packet,context)->YachtClient.receive(packet.json));
    }
    public static void broadcast(YachtRuntime.Controller c) {
        for(var player:c.ship.getLevel().players())if(player.position().distanceToSqr(YachtRuntime.point(c.ship,0,0,0))<160*160)
            send(c,player,false,false);
    }
    public static void send(YachtRuntime.Controller c,ServerPlayer player,boolean open,boolean map) {
        var json=new JsonObject();json.addProperty("ship",c.ship.getUniqueId().toString());json.addProperty("dimension",player.level().dimension().location().toString());json.addProperty("open",open);
        var origin=YachtRuntime.point(c.ship,0,0,0);json.addProperty("x",origin.x);json.addProperty("z",origin.z);json.addProperty("status",c.status);json.addProperty("control",c.owns(player));json.addProperty("speed",c.speed);
        if(c.destination!=null){var destination=new JsonArray();destination.add(c.destination.x);destination.add(c.destination.z);json.add("destination",destination);}
        var route=new JsonArray();for(var p:c.route){var a=new JsonArray();a.add(p.x);a.add(p.z);route.add(a);}json.add("route",route);
        var masks=new JsonArray();
        for(var other:YachtRuntime.ACTIVE.values())if(other.ship.getLevel()==player.level())for(var region:other.masks) {
            var volume=region.getVolume();var a=new JsonArray();var min=volume.getMinBlockPos();var max=volume.getMaxBlockPos();
            for(int v:new int[]{min.getX(),min.getY(),min.getZ(),max.getX(),max.getY(),max.getZ()})a.add(v);masks.add(a);
        }
        json.add("masks",masks);
        if(map) {
            var tiles=new JsonArray();int cx=(int)Math.floor(origin.x),cz=(int)Math.floor(origin.z);json.addProperty("mapX",cx);json.addProperty("mapZ",cz);
            for(int z=-16;z<=16;z++)for(int x=-16;x<=16;x++) {
                int wx=cx+x*8,wz=cz+z*8;
                var pos=new net.minecraft.core.BlockPos(wx,c.waterY-1,wz);
                tiles.add(!player.level().hasChunkAt(pos)?0:YachtNavigation.column(player.serverLevel(),wx,wz,c.waterY)?1:2);
            }
            json.add("tiles",tiles);
        }
        PacketDistributor.sendToPlayer(player,new Snapshot(json.toString()));
    }
}
