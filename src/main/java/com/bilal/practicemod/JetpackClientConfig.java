package com.bilal.practicemod;

import net.neoforged.neoforge.common.ModConfigSpec;

// Client-side tuning for the jetpack flight body poses. This is a CLIENT-type config: it is purely
// visual and per-installation, so every player can tune (or disable) the pose layer for themselves
// without affecting flight physics or anyone else's view.
public class JetpackClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_FLIGHT_POSES = BUILDER
            .comment("Whether the player's body is posed (hover lean / cruise pitch) during jetpack flight.")
            .define("enableFlightPoses", true);

    public static final ModConfigSpec.DoubleValue CRUISE_SPEED_THRESHOLD = BUILDER
            .comment("Horizontal speed, in blocks/tick, at which the cruise pose starts blending in over the",
                    "hover pose. Below this the wearer holds the upright hover lean.")
            .defineInRange("cruiseSpeedThreshold", 0.25, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue CRUISE_FULL_SPEED = BUILDER
            .comment("Horizontal speed, in blocks/tick, at which the cruise pose reaches full effect (body at",
                    "maxCruisePitchDegrees). The blend between the two speeds is smoothstepped.")
            .defineInRange("cruiseFullSpeed", 0.75, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue MAX_CRUISE_PITCH_DEGREES = BUILDER
            .comment("Forward body pitch, in degrees, at full cruise speed (90 = flat/horizontal).")
            .defineInRange("maxCruisePitchDegrees", 75.0, 0.0, 90.0);

    public static final ModConfigSpec.DoubleValue HOVER_LEAN_DEGREES = BUILDER
            .comment("Backward body lean, in degrees, while hovering on thrust with little horizontal speed.")
            .defineInRange("hoverLeanDegrees", 8.0, 0.0, 30.0);

    public static final ModConfigSpec.DoubleValue BLEND_IN_TICKS = BUILDER
            .comment("Ticks for the flight pose to blend in when thrust fires.")
            .defineInRange("blendInTicks", 6.0, 1.0, 100.0);

    public static final ModConfigSpec.DoubleValue BLEND_OUT_TICKS = BUILDER
            .comment("Ticks for the body to settle back upright after thrust cuts (the follow-through).",
                    "An out-of-fuel stall ignores this and drops the pose on the harsh 3-tick stall cut.")
            .defineInRange("blendOutTicks", 14.0, 1.0, 100.0);

    public static final ModConfigSpec.DoubleValue SPEED_SMOOTHING_TICKS = BUILDER
            .comment("Time constant, in ticks, of the exponential smoothing applied to horizontal speed before",
                    "it drives the hover-to-cruise blend. Higher = lazier response to speed changes.")
            .defineInRange("speedSmoothingTicks", 4.0, 1.0, 40.0);

    public static final ModConfigSpec.DoubleValue HOVER_ARM_OUT_DEGREES = BUILDER
            .comment("How far the arms angle outward from the body in the hover pose, in degrees.")
            .defineInRange("hoverArmOutDegrees", 14.0, 0.0, 45.0);

    public static final ModConfigSpec.DoubleValue HOVER_ARM_BACK_DEGREES = BUILDER
            .comment("How far the arms angle backward in the hover pose, in degrees.")
            .defineInRange("hoverArmBackDegrees", 6.0, 0.0, 45.0);

    public static final ModConfigSpec.DoubleValue HOVER_LEG_LOOSE_DEGREES = BUILDER
            .comment("Asymmetric loose-leg splay in the hover pose, in degrees.")
            .defineInRange("hoverLegLooseDegrees", 7.0, 0.0, 45.0);

    public static final ModConfigSpec.DoubleValue CRUISE_ARM_SWEEP_DEGREES = BUILDER
            .comment("How far the arms sweep back along the sides in the cruise pose, in degrees.")
            .defineInRange("cruiseArmSweepDegrees", 35.0, 0.0, 80.0);

    public static final ModConfigSpec.DoubleValue CRUISE_LEG_TRAIL_DEGREES = BUILDER
            .comment("How far the legs trail behind the body in the cruise pose, in degrees.")
            .defineInRange("cruiseLegTrailDegrees", 8.0, 0.0, 45.0);

    public static final ModConfigSpec.DoubleValue HEAD_COUNTER_MAX_DEGREES = BUILDER
            .comment("Neck-bend limit, in degrees, on the head counter-rotation that keeps the wearer's gaze on",
                    "the camera while the body is pitched.")
            .defineInRange("headCounterMaxDegrees", 60.0, 0.0, 90.0);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
