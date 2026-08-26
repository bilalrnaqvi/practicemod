package com.bilal.practicemod.network;

import com.bilal.practicemod.JetpackFlightHandler;
import com.bilal.practicemod.JetpackMenu;
import com.bilal.practicemod.PracticeMod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class JetpackNetworking {
    public static void register(RegisterPayloadHandlersEvent event) {
        // Bumped when the umbrella input payload dropped its deployment flag, so a stale client
        // fails the handshake cleanly instead of desyncing the byte stream.
        PayloadRegistrar registrar = event.registrar("8");
        registrar.playToServer(JetpackInputPayload.TYPE, JetpackInputPayload.STREAM_CODEC, JetpackNetworking::handleJetpackInput);
        registrar.playToServer(OpenJetpackMenuPayload.TYPE, OpenJetpackMenuPayload.STREAM_CODEC, JetpackNetworking::handleOpenJetpackMenu);
        registrar.playToServer(UmbrellaGliderInputPayload.TYPE, UmbrellaGliderInputPayload.STREAM_CODEC,
                JetpackNetworking::handleUmbrellaGliderInput);
        registrar.playToClient(JetpackAnimSyncPayload.TYPE, JetpackAnimSyncPayload.STREAM_CODEC, JetpackNetworking::handleAnimSync);
    }

    private static void handleJetpackInput(JetpackInputPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer serverPlayer) {
            serverPlayer.setData(PracticeMod.JETPACK_THRUSTING, payload.jumping());
            // Don't trust client-supplied floats: clamp to the valid impulse range before use.
            // NaN survives Mth.clamp, so scrub it too - it would poison delta movement permanently.
            serverPlayer.setData(PracticeMod.JETPACK_FORWARD, sanitizeImpulse(payload.forward()));
            serverPlayer.setData(PracticeMod.JETPACK_STRAFE, sanitizeImpulse(payload.strafe()));
            serverPlayer.setData(PracticeMod.JETPACK_OVERDRIVE, payload.boost());
            serverPlayer.setData(PracticeMod.JETPACK_SNEAKING, payload.sneaking());
            serverPlayer.setData(PracticeMod.JETPACK_HOVER_MODE, payload.hoverMode());
            // Hitches are cosmetic and client-owned; the server only relays them. Latch rather than
            // assign, so that if two input packets land in one server tick the pending twitch isn't
            // cleared by the second before JetpackFlightHandler has broadcast it.
            if (payload.hitch()) {
                serverPlayer.setData(PracticeMod.JETPACK_HITCH, true);
            }
        }
    }

    private static float sanitizeImpulse(float value) {
        return Float.isNaN(value) ? 0.0F : Mth.clamp(value, -1.0F, 1.0F);
    }

    private static void handleUmbrellaGliderInput(UmbrellaGliderInputPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        serverPlayer.setData(PracticeMod.UMBRELLA_GLIDER_FORWARD, sanitizeImpulse(payload.forward()));
        serverPlayer.setData(PracticeMod.UMBRELLA_GLIDER_STRAFE, sanitizeImpulse(payload.strafe()));
    }

    private static void handleOpenJetpackMenu(OpenJetpackMenuPayload payload, IPayloadContext context) {
        // The keybind is only a request; the server decides whether a jetpack is actually worn.
        if (context.player() instanceof ServerPlayer serverPlayer && JetpackFlightHandler.isWearingJetpack(serverPlayer)) {
            JetpackMenu.open(serverPlayer);
        }
    }

    // Stores a remote player's jetpack state into the same attachments the flight sim uses locally,
    // so JetpackAnimationController reads local and remote players identically. The local player is
    // skipped: it predicts its own state, and the payload isn't sent to it anyway.
    private static void handleAnimSync(JetpackAnimSyncPayload payload, IPayloadContext context) {
        if (context.player().level().getEntity(payload.entityId()) instanceof Player target
                && !target.isLocalPlayer()) {
            target.setData(PracticeMod.JETPACK_THRUSTING, payload.thrusting());
            target.setData(PracticeMod.JETPACK_GLIDING, payload.gliding());
            target.setData(PracticeMod.JETPACK_SURGING, payload.surging());
            target.setData(PracticeMod.JETPACK_COASTING, payload.coasting());
            target.setData(PracticeMod.JETPACK_HOVERING, payload.hovering());
            target.setData(PracticeMod.JETPACK_HEAT, payload.heat());
            target.setData(PracticeMod.JETPACK_OVERHEATED, payload.overheated());
            // Latch only; JetpackAnimationController consumes it on its next tick for this player.
            // Purely an animation cue - a remote player's hitch never touches our physics.
            if (payload.hitch()) {
                target.setData(PracticeMod.JETPACK_HITCH, true);
            }
        }
    }
}
