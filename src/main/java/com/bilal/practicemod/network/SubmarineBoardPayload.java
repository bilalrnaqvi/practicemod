package com.bilal.practicemod.network;

import com.bilal.practicemod.PracticeMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Request boarding; the server repeats the visible-hull ray and reach checks. */
public record SubmarineBoardPayload(int entityId) implements CustomPacketPayload {
    public static final Type<SubmarineBoardPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "submarine_board"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SubmarineBoardPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, SubmarineBoardPayload::entityId, SubmarineBoardPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
