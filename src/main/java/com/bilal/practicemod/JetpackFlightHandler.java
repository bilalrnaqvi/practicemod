package com.bilal.practicemod;

import com.bilal.practicemod.network.JetpackAnimSync;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
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
// The gameplay loop lives here: thrust burns fuel and builds heat; easing off drains heat and sips
// fuel back. Hitting heatMax stalls the jetpack until heat falls below the recovery threshold.
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
// trajectory while momentum bleeds at coastHorizontalDrag and the descent is capped at coastSinkCap,
// giving roughly a 1.6:1 glide ratio.
// Once speed falls under coastMinSpeed the coast ends and they fall normally, with the hover-boot
// cushion easing the descent near the ground. See JetpackServerConfig for tuning.
@EventBusSubscriber(modid = PracticeMod.MODID)
public class JetpackFlightHandler {

    // Per-tick horizontal velocity multiplier while thrusting with no WASD input - a firm brake so
    // hovering in place doesn't drift (0.85^10 ~ 20%, stops within a second). Also bleeds horizontal
    // momentum during a plain fall, once a coast has decayed below coastMinSpeed.
    private static final double HOVER_HORIZONTAL_DRAG = 0.85;

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
            // it (and any stall) instantly instead of cooling gradually off-body.
            if (player.getData(PracticeMod.JETPACK_HEAT) > 0.0F
                    || Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_OVERHEATED))) {
                player.setData(PracticeMod.JETPACK_HEAT, 0.0F);
                player.setData(PracticeMod.JETPACK_OVERHEATED, false);
            }
            setGliding(player, false);
            setSurging(player, false);
            setCoasting(player, false);
            syncToClients(player, false, false, false, false);
            return;
        }

        // Don't fight elytra gliding or active creative/spectator flight. Heat keeps draining, so
        // an overheat stall ridden out on an elytra glide recovers mid-air.
        if (player.isFallFlying() || player.getAbilities().flying) {
            updateHeat(player, false, false);
            setGliding(player, false);
            setSurging(player, false);
            setCoasting(player, false);
            syncToClients(player, false, false, false, false);
            return;
        }

        boolean wantsThrust = Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_THRUSTING));
        boolean overheated = Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_OVERHEATED));
        boolean hasFuel = JetpackFuelHandler.getFuel(chest) > 0.0F;

        // Effective thrust: heat is what ends any single burn (~11.1s of glide, ~5.6s of surge at
        // defaults), forcing pulsed rather than sustained thrust; fuel is the looser session-long
        // budget. Uses the previous tick's overheat latch, so both sides agree.
        boolean thrusting = wantsThrust && hasFuel && !overheated;

        // Boost engagement depends on the profile:
        //  - OVERDRIVE: the surge, spent on demand by holding the boost key while thrusting. Known
        //    before the heat update (it's input-driven), and it makes heat climb faster.
        //  - CLASSIC: no surge; the afterburner is the passive heat band, read from post-update heat.
        // Both sides compute in the same order, so the client's predicted thrust matches the server's.
        boolean overdrive = JetpackServerConfig.BOOST_PROFILE.get() == JetpackServerConfig.BoostProfile.OVERDRIVE;
        boolean surging = false;
        boolean afterburner = false;
        if (overdrive) {
            surging = thrusting && Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_OVERDRIVE));
            updateHeat(player, thrusting, surging);
        } else {
            updateHeat(player, thrusting, false);
            float heat = player.getData(PracticeMod.JETPACK_HEAT);
            afterburner = thrusting
                    && heat >= JetpackServerConfig.OPTIMAL_ZONE_START.get()
                    && heat < JetpackServerConfig.OPTIMAL_ZONE_END.get();
        }

        boolean hovering = false;
        boolean coasting = false;
        if (thrusting) {
            if (surging) {
                applySurgeThrust(player);
            } else {
                applyThrust(player, afterburner);
            }
            player.fallDistance = 0.0F;

            if (player.level() instanceof ServerLevel serverLevel) {
                // Fuel writes are server-only; vanilla item syncing delivers them to clients.
                float burn = (float) (double) JetpackServerConfig.FUEL_BURN_PER_TICK.get()
                        * (surging || afterburner
                                ? (float) (double) JetpackServerConfig.AFTERBURNER_FUEL_MULTIPLIER.get() : 1.0F);
                JetpackFuelHandler.setFuel(chest, JetpackFuelHandler.getFuel(chest) - burn);
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
            boolean airborne = !player.onGround() && !player.isInWater() && !player.isInLava();

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
                    hovering = true; // drives the airbrake pose + exhaust (repurposed glide flag)
                }
            }

            if (!player.onGround()) {
                double drag = coasting
                        ? JetpackServerConfig.COAST_HORIZONTAL_DRAG.get()
                        : HOVER_HORIZONTAL_DRAG;
                player.setDeltaMovement(motion.x * drag, newY, motion.z * drag);
            }

            // Not thrusting: the pack cools and sips fuel back.
            if (player.level() instanceof ServerLevel) {
                float fuel = JetpackFuelHandler.getFuel(chest);
                float capacity = JetpackFuelHandler.getFuelCapacity();
                if (fuel < capacity) { // skip the write at cap so item syncing goes quiet
                    JetpackFuelHandler.setFuel(chest,
                            fuel + (float) (double) JetpackServerConfig.FUEL_REGEN_PER_TICK.get());
                }
            }
        }

        setGliding(player, hovering);
        setSurging(player, surging);
        setCoasting(player, coasting);
        syncToClients(player, thrusting, hovering, surging, coasting);
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

    // Broadcasts this tick's animation-relevant state (effective thrust/glide/surge/coast + heat) to
    // clients tracking this player, so THEIR animations of us stay live. Called after the sim so
    // values are final.
    private static void syncToClients(Player player, boolean thrusting, boolean gliding,
            boolean surging, boolean coasting) {
        if (player instanceof ServerPlayer serverPlayer) {
            JetpackAnimSync.sync(serverPlayer, thrusting, gliding, surging, coasting);
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
    // - accelerate along the input direction, clamp to the glide cap. In CLASSIC the heat-band
    // afterburner multiplies the acceleration AND the caps, so the band genuinely raises top speed.
    // Runs identically on both sides.
    private static void applyThrust(Player player, boolean afterburner) {
        Vec3 motion = player.getDeltaMovement();
        double mult = afterburner ? JetpackServerConfig.AFTERBURNER_THRUST_MULTIPLIER.get() : 1.0;
        double accel = JetpackServerConfig.THRUST_ACCEL.get() * mult;

        // Vertical: jump+sneak eases toward a gentle controlled sink (hover); jump alone climbs to
        // the ascent cap. Easing by at most `accel` per tick keeps the hover from snapping.
        double newY;
        if (player.isShiftKeyDown()) {
            double target = -JetpackServerConfig.HOVER_DESCENT_SPEED.get();
            newY = motion.y + Mth.clamp(target - motion.y, -accel, accel);
        } else {
            newY = Math.min(motion.y + accel, JetpackServerConfig.ASCENT_CAP.get() * mult);
        }

        // Horizontal: steer along WASD rotated by yaw. With no input, bleed sideways momentum firmly
        // so letting off the keys brings the player to a controllable stop rather than drifting.
        boolean steering = applyHorizontalInput(player, JetpackServerConfig.GLIDE_HORIZONTAL_ACCEL.get() * mult);
        Vec3 steered = player.getDeltaMovement();
        double nx = steered.x;
        double nz = steered.z;
        if (!steering) {
            nx *= HOVER_HORIZONTAL_DRAG;
            nz *= HOVER_HORIZONTAL_DRAG;
        }
        double cap = JetpackServerConfig.GLIDE_HORIZONTAL_CAP.get() * mult;
        double speed = Math.hypot(nx, nz);
        if (speed > cap && speed > 1.0e-6) {
            nx *= cap / speed;
            nz *= cap / speed;
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

    // Heat climbs while thrusting and falls otherwise, both at config-driven rates. Riding the surge
    // climbs it faster still (`boosting`), which is what keeps the fast mode from being free. Hitting
    // heatMax latches the overheat stall, which only clears once heat drops back below the recovery
    // threshold - so a maxed jetpack needs a real cool-down, not just a moment off the throttle.
    // Coasting counts as "not thrusting" here: an unpowered glide cools the pack like any other idle.
    private static void updateHeat(Player player, boolean thrusting, boolean boosting) {
        float heat = player.getData(PracticeMod.JETPACK_HEAT);
        boolean overheated = Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_OVERHEATED));

        if (!thrusting && heat <= 0.0F && !overheated) {
            return; // Cold and idle - nothing to simulate (avoids touching data on every player).
        }

        float heatMax = (float) (double) JetpackServerConfig.HEAT_MAX.get();
        float rate;
        if (thrusting) {
            rate = (float) (double) JetpackServerConfig.HEAT_GAIN_PER_TICK.get();
            if (boosting) {
                rate *= (float) (double) JetpackServerConfig.OVERDRIVE_HEAT_MULTIPLIER.get();
            }
        } else {
            rate = -(float) (double) JetpackServerConfig.HEAT_DRAIN_PER_TICK.get();
        }
        float newHeat = Mth.clamp(heat + rate, 0.0F, heatMax);
        player.setData(PracticeMod.JETPACK_HEAT, newHeat);

        if (!overheated && newHeat >= heatMax) {
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
