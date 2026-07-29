package com.bilal.practicemod;

import net.neoforged.neoforge.common.ModConfigSpec;

// Server-authoritative tuning for the jetpack. This is a SERVER-type config: it lives per-world
// (not per-installation) and is synced to every client on join, so all players in a session fly
// with the same numbers regardless of their own client config.
//
// Flight model (at defaults) - two thrust modes plus a coast:
//  - GLIDE (hold jump, no boost key): thrust lifts straight up toward ascentCap, jump+sneak settles
//    into a hover near hoverDescentSpeed for fine altitude control, and horizontal travel is
//    WASD-steered relative to facing up to glideHorizontalCap. The body stays upright. This is the
//    controllable, build-and-maneuver mode.
//  - SURGE (hold jump + the boost key, OVERDRIVE profile only): the "Superman" mode. Acceleration
//    follows the look direction and is clamped by a single 3-D surgeSpeedCap, so pitching down dives
//    and pitching up climbs - but jump and sneak keep their vertical authority via surgeVerticalBias,
//    so a level camera still climbs and sneak always descends. Costs the afterburner fuel/heat
//    multipliers.
//  - COAST (release jump while airborne and still moving): the pack keeps the player flying rather
//    than dropping them. Horizontal momentum bleeds gently at coastHorizontalDrag and the descent is
//    capped at coastSinkCap, giving roughly a 1.6:1 glide ratio that decays to nothing on its own -
//    a long, controlled arc, not an elytra. Below coastMinSpeed the coast ends and the player falls
//    normally, cushioned near the ground by the "hover boots" (soft touchdown within hoverBootRange).
//
// The boost has two selectable profiles (boostProfile): OVERDRIVE, where the boost key engages the
// surge on demand; and CLASSIC, where there is no surge and the afterburner is instead the passive
// heat band (optimalZoneStart..optimalZoneEnd) you hold the throttle to reach, multiplying glide
// thrust by afterburnerThrustMultiplier. Both profiles share the afterburner fuel/heat multipliers.
//
// Timing (at defaults): continuous hold redlines in heatMax/heatGain = 200/0.9 ~ 222 ticks (~11.1s),
// or ~111 ticks (~5.6s) riding the surge; a full stall recovers over (heatMax-recovery)/heatDrain =
// 120/1.3 ~ 92 ticks (~4.6s) before thrust comes back. Heat, not fuel, is what ends a burn: a full
// tank (1500) is ~37.5s of continuous glide thrust, ~25s of surge - several redlines' worth.
public class JetpackServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Selects how the boost is engaged. Ship both so the feel can be A/B'd in-game.
    public enum BoostProfile {
        OVERDRIVE, // the look-directed surge, spent on demand (hold the boost key while thrusting)
        CLASSIC    // no surge; the afterburner is the passive heat band (optimalZoneStart..optimalZoneEnd)
    }

    public static final ModConfigSpec.EnumValue<BoostProfile> BOOST_PROFILE = BUILDER
            .comment("Which boost model to fly with:",
                    "  OVERDRIVE - holding the boost key (the sprint key) while thrusting engages the SURGE:",
                    "              look-directed 'Superman' flight at surgeAccel/surgeSpeedCap, flat body pose,",
                    "              and the afterburner fuel/heat cost. You decide when the jetpack hits hard.",
                    "  CLASSIC   - no surge; the boost key does nothing and the afterburner is the passive heat",
                    "              band (optimalZoneStart..optimalZoneEnd), which multiplies glide thrust by",
                    "              afterburnerThrustMultiplier. Hold the throttle to reach the sweet spot.")
            .defineEnum("boostProfile", BoostProfile.OVERDRIVE);

    public static final ModConfigSpec.DoubleValue THRUST_ACCEL = BUILDER
            .comment("Upward acceleration added per tick while thrusting, in blocks/tick^2 - the punch.",
                    "Only the part ABOVE vanilla gravity (~0.08/tick) is net lift, so this is far more",
                    "sensitive than it looks: 0.105 reaches the ascent cap in ~0.86s, but 0.09 - barely",
                    "lower - takes ~2.4s. Keep it well clear of 0.08 or the climb turns to mush.")
            .defineInRange("thrustAccel", 0.105, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue ASCENT_CAP = BUILDER
            .comment("Top climb speed while thrusting, in blocks/tick (hard clamp).",
                    "Invariant: must stay GREATER than coastSinkCap, or a hold/release rhythm can never",
                    "net upward over time.")
            .defineInRange("ascentCap", 0.39, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue HOVER_DESCENT_SPEED = BUILDER
            .comment("Downward speed the jetpack settles to while thrusting with sneak held (jump+sneak = hover),",
                    "in blocks/tick. A gentle sink for fine altitude control - landings and building. 0 = a",
                    "perfectly stationary hover.")
            .defineInRange("hoverDescentSpeed", 0.06, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue GLIDE_HORIZONTAL_ACCEL = BUILDER
            .comment("Horizontal acceleration in GLIDE mode (thrusting without the boost key), in blocks/tick^2,",
                    "applied along the WASD input rotated by the player's facing (not look pitch). Deliberately",
                    "gentler than the surge: this is the precise, upright maneuvering mode.")
            .defineInRange("glideHorizontalAccel", 0.046, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue GLIDE_HORIZONTAL_CAP = BUILDER
            .comment("Top horizontal speed in GLIDE mode, in blocks/tick (hard clamp). Keep it well below",
                    "surgeSpeedCap so the surge is unambiguously the fast mode.")
            .defineInRange("glideHorizontalCap", 0.36, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue SURGE_ACCEL = BUILDER
            .comment("Acceleration in SURGE mode, in blocks/tick^2, applied along the look direction (blended",
                    "with jump/sneak by surgeVerticalBias). The surge cancels gravity separately, so this is",
                    "pure steering authority - the whole value goes into moving where you are pointed.")
            .defineInRange("surgeAccel", 0.117, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue SURGE_SPEED_CAP = BUILDER
            .comment("Top speed in SURGE mode, in blocks/tick, as a single 3-D clamp on the whole velocity",
                    "vector (not split horizontal/vertical) - the surge's whole point is that velocity follows",
                    "the look vector, so dives, climbs and level runs all top out at the same speed.")
            .defineInRange("surgeSpeedCap", 0.72, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue SURGE_VERTICAL_BIAS = BUILDER
            .comment("How much vertical authority jump and sneak keep in SURGE mode, blended into the look",
                    "direction before it is normalized. This is what lets a level camera still climb on jump",
                    "(and sneak still descend) instead of forcing the player to aim the mouse up. At 0.30 you",
                    "must pitch down past ~17 degrees to overcome the climb and start diving. 0 = pure look",
                    "steering (aim up to go up); 1 = vertical is almost entirely jump/sneak.")
            .defineInRange("surgeVerticalBias", 0.30, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue COAST_HORIZONTAL_DRAG = BUILDER
            .comment("Per-tick horizontal velocity multiplier while COASTING (airborne, throttle released, still",
                    "moving). This is what preserves the flight trajectory after a thrust cut instead of braking",
                    "to a stop: 0.98 halves speed in ~34 ticks (~1.7s), so a full-speed surge coasts ~31 blocks",
                    "over ~4.9s. Lower brakes harder; 1.0 would coast forever.")
            .defineInRange("coastHorizontalDrag", 0.98, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue COAST_SINK_CAP = BUILDER
            .comment("Maximum downward speed while COASTING, in blocks/tick - the 'slight fall' of an unpowered",
                    "glide, rather than the full gravity drop. Clamps downward only, so releasing thrust mid-climb",
                    "still carries the upward momentum first. At 0.23 a full coast loses at most ~23 blocks of",
                    "altitude, about a 1.6:1 glide ratio. Scale this with the speed caps: it is the other half",
                    "of the ascentCap invariant above, and it sets the glide ratio against glideHorizontalCap.")
            .defineInRange("coastSinkCap", 0.23, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue COAST_MIN_SPEED = BUILDER
            .comment("Horizontal speed, in blocks/tick, below which the coast ends and the player falls normally",
                    "(momentum bleeds off fast and the flight pose settles). Keeps a coast from lingering as a",
                    "slow float once the speed it was trading on is gone.")
            .defineInRange("coastMinSpeed", 0.10, 0.0, 10.0);

    public static final ModConfigSpec.BooleanValue HOVER_BOOT_ENABLED = BUILDER
            .comment("Whether the jetpack cushions a descent near the ground (the 'hover boots' landing assist).")
            .define("hoverBootEnabled", true);

    public static final ModConfigSpec.DoubleValue HOVER_BOOT_RANGE = BUILDER
            .comment("How close to the ground (in blocks, measured straight down) the hover-boot cushion begins",
                    "easing a descent. Outside this range the player falls normally.")
            .defineInRange("hoverBootRange", 3.0, 0.0, 64.0);

    public static final ModConfigSpec.DoubleValue HOVER_BOOT_ENTRY_DESCENT = BUILDER
            .comment("Maximum downward speed (blocks/tick) the cushion allows right at hoverBootRange - near a",
                    "normal fall, so entering the cushion is barely noticeable. Eased down toward hoverBootMinDescent",
                    "as the player nears the ground.")
            .defineInRange("hoverBootEntryDescent", 0.5, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue HOVER_BOOT_MIN_DESCENT = BUILDER
            .comment("Downward speed (blocks/tick) the cushion settles to just above the ground - the soft",
                    "touchdown speed. 0 = a dead stop on contact.")
            .defineInRange("hoverBootMinDescent", 0.05, 0.0, 10.0);

    public static final ModConfigSpec.BooleanValue NEGATE_FALL_DAMAGE = BUILDER
            .comment("Whether wearing the jetpack negates all fall damage.")
            .define("negateFallDamage", true);

    public static final ModConfigSpec.DoubleValue FUEL_CAPACITY = BUILDER
            .comment("Maximum fuel the jetpack's tank holds. Fuel is stored on the item and persists.",
                    "At defaults a full tank is ~37.5s of continuous glide thrust (~25s of surge) - deliberately",
                    "several redlines' worth, since heat is what ends any single burn. This is the session-long",
                    "budget, not the per-burn one.")
            .defineInRange("fuelCapacity", 1500.0, 1.0, 1_000_000.0);

    public static final ModConfigSpec.DoubleValue FUEL_BURN_PER_TICK = BUILDER
            .comment("Fuel drained per tick while thrusting. At defaults a full tank is ~37.5s of continuous thrust.")
            .defineInRange("fuelBurnPerTick", 2.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue AFTERBURNER_FUEL_MULTIPLIER = BUILDER
            .comment("Fuel burn multiplier while boosting - the OVERDRIVE surge, or the CLASSIC heat band",
                    "(optimalZoneStart..optimalZoneEnd). The running cost of the fast mode.")
            .defineInRange("afterburnerFuelMultiplier", 1.5, 1.0, 10.0);

    public static final ModConfigSpec.DoubleValue FUEL_REGEN_PER_TICK = BUILDER
            .comment("Fuel recovered per tick while the jetpack is worn and NOT thrusting, capped at fuelCapacity.",
                    "This is what makes a disciplined burn-glide cruise roughly fuel-neutral. Keep it in step",
                    "with the heat cadence: fuel breaks even at regen/(burn+regen) hold, so 2.6 against a 2.0",
                    "burn is neutral at ~57% hold, just under the ~59% the heat rates allow. Let it fall behind",
                    "and fuel silently becomes the binding limit instead of heat. Set to 0 for a strict",
                    "big-tank / no-air-refuel model.")
            .defineInRange("fuelRegenPerTick", 2.6, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue FUEL_PER_BLAZE_POWDER = BUILDER
            .comment("Fuel restored per blaze powder when refueling (sneak-right-click with the jetpack in hand).",
                    "Scale with fuelCapacity to hold the refuel cost steady: at 375 a full tank is 4 powders.")
            .defineInRange("fuelPerBlazePowder", 375.0, 1.0, 1_000_000.0);

    public static final ModConfigSpec.DoubleValue HEAT_MAX = BUILDER
            .comment("The heat scale's ceiling (the redline). Reaching it triggers the overheat stall.",
                    "At 200 a cold jetpack redlines in ~11.1s of continuous hold - long enough to build and",
                    "hold real horizontal speed before a stall drops you into a glide. Prefer retuning burn",
                    "length via heatGainPerTick and leaving this at 200: the zone boundaries, the recovery",
                    "threshold and the HUD are all read against this scale.")
            .defineInRange("heatMax", 200.0, 1.0, 1_000_000.0);

    public static final ModConfigSpec.DoubleValue HEAT_GAIN_PER_TICK = BUILDER
            .comment("Heat added per tick while thrusting. This - not fuel - is what ends a burn, so it is the",
                    "dial for how long a player can hold thrust. 0.90 redlines a cold jetpack in ~11.1s of",
                    "continuous hold, or ~5.6s riding the surge (overdriveHeatMultiplier doubles the rate).")
            .defineInRange("heatGainPerTick", 0.90, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue HEAT_DRAIN_PER_TICK = BUILDER
            .comment("Heat removed per tick while not thrusting. Above heatGainPerTick, so the heat-neutral",
                    "cadence - drain/(gain+drain) - sits at ~59% hold: a player easing off between burns can",
                    "stay airborne indefinitely. Also sets the post-stall recovery time, which is why it stays",
                    "high rather than dropping alongside the gain rate.")
            .defineInRange("heatDrainPerTick", 1.3, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue OPTIMAL_ZONE_START = BUILDER
            .comment("Heat level where the afterburner band begins (extra thrust, extra fuel burn).")
            .defineInRange("optimalZoneStart", 140.0, 0.0, 1_000_000.0);

    public static final ModConfigSpec.DoubleValue OPTIMAL_ZONE_END = BUILDER
            .comment("Heat level where the afterburner band ends and the critical zone begins.")
            .defineInRange("optimalZoneEnd", 180.0, 0.0, 1_000_000.0);

    public static final ModConfigSpec.DoubleValue AFTERBURNER_THRUST_MULTIPLIER = BUILDER
            .comment("Multiplier on glide thrust acceleration AND both glide caps while the CLASSIC heat-band",
                    "afterburner is active. CLASSIC only - the OVERDRIVE profile boosts by switching to the",
                    "surge (surgeAccel/surgeSpeedCap) instead of scaling the glide numbers.")
            .defineInRange("afterburnerThrustMultiplier", 1.25, 1.0, 10.0);

    public static final ModConfigSpec.DoubleValue OVERDRIVE_HEAT_MULTIPLIER = BUILDER
            .comment("Heat-gain multiplier while the surge is active, so leaning on the boost redlines faster -",
                    "the cost of spending the surge. Only applies to the OVERDRIVE profile.")
            .defineInRange("overdriveHeatMultiplier", 2.0, 1.0, 100.0);

    public static final ModConfigSpec.DoubleValue OVERHEAT_RECOVERY_THRESHOLD = BUILDER
            .comment("After an overheat stall, heat must fall to this level before thrust comes back online.")
            .defineInRange("overheatRecoveryThreshold", 80.0, 0.0, 1_000_000.0);

    public static final ModConfigSpec.DoubleValue OVERHEAT_DAMAGE = BUILDER
            .comment("Damage dealt to the wearer once, at the moment the overheat stall latches. 0 disables it.")
            .defineInRange("overheatDamage", 0.0, 0.0, 100.0);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
