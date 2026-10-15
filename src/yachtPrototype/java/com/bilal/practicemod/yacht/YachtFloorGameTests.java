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
    @GameTest(template="empty", batch="yacht_floor", timeoutTicks=680)
    public void movingFloorSupportsUnmountedEntityThroughAccelerationAndStop(GameTestHelper h) {
        runProbe(h,new Vec3(0,100,0));
    }

    @GameTest(template="empty", batch="yacht_precision", timeoutTicks=680)
    public void movingFloorAtMillionBlockCoordinates(GameTestHelper h) {
        runProbe(h,new Vec3(1_000_000,100,1_000_000));
    }

    private void runProbe(GameTestHelper h, Vec3 p) {
        var ship=YachtFloorPrototype.spawn(h.getLevel(),p.x,p.y,p.z);
        ArmorStand occupant=EntityType.ARMOR_STAND.create(h.getLevel());
        occupant.setPos(p.x+8.5,p.y+1.05,p.z+2.5);
        h.getLevel().addFreshEntity(occupant);
        final Vec3[] settled = new Vec3[1];
        h.runAfterDelay(39, () -> settled[0] = ship.logicalPose().transformPositionInverse(occupant.position()));
        for (int tick=100;tick<=600;tick+=100) {
            final int sample=tick;
            h.runAfterDelay(tick,()->System.out.println("YACHT_SAMPLE tick="+sample+" game="+h.getLevel().getGameTime()+" pose="+ship.logicalPose().position()+" velocity="+dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(ship).getLinearVelocity(new org.joml.Vector3d())));
        }
        h.runAfterDelay(640,()->{
            try {
            h.assertFalse(ship.isRemoved(),"fixture unloaded before motion completed");
            var pose=YachtFloorPrototype.worldPoint(ship,0,0,0);
            h.assertTrue(pose.z-p.z>10 && pose.z-p.z<20,"platform traveled physically: "+(pose.z-p.z)+" physicsSteps="+YachtFloorPrototype.physicsSteps+" mass="+ship.getMassTracker().getMass()+" velocity="+dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(ship).getLinearVelocity(new org.joml.Vector3d()));
            h.assertTrue(Math.abs(pose.y-p.y)<.1,"height hold: "+(pose.y-p.y));
            h.assertFalse(occupant.isPassenger(),"occupant was never mounted");
            h.assertTrue(Math.abs(occupant.getY()-YachtFloorPrototype.worldPoint(ship,8.5,1,2.5).y)<.1,"occupant remains on lower deck: "+occupant.position());
            var local=ship.logicalPose().transformPositionInverse(occupant.position());
            double drift=Math.hypot(local.x-settled[0].x,local.z-settled[0].z);
            h.assertTrue(drift<.05,"grounded relative drift stays within .05: "+drift);
            System.out.println("YACHT_RESULT origin="+p+" travel="+(pose.z-p.z)+" heightError="+(pose.y-p.y)+" drift="+drift);
            } finally {
                occupant.discard();
                YachtFloorPrototype.dispose(ship);
            }
            h.succeed();
        });
    }
}
