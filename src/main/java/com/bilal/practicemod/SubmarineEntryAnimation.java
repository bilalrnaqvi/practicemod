package com.bilal.practicemod;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Blender-authored tracks sampled against the synchronized server boarding clock. */
public final class SubmarineEntryAnimation {
    public static final int DURATION = BlenderBoardingTrack.DURATION;
    public static final int CABIN_TICK = 38;
    public static final Vec3 PILOT = new Vec3(DeepseekerPilot.X, DeepseekerPilot.Y, DeepseekerPilot.Z);
    public static final Vec3 LADDER_START = vector(0, 0);
    public static final Vec3 CHAIR_PIVOT = new Vec3(0,0,DeepseekerPilot.Z);
    private SubmarineEntryAnimation() {}
    private static float sample(float t, int channel) {
        t=Mth.clamp(t,0,DURATION); int a=(int)t, b=Math.min(a+1,DURATION);
        return Mth.lerp(t-a,BlenderBoardingTrack.FRAMES[a][channel],BlenderBoardingTrack.FRAMES[b][channel]);
    }
    private static Vec3 vector(float t,int channel) {
        return new Vec3(sample(t,channel),sample(t,channel+1),sample(t,channel+2));
    }
    public static float ease(float t,float a,float b) {
        float x=Mth.clamp((t-a)/(b-a),0,1);return x*x*x*(x*(x*6-15)+10);
    }
    public static Vec3 eye(Vec3 ignored,float t) {
        return vector(t,0).lerp(PILOT,ease(t,DURATION-6,DURATION));
    }
    public static Vec3 cameraEye(float t) { return vector(t,6).lerp(PILOT,ease(t,DURATION-6,DURATION)); }
    public static float cameraHeading(float t) { return sample(t,9); }
    public static float cameraPitch(float t) { return Mth.lerp(ease(t,DURATION-6,DURATION),sample(t,10),0); }
    public static float heading(Vec3 ignored,float t) { return Mth.lerp(ease(t,80,82),sample(t,3),chair(t)); }
    public static float hatch(float t) { return sample(t,4); }
    public static float chair(float t) { return sample(t,5); }
    public static float door(float t) { return 0; }
    public static float gauge(float t) { return 2*Mth.sin(t*Mth.TWO_PI/48); }
    public static float seated(float t) { return ease(t,76,82); }
    public static float phase(float t) { return t; }
    public static float bend(float t) { return 0; }
    public static float tuck(float t) { return 0; }
    public static float climb(float t) { return 0; }
    public static Vec3 hatchHandle(float t) {
        double a=Math.toRadians(hatch(t)), y=DeepseekerCabinRig.HANDLE_Y-DeepseekerCabinRig.HATCH_Y,
            z=DeepseekerCabinRig.HANDLE_Z-DeepseekerCabinRig.HATCH_Z;
        return new Vec3(DeepseekerCabinRig.HANDLE_X,DeepseekerCabinRig.HATCH_Y+y*Math.cos(a)-z*Math.sin(a),
            DeepseekerCabinRig.HATCH_Z+y*Math.sin(a)+z*Math.cos(a));
    }
}
