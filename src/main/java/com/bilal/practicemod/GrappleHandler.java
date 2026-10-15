package com.bilal.practicemod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = PracticeMod.MODID)
public final class GrappleHandler {
    private GrappleHandler() {}

    static boolean available(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isPassenger()
                && !player.isSleeping() && !player.isInWater() && !player.isInLava()
                && !player.isFallFlying() && !player.getAbilities().flying
                && !JetpackFlightHandler.isWearingJetpack(player)
                && !Boolean.TRUE.equals(player.getData(PracticeMod.ROCKET_BOOTS_THRUSTING));
    }

    static boolean start(Player player) {
        if (!available(player)) return reject(player);
        GrappleState target = GrappleTargeting.find(player);
        if (!target.active()) return reject(player);
        player.setData(PracticeMod.GRAPPLE_STATE, target);
        player.setData(PracticeMod.GRAPPLE_TICKS, 0);
        player.setData(PracticeMod.UMBRELLA_GLIDER_DEPLOYED, false);
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.7F, 1.4F);
        }
        return true;
    }

    private static boolean reject(Player player) {
        // Explicitly sync rejection even when the server was already idle: a predicting owner
        // may have seen a different surface while the use packet was in flight.
        player.setData(PracticeMod.GRAPPLE_STATE, GrappleState.IDLE);
        return false;
    }

    public static boolean active(Player player) { return player.getData(PracticeMod.GRAPPLE_STATE).active(); }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() && !player.isLocalPlayer()) return;
        tick(player);
    }

    static void tick(Player player) {
        if (!available(player) || player.onGround()) {
            player.setData(PracticeMod.GRAPPLE_GLIDE_CAP, 0.0F);
            player.setData(PracticeMod.GRAPPLE_COAST_TICKS, 0);
        } else if (player.getData(PracticeMod.GRAPPLE_COAST_TICKS) > 0) {
            player.setData(PracticeMod.GRAPPLE_COAST_TICKS, player.getData(PracticeMod.GRAPPLE_COAST_TICKS) - 1);
        } else {
            player.setData(PracticeMod.GRAPPLE_GLIDE_CAP, 0.0F);
        }
        GrappleState state = player.getData(PracticeMod.GRAPPLE_STATE);
        if (!state.active()) return;
        int ticks = player.getData(PracticeMod.GRAPPLE_TICKS) + 1;
        player.setData(PracticeMod.GRAPPLE_TICKS, ticks);
        if (!available(player) || !player.isUsingItem()
                || !player.getUseItem().is(PracticeMod.GRAPPLING_HOOK.get())
                || ticks > 160 || !anchorValid(player, state)) {
            release(player);
            return;
        }
        Vec3 movement = player.getKnownMovement();
        if (GrapplePhysics.shouldRelease(player.position(), state.anchor(), movement)) {
            if (GrappleTargeting.clearExit(player, state)) {
                Vec3 launch = GrapplePhysics.launch(movement, state.outward());
                player.setDeltaMovement(launch);
                release(player, launch);
            } else {
                release(player);
            }
            return;
        }
        Vec3 next = GrapplePhysics.pull(movement, player.position(), state);
        // Never force the player through geometry. If the approach touches a wall, climb on
        // its outside; newly blocked headroom cancels instead of granting a through-wall launch.
        if (!player.level().noCollision(player, player.getBoundingBox().expandTowards(next))) {
            next = new Vec3(0, Math.max(0.3, next.y), 0);
            if (!player.level().noCollision(player, player.getBoundingBox().expandTowards(next))) {
                release(player);
                return;
            }
        }
        player.setDeltaMovement(next);
        player.fallDistance = 0;
    }

    static boolean anchorValid(Player player, GrappleState state) {
        if (!player.level().hasChunkAt(state.block())
                || player.level().getBlockState(state.block()).getCollisionShape(player.level(), state.block()).isEmpty()
                || player.getEyePosition().distanceTo(state.anchor()) > GrapplePhysics.RANGE + 4) return false;
        return GrappleTargeting.visible(player, state);
    }

    static void release(Player player) {
        release(player, player.getKnownMovement());
    }

    private static void release(Player player, Vec3 retainedVelocity) {
        if (!active(player)) return;
        player.setData(PracticeMod.GRAPPLE_STATE, GrappleState.IDLE);
        // Do not zero or replace velocity on detachment. The ordinary movement controller owns
        // the ballistic arc; the umbrella can later spend this finite excess-speed allowance.
        double speed = retainedVelocity.horizontalDistance();
        if (available(player) && Double.isFinite(speed) && speed > UmbrellaGliderPhysics.HORIZONTAL_SPEED_CAP) {
            player.setData(PracticeMod.GRAPPLE_GLIDE_CAP, (float) Math.min(GrapplePhysics.MAX_SPEED, speed));
            player.setData(PracticeMod.GRAPPLE_COAST_TICKS, 200);
        }
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.blockPosition(), SoundEvents.TRIPWIRE_DETACH, SoundSource.PLAYERS, 0.5F, 1.3F);
        }
    }
}
