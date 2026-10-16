package com.bilal.practicemod.yacht.runtime;

import dev.ryanhcode.sable.api.sublevel.*;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.water_occlusion.*;
import dev.ryanhcode.sable.util.BoundedBitVolume3i;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import net.minecraft.util.Unit;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;
import java.util.*;

@EventBusSubscriber(modid="practicemod")
public final class YachtRuntime {
    public static final Map<UUID,Controller> ACTIVE=new HashMap<>();
    public static final class Controller {
        public final ServerSubLevel ship;public final CompoundTag data;public final int waterY;
        public String status="STOPPED — route must be selected";
        public List<Vec3> route=new ArrayList<>(); public YachtNavigation.Search search;public Vec3 destination;
        public double speed,yawRate;public Vec3 velocity=Vec3.ZERO;public boolean ticket;public long age;
        final List<WaterOcclusionRegion> masks=new ArrayList<>();
        Controller(ServerSubLevel ship) {this.ship=ship;data=ship.getUserDataTag();waterY=data.getInt("waterY");
            SubLevelContainer.getContainer(ship.getLevel()).removeForceLoadTicket(ship,SubLevelLoadingTicketType.COMMAND_FORCED,Unit.INSTANCE);
            installMasks(this);
        }
        public boolean owns(Player player) {return player.getUUID().equals(data.getUUID("owner")) || player.hasPermissions(2) || data.getString("navigator").equals(player.getUUID().toString());}
        public void stop(String reason) {route.clear();search=null;status=reason;}
    }
    public static Vec3 point(ServerSubLevel ship,double x,double y,double z) {
        var c=ship.getPlot().getCenterBlock();return ship.logicalPose().transformPosition(new Vec3(c.getX()+x,c.getY()+y,c.getZ()+z));
    }
    public static Vec3 local(ServerSubLevel ship,Vec3 world) {
        var c=ship.getPlot().getCenterBlock();return ship.logicalPose().transformPositionInverse(world).subtract(c.getX(),c.getY(),c.getZ());
    }
    public static boolean aboard(Controller c,Player p) {
        var v=local(c.ship,p.position());return Math.abs(v.x)<9 && Math.abs(v.z)<31 && v.y>-9 && v.y<15;
    }
    public static Controller nearest(Player p) {
        return ACTIVE.values().stream().filter(c->c.ship.getLevel()==p.level() && point(c.ship,0,0,0).distanceToSqr(p.position())<90*90)
                .min(Comparator.comparingDouble(c->point(c.ship,0,0,0).distanceToSqr(p.position()))).orElse(null);
    }
    public static Controller launch(ServerPlayer player) {
        var level=player.serverLevel();var direction=player.getLookAngle().multiply(1,0,1).normalize();
        var position=player.position().add(direction.scale(42));
        if(!level.hasChunkAt(BlockPos.containing(position)))throw new IllegalArgumentException("Launch water must already be loaded.");
        int waterY=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,(int)Math.floor(position.x),(int)Math.floor(position.z));
        if(ACTIVE.values().stream().filter(c->c.ship.getLevel()==level).count()>=4)throw new IllegalArgumentException("Four active yachts are the current server limit.");
        if(!YachtNavigation.clear(level,position.x,position.z,waterY))throw new IllegalArgumentException("Need loaded open water: a 68-block turning area, five blocks deep, with no overhead terrain.");
        for(var c:ACTIVE.values())if(c.ship.getLevel()==level && point(c.ship,0,0,0).distanceTo(position)<75)throw new IllegalArgumentException("Another yacht is too close.");
        if(!level.getEntities((Entity)null,new AABB(position.x-10,waterY-4,position.z-31,position.x+10,waterY+18,position.z+31),e->e instanceof LivingEntity).isEmpty())
            throw new IllegalArgumentException("People or animals are in the placement area.");
        return assemble(level,player.getUUID(),player.getName().getString(),position,waterY);
    }
    static Controller assemble(ServerLevel level,UUID owner,String ownerName,Vec3 position,int waterY) {
        var pose=new Pose3d();pose.position().set(position.x,waterY+5,position.z);
        var container=SubLevelContainer.getContainer(level);var ship=(ServerSubLevel)container.allocateNewSubLevel(pose);
        var data=new CompoundTag();data.putBoolean("whiteHorizon",true);data.putInt("schema",1);data.putUUID("owner",owner);data.putInt("waterY",waterY);data.putString("model",YachtAssets.DATA.get("source_sha256").getAsString());
        ship.setUserDataTag(data);ship.setName("White Horizon — "+ownerName);
        try {
        var plot=ship.getPlot();var center=plot.getCenterBlock();var chunks=new HashSet<net.minecraft.world.level.ChunkPos>();
        for(var e:YachtAssets.DATA.getAsJsonArray("cells")) {var a=e.getAsJsonArray();chunks.add(new net.minecraft.world.level.ChunkPos(center.offset(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt())));}
        for(var chunk:chunks)plot.newEmptyChunk(chunk);
        var blocks=plot.getEmbeddedLevelAccessor();
        for(var e:YachtAssets.DATA.getAsJsonArray("cells")) {var a=e.getAsJsonArray();blocks.setBlock(new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()),YachtAssets.HULL.defaultBlockState().setValue(YachtAssets.CELL,a.get(3).getAsInt()),2);}
        // Assembly changes Sable's reference from plot origin to center of mass.
        // Correct the initial placement once, before boarding; voyages use velocity only.
        var actual=point(ship,0,0,0);ship.logicalPose().position().add(position.x-actual.x,waterY+5-actual.y,position.z-actual.z);
        container.physicsSystem().getPipeline().teleport(ship,ship.logicalPose().position(),ship.logicalPose().orientation());
        ship.updateLastPose();var controller=new Controller(ship);ACTIVE.put(ship.getUniqueId(),controller);ticket(controller,true);
        // Create once and persist the actor UUID with the same backend record as its hull.
        var captain=EntityType.VILLAGER.create(level);captain.setNoAi(true);captain.setInvulnerable(true);captain.setPersistenceRequired();captain.setCustomName(Component.literal("Captain · White Horizon"));captain.setCustomNameVisible(true);
        captain.setPos(point(ship,-2.5,4.1,17));level.addFreshEntity(captain);data.putUUID("captain",captain.getUUID());
        controller.status="PREPARING — hull settling";return controller;
        } catch(RuntimeException failure) {dispose(ship);throw failure;}
    }
    static void dispose(ServerSubLevel ship) {
        YachtFixtures.STORAGE.remove(ship.getUniqueId());
        var c=ACTIVE.remove(ship.getUniqueId());
        if(c!=null) {var water=WaterOcclusionContainer.getContainer(ship.getLevel());if(water!=null)c.masks.forEach(water::removeRegion);}
        if(ship.getUserDataTag().hasUUID("captain")){var captain=ship.getLevel().getEntity(ship.getUserDataTag().getUUID("captain"));if(captain!=null)captain.discard();}
        var container=SubLevelContainer.getContainer(ship.getLevel());
        container.removeForceLoadTicket(ship,SubLevelLoadingTicketType.COMMAND_FORCED,Unit.INSTANCE);
        container.removeSubLevel(ship,dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
    }
    static boolean trafficClear(Controller c,Vec3 origin) {
        for(var other:ACTIVE.values())if(other!=c && other.ship.getLevel()==c.ship.getLevel() && point(other.ship,0,0,0).distanceToSqr(origin)<72*72)return false;
        for(var entity:c.ship.getLevel().getEntities((Entity)null,new AABB(origin.x-35,c.waterY-4,origin.z-35,origin.x+35,c.waterY+18,origin.z+35),e->e instanceof Player)) {
            if(!aboard(c,(Player)entity) && local(c.ship,entity.position()).multiply(1,0,1).length()<35)return false;
        }
        return true;
    }
    static void installMasks(Controller c) {
        var container=WaterOcclusionContainer.getContainer(c.ship.getLevel());if(container==null)return;
        var center=c.ship.getPlot().getCenterBlock();
        for(var raw:YachtAssets.DATA.getAsJsonObject("layout").getAsJsonArray("dry_volume_proposals")) {
            var a=raw.getAsJsonArray();var min=center.offset((int)Math.floor(a.get(0).getAsDouble()),(int)Math.floor(a.get(1).getAsDouble()),(int)Math.floor(a.get(2).getAsDouble()));
            var max=center.offset((int)Math.ceil(a.get(3).getAsDouble())-1,(int)Math.ceil(a.get(4).getAsDouble())-1,(int)Math.ceil(a.get(5).getAsDouble())-1);
            var volume=new BoundedBitVolume3i(min.getX(),min.getY(),min.getZ(),max.getX(),max.getY(),max.getZ());
            for(var p:BlockPos.betweenClosed(min,max))volume.setOccupied(p.getX(),p.getY(),p.getZ(),true);
            c.masks.add(container.addRegion(volume));
        }
    }
    static void ticket(Controller c,boolean enabled) {
        if(c.ticket==enabled)return;var container=SubLevelContainer.getContainer(c.ship.getLevel());
        if(enabled)container.addForceLoadTicket(c.ship,SubLevelLoadingTicketType.COMMAND_FORCED,Unit.INSTANCE);
        else container.removeForceLoadTicket(c.ship,SubLevelLoadingTicketType.COMMAND_FORCED,Unit.INSTANCE);
        c.ticket=enabled;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for(var level:event.getServer().getAllLevels()) {
            var container=SubLevelContainer.getContainer(level);if(container==null)continue;
            for(var ship:container.getAllSubLevels())if(ship.getUserDataTag()!=null && ship.getUserDataTag().getBoolean("whiteHorizon"))
                ACTIVE.computeIfAbsent(ship.getUniqueId(),id->new Controller(ship));
        }
        for(var iterator=ACTIVE.values().iterator();iterator.hasNext();) {
            var c=iterator.next();if(c.ship.isRemoved()) {var masks=WaterOcclusionContainer.getContainer(c.ship.getLevel());if(masks!=null)c.masks.forEach(masks::removeRegion);YachtFixtures.STORAGE.remove(c.ship.getUniqueId());iterator.remove();continue;}
            c.age++;
            if(c.data.hasUUID("captain")) {
                var actor=c.ship.getLevel().getEntity(c.data.getUUID("captain"));
                if(actor!=null && local(c.ship,actor.position()).distanceToSqr(new Vec3(-2.5,4,17))>6.25) {
                    // Keep only the NPC at its bridge station if bumped; never reposition passengers.
                    var anchor=point(c.ship,-2.5,4.05,17);actor.teleportTo(anchor.x,anchor.y,anchor.z);
                }
            }
            var origin=point(c.ship,0,0,0);var level=c.ship.getLevel();
            boolean occupied=level.players().stream().anyMatch(p->aboard(c,p));
            if(!occupied && (!c.route.isEmpty() || c.search!=null))c.stop("STOPPED — no passengers aboard");
            if(c.age==100 && c.status.startsWith("PREPARING"))c.status="DOCKED — ready";
            if(c.search!=null) {c.search.tick();if(c.search.done){c.route=new ArrayList<>(c.search.result);c.status=c.route.isEmpty()?"BLOCKED — no safe route in surveyed water":"SAILING";c.search=null;}}
            double desired=0;c.yawRate=0;Vec3 heading=Vec3.ZERO;
            if(c.age>100 && !c.route.isEmpty()) {
                var target=c.route.getFirst();var delta=target.subtract(origin).multiply(1,0,1);double distance=delta.length();
                if(distance<.45) {c.route.removeFirst();if(c.route.isEmpty())c.status="ARRIVED";}
                else {
                    heading=delta.normalize();var forward=c.ship.logicalPose().transformNormal(new Vec3(0,0,1));
                    double angle=Math.atan2(heading.x,heading.z)-Math.atan2(forward.x,forward.z);angle=Math.atan2(Math.sin(angle),Math.cos(angle));
                    c.yawRate=c.speed>.02 && Math.abs(angle)>.15?0:Math.clamp(angle,-Math.toRadians(1),Math.toRadians(1));
                    desired=Math.abs(angle)<.15?Math.min(.8,Math.sqrt(.2*Math.max(0,distance-.3))):0;
                    var ahead=origin.add(heading.scale(8));
                    if(!YachtNavigation.clear(level,origin.x,origin.z,c.waterY) || !YachtNavigation.clear(level,ahead.x,ahead.z,c.waterY) || !trafficClear(c,origin)) {org.slf4j.LoggerFactory.getLogger(YachtRuntime.class).info("Yacht stopped: current={}, ahead={}, traffic={}",YachtNavigation.diagnose(level,origin.x,origin.z,c.waterY),YachtNavigation.diagnose(level,ahead.x,ahead.z,c.waterY),trafficClear(c,origin));c.stop("BLOCKED — terrain, traffic, or unloaded water ahead");desired=0;c.speed=0;c.yawRate=0;}
                }
            }
            c.speed+=Math.clamp(desired-c.speed,-.005,.005);
            if(heading.lengthSqr()>0 && desired>0)c.velocity=heading.scale(c.speed);else c.velocity=c.velocity.lengthSqr()>0?c.velocity.normalize().scale(c.speed):Vec3.ZERO;
            ticket(c,occupied || c.speed>.001 || c.search!=null || c.age<120);
            if(c.age%20==0)YachtNetwork.broadcast(c);
        }
    }
    @SubscribeEvent public static void physics(ForgeSablePrePhysicsTickEvent event) {
        for(var c:ACTIVE.values())if(c.ship.getLevel()==event.getPhysicsSystem().getLevel() && !c.ship.isRemoved()) {
            var handle=RigidBodyHandle.of(c.ship);if(handle==null)continue;var origin=point(c.ship,0,0,0);
            var current=handle.getLinearVelocity(new Vector3d());var gravity=DimensionPhysicsData.getGravity(c.ship.getLevel(),c.ship.logicalPose().position());
            var desired=new Vector3d(c.velocity.x,Math.clamp((c.waterY+5-origin.y)*2,-.5,.5),c.velocity.z);
            var angular=handle.getAngularVelocity(new Vector3d()).negate();angular.y+=c.yawRate;
            handle.addLinearAndAngularVelocity(desired.sub(current).sub(new Vector3d(gravity).mul(event.getTimeStep())),angular);
        }
    }
    @SubscribeEvent public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        for(var c:ACTIVE.values()){c.stop("STOPPED");c.velocity=Vec3.ZERO;ticket(c,false);}ACTIVE.clear();YachtFixtures.STORAGE.clear();YachtNetwork.LAST.clear();
    }
}
