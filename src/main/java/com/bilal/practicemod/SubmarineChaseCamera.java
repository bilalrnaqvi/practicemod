package com.bilal.practicemod;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import java.util.LinkedHashSet;

/** Vehicle-centered, level-horizon F5 camera with perspective fitting and a terrain-safe boom. */
@EventBusSubscriber(modid = PracticeMod.MODID, value = Dist.CLIENT)
public final class SubmarineChaseCamera {
    public record View(Vec3 position, float yaw, float pitch, boolean cockpit) {}
    private static SubmarineEntity tracked;
    private static boolean mirrored, fallback;
    private static long previousNanos;
    private static double boom = Double.NaN, effectiveFov = Double.NaN;
    private static final double[][] MODEL_POINTS;
    private static final Vec3 MODEL_CENTER;
    private static final double[][] projected;
    static {
        var unique = new LinkedHashSet<Vec3>();
        for (float[] cube : DeepseekerGeometry.CUBES)
            for (int i=0; i<cube.length; i+=8) unique.add(new Vec3(cube[i],cube[i+1],cube[i+2]));
        MODEL_POINTS = new double[unique.size()][3];
        double[] lo={Double.MAX_VALUE,Double.MAX_VALUE,Double.MAX_VALUE}, hi={-Double.MAX_VALUE,-Double.MAX_VALUE,-Double.MAX_VALUE};
        int index=0;
        for (Vec3 p : unique) {
            double[] v={p.x,p.y,p.z}; MODEL_POINTS[index++]=v;
            for (int a=0;a<3;a++) {lo[a]=Math.min(lo[a],v[a]);hi[a]=Math.max(hi[a],v[a]);}
        }
        MODEL_CENTER=new Vec3((lo[0]+hi[0])/2,(lo[1]+hi[1])/2,(lo[2]+hi[2])/2);
        projected=new double[MODEL_POINTS.length][3];
    }

    private SubmarineChaseCamera() {}

    public static void reset() {
        tracked=null; previousNanos=0; boom=Double.NaN; effectiveFov=Double.NaN; fallback=false;
    }

    public static View update(Camera camera, BlockGetter level, Entity entity, boolean detached, boolean reverse, float partial) {
        if (entity.getVehicle() instanceof SubmarineEntity cabin && cabin.isParked()) {
            reset();
            if (cabin.isCabinTransition()) return new View(
                    cabin.modelToWorld(SubmarineEntryAnimation.eye(cabin.entryOrigin(), cabin.cabinAnimationTime(partial)), partial),
                    cabin.getYRot() - SubmarineEntryAnimation.heading(cabin.entryOrigin(), cabin.cabinAnimationTime(partial)), 0, true);
            return new View(entity.getEyePosition(partial), entity.getViewYRot(partial), entity.getViewXRot(partial), true);
        }
        if (entity instanceof Player boardingPlayer && boardingPlayer.isAlive()
                && boardingPlayer.getVehicle() instanceof SubmarineEntity boardingSub && boardingSub.isBoarding()) {
            reset();
            return detached ? SubmarineEntryCamera.view(level,boardingPlayer,boardingSub,partial)
                    : SubmarineEntryCamera.firstPerson(boardingSub,partial);
        }
        if (!detached || !(entity instanceof Player player) || !player.isAlive()
                || !(player.getVehicle() instanceof SubmarineEntity sub) || sub.getControllingPassenger()!=player) {
            reset(); return null;
        }
        long now=System.nanoTime();
        boolean reset=tracked!=sub || mirrored!=reverse;
        double dt=previousNanos==0 ? 0 : (now-previousNanos)*1e-9;
        if (reset) {reset(); tracked=sub; mirrored=reverse;}
        previousNanos=now;
        // Mouse look stays immediate; only boom length eases. Ship pitch and damage roll
        // affect its geometry, never the camera horizon or the user's steering inputs.
        float yaw=camera.getYRot(), pitch=Mth.clamp(camera.getXRot()+18, -35, 70);
        float hullYaw=(180-Mth.rotLerp(partial,sub.yRotO,sub.getYRot()))*Mth.DEG_TO_RAD;
        var rotation=new Matrix3f().rotationY(hullYaw)
                .rotateZ(SubmarineRenderer.visualRoll(sub,partial)*Mth.DEG_TO_RAD)
                .rotateX(-Mth.lerp(partial,sub.xRotO,sub.getXRot())*Mth.DEG_TO_RAD);
        var mc=Minecraft.getInstance();
        double fov=Double.isFinite(effectiveFov) ? effectiveFov : mc.options.fov().get();
        double aspect=(double)mc.getWindow().getWidth()/Math.max(1,mc.getWindow().getHeight());
        Vec3 center=SubmarineHeadlight.modelPosition(sub,partial,MODEL_CENTER);
        Frame frame=frame(center,rotation,yaw,pitch,fov,aspect,dt);
        // Keep the entire near plane on the submerged side of the surface. Lower the
        // orbit angle, then refit the actual mesh instead of pushing the eye into the hull.
        double ceiling=SubmarineCameraCollision.waterCeiling(level,center);
        ceiling=Math.min(ceiling,SubmarineCameraCollision.waterCeiling(level,frame.position()));
        if (frame.position().y>ceiling) {
            float low=-80, high=pitch;
            frame=frame(center,rotation,yaw,low,fov,aspect,dt);
            for (int i=0;i<10;i++) {
                float trial=(low+high)/2;
                Frame candidate=frame(center,rotation,yaw,trial,fov,aspect,dt);
                if (candidate.position().y<=ceiling) {low=trial;frame=candidate;}
                else high=trial;
            }
            pitch=low;
        }
        var fit=frame.fit();
        Vec3 anchor=frame.anchor(), forward=frame.forward();
        double desired=frame.distance();
        double safe=SubmarineCameraCollision.clearBoom(level,player,anchor,forward.scale(-1),desired);
        // A wall can shorten an underwater orbit back across the surface. Use the dry
        // cockpit in that confined case, just as when terrain would push into the hull.
        if (anchor.subtract(forward.scale(safe)).y>ceiling) safe=0;
        // Check the full desired boom for recovery, even while rendering the cockpit fallback.
        boolean wasFallback=fallback;
        fallback=SubmarineCameraRig.cockpitFallback(fallback,safe,fit.minimumDistance());
        if (fallback) {
            boom=SubmarineCameraRig.chaseDistance(fit);
            if (!wasFallback) mc.gui.setOverlayMessage(Component.translatable("message.practicemod.submarine_camera_tight"),false);
            Vec3 eye=sub.isBoarding() ? SubmarineHeadlight.modelPosition(sub,partial,sub.entryEye(partial))
                    : sub.getPosition(partial).add(0,sub.pilotEyeOffset(),0);
            return new View(eye,player.getViewYRot(partial),player.getViewXRot(partial),true);
        }
        boom=Math.min(desired,safe);
        // Keep the same collision-tested anchor when shortened; no unchecked sideways motion.
        return new View(anchor.subtract(forward.scale(boom)),yaw,pitch,false);
    }

    private record Frame(Vec3 anchor, Vec3 forward, double distance, SubmarineCameraRig.Fit fit) {
        Vec3 position() { return anchor.subtract(forward.scale(distance)); }
    }

    private static Frame frame(Vec3 center, Matrix3f rotation, float yaw, float pitch,
            double fov, double aspect, double dt) {
        double y=Math.toRadians(yaw), p=Math.toRadians(pitch);
        Vec3 forward=new Vec3(-Math.sin(y)*Math.cos(p),-Math.sin(p),Math.cos(y)*Math.cos(p));
        Vec3 right=new Vec3(-Math.cos(y),0,-Math.sin(y));
        Vec3 up=right.cross(forward);
        var v=new Vector3f();
        for (int i=0;i<MODEL_POINTS.length;i++) {
            double[] point=MODEL_POINTS[i];
            rotation.transform((float)(point[0]-MODEL_CENTER.x),(float)(point[1]-MODEL_CENTER.y),(float)(point[2]-MODEL_CENTER.z),v);
            projected[i][0]=v.x*right.x+v.y*right.y+v.z*right.z;
            projected[i][1]=v.x*up.x+v.y*up.y+v.z*up.z;
            projected[i][2]=v.x*forward.x+v.y*forward.y+v.z*forward.z;
        }
        var fit=SubmarineCameraRig.fit(projected,fov,aspect);
        double target=SubmarineCameraRig.chaseDistance(fit);
        double distance=Double.isFinite(boom) ? SubmarineCameraRig.ease(boom,target,dt) : target;
        distance=Math.max(distance,fit.minimumDistance()+2);
        return new Frame(center.add(up.scale(fit.lift(distance))),forward,distance,fit);
    }

    @SubscribeEvent
    static void chaseFog(ViewportEvent.RenderFog event) {
        Camera camera=event.getCamera();
        if (tracked==null || fallback || !camera.isDetached()
                || camera.getEntity()==null || camera.getEntity()!=tracked.getControllingPassenger()
                || camera.getEntity().getVehicle()!=tracked
                || event.getType()!=net.minecraft.world.level.material.FogType.WATER) return;
        if (camera.getEntity() instanceof net.minecraft.world.entity.LivingEntity living
                && (living.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
                || living.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS))) return;
        // The long vehicle boom must not consume the pilot's underwater visibility.
        // Retain biome water color and distant fog, but start it beyond the whole hull.
        Vec3 center=SubmarineHeadlight.modelPosition(tracked,(float)event.getPartialTick(),MODEL_CENTER);
        float clear=(float)camera.getPosition().distanceTo(center)+12;
        event.setNearPlaneDistance(Math.max(event.getNearPlaneDistance(),clear));
        event.setFarPlaneDistance(Math.max(event.getFarPlaneDistance(),clear+48));
        event.setFogShape(com.mojang.blaze3d.shaders.FogShape.SPHERE);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void fov(ViewportEvent.ComputeFov event) {
        if (tracked!=null && event.usedConfiguredFov() && event.getCamera().getEntity()==tracked.getControllingPassenger())
            effectiveFov=event.getFOV(); // Observe the final lens; never change the user's FOV setting.
    }

    @SubscribeEvent
    static void hidePilotInTightSpace(RenderPlayerEvent.Pre event) {
        if (fallback && tracked!=null && event.getEntity()==Minecraft.getInstance().getCameraEntity()
                && event.getEntity().getVehicle()==tracked) event.setCanceled(true);
    }
}
