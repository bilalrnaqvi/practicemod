package com.bilal.practicemod.network;

import com.bilal.practicemod.PracticeMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SubmarineInputPayload(int entityId, boolean rise, boolean descend, boolean exit)
        implements CustomPacketPayload {
    public static final Type<SubmarineInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "submarine_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SubmarineInputPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, SubmarineInputPayload::entityId,
                    ByteBufCodecs.BOOL, SubmarineInputPayload::rise,
                    ByteBufCodecs.BOOL, SubmarineInputPayload::descend,
                    ByteBufCodecs.BOOL, SubmarineInputPayload::exit, SubmarineInputPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
