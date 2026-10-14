package com.bilal.practicemod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/** Transient server-validated anchor, synchronized to the owner and tracking viewers. */
public record GrappleState(boolean active, Vec3 anchor, BlockPos block) {
    public static final GrappleState IDLE = new GrappleState(false, Vec3.ZERO, BlockPos.ZERO);
    public static final StreamCodec<RegistryFriendlyByteBuf, GrappleState> STREAM_CODEC = StreamCodec.of(
            (buffer, state) -> {
                buffer.writeBoolean(state.active());
                buffer.writeDouble(state.anchor().x);
                buffer.writeDouble(state.anchor().y);
                buffer.writeDouble(state.anchor().z);
                buffer.writeBlockPos(state.block());
            }, buffer -> new GrappleState(buffer.readBoolean(),
                    new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()), buffer.readBlockPos()));
}
