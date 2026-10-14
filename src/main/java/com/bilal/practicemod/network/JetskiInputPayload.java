package com.bilal.practicemod.network;

import com.bilal.practicemod.PracticeMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record JetskiInputPayload(int entityId, float throttle, float steering, boolean boost) implements CustomPacketPayload {
    public static final Type<JetskiInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "jetski_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JetskiInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, JetskiInputPayload::entityId,
            ByteBufCodecs.FLOAT, JetskiInputPayload::throttle,
            ByteBufCodecs.FLOAT, JetskiInputPayload::steering,
            ByteBufCodecs.BOOL, JetskiInputPayload::boost, JetskiInputPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
