package com.bilal.practicemod.network;

import com.bilal.practicemod.PracticeMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Only input intent crosses the wire; the server owns movement and furniture selection. */
public record SubmarineCabinPayload(int entityId, int action, float forward, float strafe) implements CustomPacketPayload {
    public static final Type<SubmarineCabinPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "submarine_cabin"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SubmarineCabinPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SubmarineCabinPayload::entityId,
            ByteBufCodecs.VAR_INT, SubmarineCabinPayload::action,
            ByteBufCodecs.FLOAT, SubmarineCabinPayload::forward,
            ByteBufCodecs.FLOAT, SubmarineCabinPayload::strafe, SubmarineCabinPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
