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
            .comment("Horizontal speed, as a fraction of the server's horizontalCap (glideHorizontalCap), at",
                    "which the flat cruise pose starts blending in while UNPOWERED (coasting, or following",
                    "through after a thrust cut). Below this the wearer rides the coast down upright. Under",
                    "power the pose is mode-driven instead - surge is flat, glide is upright - so these two",
                    "speeds do not apply.")
            .defineInRange("cruiseSpeedThreshold", 0.30, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue CRUISE_FULL_SPEED = BUILDER
            .comment("Horizontal speed, as a fraction of the server's horizontalCap (glideHorizontalCap), at",
                    "which the unpowered cruise pose reaches full effect (body at maxCruisePitchDegrees). The",
                    "blend between the two speeds is smoothstepped. Both speeds are fractions of the cap rather",
                    "than absolute blocks/tick precisely so they stay reachable when the cap is retuned: an",
                    "absolute value set above the cap can never be hit, and the body would never pitch past a",
                    "fraction of maxCruisePitchDegrees. The afterburner multiplies the cap, so speeds past it",
                    "are clamped and simply pin the pose at full cruise.")
            .defineInRange("cruiseFullSpeed", 0.85, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue MAX_CRUISE_PITCH_DEGREES = BUILDER
            .comment("Forward body pitch, in degrees, at full cruise - the surge's 'Superman' attitude, and the",
                    "attitude a fast coast follows through in. 90 = flat/horizontal.")
            .defineInRange("maxCruisePitchDegrees", 90.0, 0.0, 90.0);

    public static final ModConfigSpec.DoubleValue HOVER_LEAN_DEGREES = BUILDER
            .comment("Body lean in degrees while hovering on thrust with little horizontal speed. Positive leans",
                    "forward, negative leans back.")
            .defineInRange("hoverLeanDegrees", 12.0, -30.0, 30.0);

    public static final ModConfigSpec.DoubleValue BLEND_IN_TICKS = BUILDER
            .comment("Ticks for the flight pose to blend in when thrust fires.")
            .defineInRange("blendInTicks", 6.0, 1.0, 100.0);

    public static final ModConfigSpec.DoubleValue BLEND_OUT_TICKS = BUILDER
            .comment("Ticks for the body to settle back upright once flight ends - which is when the coast has",
                    "decayed, not when thrust cuts, so the pose holds through the whole glide down. An",
                    "out-of-fuel stall with no momentum left ignores this and drops the pose on the harsh",
                    "3-tick stall cut.")
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
            .comment("Arm angle in the cruise pose. Negative extends the arms forward past the head (superman);",
                    "positive sweeps them back along the sides (dive). The default is a small positive sweep, so",
                    "the arms stay relaxed against the body as speed builds - the superman reach is deliberately",
                    "not the shipped look.")
            .defineInRange("cruiseArmSweepDegrees", 20.0, -180.0, 80.0);

    public static final ModConfigSpec.DoubleValue CRUISE_LEG_TRAIL_DEGREES = BUILDER
            .comment("How far the legs trail behind the body in the cruise pose, in degrees.")
            .defineInRange("cruiseLegTrailDegrees", 8.0, 0.0, 45.0);

    public static final ModConfigSpec.DoubleValue HEAD_COUNTER_MAX_DEGREES = BUILDER
            .comment("Neck-bend limit, in degrees, on the head counter-rotation that keeps the wearer's gaze on",
                    "the camera while the body is pitched.")
            .defineInRange("headCounterMaxDegrees", 60.0, 0.0, 90.0);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
