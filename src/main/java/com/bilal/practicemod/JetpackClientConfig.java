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
                    "which the flat cruise pose starts blending in while coasting out of a SURGE. Below this",
                    "the wearer rides the coast down upright.",
                    "The attitude is otherwise mode-driven, and these two speeds do not apply to it: under",
                    "power the surge is flat and the glide upright, and a coast that followed ordinary glide",
                    "thrust simply keeps standing. Only the surge's follow-through is speed-blended - holding a",
                    "direction key under glide thrust pins speed at the cap, so a speed-driven coast would flop",
                    "the body flat the instant the throttle was released.")
            .defineInRange("cruiseSpeedThreshold", 0.30, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue CRUISE_FULL_SPEED = BUILDER
            .comment("Horizontal speed, as a fraction of the server's horizontalCap (glideHorizontalCap), at",
                    "which the post-surge coast pose reaches full effect (body at maxCruisePitchDegrees). The",
                    "blend between the two speeds is smoothstepped. Both speeds are fractions of the cap rather",
                    "than absolute blocks/tick precisely so they stay reachable when the cap is retuned: an",
                    "absolute value set above the cap can never be hit, and the body would never pitch past a",
                    "fraction of maxCruisePitchDegrees. The afterburner multiplies the cap, so speeds past it",
                    "are clamped and simply pin the pose at full cruise.")
            .defineInRange("cruiseFullSpeed", 0.85, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue MAX_CRUISE_PITCH_DEGREES = BUILDER
            .comment("Forward body pitch, in degrees, at full cruise - the surge's 'Superman' attitude, and the",
                    "attitude a fast coast OUT OF A SURGE follows through in. 90 = flat/horizontal.")
            .defineInRange("maxCruisePitchDegrees", 90.0, 0.0, 90.0);

    public static final ModConfigSpec.DoubleValue HOVER_LEAN_DEGREES = BUILDER
            .comment("Body lean in degrees while hovering on thrust with little horizontal speed. Positive leans",
                    "forward, negative leans back.")
            .defineInRange("hoverLeanDegrees", 12.0, -30.0, 30.0);

    public static final ModConfigSpec.DoubleValue DIRECTIONAL_LEAN_DEGREES = BUILDER
            .comment("Maximum lean into the direction of travel, in degrees, while hovering on thrust. Ramps in",
                    "from a standstill up to cruiseSpeedThreshold, where the cruise pose takes over.")
            .defineInRange("directionalLeanDegrees", 18.0, 0.0, 60.0);

    public static final ModConfigSpec.DoubleValue CRUISE_DIRECTIONAL_BLEND = BUILDER
            .comment("How much the cruise pose follows the direction of travel. 1 = the body always leads along",
                    "the travel vector (flying sideways at speed gives a sideways superman). 0 = the cruise pitch",
                    "is locked to body-forward regardless of travel direction, matching the old behaviour.")
            .defineInRange("cruiseDirectionalBlend", 1.0, 0.0, 1.0);

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

    // --- HOVER CLIMB / DESCEND ATTITUDE ---
    //
    // All three scale with a signed -1..1 factor: the wearer's realized vertical speed normalised
    // against the server's hoverClimbRate, then smoothed on speedSmoothingTicks. So they read as one
    // continuous attitude that eases through neutral as the trim reverses, never as three poses being
    // switched between - and because the signal is measured rather than taken off the keys, it tracks
    // the altitude spring's real settling curve and applies to remote players unchanged.

    public static final ModConfigSpec.DoubleValue HOVER_CLIMB_PITCH_DEGREES = BUILDER
            .comment("Extra body pitch at full-rate hover climb or descent, in degrees, on top of",
                    "hoverLeanDegrees. Positive means a climb tucks the chest forward over the thrust while a",
                    "descent flares back upright to meet the ground - at the defaults the body travels between",
                    "about 4 and 20 degrees of forward lean across the trim range.")
            .defineInRange("hoverClimbPitchDegrees", 8.0, -45.0, 45.0);

    public static final ModConfigSpec.DoubleValue HOVER_CLIMB_LEG_DEGREES = BUILDER
            .comment("How far the legs swing at full-rate hover climb or descent, in degrees. Positive trails",
                    "them behind on the way up and brings the knees forward on the way down, which is what sells",
                    "a descent as settling onto something rather than sinking.")
            .defineInRange("hoverClimbLegDegrees", 10.0, -45.0, 45.0);

    public static final ModConfigSpec.DoubleValue HOVER_DESCEND_ARM_DEGREES = BUILDER
            .comment("Extra outward arm flare at a full-rate hover descent, in degrees, on top of",
                    "hoverArmOutDegrees. Descent only - a climb leaves the arms where the hover pose put them.")
            .defineInRange("hoverDescendArmDegrees", 8.0, 0.0, 45.0);

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

    // --- hover engine character (purely cosmetic; the impulse itself is server config) ---

    public static final ModConfigSpec.DoubleValue HOVER_HITCH_CAMERA_ROLL_DEGREES = BUILDER
            .comment("Peak first-person camera ROLL, in degrees, on each hover engine hitch, decaying over the",
                    "same 8 ticks as the impulse. Roll ONLY - the hitch never touches pitch or yaw, because",
                    "those aim the block-placement ray and an angular nudge grows with reach, while roll spins",
                    "the view around that ray and leaves it exactly where it was pointing.",
                    "Set to 0 to disable camera motion entirely; the physical hitch and its sound/particles",
                    "are unaffected.")
            .defineInRange("hoverHitchCameraRollDegrees", 1.5, 0.0, 15.0);

    public static final ModConfigSpec.BooleanValue HOVER_HITCH_FEEDBACK = BUILDER
            .comment("Whether an engine hitch plays its sputter sound and puffs smoke at the nozzles. The nozzle",
                    "twitch on the model and the impulse itself are separate - this only silences the audio and",
                    "particles, for players who want the pack to hitch without the noise.")
            .define("hoverHitchFeedback", true);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
