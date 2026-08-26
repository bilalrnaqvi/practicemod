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
//  - HOVER (the H toggle, airborne): the build-mode replacement for creative flight, and the one mode
//    that takes precedence over every other. A soft altitude spring holds the player's height while
//    WASD moves them horizontally at the gentler hover accel/cap; jump and sneak become slow altitude
//    trim rather than throttle. Cheap on fuel and actively COOLING on heat, so it can be held for a
//    whole building session. See the HOVER MODE block below.
//  - COAST (release jump while airborne and still moving): the pack keeps the player flying rather
//    than dropping them. Horizontal momentum bleeds very gently at coastHorizontalDrag and the descent
//    is capped at coastSinkCap, so speed built under power SURVIVES the gap between burns - which is
//    what makes the pack viable for travel: boost, run out, keep carrying, cool down, boost again.
//    Below coastMinSpeed the coast ends and the player falls normally, cushioned near the ground by
//    the "hover boots" (soft touchdown within hoverBootRange).
//
// Speed carried into a burn is never thrown away in one tick: glideHorizontalCap is a SOFT ceiling,
// so momentum above it bleeds off at overspeedBleed instead of being clamped down instantly. See
// glideHorizontalCap and overspeedBleed.
//
// The boost has two selectable profiles (boostProfile): OVERDRIVE, where the boost key engages the
// surge on demand; and CLASSIC, where there is no surge and the afterburner is instead the passive
// heat band (optimalZoneStart..optimalZoneEnd) you hold the throttle to reach, multiplying glide
// thrust by afterburnerThrustMultiplier. OVERDRIVE uses the explicit surge heat multiplier; both
// profiles use the afterburner fuel multiplier when their fast mode is active.
//
// Timing (at defaults): ordinary thrust produces heat with or without sprint. Non-sprint thrust
// enters the final-7% red zone in roughly 16.75 seconds; sprint surge applies the 2x heat multiplier
// and enters it in roughly 8.4 seconds. At the discrete crossing value, cooling to the default
// recovery threshold takes 44 ticks (2.2s). These are tuning targets rather than special hard-coded
// deadlines.
public class JetpackServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // The final 7% of the boost meter is the visible red zone. Crossing into it triggers the
    // overheat lockout; this is a fixed readability rule rather than a server tuning variable.
    public static final float OVERHEAT_ZONE_START_FRACTION = 0.93F;

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
            .defineInRange("ascentCap", 0.429, 0.0, 10.0);

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
            .comment("Top horizontal speed in GLIDE mode, in blocks/tick. Keep it well below surgeSpeedCap so",
                    "the surge is unambiguously the fast mode.",
                    "This is a SOFT ceiling, not a hard clamp: it bounds what glide thrust can ACCELERATE to,",
                    "but speed already carried in from a surge or a coast is not discarded to reach it - the",
                    "excess bleeds down at overspeedBleed instead. Without that, releasing the boost key while",
                    "still holding jump would throw away half the surge's speed in a single tick.")
            .defineInRange("glideHorizontalCap", 0.396, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue OVERSPEED_BLEED = BUILDER
            .comment("Per-tick multiplier on horizontal speed that is ABOVE glideHorizontalCap while thrusting -",
                    "how fast carried momentum washes out once the surge that earned it ends. At 0.985 a full",
                    "surge (0.9504) settles to the glide cap in ~58 ticks (~2.9s). Deliberately firmer than",
                    "coastHorizontalDrag: an UNPOWERED coast is meant to preserve momentum, while glide thrust",
                    "is the pack asserting its own speed regime. 1.0 would let surge speed be held forever on",
                    "glide thrust; at or below the cap this value has no effect at all.")
            .defineInRange("overspeedBleed", 0.985, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue SURGE_ACCEL = BUILDER
            .comment("Acceleration in SURGE mode, in blocks/tick^2, applied along the look direction (blended",
                    "with jump/sneak by surgeVerticalBias). The surge cancels gravity separately, so this is",
                    "pure steering authority - the whole value goes into moving where you are pointed.")
            .defineInRange("surgeAccel", 0.1404, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue SURGE_SPEED_CAP = BUILDER
            .comment("Top speed in SURGE mode, in blocks/tick, as a single 3-D clamp on the whole velocity",
                    "vector (not split horizontal/vertical) - the surge's whole point is that velocity follows",
                    "the look vector, so dives, climbs and level runs all top out at the same speed.")
            .defineInRange("surgeSpeedCap", 0.9504, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue SURGE_VERTICAL_BIAS = BUILDER
            .comment("How much vertical authority jump and sneak keep in SURGE mode, blended into the look",
                    "direction before it is normalized. This is what lets a level camera still climb on jump",
                    "(and sneak still descend) instead of forcing the player to aim the mouse up. At 0.30 you",
                    "must pitch down past ~17 degrees to overcome the climb and start diving. 0 = pure look",
                    "steering (aim up to go up); 1 = vertical is almost entirely jump/sneak.")
            .defineInRange("surgeVerticalBias", 0.30, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue COAST_HORIZONTAL_DRAG = BUILDER
            .comment("Per-tick horizontal velocity multiplier while COASTING (airborne, throttle released, still",
                    "moving). This preserves the trajectory when the player releases either boost or thrust,",
                    "so ordinary flight builds on the speed a surge already earned instead of restarting.",
                    "0.995 halves speed in ~138 ticks (~6.9s). Left uninterrupted a coast runs ~110 blocks over",
                    "~18.7s before hitting coastMinSpeed, but sheds ~86 blocks of altitude doing it at",
                    "coastSinkCap - so in practice terrain ends the coast well before the speed floor does.",
                    "Lower brakes harder; 1.0 would coast forever.")
            .defineInRange("coastHorizontalDrag", 0.995, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue COAST_SINK_CAP = BUILDER
            .comment("Maximum downward speed while COASTING, in blocks/tick - the 'slight fall' of an unpowered",
                    "glide, rather than the full gravity drop. Clamps downward only, so releasing thrust mid-climb",
                    "still carries the upward momentum first. At 0.23 against glideHorizontalCap that is about a",
                    "1.7:1 glide ratio, steeper than 4.1:1 straight out of a surge. This is also what bounds how",
                    "long a coast can actually last: at coastHorizontalDrag 0.995 the horizontal decay alone",
                    "would run ~18.7s, but the sink spends ~86 blocks of altitude over that time, so altitude -",
                    "not drag - is normally what ends the coast. Scale this with the speed caps: it is the other",
                    "half of the ascentCap invariant above.")
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

    // --- HOVER MODE (the H toggle) ---
    //
    // A soft altitude spring, not a velocity clamp. Every tick the controller measures the error
    // between the player's Y and a stored target Y and commands
    //     newVy = vy + hoverAltitudeGain * (targetY - y) - hoverDampingGain * vy
    // where vy is the vertical distance the player ACTUALLY moved last tick. Because the sim runs at
    // PlayerTickEvent.Post - after vanilla has already moved the player and rewritten deltaMovement -
    // overwriting deltaMovement.y here means the next tick moves by exactly this value, so gravity
    // never enters the loop and no gravity-cancelling term is needed (unlike the surge, which
    // accumulates into Y instead of overwriting it).
    //
    // The spring being soft is load-bearing, not a stylistic choice: the engine-character hum and the
    // client-side hitches are velocity nudges, and a hard clamp would erase them the same tick they
    // fire. The spring instead absorbs them and returns the player to exactly the same altitude, which
    // is what makes the flavor free of gameplay cost.

    public static final ModConfigSpec.DoubleValue HOVER_ALTITUDE_GAIN = BUILDER
            .comment("Proportional gain of the hover altitude spring: how hard a block of altitude error",
                    "pulls, in blocks/tick^2 per block. Tuned as a PAIR with hoverDampingGain - at the",
                    "defaults a 0.3-block displacement settles in ~10 ticks with ~13% overshoot, which reads",
                    "as a visible bob rather than a bounce. Raising this alone makes the pack oscillate;",
                    "raise hoverDampingGain with it.")
            .defineInRange("hoverAltitudeGain", 0.22, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue HOVER_DAMPING_GAIN = BUILDER
            .comment("Derivative gain of the hover altitude spring: the fraction of current vertical speed",
                    "bled off per tick. 0 = undamped (the pack oscillates forever); 1 = deadbeat, which kills",
                    "the hum and the hitches along with the overshoot. See hoverAltitudeGain for the pairing.")
            .defineInRange("hoverDampingGain", 0.45, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue HOVER_VERTICAL_CAP = BUILDER
            .comment("Symmetric clamp on the hover spring's commanded vertical speed, in blocks/tick - the",
                    "backstop that keeps a large error (a ceiling shove, a big fall onto the spring) from",
                    "becoming a launch. Invariant: keep it comfortably ABOVE hoverClimbRate and",
                    "hoverDescendRate, or the spring cannot track its own trim and jump/sneak stall out.")
            .defineInRange("hoverVerticalCap", 0.25, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue HOVER_TARGET_LEASH = BUILDER
            .comment("How far, in blocks, the hover target altitude may sit from the player's ACTUAL altitude.",
                    "Re-clamped every tick, and it is what makes hovering into a ceiling safe: without it the",
                    "controller banks up unreachable error while the player is pinned, then spends it all at",
                    "once as a launch the moment they clear the obstruction. Also bounds how far a single",
                    "held-jump trim can run ahead of the body.")
            .defineInRange("hoverTargetLeash", 2.0, 0.0, 64.0);

    public static final ModConfigSpec.DoubleValue HOVER_CLIMB_RATE = BUILDER
            .comment("How fast holding jump raises the hover target altitude, in blocks/tick (~2.8 blocks/s at",
                    "the default). This is altitude TRIM for building - brisk enough not to feel like a chore,",
                    "still far slower than thrust's ascentCap, because hover is the precision mode rather than",
                    "a way to gain height. Neither jump nor sneak held: the target holds where it is.",
                    "Also the reference rate the climb/descend body pitch normalises against (see the client",
                    "config's hoverClimbPitchDegrees).")
            .defineInRange("hoverClimbRate", 0.14, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue HOVER_DESCEND_RATE = BUILDER
            .comment("How fast holding sneak lowers the hover target altitude, in blocks/tick. The mirror of",
                    "hoverClimbRate; keep the two equal unless you deliberately want asymmetric trim.")
            .defineInRange("hoverDescendRate", 0.14, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue HOVER_HORIZONTAL_ACCEL = BUILDER
            .comment("Horizontal acceleration while hovering, in blocks/tick^2, along the WASD input rotated by",
                    "the player's facing. Held at 85% of glideHorizontalAccel (see hoverHorizontalCap for why",
                    "the pair is scaled together), which reaches the hover cap in ~8 ticks - responsive enough",
                    "to nudge a block sideways without feeling floaty. Note vanilla's own air control adds",
                    "another ~0.02/tick of impulse on top of this, so the realised figure is a little brisker",
                    "than the number alone suggests.")
            .defineInRange("hoverHorizontalAccel", 0.0391, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue HOVER_HORIZONTAL_CAP = BUILDER
            .comment("Top horizontal speed while hovering, in blocks/tick (hard clamp). Kept at 0.306 while",
                    "regular thrust was sped up, so hover remains the steadier block-placement mode. The wider",
                    "speed gap plus the much firmer hoverStopDrag distinguishes it from travel thrust.",
                    "Invariant: must stay BELOW glideHorizontalCap, so thrust traversal is still unambiguously",
                    "the faster way to cross a distance. Also the reference speed the hitch rate scales against",
                    "(see hoverHitchChancePerTick).")
            .defineInRange("hoverHorizontalCap", 0.306, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue HOVER_STOP_DRAG = BUILDER
            .comment("Per-tick horizontal velocity multiplier while hovering with no WASD input - a hard brake,",
                    "much firmer than the thrust-mode drag, so releasing the keys parks the player on the block",
                    "they were aiming at instead of drifting past it (~0.66 blocks of coast from the cap at the",
                    "defaults, against ~1.3 under thrust). With accel and top speed now close to thrust's, this",
                    "is what carries most of hover's building-mode character on its own.",
                    "This is also the horizontal counterpart to the altitude spring: it is what absorbs a",
                    "hitch's sideways kick, which has no spring of its own to pull it back.")
            .defineInRange("hoverStopDrag", 0.75, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue HOVER_FUEL_BURN_PER_TICK = BUILDER
            .comment("Fuel drained per tick while hovering. Far cheaper than thrust (2.0/tick), because hover is",
                    "meant to be held for a whole building session rather than pulsed: at the default 1500-unit",
                    "tank this is ~3.1 minutes of continuous hovering, and the idle regen tops the tank back up",
                    "between stints.")
            .defineInRange("hoverFuelBurnPerTick", 0.4, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue HOVER_HEAT_CEILING = BUILDER
            .comment("In the optional CLASSIC profile, the heat level hovering settles at. CLASSIC hover does",
                    "not use heatGainPerTick: heat eases toward",
                    "this value from EITHER direction, so entering hover hot actively COOLS the pack and hover",
                    "can never stall a builder mid-session.",
                    "Invariant: must sit below optimalZoneStart (140), so parking in hover never drifts into",
                    "the afterburner band and can never reach the redline. OVERDRIVE hover instead cools to 0.")
            .defineInRange("hoverHeatCeiling", 35.0, 0.0, 1_000_000.0);

    public static final ModConfigSpec.DoubleValue HOVER_HEAT_RATE = BUILDER
            .comment("In the optional CLASSIC profile, how fast heat eases toward hoverHeatCeiling while",
                    "hovering, in heat/tick, in whichever direction it needs to move. OVERDRIVE hover uses",
                    "heatDrainPerTick instead so the boost meter rapidly returns to cold.")
            .defineInRange("hoverHeatRate", 0.5, 0.0, 100.0);

    // --- hover engine character ---
    // Flavor, never difficulty. Both channels below are bounded so that they move the player's
    // POSITION only, never the look direction - a translated pick ray hits a distant surface offset by
    // exactly the eye's displacement, whereas an angular nudge scales with reach. That is the whole
    // reason these are safe to run while someone is placing blocks.

    public static final ModConfigSpec.DoubleValue HOVER_JITTER_AMPLITUDE = BUILDER
            .comment("Peak vertical velocity, in blocks/tick, of the continuous hover hum - three sine terms of",
                    "the world game time at incommensurate frequencies, so it never settles into an audible",
                    "rhythm and both sides compute the identical value from the clock with nothing to sync.",
                    "Against the default spring gains this works out to at most ~0.06 blocks of actual eye",
                    "movement (about one pixel of a block face), which is why it cannot move a block placement",
                    "off its grid cell. 0 disables the hum.")
            .defineInRange("hoverJitterAmplitude", 0.015, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue HOVER_HITCH_CHANCE_PER_TICK = BUILDER
            .comment("Per-tick probability of an engine hitch at FULL hover speed, before the speed scaling.",
                    "A constant probability, so the intervals are memoryless and never read as a rhythm: 0.01",
                    "averages one hitch every ~5 seconds while moving at hoverHorizontalCap. The probability is",
                    "scaled linearly by horizontal speed as a fraction of that cap, so a player parked to place",
                    "a block feels the pack go quiet - see JetpackHoverHitch for why that is the safety",
                    "property, not just a flourish.")
            .defineInRange("hoverHitchChancePerTick", 0.01, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue HOVER_HITCH_VERTICAL_KICK = BUILDER
            .comment("Peak vertical velocity of one hitch, in blocks/tick, decaying to nothing over 8 ticks.",
                    "Sized so the altitude spring fully reclaims it - the player ends at exactly the altitude",
                    "they started at, having deviated by at most ~0.12 blocks on the way. Keep it small: this",
                    "is meant to be felt, not flown through.")
            .defineInRange("hoverHitchVerticalKick", 0.06, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue HOVER_HITCH_HORIZONTAL_KICK = BUILDER
            .comment("Peak horizontal velocity of one hitch, in blocks/tick, in a random direction and decaying",
                    "over the same 8 ticks. Deliberately smaller than the vertical kick, because horizontal has",
                    "no spring to restore it - hoverStopDrag simply brakes it away within ~3 ticks, for a net",
                    "displacement well under a tenth of a block.")
            .defineInRange("hoverHitchHorizontalKick", 0.025, 0.0, 1.0);

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
                    "The default is 0: fuel is a finite, item-like supply restored with Blaze Powder, while",
                    "the separate boost-heat meter is what rapidly refills during flight. Servers can raise",
                    "this value if they deliberately want passive fuel recovery.")
            .defineInRange("fuelRegenPerTick", 0.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue FUEL_PER_BLAZE_POWDER = BUILDER
            .comment("Fuel supplied by each Blaze Powder consumed from the stack stored in the jetpack menu.",
                    "Scale with fuelCapacity to hold the cost steady: at 375 a full 1500-unit tank is 4 powders.")
            .defineInRange("fuelPerBlazePowder", 375.0, 1.0, 1_000_000.0);

    public static final ModConfigSpec.DoubleValue HEAT_MAX = BUILDER
            .comment("The boost-heat scale's ceiling. The final 7% begins at 186 with the default maximum",
                    "of 200; crossing into that red zone locks all thrust. Prefer retuning burn duration via",
                    "heatGainPerTick and leaving this at 200 because the recovery timing and HUD use this scale.")
            .defineInRange("heatMax", 200.0, 1.0, 1_000_000.0);

    public static final ModConfigSpec.DoubleValue HEAT_GAIN_PER_TICK = BUILDER
            .comment("Heat added per tick while thrusting, whether or not sprint-assisted surge is active.",
                    "At 0.555..., non-sprint thrust enters the red zone in roughly 16.75 seconds; sprint surge",
                    "enters it in roughly 8.4 seconds with the default 2x multiplier.")
            .defineInRange("heatGainPerTick", 0.5555555556, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue HEAT_DRAIN_PER_TICK = BUILDER
            .comment("Heat removed per tick whenever boost is not actively heating the meter. At the default,",
                    "a discrete red-zone crossing reaches the recovery threshold in 44 ticks (2.2 seconds), and",
                    "partial boost use clears proportionally faster.")
            .defineInRange("heatDrainPerTick", 2.3, 0.0, 100.0);

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
            .comment("Additional heat-gain multiplier while sprint-assisted OVERDRIVE surge is active.",
                    "The default 2.0 makes sprint thrust consume boost charge twice as fast as",
                    "non-sprint thrust.")
            .defineInRange("overdriveHeatMultiplier", 2.0, 1.0, 100.0);

    public static final ModConfigSpec.DoubleValue OVERHEAT_RECOVERY_THRESHOLD = BUILDER
            .comment("After crossing into the final-7% red zone, heat must fall to this level before thrust",
                    "comes back online. With the default rates, cooling from either discrete crossing value",
                    "to 87 at 2.3 heat/tick takes 44 ticks (2.2 seconds).")
            .defineInRange("overheatRecoveryThreshold", 87.0, 0.0, 1_000_000.0);

    public static final ModConfigSpec.DoubleValue OVERHEAT_DAMAGE = BUILDER
            .comment("Damage dealt to the wearer once, at the moment the overheat lockout latches. 0 disables it.")
            .defineInRange("overheatDamage", 0.0, 0.0, 100.0);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
