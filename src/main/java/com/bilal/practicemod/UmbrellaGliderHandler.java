package com.bilal.practicemod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Shared umbrella movement simulation. Deployment is derived state rather than an input edge:
 * holding the canopy in either hand arms it, and the canopy opens on its own once the player is
 * genuinely falling. Because the rule is a pure function of state both sides already have, the
 * owning client and the server reach the same answer every tick without a request/correct packet
 * exchange; the synchronized attachment still carries the result to remote viewers for rendering.
 */
@EventBusSubscriber(modid = PracticeMod.MODID)
public final class UmbrellaGliderHandler {
    private static final double MIN_DEPLOY_DESCENT = -0.05;
    // A vanilla jump peaks around 1.25 blocks, so its descent crosses MIN_DEPLOY_DESCENT one tick
    // after the apex. Requiring real accumulated fall distance on the opening edge is what keeps the
    // canopy from twitching open on every hop now that no keypress gates it.
    private static final float MIN_DEPLOY_FALL_DISTANCE = 1.5F;
    private static final double HORIZONTAL_ACCELERATION = 0.035;
    private static final double STEERING_DRAG = 0.995;
    private static final double IDLE_DRAG = 0.985;

    private UmbrellaGliderHandler() {
    }

    /**
     * The selected hotbar slot or the offhand. {@code getMainHandItem} resolves to the inventory's
     * selected slot, which the server tracks from the client's carried-item packet, so this reads
     * identically and authoritatively on both sides.
     */
    public static boolean isHoldingUmbrella(Player player) {
        return player.getMainHandItem().is(PracticeMod.UMBRELLA_GLIDER.get())
                || player.getOffhandItem().is(PracticeMod.UMBRELLA_GLIDER.get());
    }

    public static boolean isDeployed(Player player) {
        return Boolean.TRUE.equals(player.getData(PracticeMod.UMBRELLA_GLIDER_DEPLOYED));
    }

    /** A strict activation gate: held in hand, plus a real, ordinary fall already underway. */
    public static boolean canDeploy(Player player) {
        return isValidBaseState(player)
                && player.getKnownMovement().y < MIN_DEPLOY_DESCENT
                && player.fallDistance > MIN_DEPLOY_FALL_DISTANCE;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();

        // Remote players receive the state for rendering only. Their motion belongs to their own
        // client and the server, not to another viewer's prediction loop.
        if (player.level().isClientSide() && !player.isLocalPlayer()) {
            return;
        }

        tickGlider(player);
    }

    // Package-private so the NeoForge GameTest can drive exactly the production state machine.
    static void tickGlider(Player player) {
        if (!isDeployed(player)) {
            if (!canDeploy(player)) {
                return;
            }
            open(player);
        }
        if (!isValidGlidingState(player)) {
            close(player);
            return;
        }

        applyGlideMovement(player);
        // Protection exists only while the synchronized deployed state is true. Clearing the
        // accumulated distance each active tick means closing in mid-air starts a fresh ordinary
        // fall rather than retaining protection from the earlier controlled descent.
        player.fallDistance = 0.0F;
    }

    private static boolean isValidGlidingState(Player player) {
        return isValidBaseState(player)
                && player.getKnownMovement().y <= 0.0;
    }

    private static boolean isValidBaseState(Player player) {
        return isHoldingUmbrella(player)
                && player.isAlive()
                && !player.onGround()
                && !player.isInWater()
                && !player.isInLava()
                && !player.isSwimming()
                && !player.onClimbable()
                && !player.isPassenger()
                && !player.isSleeping()
                && !player.isAutoSpinAttack()
                && !player.isFallFlying()
                && !player.getAbilities().flying
                && !player.isSpectator()
                && !JetpackFlightHandler.isWearingJetpack(player);
    }

    private static void open(Player player) {
        player.setData(PracticeMod.UMBRELLA_GLIDER_DEPLOYED, true);

        // Server-side only: the owning client derives the very same opening edge locally this tick,
        // and playing it on both would double up the sound for the player who deployed.
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.7F, 1.35F);
        }
    }

    private static void applyGlideMovement(Player player) {
        float forward = player.getData(PracticeMod.UMBRELLA_GLIDER_FORWARD);
        float strafe = player.getData(PracticeMod.UMBRELLA_GLIDER_STRAFE);
        boolean steering = forward != 0.0F || strafe != 0.0F;

        // For an owning client getKnownMovement is ordinary delta movement. A ServerPlayer does not
        // run travel(), however; it returns the last displacement accepted from a client movement
        // packet. Using this API is what lets the server validate a real fall and simulate from the
        // player's observed velocity instead of a stale jump impulse.
        Vec3 movement = player.getKnownMovement();
        double drag = steering ? STEERING_DRAG : IDLE_DRAG;
        // A malformed motion vector should never survive the controller and poison player movement.
        double x = Double.isFinite(movement.x) ? movement.x : 0.0;
        double z = Double.isFinite(movement.z) ? movement.z : 0.0;
        if (steering) {
            // This is Entity#moveRelative's yaw transform applied to the observed movement vector,
            // so the local prediction and ServerPlayer path use exactly the same input math.
            double inputLength = Math.hypot(strafe, forward);
            double inputScale = inputLength > 1.0 ? 1.0 / inputLength : 1.0;
            double localX = strafe * inputScale * HORIZONTAL_ACCELERATION;
            double localZ = forward * inputScale * HORIZONTAL_ACCELERATION;
            float yaw = player.getYRot() * Mth.DEG_TO_RAD;
            float sin = Mth.sin(yaw);
            float cos = Mth.cos(yaw);
            x += localX * cos - localZ * sin;
            z += localZ * cos + localX * sin;
        }
        x *= drag;
        z *= drag;
        double capScale = UmbrellaGliderPhysics.horizontalCapScale(x, z);
        player.setDeltaMovement(x * capScale,
                UmbrellaGliderPhysics.nextVerticalVelocity(movement.y), z * capScale);
    }

    private static void close(Player player) {
        if (!isDeployed(player)) {
            return;
        }
        player.setData(PracticeMod.UMBRELLA_GLIDER_DEPLOYED, false);
    }

    @SubscribeEvent
    static void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !isDeployed(player)
                || !isHoldingUmbrella(player)) {
            return;
        }

        event.setCanceled(true);
        player.fallDistance = 0.0F;
        // Landing is an immediate close edge, not a protection latch that survives until a later
        // tick. Small landings that do not raise LivingFallEvent are closed by the tick validity gate.
        close(player);
    }
}
