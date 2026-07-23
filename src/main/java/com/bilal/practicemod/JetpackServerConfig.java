package com.bilal.practicemod;

import net.neoforged.neoforge.common.ModConfigSpec;

// Server-authoritative tuning for the jetpack. This is a SERVER-type config: it lives per-world
// (not per-installation) and is synced to every client on join, so all players in a session fly
// with the same numbers regardless of their own client config.
//
// Tuning notes for the fuel/heat/flight loop (at defaults): continuous hold redlines in 100/1.4 ~ 71
// ticks (~3.6s); full-stall recovery takes 60/1.3 ~ 46 ticks (~2.3s); the heat-neutral cadence is
// heatDrain/(heatGain+heatDrain) ~ 48% hold time, and at that cadence fuel is also roughly
// neutral (avg burn 0.96/t vs avg regen 0.93/t). Flight is a thrust-dominated sawtooth: at the
// heat-neutral cadence the vertical average is ~0.48*ascentCap - 0.52*sinkCap ~ +0.12 blocks/tick
// - steep climbs, brief shallow dips. Continuous thrust empties a full tank in ~25s.
public class JetpackServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue THRUST_ACCEL = BUILDER
            .comment("Upward acceleration added per tick while thrusting, in blocks/tick^2 - the punch.",
                    "Net of vanilla gravity, 0.14 reaches the ascent cap in ~0.75s; ~0.22 reaches it in ~0.3s.")
            .defineInRange("thrustAccel", 0.14, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue ASCENT_CAP = BUILDER
            .comment("Top climb speed while thrusting, in blocks/tick (hard clamp).",
                    "Invariant: must stay GREATER than sinkCap, or a hold/release rhythm can never",
                    "net upward over time.")
            .defineInRange("ascentCap", 0.80, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue HORIZONTAL_ACCEL = BUILDER
            .comment("Horizontal acceleration while thrusting, in blocks/tick^2, applied along the horizontal",
                    "component of the look direction: look at the horizon for full forward carry, straight up",
                    "for a pure vertical climb. This is the traversal.")
            .defineInRange("horizontalAccel", 0.10, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue HORIZONTAL_CAP = BUILDER
            .comment("Top horizontal speed while thrusting, in blocks/tick (hard clamp).")
            .defineInRange("horizontalCap", 0.45, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue SINK_CAP = BUILDER
            .comment("Maximum downward speed during the rest phase (worn, airborne, fuel > 0, not thrusting),",
                    "in blocks/tick. A brisk controlled drop - not a plummet, not a float.",
                    "Invariant: must stay LESS than ascentCap (see there).")
            .defineInRange("sinkCap", 0.50, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue GLIDE_HORIZONTAL_DRAG = BUILDER
            .comment("Per-tick multiplier on horizontal velocity while worn, airborne, and not thrusting.",
                    "0.86 kills momentum in well under a second (0.86^20 ~ 5%) - the rest phase covers",
                    "almost no ground. 1.0 disables it.")
            .defineInRange("glideHorizontalDrag", 0.86, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue NEGATE_FALL_DAMAGE = BUILDER
            .comment("Whether wearing the jetpack negates all fall damage.")
            .define("negateFallDamage", true);

    public static final ModConfigSpec.DoubleValue FUEL_CAPACITY = BUILDER
            .comment("Maximum fuel the jetpack's tank holds. Fuel is stored on the item and persists.")
            .defineInRange("fuelCapacity", 1000.0, 1.0, 1_000_000.0);

    public static final ModConfigSpec.DoubleValue FUEL_BURN_PER_TICK = BUILDER
            .comment("Fuel drained per tick while thrusting. At defaults a full tank is ~25s of continuous thrust.")
            .defineInRange("fuelBurnPerTick", 2.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue AFTERBURNER_FUEL_MULTIPLIER = BUILDER
            .comment("Fuel burn multiplier while thrusting in the afterburner band (optimalZoneStart..optimalZoneEnd).")
            .defineInRange("afterburnerFuelMultiplier", 1.5, 1.0, 10.0);

    public static final ModConfigSpec.DoubleValue FUEL_REGEN_PER_TICK = BUILDER
            .comment("Fuel recovered per tick while the jetpack is worn and NOT thrusting, capped at fuelCapacity.",
                    "This is what makes a disciplined burn-glide cruise roughly fuel-neutral. Set to 0 for a",
                    "strict big-tank / no-air-refuel model.")
            .defineInRange("fuelRegenPerTick", 1.8, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue FUEL_PER_BLAZE_POWDER = BUILDER
            .comment("Fuel restored per blaze powder when refueling (sneak-right-click with the jetpack in hand).")
            .defineInRange("fuelPerBlazePowder", 250.0, 1.0, 1_000_000.0);

    public static final ModConfigSpec.DoubleValue HEAT_MAX = BUILDER
            .comment("The heat scale's ceiling (the redline). Reaching it triggers the overheat stall.")
            .defineInRange("heatMax", 100.0, 1.0, 1_000_000.0);

    public static final ModConfigSpec.DoubleValue HEAT_GAIN_PER_TICK = BUILDER
            .comment("Heat added per tick while thrusting. 1.4 redlines a cold jetpack in ~3.6s of continuous hold.")
            .defineInRange("heatGainPerTick", 1.4, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue HEAT_DRAIN_PER_TICK = BUILDER
            .comment("Heat removed per tick while not thrusting. Slightly below heatGainPerTick so the",
                    "heat-neutral cadence sits just under a 50% hold/release duty cycle.")
            .defineInRange("heatDrainPerTick", 1.3, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue OPTIMAL_ZONE_START = BUILDER
            .comment("Heat level where the afterburner band begins (extra thrust, extra fuel burn).")
            .defineInRange("optimalZoneStart", 70.0, 0.0, 1_000_000.0);

    public static final ModConfigSpec.DoubleValue OPTIMAL_ZONE_END = BUILDER
            .comment("Heat level where the afterburner band ends and the critical zone begins.")
            .defineInRange("optimalZoneEnd", 90.0, 0.0, 1_000_000.0);

    public static final ModConfigSpec.DoubleValue AFTERBURNER_THRUST_MULTIPLIER = BUILDER
            .comment("Multiplier on thrust acceleration AND both speed caps while in the afterburner band.")
            .defineInRange("afterburnerThrustMultiplier", 1.25, 1.0, 10.0);

    public static final ModConfigSpec.DoubleValue OVERHEAT_RECOVERY_THRESHOLD = BUILDER
            .comment("After an overheat stall, heat must fall to this level before thrust comes back online.")
            .defineInRange("overheatRecoveryThreshold", 40.0, 0.0, 1_000_000.0);

    public static final ModConfigSpec.DoubleValue OVERHEAT_DAMAGE = BUILDER
            .comment("Damage dealt to the wearer once, at the moment the overheat stall latches. 0 disables it.")
            .defineInRange("overheatDamage", 0.0, 0.0, 100.0);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
