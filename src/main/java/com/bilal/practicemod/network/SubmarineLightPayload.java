package com.bilal.practicemod.network;

import com.bilal.practicemod.PracticeMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One request per press; the server verifies the pilot and owns the toggle. */
public record SubmarineLightPayload(int entityId) implements CustomPacketPayload {
    public static final Type<SubmarineLightPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "submarine_light"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SubmarineLightPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, SubmarineLightPayload::entityId, SubmarineLightPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
