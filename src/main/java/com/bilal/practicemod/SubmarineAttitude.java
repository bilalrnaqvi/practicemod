package com.bilal.practicemod;

/** Minecraft positive X pitch looks down; authoring forward is -Z. */
public final class SubmarineAttitude {
    public static float noseUp(float minecraftPitch) { return -minecraftPitch; }
    public static float cardRoll(float vehicleRoll) { return -vehicleRoll; }
    /** In screen coordinates, nose-up moves the horizon downward. */
    public static float horizon(float minecraftPitch) { return 32 + noseUp(minecraftPitch) * 0.6F; }
    private SubmarineAttitude() {}
}
