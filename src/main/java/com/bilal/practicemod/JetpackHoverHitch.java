package com.bilal.practicemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// The hover pack's discrete engine HITCHES: an occasional stumble in the thrust that makes the
// jetpack feel like a machine rather than a hover platform. Purely the owning client's business -
// it rolls them, applies the impulse to its own velocity, and reports the event upward as a cosmetic
// cue (JetpackInputPayload -> JetpackAnimSyncPayload) so other players see the twitch. The server
// neither rolls nor applies them; both sides' altitude springs converge on the same target Y, so the
// divergence a hitch opens up is transient and far under a block.
//
// WHY THIS IS SAFE TO SHIP INTO A BUILDING MODE
//
// The whole feature is flavor and must never cost the player block-placement accuracy, which comes
// down to three properties, all of them load-bearing:
//
//  1. The hitch probability scales with horizontal speed and goes to zero when the player is parked.
//     A player standing still to place a block feels the pack go quiet - the hitches only exist while
//     they are travelling, when a few hundredths of a block cannot matter. This is the property that
//     makes the rest of it safe, not a flourish on top of it.
//  2. The impulse only ever moves the player's POSITION. A translated pick ray hits a distant surface
//     offset by exactly the eye's displacement (a hundredth of a block), while an angular nudge would
//     grow with reach. Nothing here touches look direction.
//  3. The camera reaction is ROLL only, for the same reason - roll spins the view around the pick ray
//     and leaves it aimed exactly where it was. Pitch and yaw are off limits at any amplitude.
//
// The altitude spring in JetpackFlightHandler reclaims the vertical impulse completely (the player
// ends at the altitude they started at) and hoverStopDrag brakes away the horizontal one, so there is
// no net drift in either axis. The magnitudes are also orders of magnitude below vanilla's movement
// validation: the server's "moved too quickly" check trips on 100 blocks^2 of unexplained movement,
// and a full hitch is worth well under 0.15 blocks.
@EventBusSubscriber(modid = PracticeMod.MODID, value = Dist.CLIENT)
public final class JetpackHoverHitch {

    // The impulse decays to ~3% by tick 8, so it is spent well inside the spring's ~10-tick recovery.
    private static final float LIFETIME_TICKS = 8.0F;
    private static final float DECAY = 0.45F;

    // Ticks since the current hitch fired, or -1 when idle. Only ever one at a time - a fresh roll
    // restarts the clock rather than stacking, so amplitude cannot compound.
    private static float age = -1.0F;

    // Unit horizontal direction of the current hitch, and the sign of its camera roll.
    private static double dirX = 1.0;
    private static double dirZ;
    private static float rollSign = 1.0F;

    private JetpackHoverHitch() {
    }

    // Rolls and applies the local player's hitch. Called from JetpackClientHandler on
    // ClientTickEvent.Pre, i.e. BEFORE the player's own tick: the impulse lands on the velocity the
    // hover controller committed at the end of last tick, vanilla's move carries it, and this tick's
    // controller pass then sees it in the realized position delta and starts damping it out. Returns
    // whether a hitch fired this tick, for the input payload to relay.
    public static boolean tick(LocalPlayer player) {
        if (age >= 0.0F) {
            age = age >= LIFETIME_TICKS ? -1.0F : age + 1.0F;
        }

        // JETPACK_HOVERING is the flight sim's gated result from last tick (armed, airborne, fuelled,
        // un-stalled). One tick of lag on a flavor effect is not worth recomputing the gate here.
        if (!JetpackServerConfig.SPEC.isLoaded()
                || !Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_HOVERING))) {
            age = -1.0F;
            return false;
        }

        boolean fired = roll(player);

        if (age >= 0.0F) {
            double falloff = Math.exp(-age * DECAY);
            double vertical = -JetpackServerConfig.HOVER_HITCH_VERTICAL_KICK.get() * falloff;
            double horizontal = JetpackServerConfig.HOVER_HITCH_HORIZONTAL_KICK.get() * falloff;
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.add(dirX * horizontal, vertical, dirZ * horizontal));
        }
        return fired;
    }

    // A per-tick Poisson event: a constant probability, so the gaps between hitches are memoryless
    // and never settle into something the player can anticipate as a rhythm. The probability is
    // scaled linearly by horizontal speed against hoverHorizontalCap - see the class comment for why
    // that scaling is the safety property.
    private static boolean roll(LocalPlayer player) {
        double cap = JetpackServerConfig.HOVER_HORIZONTAL_CAP.get();
        if (cap <= 0.0) {
            return false;
        }
        Vec3 motion = player.getDeltaMovement();
        double speedFraction = Mth.clamp(Math.hypot(motion.x, motion.z) / cap, 0.0, 1.0);
        double chance = JetpackServerConfig.HOVER_HITCH_CHANCE_PER_TICK.get() * speedFraction;
        if (chance <= 0.0 || player.getRandom().nextDouble() >= chance) {
            return false;
        }

        double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
        dirX = Math.cos(angle);
        dirZ = Math.sin(angle);
        rollSign = player.getRandom().nextBoolean() ? 1.0F : -1.0F;
        age = 0.0F;
        // The cue the animation layer and the server relay both read; JetpackAnimationController
        // consumes it later this same tick.
        player.setData(PracticeMod.JETPACK_HITCH, true);
        return true;
    }

    // A brief camera ROLL on each hitch, decaying on the same curve as the impulse. Roll only, ever:
    // pitch and yaw aim the block-placement ray, and rolling the view leaves that ray untouched.
    // Amplitude 0 (the config default is 1.5 degrees) disables camera motion entirely without
    // affecting the physical hitch or its sound and particles.
    @SubscribeEvent
    static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (age < 0.0F) {
            return;
        }
        double amplitude = JetpackClientConfig.HOVER_HITCH_CAMERA_ROLL_DEGREES.get();
        // Third person already shows the hitch on the body and the nozzles; rolling the world around
        // an off-body camera just reads as a bug.
        if (amplitude <= 0.0 || !Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            return;
        }
        float sampleAge = age + (float) event.getPartialTick();
        event.setRoll(event.getRoll() + rollSign * (float) amplitude * (float) Math.exp(-sampleAge * DECAY));
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        age = -1.0F;
    }
}
