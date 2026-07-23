package com.bilal.practicemod.network;

import com.bilal.practicemod.PracticeMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Sent client -> server every client tick while the jetpack is equipped, since vanilla only syncs
// the jump-key-held state for passengers steering a vehicle, not for a normal on-foot player.
public record JetpackInputPayload(boolean jumping) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<JetpackInputPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "jetpack_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JetpackInputPayload> STREAM_CODEC = StreamCodec
            .composite(ByteBufCodecs.BOOL, JetpackInputPayload::jumping, JetpackInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
