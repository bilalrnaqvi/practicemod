package com.bilal.practicemod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Short-burst flight for the rocket boots. The owning client and server run the same heat clock and
 * the same normal jetpack thrust function, preserving responsive prediction without introducing a
 * second input packet. Unlike the jetpack, the boots have no fuel, sprint surge, hover, coast, or
 * landing cushion: a full continuous burst of ordinary thrust fills their heat capacity and locks
 * them until it has cooled completely (four seconds at the default config).
 */
@EventBusSubscriber(modid = PracticeMod.MODID)
public final class RocketBootsFlightHandler {
    private static final float TICKS_PER_SECOND = 20.0F;
    private static final double THRUST_SCALE = 0.5;

    private RocketBootsFlightHandler() {
    }

    public static boolean isWearingRocketBoots(Player player) {
        return player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof RocketBootsItem;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();

        // Only the owning client predicts movement. Remote clients receive the synchronized
        // ROCKET_BOOTS_THRUSTING attachment solely for their render layer.
        if (player.level().isClientSide() && !player.isLocalPlayer()) {
            return;
        }
        if (!JetpackServerConfig.SPEC.isLoaded()) {
            return;
        }

        tickRocketBoots(player);
    }

    // Package-private so tests can exercise the production state machine directly.
    static void tickRocketBoots(Player player) {
        // A worn jetpack owns flight whenever both items are equipped. This prevents their shared
        // normal-thrust routine from being applied twice and keeps the boots a genuinely weaker
        // alternative rather than a stackable jetpack upgrade.
        boolean wearing = isWearingRocketBoots(player) && !JetpackFlightHandler.isWearingJetpack(player);
        if (!wearing) {
            reset(player);
            return;
        }

        float maxHeat = Math.max(1.0F,
                (float) (double) JetpackServerConfig.ROCKET_BOOTS_BOOST_SECONDS.get() * TICKS_PER_SECOND);
        float heat = Mth.clamp(player.getData(PracticeMod.ROCKET_BOOTS_HEAT), 0.0F, maxHeat);
        boolean overheated = Boolean.TRUE.equals(player.getData(PracticeMod.ROCKET_BOOTS_OVERHEATED));
        boolean wasThrusting = Boolean.TRUE.equals(player.getData(PracticeMod.ROCKET_BOOTS_THRUSTING));

        // Elytra and creative/spectator flight already own movement. The boots still cool while one
        // of those modes is active, so switching back never preserves a frozen partial cooldown.
        boolean movementAvailable = !player.isFallFlying() && !player.getAbilities().flying;
        boolean wantsThrust = Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_THRUSTING));
        boolean thrusting = movementAvailable && wantsThrust && !overheated && heat < maxHeat;

        if (thrusting) {
            // Reuse the non-sprint jetpack controller at half strength. Acceleration and movement
            // caps are both scaled, so a sustained boots burn cannot eventually reach full-pack
            // performance.
            JetpackFlightHandler.applyNormalThrust(player, THRUST_SCALE);
            player.fallDistance = 0.0F;
            heat = Math.min(maxHeat, heat + 1.0F);
            if (heat >= maxHeat) {
                overheated = true;
                if (player.level() instanceof ServerLevel level) {
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.PLAYERS,
                            0.75F, 1.25F);
                }
            } else if (!wasThrusting && player.level() instanceof ServerLevel level) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.3F, 1.55F);
            }
        } else if (heat > 0.0F) {
            float cooldownTicks = Math.max(1.0F,
                    (float) (double) JetpackServerConfig.ROCKET_BOOTS_COOLDOWN_SECONDS.get()
                            * TICKS_PER_SECOND);
            heat = Math.max(0.0F, heat - maxHeat / cooldownTicks);
            // Reaching the configured limit is a real lockout: partial cooling cannot be feathered
            // into tiny extra pulses. A voluntarily released, non-overheated burst can be resumed.
            if (overheated && heat <= 0.0F) {
                overheated = false;
            }
        }

        setHeat(player, heat);
        setOverheated(player, overheated);
        setThrusting(player, thrusting);
    }

    private static void reset(Player player) {
        setHeat(player, 0.0F);
        setOverheated(player, false);
        setThrusting(player, false);
    }

    private static void setHeat(Player player, float heat) {
        if (Float.compare(player.getData(PracticeMod.ROCKET_BOOTS_HEAT), heat) != 0) {
            player.setData(PracticeMod.ROCKET_BOOTS_HEAT, heat);
        }
    }

    private static void setOverheated(Player player, boolean overheated) {
        if (overheated != Boolean.TRUE.equals(player.getData(PracticeMod.ROCKET_BOOTS_OVERHEATED))) {
            player.setData(PracticeMod.ROCKET_BOOTS_OVERHEATED, overheated);
        }
    }

    private static void setThrusting(Player player, boolean thrusting) {
        if (thrusting != Boolean.TRUE.equals(player.getData(PracticeMod.ROCKET_BOOTS_THRUSTING))) {
            // This attachment is synchronized. Local prediction writes it here too, while the
            // server copy supplies the authoritative state to every tracking renderer.
            player.setData(PracticeMod.ROCKET_BOOTS_THRUSTING, thrusting);
        }
    }
}
