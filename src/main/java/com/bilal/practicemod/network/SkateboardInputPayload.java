package com.bilal.practicemod.network;

import com.bilal.practicemod.PracticeMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SkateboardInputPayload(int entityId, int buttons, float steering, float yaw)
        implements CustomPacketPayload {
    public static final Type<SkateboardInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "skateboard_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkateboardInputPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, SkateboardInputPayload::entityId,
                    ByteBufCodecs.VAR_INT, SkateboardInputPayload::buttons,
                    ByteBufCodecs.FLOAT, SkateboardInputPayload::steering,
                    ByteBufCodecs.FLOAT, SkateboardInputPayload::yaw, SkateboardInputPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
