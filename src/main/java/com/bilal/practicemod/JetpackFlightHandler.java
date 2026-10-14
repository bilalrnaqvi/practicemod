package com.bilal.practicemod;

import com.bilal.practicemod.network.JetpackAnimSync;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// Runs on both logical sides (see PlayerTickEvent docs), applying identical physics from a shared
// "is jumping" flag: on the client it's set directly from local input each tick (JetpackClientHandler),
// on the server it's set from the synced JetpackInputPayload (JetpackNetworking). This mirrors how
// vanilla's own client-predicted / server-authoritative movement stays in sync without a mod needing
// to fight the engine. Heat is simulated the same dual-sided way; fuel is only ever written by the
// server, with the client reading the copy that vanilla inventory syncing delivers.
//
// The gameplay loop lives here: every powered mode burns the separate consumable fuel supply, and
// ordinary thrust builds boost heat whether or not sprint-assisted surge is engaged. Hover and
// unpowered flight cool the boost channel. Crossing into the meter's final-7% red zone temporarily
// locks both ordinary and sprint thrust until heat falls below the recovery threshold.
//
// Thrust has two modes. GLIDE (jump alone) lifts straight up and steers horizontally by yaw at the
// gentler glide accel/cap, keeping the body upright - the precise maneuvering mode. SURGE (jump +
// the boost key, OVERDRIVE profile) is the "Superman" mode: acceleration follows the look direction
// under a single 3-D speed cap, so pitching down dives, while surgeVerticalBias keeps jump and sneak
// in charge of climbing and descending so the player never has to aim the mouse up to gain altitude.
// The surge costs the afterburner fuel and heat multipliers. In CLASSIC there is no surge; the heat
// band (optimalZoneStart..optimalZoneEnd) multiplies glide thrust instead.
//
// Releasing thrust does NOT drop the player: with speed still on the clock they COAST, keeping their
// trajectory while momentum bleeds at coastHorizontalDrag and the descent is capped at coastSinkCap.
// Once speed falls under coastMinSpeed the coast ends and they fall normally, with the hover-boot
// cushion easing the descent near the ground. See JetpackServerConfig for tuning.
//
// Momentum is the through-line of all three: the glide cap is a soft ceiling rather than a clamp, so
// releasing surge into ordinary thrust or a coast preserves the speed already earned instead of
// resetting it. Together they make the pack a continuous travel tool rather than a sequence of
// unrelated hops.
//
// HOVER MODE (the H toggle) sits in front of all of that: while it is armed and the player is
// airborne, fuelled and un-stalled, it replaces the thrust/glide/coast branches entirely with a soft
// altitude spring plus WASD ground-style movement - the build-mode substitute for creative flight.
// Jump and sneak stop being throttle and become slow altitude trim. On the ground it stays dormant so
// the player takes off on ordinary thrust and hover catches them once their feet leave the floor.
@EventBusSubscriber(modid = PracticeMod.MODID)
public class JetpackFlightHandler {

    // Per-tick horizontal velocity multiplier while THRUSTING with no WASD input - a firm brake so
    // holding position on thrust doesn't drift (0.85^10 ~ 20%, stops within a second). Also bleeds
    // horizontal momentum during a plain fall, once a coast has decayed below coastMinSpeed.
    // Not to be confused with hoverStopDrag, the harder brake belonging to hover mode.
    private static final double THRUST_HORIZONTAL_DRAG = 0.85;

    public static boolean isWearingJetpack(Player player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof JetpackItem;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();

        // On the client this event also fires for other (remote) players, whose movement and input
        // we don't own - only simulate the player this game instance controls.
        if (player.level().isClientSide() && !player.isLocalPlayer()) {
            return;
        }

        // Server config values aren't available until the world is loaded / synced.
        if (!JetpackServerConfig.SPEC.isLoaded()) {
            return;
        }

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof JetpackItem)) {
            // Heat is a property of the worn jetpack, not the player: taking the pack off resets
            // it (and any stall) instantly instead of cooling gradually off-body. Hover mode is
            // armed state on the same pack, so it resets on the same edge - putting a jetpack on
            // must never hand the player a secretly-armed hover toggle.
            if (player.getData(PracticeMod.JETPACK_HEAT) > 0.0F
                    || Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_OVERHEATED))) {
                player.setData(PracticeMod.JETPACK_HEAT, 0.0F);
                player.setData(PracticeMod.JETPACK_OVERHEATED, false);
            }
            if (Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_HOVER_MODE))) {
                player.setData(PracticeMod.JETPACK_HOVER_MODE, false);
            }
            setGliding(player, false);
            setSurging(player, false);
            setCoasting(player, false);
            setHovering(player, false);
            syncToClients(player, false, false, false, false, false);
            return;
        }

        // Don't fight elytra gliding or active creative/spectator flight. Heat keeps draining, so
        // an overheat stall ridden out on an elytra glide recovers mid-air. Hover skips here too:
        // it stays armed, but holding altitude against a mode that already owns vertical movement
        // would just be two controllers fighting.
        if (player.isFallFlying() || player.getAbilities().flying) {
            updateHeat(player, false, false);
            setGliding(player, false);
            setSurging(player, false);
            setCoasting(player, false);
            setHovering(player, false);
            syncToClients(player, false, false, false, false, false);
            return;
        }

        boolean wantsThrust = Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_THRUSTING));
        boolean overheated = Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_OVERHEATED));
        boolean hasFuel = JetpackFuelHandler.hasFuelSupply(chest);
        boolean airborne = !player.onGround() && !player.isInWater() && !player.isInLava();
        boolean overdrive = JetpackServerConfig.BOOST_PROFILE.get() == JetpackServerConfig.BoostProfile.OVERDRIVE;

        // Hover takes precedence over thrust and glide alike. It is dormant on the ground, so a
        // player standing still with hover armed takes off on ordinary thrust and is caught the
        // moment they leave the floor. An empty tank blocks it. In OVERDRIVE, boost heat cannot
        // disable this precision-flight mode; CLASSIC retains its original all-thrust heat stall.
        boolean hovering = Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_HOVER_MODE))
                && airborne && hasFuel && (overdrive || !overheated);

        // Crossing into the final-7% red zone locks all thrust for the recovery period. Hover is a
        // separate precision mode; while hovering, jump is altitude trim rather than throttle.
        boolean thrusting = !hovering && wantsThrust && hasFuel && !overheated;

        // Boost engagement depends on the profile:
        //  - OVERDRIVE: the surge, spent on demand by holding the boost key while thrusting. Known
        //    before the heat update (it's input-driven), and it makes heat climb faster.
        //  - CLASSIC: no surge; the afterburner is the passive heat band, read from post-update heat.
        // Both sides compute in the same order, so the client's predicted thrust matches the server's.
        // Hover owns the heat channel outright when engaged, so it takes neither path.
        boolean surging = false;
        boolean afterburner = false;
        if (hovering) {
            if (overdrive) {
                updateHeat(player, false, false);
            } else {
                updateHoverHeat(player);
            }
        } else if (overdrive) {
            // Ordinary thrust consumes boost charge even without the sprint modifier. Sprint-assisted
            // surge heats faster, so its stronger movement has a correspondingly higher meter cost.
            surging = thrusting && !overheated
                    && Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_OVERDRIVE));
            updateHeat(player, thrusting, surging);
        } else {
            updateHeat(player, thrusting, false);
            float heat = player.getData(PracticeMod.JETPACK_HEAT);
            afterburner = thrusting
                    && heat >= JetpackServerConfig.OPTIMAL_ZONE_START.get()
                    && heat < JetpackServerConfig.OPTIMAL_ZONE_END.get();
        }

        // The near-ground hover-boot cushion, which predates hover mode and is unrelated to it (it
        // drives the airbrake pose through the JETPACK_GLIDING attachment).
        boolean cushioning = false;
        boolean coasting = false;
        if (hovering) {
            applyHover(player);
            player.fallDistance = 0.0F;

            if (player.level() instanceof ServerLevel) {
                // Fuel writes are server-only; vanilla item syncing delivers them to clients.
                JetpackFuelHandler.consumeFuel(chest,
                        (float) (double) JetpackServerConfig.HOVER_FUEL_BURN_PER_TICK.get());
            }
        } else if (thrusting) {
            if (surging) {
                applySurgeThrust(player);
            } else {
                applyThrust(player, afterburner, 1.0);
            }
            player.fallDistance = 0.0F;

            if (player.level() instanceof ServerLevel serverLevel) {
                // Fuel writes are server-only; vanilla item syncing delivers them to clients.
                float burn = (float) (double) JetpackServerConfig.FUEL_BURN_PER_TICK.get()
                        * (surging || afterburner
                                ? (float) (double) JetpackServerConfig.AFTERBURNER_FUEL_MULTIPLIER.get() : 1.0F);
                JetpackFuelHandler.consumeFuel(chest, burn);
                emitThrustFeedback(serverLevel, player);
            }
        } else {
            // No thrust, but not necessarily a drop. With real speed still on the clock the player
            // COASTS: the trajectory carries, momentum bleeds gently, and the descent is capped to a
            // slight sink, so cutting the throttle at speed reads as an unpowered glide rather than
            // falling out of the sky. Once speed decays past coastMinSpeed the coast ends and the
            // firm fall drag takes over. Layered on top is the hover-boot cushion: descending within
            // hoverBootRange of the ground clamps the fall to an eased slow-fall that gets gentler
            // the closer they get, settling them down softly. Both clamps narrow the same running
            // newY, so the gentler one always wins. The ground scan is a downward raycast that
            // resolves identically on both sides, so the local player's prediction matches.
            Vec3 motion = player.getDeltaMovement();
            double newY = motion.y;

            coasting = airborne
                    && Math.hypot(motion.x, motion.z) >= JetpackServerConfig.COAST_MIN_SPEED.get();
            if (coasting) {
                double sinkCap = JetpackServerConfig.COAST_SINK_CAP.get();
                if (newY < -sinkCap) {
                    newY = -sinkCap; // clamp DOWNWARD only - releasing mid-climb keeps its momentum
                }
                player.fallDistance = 0.0F; // a coast is controlled flight, not a fall
            }

            if (airborne && newY < 0.0 && JetpackServerConfig.HOVER_BOOT_ENABLED.get()) {
                double range = JetpackServerConfig.HOVER_BOOT_RANGE.get();
                double gap = groundGap(player, range);
                if (gap >= 0.0 && gap <= range) {
                    // frac: 0 at the ground, 1 at the threshold edge. Descent cap eases from the
                    // near-normal entry speed down to the gentle touchdown speed as frac -> 0.
                    double frac = Mth.clamp(gap / range, 0.0, 1.0);
                    double maxDescent = Mth.lerp(frac,
                            JetpackServerConfig.HOVER_BOOT_MIN_DESCENT.get(),
                            JetpackServerConfig.HOVER_BOOT_ENTRY_DESCENT.get());
                    if (newY < -maxDescent) {
                        newY = -maxDescent; // clamp DOWNWARD only; never push the player up
                    }
                    player.fallDistance = 0.0F;
                    cushioning = true; // drives the airbrake pose + exhaust (repurposed glide flag)
                }
            }

            if (!player.onGround()) {
                double drag = coasting
                        ? JetpackServerConfig.COAST_HORIZONTAL_DRAG.get()
                        : THRUST_HORIZONTAL_DRAG;
                player.setDeltaMovement(motion.x * drag, newY, motion.z * drag);
            }

            // Not thrusting: the pack cools. Servers may opt into passive fuel recovery, but the
            // default treats fuel as a finite consumable replenished with Blaze Powder.
            if (player.level() instanceof ServerLevel) {
                float fuel = JetpackFuelHandler.getFuel(chest);
                float capacity = JetpackFuelHandler.getFuelCapacity();
                if (fuel < capacity) { // skip the write at cap so item syncing goes quiet
                    JetpackFuelHandler.setFuel(chest,
                            fuel + (float) (double) JetpackServerConfig.FUEL_REGEN_PER_TICK.get());
                }
            }
        }

        setGliding(player, cushioning);
        setSurging(player, surging);
        setCoasting(player, coasting);
        setHovering(player, hovering);
        syncToClients(player, thrusting, cushioning, surging, coasting, hovering);
    }

    // The hover altitude spring plus its horizontal channel. Runs identically on both sides; see the
    // HOVER MODE block in JetpackServerConfig for the control law and why it is a spring rather than
    // a clamp.
    //
    // Both the vertical and horizontal channels OVERWRITE delta movement rather than accumulating
    // into it, which is what lets the vertical loop ignore gravity entirely: this runs at
    // PlayerTickEvent.Post, after vanilla has already moved the player and rewritten deltaMovement
    // for next tick, so whatever we store here is exactly the movement the next tick performs.
    private static void applyHover(Player player) {
        double y = player.getY();

        // JETPACK_HOVERING still holds LAST tick's value here (the sim writes it after this returns),
        // so it is exactly the "was hover already running?" edge both the target and the velocity
        // sample need.
        boolean engaged = Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_HOVERING));

        // Velocity as the distance the player ACTUALLY covered since the last hover tick, not
        // getDeltaMovement(): that field has already had this tick's gravity and drag folded into it
        // and describes the next tick's start, so damping against it would bake in a permanent sag.
        // Differencing our own sample (rather than Entity#yo - see JETPACK_HOVER_PREV_Y) measures the
        // same real movement on both sides, and picks up the client-side hitch impulses for free,
        // which is precisely what the damping term needs to see in order to absorb them.
        double vy = engaged ? y - player.getData(PracticeMod.JETPACK_HOVER_PREV_Y) : 0.0;
        player.setData(PracticeMod.JETPACK_HOVER_PREV_Y, y);

        // --- altitude target ---
        // Held across ticks, seeded from the player's own altitude the tick hover engages (the
        // attachment still holds a stale target from the last hover session otherwise).
        double targetY = engaged ? player.getData(PracticeMod.JETPACK_HOVER_TARGET_Y) : y;

        // Fine trim. Jump wins over sneak if somehow both are held; neither means the target holds.
        if (Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_THRUSTING))) {
            targetY += JetpackServerConfig.HOVER_CLIMB_RATE.get();
        } else if (Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_SNEAKING))) {
            targetY -= JetpackServerConfig.HOVER_DESCEND_RATE.get();
        }

        // The leash. Without this, hovering up into a ceiling (or trimming down onto a floor) keeps
        // moving a target the player cannot follow, and the controller banks the error up as stored
        // energy that fires them off the moment the obstruction clears.
        double leash = JetpackServerConfig.HOVER_TARGET_LEASH.get();
        targetY = Mth.clamp(targetY, y - leash, y + leash);
        player.setData(PracticeMod.JETPACK_HOVER_TARGET_Y, targetY);

        // --- horizontal ---
        // Same yaw-relative WASD steering as the glide, at the gentler hover rates. With no input the
        // stop drag brakes hard so the player parks on the block they were aiming at.
        //
        // Deliberately a HARD cap, unlike the glide's soft ceiling: hover is the precision build mode,
        // and dropping straight to its cap on entry is the correct feel there - toggling hover on at
        // speed should park you, not fling you across the build. Momentum carry belongs to the travel
        // modes.
        boolean steering = applyHorizontalInput(player, JetpackServerConfig.HOVER_HORIZONTAL_ACCEL.get());
        Vec3 steered = player.getDeltaMovement();
        double nx = steered.x;
        double nz = steered.z;
        if (!steering) {
            double drag = JetpackServerConfig.HOVER_STOP_DRAG.get();
            nx *= drag;
            nz *= drag;
        }
        double cap = JetpackServerConfig.HOVER_HORIZONTAL_CAP.get();
        double speed = Math.hypot(nx, nz);
        if (speed > cap && speed > 1.0e-6) {
            nx *= cap / speed;
            nz *= cap / speed;
        }

        // --- vertical: the PD spring, plus the engine hum ---
        double newY = vy
                + JetpackServerConfig.HOVER_ALTITUDE_GAIN.get() * (targetY - y)
                - JetpackServerConfig.HOVER_DAMPING_GAIN.get() * vy
                + hoverHum(player);
        double verticalCap = JetpackServerConfig.HOVER_VERTICAL_CAP.get();

        player.setDeltaMovement(nx, Mth.clamp(newY, -verticalCap, verticalCap), nz);
    }

    // The continuous engine hum: a small vertical velocity offset built from three sine terms of the
    // world clock at incommensurate frequencies (periods of roughly 20, 12 and 5 ticks), so it never
    // settles into a rhythm the ear can lock onto. Weights sum to 1, so the peak is hoverJitterAmplitude.
    //
    // Deterministic in level time and nothing else, so every side computes the identical value with
    // zero syncing - which is why this one can be part of the shared simulation while the hitches
    // below it cannot. The altitude spring pulls the integral back to the target continuously, so the
    // hum wanders the eye by at most ~0.06 blocks and never accumulates.
    private static double hoverHum(Player player) {
        double amplitude = JetpackServerConfig.HOVER_JITTER_AMPLITUDE.get();
        if (amplitude <= 0.0) {
            return 0.0;
        }
        double t = player.level().getGameTime();
        return amplitude * (0.6 * Math.sin(t * 0.31)
                + 0.3 * Math.sin(t * 0.53)
                + 0.1 * Math.sin(t * 1.19));
    }

    // CLASSIC hover deliberately does NOT use heatGainPerTick. Heat eases toward hoverHeatCeiling
    // from either direction at hoverHeatRate, so a builder can never stall out mid-session. The
    // default OVERDRIVE profile bypasses this method and cools toward zero like every non-surge mode.
    private static void updateHoverHeat(Player player) {
        float heat = player.getData(PracticeMod.JETPACK_HEAT);
        float ceiling = (float) (double) JetpackServerConfig.HOVER_HEAT_CEILING.get();
        float rate = (float) (double) JetpackServerConfig.HOVER_HEAT_RATE.get();
        float delta = Mth.clamp(ceiling - heat, -rate, rate);
        if (delta != 0.0F) {
            player.setData(PracticeMod.JETPACK_HEAT, heat + delta);
        }
    }

    // Distance straight down from the player's feet to the first collidable block, via a downward
    // raycast (fluids ignored). Returns -1 if nothing is within `range`. Deterministic on both sides,
    // so the client's hover-boot prediction matches the server.
    private static double groundGap(Player player, double range) {
        Vec3 from = player.position();
        Vec3 to = from.subtract(0.0, range + 0.1, 0.0);
        BlockHitResult hit = player.level().clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return -1.0;
        }
        return from.y - hit.getLocation().y;
    }

    // Guarded writes so grounded players don't churn attachment data every tick.
    private static void setGliding(Player player, boolean gliding) {
        if (gliding != Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_GLIDING))) {
            player.setData(PracticeMod.JETPACK_GLIDING, gliding);
        }
    }

    private static void setSurging(Player player, boolean surging) {
        if (surging != Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_SURGING))) {
            player.setData(PracticeMod.JETPACK_SURGING, surging);
        }
    }

    private static void setCoasting(Player player, boolean coasting) {
        if (coasting != Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_COASTING))) {
            player.setData(PracticeMod.JETPACK_COASTING, coasting);
        }
    }

    private static void setHovering(Player player, boolean hovering) {
        if (hovering != Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_HOVERING))) {
            player.setData(PracticeMod.JETPACK_HOVERING, hovering);
        }
        updateHoverPose(player, hovering);
    }

    // Hover owns the wearer's pose outright. In this mode sneak is altitude trim, not a crouch, so the
    // vanilla crouch has no business firing - and it isn't merely cosmetic: LocalPlayer.aiStep feeds
    // isMovingSlowly() straight into KeyboardInput.tick, which scales WASD by the sneaking-speed
    // attribute (0.3). Left alone, holding descend would cut hover's steering authority to under a
    // third and make the horizontal cap unreachable. Forcing the pose fixes the look and the handling
    // in one move, and restores the standing eye height so the camera doesn't dip on the way down.
    //
    // NeoForge's forced pose short-circuits Player#updatePlayerPose, and this runs on both logical
    // sides, so the pose replicates and remote viewers see the same thing. Hooked into setHovering
    // because that is already the one choke point every exit path funnels through - unequip, elytra,
    // creative flight, landing, dry tank, overheat.
    //
    // Only ever writes STANDING or clears our own STANDING back to null, so a forced pose belonging to
    // some other mod is left alone rather than stomped.
    private static void updateHoverPose(Player player, boolean hovering) {
        // Re-evaluated every tick rather than latched on the engage edge: setPose(forcedPose) skips
        // vanilla's fit test, so hovering into a gap too short to stand up in has to hand the pose back
        // or the hitbox clips into the ceiling. This is canPlayerFitWithinBlocksAndEntitiesWhen's body,
        // which is protected, spelled out with the public API.
        boolean wantStanding = hovering && player.level().noCollision(player,
                player.getDimensions(Pose.STANDING).makeBoundingBox(player.position()).deflate(1.0E-7));

        Pose forced = player.getForcedPose();
        if (wantStanding) {
            if (forced == null) {
                // Guarded because setForcedPose is documented as an edge-set, not a per-tick write.
                player.setForcedPose(Pose.STANDING);
            }
        } else if (forced == Pose.STANDING) {
            player.setForcedPose(null);
        }
    }

    // Broadcasts this tick's animation-relevant state (effective thrust/glide/surge/coast/hover +
    // heat) to clients tracking this player, so THEIR animations of us stay live. Called after the
    // sim so values are final.
    //
    // Also drains the pending hitch latch: the owning client rolled it and sent it up on this tick's
    // input packet, and this is the one place it gets forwarded to everyone watching. The server
    // never rolls a hitch of its own and never applies the impulse - it only relays the cue.
    private static void syncToClients(Player player, boolean thrusting, boolean gliding,
            boolean surging, boolean coasting, boolean hovering) {
        if (player instanceof ServerPlayer serverPlayer) {
            boolean hitch = Boolean.TRUE.equals(serverPlayer.getData(PracticeMod.JETPACK_HITCH));
            if (hitch) {
                serverPlayer.setData(PracticeMod.JETPACK_HITCH, false);
            }
            JetpackAnimSync.sync(serverPlayer, thrusting, gliding, surging, coasting, hovering, hitch);
        }
    }

    // The Superman mode. Acceleration follows the look direction, so pitching down dives and the
    // whole velocity vector tracks the crosshair - but pure look steering would force the player to
    // aim the mouse up just to gain altitude, which nobody wants. So jump and sneak keep vertical
    // authority: surgeVerticalBias is folded into the look vector's Y before it is normalized, which
    // biases a level camera into a climb (jump is necessarily held here - it IS the throttle) and
    // makes sneak an unconditional descent. Pitching down past ~17 degrees at the default bias
    // overcomes the bias and dives.
    //
    // The vertical channel also cancels gravity outright. Normalizing spreads surgeAccel across all
    // three axes, so a level look only puts ~0.05/tick into Y - less than the ~0.08/tick of gravity,
    // which would make "hold jump and look straight ahead" sink instead of climb. Cancelling gravity
    // first makes the blended direction the ONLY thing deciding climb or dive, at any look angle.
    // (The glide path doesn't need this: it overwrites Y outright rather than accumulating into it.)
    //
    // One 3-D speed cap rather than split horizontal/vertical caps, so a dive, a climb and a level
    // run all top out at the same speed. Runs identically on both sides.
    private static void applySurgeThrust(Player player) {
        double bias = JetpackServerConfig.SURGE_VERTICAL_BIAS.get();
        Vec3 look = player.getLookAngle();
        double dirY = player.isShiftKeyDown() ? Math.min(look.y, -bias) : look.y + bias;

        Vec3 dir = new Vec3(look.x, dirY, look.z);
        if (dir.lengthSqr() < 1.0e-6) {
            return; // degenerate look+bias combination; leave momentum untouched this tick
        }
        // Read gravity from the attribute rather than hardcoding 0.08, so slow falling (and any
        // other gravity modifier) still behaves sensibly under thrust.
        double gravity = player.getAttributeValue(Attributes.GRAVITY);
        Vec3 accel = dir.normalize().scale(JetpackServerConfig.SURGE_ACCEL.get());
        player.setDeltaMovement(player.getDeltaMovement().add(accel.x, accel.y + gravity, accel.z));

        // Strafing still trims the line sideways, at the gentler glide rate - the surge is committed
        // forward flight, not free-form steering. A strafe-only input carries no Y, so this can't
        // fight the vertical channel above.
        float strafe = player.getData(PracticeMod.JETPACK_STRAFE);
        if (strafe != 0.0F) {
            player.moveRelative((float) (double) JetpackServerConfig.GLIDE_HORIZONTAL_ACCEL.get(),
                    new Vec3(strafe, 0.0, 0.0));
        }

        Vec3 motion = player.getDeltaMovement();
        double cap = JetpackServerConfig.SURGE_SPEED_CAP.get();
        double speed = motion.length();
        if (speed > cap && speed > 1.0e-6) {
            player.setDeltaMovement(motion.scale(cap / speed));
        }
    }

    // The punch, in glide mode. Vertical is decoupled from look entirely: holding jump kicks straight
    // up toward the ascent cap, and holding sneak too settles into a gentle hover (descend toward
    // hoverDescentSpeed) for fine altitude control. Horizontal is WASD steered relative to facing yaw
    // - accelerate along the input direction under a SOFT ceiling that bounds what thrust can build
    // to without discarding momentum carried in from a surge or a coast (see the block below). In
    // CLASSIC the heat-band afterburner multiplies the acceleration AND the caps, so the band
    // genuinely raises top speed. Runs identically on both sides.
    /** Shared entry point for devices that use the jetpack's ordinary, non-sprint thrust profile. */
    static void applyNormalThrust(Player player) {
        applyNormalThrust(player, 1.0);
    }

    /**
     * Shared ordinary-thrust controller with a device-specific strength scale. Scaling both the
     * net acceleration and caps keeps weaker devices weaker throughout a sustained burn, rather
     * than merely making them take longer to reach the jetpack's full speed.
     */
    static void applyNormalThrust(Player player, double thrustScale) {
        applyThrust(player, false, thrustScale);
    }

    private static void applyThrust(Player player, boolean afterburner, double thrustScale) {
        Vec3 motion = player.getDeltaMovement();
        double profileMult = afterburner ? JetpackServerConfig.AFTERBURNER_THRUST_MULTIPLIER.get() : 1.0;
        double mult = thrustScale * profileMult;
        double configuredAccel = JetpackServerConfig.THRUST_ACCEL.get() * profileMult;
        // PlayerTick.Post sees gravity already applied. Scale the acceleration above gravity, not
        // the raw configured value: half of the default 0.105 would be below gravity (~0.08) and
        // could never lift off. At scale 0.5 this yields half the ordinary jetpack's net climb.
        double gravity = player.getAttributeValue(Attributes.GRAVITY);
        double accel = gravity + (configuredAccel - gravity) * thrustScale;

        // Vertical: jump+sneak eases toward a gentle controlled sink (hover); jump alone climbs to
        // the ascent cap. Easing by at most `accel` per tick keeps the hover from snapping.
        double newY;
        if (Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_SNEAKING))) {
            double target = -JetpackServerConfig.HOVER_DESCENT_SPEED.get();
            newY = motion.y + Mth.clamp(target - motion.y, -accel, accel);
        } else {
            newY = Math.min(motion.y + accel, JetpackServerConfig.ASCENT_CAP.get() * mult);
        }

        // Horizontal: steer along WASD rotated by yaw, under a SOFT ceiling.
        //
        // The cap bounds what glide thrust can ACCELERATE to; it is not a licence to discard speed the
        // player already had. Clamping to it outright meant releasing the boost key while still holding
        // jump threw away half a surge in a single tick, and re-entering thrust from a fast coast did
        // the same - which is what made the pack useless for travel. Instead the ceiling starts at
        // whatever momentum arrived this tick and ratchets down by overspeedBleed until it reaches the
        // cap, so carried speed washes out over ~2s rather than instantly.
        //
        // Three properties fall out of this, and all three matter:
        //  - At or below the cap it is EXACTLY today's behaviour: entrySpeed * bleed < cap whenever
        //    entrySpeed <= cap, so the ceiling collapses to the cap and ordinary glide flight is
        //    unchanged - same top speed, same handling.
        //  - WASD can redirect carried momentum but never add to it: the acceleration goes in
        //    normally, then the renormalize below pulls the result back to the ceiling, so a turn
        //    keeps its speed while a straight-ahead push cannot climb past it.
        //  - The firm no-drift brake is skipped while overspeed. It exists so holding position on
        //    thrust doesn't drift, which is a statement about low speeds; applying it to carried surge
        //    momentum would undo the whole point.
        double entrySpeed = Math.hypot(motion.x, motion.z);
        double cap = JetpackServerConfig.GLIDE_HORIZONTAL_CAP.get() * mult;
        double ceiling = Math.max(cap, entrySpeed * JetpackServerConfig.OVERSPEED_BLEED.get());
        boolean overspeed = entrySpeed > cap;

        boolean steering = applyHorizontalInput(player, JetpackServerConfig.GLIDE_HORIZONTAL_ACCEL.get() * mult);
        Vec3 steered = player.getDeltaMovement();
        double nx = steered.x;
        double nz = steered.z;
        if (!steering && !overspeed) {
            nx *= THRUST_HORIZONTAL_DRAG;
            nz *= THRUST_HORIZONTAL_DRAG;
        }
        double speed = Math.hypot(nx, nz);
        if (speed > ceiling && speed > 1.0e-6) {
            nx *= ceiling / speed;
            nz *= ceiling / speed;
        }

        player.setDeltaMovement(nx, newY, nz);
    }

    // Adds WASD-driven horizontal acceleration, rotated by the player's facing yaw, onto delta
    // movement. moveRelative also nudges Y for a pitched look, so callers overwrite Y afterward.
    // Returns whether the player supplied any movement input this tick.
    private static boolean applyHorizontalInput(Player player, double accel) {
        float forward = player.getData(PracticeMod.JETPACK_FORWARD);
        float strafe = player.getData(PracticeMod.JETPACK_STRAFE);
        if (forward == 0.0F && strafe == 0.0F) {
            return false;
        }
        // moveRelative rotates (strafe, y, forward) by yRot and normalizes over-length input, matching
        // how vanilla turns WASD into world-space acceleration.
        player.moveRelative((float) accel, new Vec3(strafe, 0.0, forward));
        return true;
    }

    // Heat climbs only when the caller marks this mode as heating and falls otherwise, both at
    // config-driven rates. In the default OVERDRIVE profile ordinary thrust heats the meter, sprint
    // surge heats it faster, and hover/coast cool it. Entering the final-7% red zone latches the
    // thrust lockout, which forces cooling and clears at the recovery threshold.
    private static void updateHeat(Player player, boolean heating, boolean boosting) {
        float heat = player.getData(PracticeMod.JETPACK_HEAT);
        boolean overheated = Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_OVERHEATED));

        if (!heating && heat <= 0.0F && !overheated) {
            return; // Cold and idle - nothing to simulate (avoids touching data on every player).
        }

        float heatMax = (float) (double) JetpackServerConfig.HEAT_MAX.get();
        float rate;
        if (heating) {
            rate = (float) (double) JetpackServerConfig.HEAT_GAIN_PER_TICK.get();
            if (boosting) {
                rate *= (float) (double) JetpackServerConfig.OVERDRIVE_HEAT_MULTIPLIER.get();
            }
        } else {
            rate = -(float) (double) JetpackServerConfig.HEAT_DRAIN_PER_TICK.get();
        }
        float newHeat = Mth.clamp(heat + rate, 0.0F, heatMax);
        player.setData(PracticeMod.JETPACK_HEAT, newHeat);

        float overheatTrigger = heatMax * JetpackServerConfig.OVERHEAT_ZONE_START_FRACTION;
        if (!overheated && newHeat >= overheatTrigger) {
            player.setData(PracticeMod.JETPACK_OVERHEATED, true);
            // Sound stays server-side (positional, everyone hears it); the smoke burst moved to
            // JetpackAnimationController, which anchors it to the vent flaps as they fly open -
            // every client sees the overheat edge via its own sim or the anim sync payload.
            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.PLAYERS, 1.0F, 1.0F);
                double damage = JetpackServerConfig.OVERHEAT_DAMAGE.get();
                if (damage > 0) {
                    player.hurt(player.damageSources().generic(), (float) damage);
                }
            }
        } else if (overheated && newHeat <= JetpackServerConfig.OVERHEAT_RECOVERY_THRESHOLD.get()) {
            player.setData(PracticeMod.JETPACK_OVERHEATED, false);
        }
    }

    // Audible warning while thrusting in the critical zone (past the afterburner band), sent from
    // the server so every nearby player hears it. The visual exhaust lives in
    // JetpackAnimationController: spawned client-side per viewer so the particles can anchor to
    // the animated nozzle positions instead of a fixed body offset.
    private static void emitThrustFeedback(ServerLevel level, Player player) {
        float heat = player.getData(PracticeMod.JETPACK_HEAT);

        if (heat >= JetpackServerConfig.OPTIMAL_ZONE_END.get() && player.tickCount % 15 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 1.6F);
        }
    }

    @SubscribeEvent
    static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && isWearingJetpack(player)
                && JetpackServerConfig.SPEC.isLoaded() && JetpackServerConfig.NEGATE_FALL_DAMAGE.get()) {
            event.setCanceled(true);
        }
    }
}
