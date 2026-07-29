package com.bilal.practicemod;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// Client-side driver for the jetpack animation. Once per tick, for EVERY player this client can
// see (PlayerTickEvent fires for remote players too), it feeds the simulation's outputs into that
// player's JetpackAnimationState and emits the matching particles and sounds. The local player's
// inputs come from its own predicted attachments; remote players' come from the same attachments,
// filled in by JetpackAnimSyncPayload (fuel needs no payload - it lives in item components that
// vanilla already syncs on equipped gear).
//
// This class only READS simulation state. It never touches movement, input, fuel, or heat, so the
// animation can never gate or delay the physics.
@EventBusSubscriber(modid = PracticeMod.MODID, value = Dist.CLIENT)
public final class JetpackAnimationController {

    // The tank fraction under which the engine starts sputtering.
    private static final float LOW_FUEL_FRACTION = 0.10F;

    // Anchor points in body-part space (see JetpackModel geometry; /16 = blocks, model y 24 = feet).
    private static final float NOZZLE_X = 2.0F;       // +- mirrored per side
    private static final float NOZZLE_EXIT_Y = 13.0F; // rest-pose exit; stretches with flare
    private static final float NOZZLE_Z = 4.75F;
    private static final float VENT_X = 4.0F;
    private static final float VENT_Y = 4.5F;
    private static final float VENT_Z = 4.5F;

    // Keyed by entity id; fastutil's int keys avoid boxing on the per-frame lookup in
    // PracticeModClient.setupModelAnimations. Entries are created when a player is first seen
    // wearing a jetpack and dropped when the entity leaves the level.
    private static final Int2ObjectOpenHashMap<JetpackAnimationState> STATES = new Int2ObjectOpenHashMap<>();

    public static JetpackAnimationState get(int entityId) {
        return STATES.get(entityId);
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide()) {
            return;
        }

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        boolean wearing = chest.getItem() instanceof JetpackItem;

        JetpackAnimationState state = STATES.get(player.getId());
        if (state == null) {
            if (!wearing) {
                return;
            }
            state = new JetpackAnimationState();
            STATES.put(player.getId(), state);
        }

        // The animation state machine (and the model's channels) work on a 0..1 heat scale; the
        // gameplay attachment runs 0..heatMax (default 100), so normalize here at the boundary.
        float heatMax = JetpackServerConfig.SPEC.isLoaded()
                ? (float) (double) JetpackServerConfig.HEAT_MAX.get()
                : 100.0F;
        float heat = Mth.clamp(player.getData(PracticeMod.JETPACK_HEAT) / heatMax, 0.0F, 1.0F);
        boolean overheated = Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_OVERHEATED));
        boolean fuelEmpty = wearing && JetpackFuelHandler.getFuel(chest) <= 0.0F;
        boolean lowFuel = wearing && !fuelEmpty
                && JetpackFuelHandler.getFuelFraction(chest) <= LOW_FUEL_FRACTION;
        // Effective thrust, mirroring JetpackFlightHandler's gating. For remote players the synced
        // flag already is the effective value; re-applying the gates is harmless and keeps this
        // uniform for the local player, whose attachment holds raw jump input.
        boolean thrusting = wearing && !overheated && !fuelEmpty
                && Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_THRUSTING));
        // The glide/surge/coast flags are written by the flight sim for the local player and by the
        // anim sync payload for remote players - the same dual path as thrusting. They arrive already
        // gated (fuel, overheat, boost profile), so they need no re-checking here.
        boolean gliding = wearing && Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_GLIDING));
        boolean surging = wearing && Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_SURGING));
        boolean coasting = wearing && Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_COASTING));

        // Horizontal speed from position deltas rather than getDeltaMovement: the deltas are what
        // actually moved on screen this tick, which also makes them valid for remote players
        // (whose delta-movement vanilla does not reliably replicate).
        double dx = player.getX() - player.xo;
        double dz = player.getZ() - player.zo;
        float horizontalSpeed = (float) Math.sqrt(dx * dx + dz * dz);

        state.tick(wearing, thrusting, gliding, surging, coasting, heat, overheated, lowFuel, fuelEmpty,
                horizontalSpeed);

        if (wearing) {
            emitFeedback(player, state, heat, overheated, gliding);
        }
    }

    // Particles and sounds, all client-spawned so they can anchor to the ANIMATED nozzle and vent
    // positions, and all triggered by the same per-tick state edges that drive the motion - which
    // is what keeps audio, particles, and movement reading as one event.
    private static void emitFeedback(Player player, JetpackAnimationState state, float heat, boolean overheated,
            boolean gliding) {
        Level level = player.level();
        float flare = state.flare(1.0F); // end-of-tick sample
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        float sin = Mth.sin(yaw);
        float cos = Mth.cos(yaw);
        double baseX = player.getX();
        double baseY = player.getY();
        double baseZ = player.getZ();

        // Continuous exhaust: flame every tick from each nozzle exit (following the flare stretch),
        // smoke thickening with heat, drifting with the player's own velocity so trails read right.
        if (flare > 0.1F) {
            Vec3 motion = player.getDeltaMovement();
            float exitY = NOZZLE_EXIT_Y + 3.0F * 0.35F * flare;
            boolean smokeTick = heat > 0.25F && player.tickCount % 3 == 0;
            for (int side = -1; side <= 1; side += 2) {
                double px = baseX + (cos * side * NOZZLE_X + sin * NOZZLE_Z) / 16.0;
                double py = baseY + (24.0F - exitY) / 16.0;
                double pz = baseZ + (sin * side * NOZZLE_X - cos * NOZZLE_Z) / 16.0;
                level.addParticle(ParticleTypes.FLAME, px, py, pz,
                        motion.x, motion.y - 0.35 * flare, motion.z);
                if (smokeTick) {
                    level.addParticle(heat > 0.75F ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
                            px, py, pz, 0.0, -0.05, 0.0);
                }
            }
        }

        // Glide exhaust: sparse small flames with the odd smoke wisp - a smoldering pilot light,
        // fire-themed but clearly not the full thrust jet. Gated on low flare so the thrust->glide
        // handoff never renders both flames at once.
        if (gliding && flare < 0.5F) {
            boolean flameTick = player.tickCount % 4 == 0;
            boolean smokeTick = player.tickCount % 8 == 0;
            if (flameTick || smokeTick) {
                Vec3 motion = player.getDeltaMovement();
                for (int side = -1; side <= 1; side += 2) {
                    double px = baseX + (cos * side * NOZZLE_X + sin * NOZZLE_Z) / 16.0;
                    double py = baseY + (24.0F - NOZZLE_EXIT_Y) / 16.0;
                    double pz = baseZ + (sin * side * NOZZLE_X - cos * NOZZLE_Z) / 16.0;
                    if (flameTick) {
                        level.addParticle(ParticleTypes.SMALL_FLAME, px, py, pz,
                                motion.x * 0.5, motion.y * 0.5 - 0.03, motion.z * 0.5);
                    }
                    if (smokeTick) {
                        level.addParticle(ParticleTypes.SMOKE, px, py, pz, 0.0, -0.02, 0.0);
                    }
                }
            }
        }

        // Sputter cough: smoke pop + raspy hiss on the same tick and same (alternating) nozzle as
        // the model's cough twitch.
        if (state.popFired()) {
            int side = state.coughSide();
            double px = baseX + (cos * side * NOZZLE_X + sin * NOZZLE_Z) / 16.0;
            double py = baseY + (24.0F - NOZZLE_EXIT_Y) / 16.0;
            double pz = baseZ + (sin * side * NOZZLE_X - cos * NOZZLE_Z) / 16.0;
            for (int i = 0; i < 3; i++) {
                level.addParticle(ParticleTypes.SMOKE, px, py, pz,
                        player.getRandom().nextGaussian() * 0.02, -0.05, player.getRandom().nextGaussian() * 0.02);
            }
            level.addParticle(ParticleTypes.SMALL_FLAME, px, py, pz, 0.0, -0.1, 0.0);
            level.playLocalSound(px, py, pz, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.35F, 1.8F, false);
        }

        // Ignition fwoosh, on the same edge as the nozzle kick.
        if (state.ignitionFired()) {
            level.playLocalSound(baseX, baseY + 0.7, baseZ, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.35F, 1.3F, false);
        }

        // Stall: one harsh clank and a final puff from both nozzles as the flare slams shut.
        if (state.stallFired()) {
            for (int side = -1; side <= 1; side += 2) {
                double px = baseX + (cos * side * NOZZLE_X + sin * NOZZLE_Z) / 16.0;
                double py = baseY + (24.0F - NOZZLE_EXIT_Y) / 16.0;
                double pz = baseZ + (sin * side * NOZZLE_X - cos * NOZZLE_Z) / 16.0;
                level.addParticle(ParticleTypes.LARGE_SMOKE, px, py, pz, 0.0, -0.02, 0.0);
            }
            level.playLocalSound(baseX, baseY + 0.7, baseZ, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 0.7F, false);
        }

        // Overheat: a burst when the vents first fly open (replacing the old server-side burst,
        // now anchored to the flaps), then lazy smoke curling out for as long as they stay open.
        boolean ventBurst = state.ventOpenFired();
        float ventOpen = state.ventOpen(1.0F);
        if (ventBurst || (overheated && ventOpen > 0.2F && player.tickCount % 4 == 0)) {
            int count = ventBurst ? 4 : 1;
            for (int side = -1; side <= 1; side += 2) {
                double px = baseX + (cos * side * VENT_X + sin * VENT_Z) / 16.0;
                double py = baseY + (24.0F - VENT_Y) / 16.0;
                double pz = baseZ + (sin * side * VENT_X - cos * VENT_Z) / 16.0;
                for (int i = 0; i < count; i++) {
                    level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, py, pz,
                            player.getRandom().nextGaussian() * 0.01, 0.04, player.getRandom().nextGaussian() * 0.01);
                }
            }
        }
    }

    @SubscribeEvent
    static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof Player player) {
            STATES.remove(player.getId());
        }
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        STATES.clear();
    }
}
