package com.bilal.practicemod.yacht;

import com.bilal.practicemod.PracticeMod;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.joml.Vector3d;

import java.util.IdentityHashMap;
import java.util.Map;

/** Disposable test-world fixture. No yacht ownership, navigation, or passenger teleporting. */
@EventBusSubscriber(modid = PracticeMod.MODID)
public final class YachtFloorPrototype {
    private record Drive(double height, long startTick, boolean turn) {}
    public static int physicsSteps;
    private static final Map<ServerSubLevel, Drive> DRIVES = new IdentityHashMap<>();

    public static ServerSubLevel spawn(ServerLevel level, double x, double y, double z) {
        return spawn(level,x,y,z,false);
    }

    public static ServerSubLevel spawn(ServerLevel level, double x, double y, double z, boolean turn) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null) throw new IllegalStateException("Sable container unavailable");
        var pose = new Pose3d();
        pose.position().set(x, y, z);
        var sub = (ServerSubLevel) container.allocateNewSubLevel(pose);
        sub.setName("White Horizon moving-floor fixture");
        var plot = sub.getPlot();
        plot.newEmptyChunk(plot.getCenterChunk());
        var blocks = plot.getEmbeddedLevelAccessor();
        // 12 x 9, lower floor at Y=1, upper floor at Y=5. Stair opening x=1..5,z=1..8.
        for (int bx = 0; bx < 12; bx++) for (int bz = 0; bz < 9; bz++) {
            blocks.setBlock(new BlockPos(bx, 0, bz), Blocks.QUARTZ_BLOCK.defaultBlockState(), 3);
            if (!(bx >= 1 && bx <= 5 && bz >= 1 && bz <= 7))
                blocks.setBlock(new BlockPos(bx, 4, bz), Blocks.QUARTZ_BLOCK.defaultBlockState(), 3);
            if (bx == 0 || bx == 11 || bz == 0 || bz == 8) {
                if (!(bx >= 7 && bx <= 8 && bz == 0))
                    blocks.setBlock(new BlockPos(bx, 5, bz), Blocks.OAK_FENCE.defaultBlockState(), 3);
            }
        }
        // Four half-block rises, a two-block landing, then the return flight.
        for (int i = 0; i < 4; i++) for (int w = 0; w < 2; w++) {
            step(blocks, 1+w, 1.5+i*.5, 2+i);
            step(blocks, 4+w, 3.5+i*.5, 5-i);
        }
        for (int bx=1; bx<=5; bx++) for (int bz=6; bz<=7; bz++)
            blocks.setBlock(new BlockPos(bx,2,bz),Blocks.QUARTZ_BLOCK.defaultBlockState(),3);
        blocks.setBlock(new BlockPos(9,1,6),Blocks.CHEST.defaultBlockState(),3);
        blocks.setBlock(new BlockPos(9,1,4),Blocks.OAK_STAIRS.defaultBlockState(),3);
        sub.updateLastPose();
        container.addForceLoadTicket(sub, dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED, net.minecraft.util.Unit.INSTANCE);
        DRIVES.put(sub, new Drive(y, level.getGameTime(), turn));
        return sub;
    }

    /** Hold a fixture until its contact probes are ready; no pose changes. */
    public static void hold(ServerSubLevel sub) {
        var drive=DRIVES.get(sub);
        DRIVES.put(sub,new Drive(drive.height(),Long.MAX_VALUE,drive.turn()));
    }

    public static void start(ServerSubLevel sub) {
        var drive=DRIVES.get(sub);
        DRIVES.put(sub,new Drive(drive.height(),sub.getLevel().getGameTime(),drive.turn()));
    }

    private static void step(net.minecraft.world.level.CommonLevelAccessor world,int x,double top,int z) {
        int y=(int)Math.ceil(top)-1;
        world.setBlock(new BlockPos(x,y,z), top == Math.floor(top)
                ? Blocks.QUARTZ_BLOCK.defaultBlockState()
                : Blocks.QUARTZ_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),3);
    }

    @SubscribeEvent
    public static void physics(ForgeSablePrePhysicsTickEvent event) {
        physicsSteps++;
        var level=event.getPhysicsSystem().getLevel();
        DRIVES.entrySet().removeIf(e -> e.getKey().isRemoved());
        for (var e : DRIVES.entrySet()) {
            var sub=e.getKey();
            if (sub.getLevel()!=level) continue;
            var handle=RigidBodyHandle.of(sub);
            if (handle==null || sub.getMassTracker()==null) continue;
            var drive=e.getValue();
            double seconds=drive.startTick()==Long.MAX_VALUE?0:(level.getGameTime()-drive.startTick())/20.0;
            // 2 s settling, bounded acceleration to .8 m/s, cruise, brake, hold.
            double speed=seconds<2?0:seconds<10?(seconds-2)*.1:seconds<20?.8:Math.max(0,.8-(seconds-20)*.1);
            var p=sub.logicalPose().position();
            var velocity=handle.getLinearVelocity(new Vector3d());
            var gravity=DimensionPhysicsData.getGravity(level,p);
            var origin = worldPoint(sub, 0, 0, 0);
            var desired=new Vector3d(0,Math.clamp((drive.height()-origin.y)*2,-.5,.5),speed);
            // Velocity feedback through the physics API, never pose teleportation.
            var delta=desired.sub(velocity).sub(new Vector3d(gravity).mul(event.getTimeStep()));
            var angular=handle.getAngularVelocity(new Vector3d()).negate();
            if (drive.turn() && seconds>=10 && seconds<20) angular.y+=Math.toRadians(1);
            handle.addLinearAndAngularVelocity(delta,angular);
        }
    }


    public static net.minecraft.world.phys.Vec3 worldPoint(ServerSubLevel sub, double x, double y, double z) {
        var center = sub.getPlot().getCenterBlock();
        return sub.logicalPose().transformPosition(new net.minecraft.world.phys.Vec3(center.getX()+x,center.getY()+y,center.getZ()+z));
    }

    public static void dispose(ServerSubLevel sub) {
        var container=SubLevelContainer.getContainer(sub.getLevel());
        container.removeForceLoadTicket(sub,
            dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED,
            net.minecraft.util.Unit.INSTANCE);
        container.removeSubLevel(sub, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        DRIVES.remove(sub);
    }

    @SubscribeEvent
    public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        for (var sub : java.util.List.copyOf(DRIVES.keySet())) dispose(sub);
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("yachtprototype").requires(s -> s.hasPermission(2))
            .then(Commands.literal("spawn").executes(ctx -> {
                var source=ctx.getSource(); var p=source.getPosition(); var level=source.getLevel();
                if (DRIVES.keySet().stream().anyMatch(sub -> sub.getLevel()==level && !sub.isRemoved())) {
                    source.sendFailure(Component.literal("One fixture per dimension. Restart this disposable test world to clear it."));
                    return 0;
                }
                // Whole 30-second test corridor must already exist and be clear.
                var region=new AABB(p.x,p.y,p.z+3,p.x+12,p.y+8,p.z+35);
                for (var b : BlockPos.betweenClosed(BlockPos.containing(region.minX,region.minY,region.minZ),
                        BlockPos.containing(region.maxX,region.maxY,region.maxZ))) {
                    if (!level.hasChunkAt(b) || !level.getBlockState(b).isAir()) {
                        source.sendFailure(Component.literal("Use an empty, loaded test area: 12 x 8 x 32 ahead.")); return 0;
                    }
                }
                hold(spawn(level,p.x,p.y,p.z+3));
                source.sendSuccess(() -> Component.literal("Disposable fixture held at +Z 3 blocks. Board, then /yachtprototype start. Creative test world only."),false);
                return 1;
            }))
            .then(Commands.literal("start").executes(ctx -> {
                var fixtures=DRIVES.keySet().stream()
                        .filter(sub -> sub.getLevel()==ctx.getSource().getLevel() && !sub.isRemoved()).toList();
                if(fixtures.size()!=1) {
                    ctx.getSource().sendFailure(Component.literal("Spawn one fixture in this dimension first."));
                    return 0;
                }
                if(DRIVES.get(fixtures.getFirst()).startTick()!=Long.MAX_VALUE) {
                    ctx.getSource().sendFailure(Component.literal("This fixture has already used its checked travel corridor. Restart the disposable world for another run."));
                    return 0;
                }
                start(fixtures.getFirst());
                ctx.getSource().sendSuccess(() -> Component.literal("Test motion: 2s hold, accelerate, cruise, brake."),false);
                return 1;
            })));

    }
}
