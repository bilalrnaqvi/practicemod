package com.bilal.practicemod.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.bilal.practicemod.PracticeMod;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Server-side broadcaster for jetpack animation state. The thrust/heat/overheat attachments are
// simulated per side and never synced by NeoForge, so without this, other clients would have no
// idea a player's jetpack is firing. sync() is called explicitly at the end of the flight sim
// (JetpackFlightHandler) - not from its own tick subscriber - so it always sees this tick's final
// values, and only sends when something observable changed (heat is quantized into buckets so
// gradual drift doesn't spam packets).
//
// Deliberately sendToPlayersTrackingEntity, NOT ...AndSelf: the local player predicts its own
// state, and correcting it mid-prediction would make its own animation stutter.
@EventBusSubscriber(modid = PracticeMod.MODID)
public final class JetpackAnimSync {

    // 2 heat units per bucket = 2% granularity on the default 0..100 scale: fine enough for smooth
    // remote animation, coarse enough that a full heat-up is only a handful of packets per second.
    private static final float HEAT_BUCKET_SIZE = 2.0F;

    private static final class Sent {
        boolean known;
        boolean thrusting;
        boolean gliding;
        boolean surging;
        boolean coasting;
        boolean hovering;
        int heatBucket;
        boolean overheated;
    }

    private static final Map<UUID, Sent> LAST_SENT = new HashMap<>();

    // Called from JetpackFlightHandler after each server-side sim tick with the effective thrust
    // (wants && fuel && !overheated) plus the glide/surge/coast/hover
    // states - the values remote animations need, not the raw input flags. `hitch` is the one-shot
    // cosmetic event the owning client relayed up this tick.
    public static void sync(ServerPlayer player, boolean thrusting, boolean gliding,
            boolean surging, boolean coasting, boolean hovering, boolean hitch) {
        float heat = player.getData(PracticeMod.JETPACK_HEAT);
        boolean overheated = Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_OVERHEATED));
        int heatBucket = (int) (heat / HEAT_BUCKET_SIZE);

        Sent last = LAST_SENT.computeIfAbsent(player.getUUID(), key -> new Sent());
        // A hitch always forces a send: it is a one-tick event, so the change filter would swallow it
        // whenever it lands in the middle of an otherwise steady hover - which is exactly when hitches
        // happen.
        if (!hitch && last.known && last.thrusting == thrusting && last.gliding == gliding
                && last.surging == surging && last.coasting == coasting && last.hovering == hovering
                && last.heatBucket == heatBucket && last.overheated == overheated) {
            return;
        }
        last.known = true;
        last.thrusting = thrusting;
        last.gliding = gliding;
        last.surging = surging;
        last.coasting = coasting;
        last.hovering = hovering;
        last.heatBucket = heatBucket;
        last.overheated = overheated;

        PacketDistributor.sendToPlayersTrackingEntity(player, new JetpackAnimSyncPayload(player.getId(),
                thrusting, gliding, surging, coasting, hovering, hitch, heat, overheated));
    }

    // A player that just came into view (or just joined) gets the tracked player's current state
    // immediately, so mid-flight or mid-overheat arrivals don't render a dead jetpack until the
    // next change.
    @SubscribeEvent
    static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target
                && event.getEntity() instanceof ServerPlayer viewer) {
            Sent last = LAST_SENT.get(target.getUUID());
            boolean thrusting = last != null && last.thrusting;
            // No hitch on a catch-up packet: it is a momentary event, not part of the state a newly
            // arriving viewer needs to be brought up to date on.
            PacketDistributor.sendToPlayer(viewer, new JetpackAnimSyncPayload(target.getId(), thrusting,
                    Boolean.TRUE.equals(target.getData(PracticeMod.JETPACK_GLIDING)),
                    Boolean.TRUE.equals(target.getData(PracticeMod.JETPACK_SURGING)),
                    Boolean.TRUE.equals(target.getData(PracticeMod.JETPACK_COASTING)),
                    Boolean.TRUE.equals(target.getData(PracticeMod.JETPACK_HOVERING)),
                    false,
                    target.getData(PracticeMod.JETPACK_HEAT),
                    Boolean.TRUE.equals(target.getData(PracticeMod.JETPACK_OVERHEATED))));
        }
    }

    @SubscribeEvent
    static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }
}
