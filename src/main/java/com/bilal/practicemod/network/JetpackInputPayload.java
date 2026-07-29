package com.bilal.practicemod.network;

import com.bilal.practicemod.PracticeMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Sent client -> server every client tick while the jetpack is equipped, since vanilla only syncs
// the jump-key-held state (and WASD impulses) for passengers steering a vehicle, not for a normal
// on-foot player. forward/strafe are the raw movement impulses in [-1, 1]; the flight sim rotates
// them by yaw so WASD steers relative to where the player faces. boost is the sprint-key-held state
// (vanilla drops the sprint FLAG mid-air, so we read the key directly) - the OVERDRIVE surge input.
// Sneak is NOT here: vanilla already syncs it, so both sides read player.isShiftKeyDown() directly.
public record JetpackInputPayload(boolean jumping, float forward, float strafe, boolean boost) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<JetpackInputPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "jetpack_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JetpackInputPayload> STREAM_CODEC = StreamCodec
            .composite(ByteBufCodecs.BOOL, JetpackInputPayload::jumping,
                    ByteBufCodecs.FLOAT, JetpackInputPayload::forward,
                    ByteBufCodecs.FLOAT, JetpackInputPayload::strafe,
                    ByteBufCodecs.BOOL, JetpackInputPayload::boost,
                    JetpackInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
