package com.bilal.practicemod.network;

import com.bilal.practicemod.PracticeMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AtvInputPayload(int entityId, float throttle, float steering) implements CustomPacketPayload {
    public static final Type<AtvInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "atv_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AtvInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AtvInputPayload::entityId,
            ByteBufCodecs.FLOAT, AtvInputPayload::throttle,
            ByteBufCodecs.FLOAT, AtvInputPayload::steering,
            AtvInputPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
