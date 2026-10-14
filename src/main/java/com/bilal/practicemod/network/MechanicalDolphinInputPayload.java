package com.bilal.practicemod.network;

import com.bilal.practicemod.PracticeMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MechanicalDolphinInputPayload(int entityId, float throttle, float yaw, float pitch, boolean rise) implements CustomPacketPayload {
    public static final Type<MechanicalDolphinInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "mechanical_dolphin_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MechanicalDolphinInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MechanicalDolphinInputPayload::entityId,
            ByteBufCodecs.FLOAT, MechanicalDolphinInputPayload::throttle,
            ByteBufCodecs.FLOAT, MechanicalDolphinInputPayload::yaw,
            ByteBufCodecs.FLOAT, MechanicalDolphinInputPayload::pitch,
            ByteBufCodecs.BOOL, MechanicalDolphinInputPayload::rise, MechanicalDolphinInputPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
