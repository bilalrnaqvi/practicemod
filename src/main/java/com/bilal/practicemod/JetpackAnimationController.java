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

    // SCAVPACK anchor points in body-part space (/16 = blocks, model y 24 = feet). The imported
    // Blockbench asset uses y-up coordinates; these values are its y positions converted to the
    // HumanoidModel's y-down space. Relief ports sit at the crown of each tank and the fan outlet is
    // centered low on the exposed chassis.
    private static final float NOZZLE_X = 7.0F * JetpackModel.PACK_SCALE; // +- mirrored per side
    private static final float NOZZLE_EXIT_Y = JetpackModel.PACK_PIVOT_Y
            + (19.65F - JetpackModel.PACK_PIVOT_Y) * JetpackModel.PACK_SCALE;
    private static final float NOZZLE_Z = JetpackModel.PACK_PIVOT_Z
            + (4.0F - JetpackModel.PACK_PIVOT_Z) * JetpackModel.PACK_SCALE;

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
        // gameplay attachment runs 0..heatMax (default 200), so normalize here at the boundary.
        float heatMax = JetpackServerConfig.SPEC.isLoaded()
                ? (float) (double) JetpackServerConfig.HEAT_MAX.get()
                : 200.0F;
        float heat = Mth.clamp(player.getData(PracticeMod.JETPACK_HEAT) / heatMax, 0.0F, 1.0F);
        boolean overheated = Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_OVERHEATED));
        boolean fuelEmpty = wearing && !JetpackFuelHandler.hasFuelSupply(chest);
        boolean lowFuel = wearing && !fuelEmpty
                && JetpackFuelHandler.getStoredBlazePowder(chest) == 0
                && JetpackFuelHandler.getFuelFraction(chest) <= LOW_FUEL_FRACTION;
        // The glide/surge/coast/hover flags are written by the flight sim for the local player and by
        // the anim sync payload for remote players - the same dual path as thrusting. They arrive
        // already gated (fuel, overheat, boost profile), so they need no re-checking here.
        boolean gliding = wearing && Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_GLIDING));
        boolean surging = wearing && Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_SURGING));
        boolean coasting = wearing && Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_COASTING));
        boolean hovering = wearing && Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_HOVERING));
        // Effective thrust, mirroring JetpackFlightHandler's gating - including hover's precedence
        // over it. Red-zone overheat locks thrust entirely. For remote players the synced flag is
        // already effective; reapplying the gate keeps this uniform for the local player, whose
        // attachment holds raw jump input (altitude trim rather than throttle while hovering).
        boolean thrusting = wearing && !overheated && !fuelEmpty && !hovering
                && Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_THRUSTING));
        // One-shot hitch cue, latched by JetpackHoverHitch (local player) or the anim sync handler
        // (remote players). Consumed here so it fires exactly one animation event.
        boolean hitch = Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_HITCH));
        if (hitch) {
            player.setData(PracticeMod.JETPACK_HITCH, false);
        }

        // Horizontal velocity from position deltas rather than getDeltaMovement: the deltas are what
        // actually moved on screen this tick, which also makes them valid for remote players
        // (whose delta-movement vanilla does not reliably replicate). The signed components go
        // through as well as the magnitude - the body lean has to aim along travel, and a scalar
        // speed cannot tell a strafe from a forward run.
        double dx = player.getX() - player.xo;
        double dz = player.getZ() - player.zo;
        float horizontalSpeed = (float) Math.sqrt(dx * dx + dz * dz);
        // The vertical component of that same delta, for the hover climb/descend attitude. Measured
        // rather than read off the trim keys for exactly the reason above: it needs no new synced
        // state, so a remote player's ascent and descent animate from what their body is observed to
        // do. It also means the pose follows the altitude spring's real settling curve, hum and all,
        // instead of snapping between three discrete key states.
        double dy = player.getY() - player.yo;

        state.tick(wearing, thrusting, gliding, surging, coasting, hovering, hitch, heat, overheated,
                lowFuel, fuelEmpty, horizontalSpeed, (float) dx, (float) dz, (float) dy);

        if (wearing) {
            emitFeedback(player, state, gliding, coasting);
        }
    }

    // Secondary particles and sounds triggered from the same per-tick edges as the model animation.
    // The primary normal/boost/hover visuals are rendered in JetpackEffectLayer, where they can use
    // the actual animated ModelPart hierarchy instead of these coarse body-space feedback anchors.
    private static void emitFeedback(Player player, JetpackAnimationState state,
            boolean gliding, boolean coasting) {
        Level level = player.level();
        float flare = state.flare(1.0F); // end-of-tick sample
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        float sin = Mth.sin(yaw);
        float cos = Mth.cos(yaw);
        double baseX = player.getX();
        double baseY = player.getY();
        double baseZ = player.getZ();

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

        // Coast plume: the engine is off, but the pack is still carrying the wearer - so it reads as
        // smoke without fire, the deliberate inverse of the thrust exhaust above. That contrast is
        // the whole point: it tells the player at a glance that they are riding momentum rather than
        // burning for it. Particles inherit only a fraction of the wearer's velocity, so they fall
        // behind into a trail instead of travelling along in formation.
        //
        // The flare threshold is the exact complement of the thrust exhaust's above, so the two can
        // never fire on the same tick: the flare eases to zero over RELEASE_TICKS after a cut, so the
        // fire dies and the smoke picks up in its place. Gated on !gliding as well, so the near-ground
        // cushion keeps its own smoldering pilot light rather than being overdrawn by the plume.
        if (coasting && !gliding && flare <= 0.1F && player.tickCount % 3 == 0) {
            Vec3 motion = player.getDeltaMovement();
            for (int side = -1; side <= 1; side += 2) {
                double px = baseX + (cos * side * NOZZLE_X + sin * NOZZLE_Z) / 16.0;
                double py = baseY + (24.0F - NOZZLE_EXIT_Y) / 16.0;
                double pz = baseZ + (sin * side * NOZZLE_X - cos * NOZZLE_Z) / 16.0;
                level.addParticle(ParticleTypes.SMOKE, px, py, pz,
                        motion.x * 0.4, motion.y * 0.4 - 0.01, motion.z * 0.4);
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

        // Hover engine hitch: a quieter, lower cousin of the sputter cough - one small puff and a
        // short low sputter on the same tick and the same (alternating) nozzle as the model's hitch
        // twitch, so the sound, the smoke, the twitch and the physical stumble all read as one event.
        // Muted by hoverHitchFeedback for players who want the stumble without the noise; the twitch
        // and the impulse are deliberately not gated on it.
        if (state.hitchFired() && JetpackClientConfig.HOVER_HITCH_FEEDBACK.get()) {
            int side = state.hitchSide();
            double px = baseX + (cos * side * NOZZLE_X + sin * NOZZLE_Z) / 16.0;
            double py = baseY + (24.0F - NOZZLE_EXIT_Y) / 16.0;
            double pz = baseZ + (sin * side * NOZZLE_X - cos * NOZZLE_Z) / 16.0;
            for (int i = 0; i < 2; i++) {
                level.addParticle(ParticleTypes.SMOKE, px, py, pz,
                        player.getRandom().nextGaussian() * 0.01, -0.03, player.getRandom().nextGaussian() * 0.01);
            }
            level.playLocalSound(px, py, pz, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.18F, 1.1F, false);
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

        // Overheat is now one authored emissive nozzle texture in JetpackEffectLayer. Keep only the
        // mechanical bloom's two synchronized audio cues here; removing the fire/dust/smoke/poof
        // particles prevents competing silhouettes and approximate body-space origins.
        boolean thermalBloomBurst = state.ventOpenFired();
        if (thermalBloomBurst) {
            level.playLocalSound(baseX, baseY + 1.0, baseZ, SoundEvents.IRON_TRAPDOOR_OPEN,
                    SoundSource.PLAYERS, 0.7F, 0.7F, false);
            level.playLocalSound(baseX, baseY + 0.8, baseZ, SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.PLAYERS, 0.45F, 0.8F, false);
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
