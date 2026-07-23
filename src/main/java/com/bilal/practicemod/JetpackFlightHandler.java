package com.bilal.practicemod;

import com.bilal.practicemod.network.JetpackAnimSync;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
// fuel back. The 70-90 heat band is the afterburner (more thrust, more burn); hitting heatMax stalls
// the jetpack until heat falls below the recovery threshold. See JetpackServerConfig for tuning.
@EventBusSubscriber(modid = PracticeMod.MODID)
public class JetpackFlightHandler {

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
            syncToClients(player, false, false);
            return;
        }

        // Don't fight elytra gliding or active creative/spectator flight. Heat keeps draining, so
        // an overheat stall ridden out on an elytra glide recovers mid-air.
        if (player.isFallFlying() || player.getAbilities().flying) {
            updateHeat(player, false);
            setGliding(player, false);
            syncToClients(player, false, false);
            return;
        }

        boolean wantsThrust = Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_THRUSTING));
        boolean overheated = Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_OVERHEATED));
        boolean hasFuel = JetpackFuelHandler.getFuel(chest) > 0.0F;

        // Effective thrust: fuel limits total flight time; heat forces pulsed rather than
        // sustained thrust. Uses the previous tick's overheat latch, so both sides agree.
        boolean thrusting = wantsThrust && hasFuel && !overheated;

        updateHeat(player, thrusting);

        // Afterburner band membership from post-update heat - both sides compute in the same
        // order, so the client's predicted thrust matches the server's.
        float heat = player.getData(PracticeMod.JETPACK_HEAT);
        boolean afterburner = thrusting
                && heat >= JetpackServerConfig.OPTIMAL_ZONE_START.get()
                && heat < JetpackServerConfig.OPTIMAL_ZONE_END.get();

        boolean gliding = false;
        if (thrusting) {
            applyThrust(player, afterburner);
            player.fallDistance = 0.0F;

            if (player.level() instanceof ServerLevel serverLevel) {
                // Fuel writes are server-only; vanilla item syncing delivers them to clients.
                float burn = (float) (double) JetpackServerConfig.FUEL_BURN_PER_TICK.get()
                        * (afterburner ? (float) (double) JetpackServerConfig.AFTERBURNER_FUEL_MULTIPLIER.get() : 1.0F);
                JetpackFuelHandler.setFuel(chest, JetpackFuelHandler.getFuel(chest) - burn);
                emitThrustFeedback(serverLevel, player);
            }
        } else {
            // The rest phase: a short, shallow dip between thrusts, not a wingsuit. With fuel the
            // sink is clamped to a brisk controlled rate; a fuel-dry pack falls at full gravity.
            // Either way heavy horizontal drag kills momentum in well under a second, so the dip
            // covers almost no ground - traversal comes from thrust. Deliberately NOT gated on
            // overheat: a stall at altitude still gets the sink clamp ("thrust and glide both cut
            // out only at zero fuel").
            gliding = !player.onGround() && hasFuel && !player.isInWater() && !player.isInLava();
            if (!player.onGround()) {
                Vec3 motion = player.getDeltaMovement();
                double drag = JetpackServerConfig.GLIDE_HORIZONTAL_DRAG.get();
                double newY = gliding
                        ? Math.max(motion.y, -JetpackServerConfig.SINK_CAP.get())
                        : motion.y;
                player.setDeltaMovement(motion.x * drag, newY, motion.z * drag);
                if (gliding) {
                    player.fallDistance = 0.0F;
                }
            }

            // The glide phase that cools heat also sips fuel back - this is what makes a
            // disciplined burn-glide cadence roughly fuel-neutral (see config notes).
            if (player.level() instanceof ServerLevel) {
                float fuel = JetpackFuelHandler.getFuel(chest);
                float capacity = JetpackFuelHandler.getFuelCapacity();
                if (fuel < capacity) { // skip the write at cap so item syncing goes quiet
                    JetpackFuelHandler.setFuel(chest,
                            fuel + (float) (double) JetpackServerConfig.FUEL_REGEN_PER_TICK.get());
                }
            }
        }

        setGliding(player, gliding);
        syncToClients(player, thrusting, gliding);
    }

    // Guarded write so grounded players don't churn attachment data every tick.
    private static void setGliding(Player player, boolean gliding) {
        if (gliding != Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_GLIDING))) {
            player.setData(PracticeMod.JETPACK_GLIDING, gliding);
        }
    }

    // Broadcasts this tick's animation-relevant state (effective thrust/glide + heat) to clients
    // tracking this player, so THEIR animations of us stay live. Called after the sim so values
    // are final.
    private static void syncToClients(Player player, boolean thrusting, boolean gliding) {
        if (player instanceof ServerPlayer serverPlayer) {
            JetpackAnimSync.sync(serverPlayer, thrusting, gliding);
        }
    }

    // The punch: an additive upward kick hard-clamped at the ascent cap, plus horizontal
    // acceleration along the horizontal component of the look vector - look at the horizon for
    // full up-and-forward carry, look straight up for a pure vertical climb. The afterburner
    // multiplies both accelerations AND both caps, so the band genuinely raises top speed. Runs
    // identically on both sides.
    private static void applyThrust(Player player, boolean afterburner) {
        Vec3 motion = player.getDeltaMovement();
        double mult = afterburner ? JetpackServerConfig.AFTERBURNER_THRUST_MULTIPLIER.get() : 1.0;

        double newY = Math.min(motion.y + JetpackServerConfig.THRUST_ACCEL.get() * mult,
                JetpackServerConfig.ASCENT_CAP.get() * mult);

        // getLookAngle is unit-length, so the horizontal push scales with cos(pitch) naturally.
        Vec3 look = player.getLookAngle();
        double accel = JetpackServerConfig.HORIZONTAL_ACCEL.get() * mult;
        double nx = motion.x + look.x * accel;
        double nz = motion.z + look.z * accel;
        double cap = JetpackServerConfig.HORIZONTAL_CAP.get() * mult;
        double speed = Math.hypot(nx, nz);
        if (speed > cap && speed > 1.0e-6) {
            nx *= cap / speed;
            nz *= cap / speed;
        }

        player.setDeltaMovement(nx, newY, nz);
    }

    // Heat climbs while thrusting and falls otherwise, both at config-driven rates. Hitting heatMax
    // latches the overheat stall, which only clears once heat drops back below the recovery
    // threshold - so a maxed jetpack needs a real cool-down, not just a moment off the throttle.
    private static void updateHeat(Player player, boolean thrusting) {
        float heat = player.getData(PracticeMod.JETPACK_HEAT);
        boolean overheated = Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_OVERHEATED));

        if (!thrusting && heat <= 0.0F && !overheated) {
            return; // Cold and idle - nothing to simulate (avoids touching data on every player).
        }

        float heatMax = (float) (double) JetpackServerConfig.HEAT_MAX.get();
        float rate = thrusting
                ? (float) (double) JetpackServerConfig.HEAT_GAIN_PER_TICK.get()
                : -(float) (double) JetpackServerConfig.HEAT_DRAIN_PER_TICK.get();
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
