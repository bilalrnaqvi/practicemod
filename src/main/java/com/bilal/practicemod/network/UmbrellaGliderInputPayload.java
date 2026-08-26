package com.bilal.practicemod.network;

import com.bilal.practicemod.PracticeMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Owner steering input for the umbrella. Deployment itself is derived from the held item on both
 * sides, so nothing about the canopy state travels in this payload.
 */
public record UmbrellaGliderInputPayload(float forward, float strafe)
        implements CustomPacketPayload {
    public static final Type<UmbrellaGliderInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "umbrella_glider_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UmbrellaGliderInputPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, UmbrellaGliderInputPayload::forward,
                    ByteBufCodecs.FLOAT, UmbrellaGliderInputPayload::strafe,
                    UmbrellaGliderInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
