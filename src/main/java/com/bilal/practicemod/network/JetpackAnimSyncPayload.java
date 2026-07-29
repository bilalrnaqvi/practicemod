package com.bilal.practicemod.network;

import com.bilal.practicemod.PracticeMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Sent server -> clients tracking a player so THEIR clients can animate that player's jetpack.
// Carries the effective thrust (already gated by fuel/overheat, unlike the raw input flag) plus
// the heat sim outputs; fuel travels separately via the item's network-synchronized data
// components. Sent by JetpackAnimSync only when something changed, so it's a few bytes at a few
// packets per second per flying player at most.
public record JetpackAnimSyncPayload(int entityId, boolean thrusting, boolean gliding, boolean surging,
        boolean coasting, float heat, boolean overheated) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<JetpackAnimSyncPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "jetpack_anim_sync"));

    // The five state flags travel packed into one byte rather than as five BOOLs: StreamCodec.composite
    // tops out at six components, and the bitmask is smaller on the wire anyway.
    private static final int FLAG_THRUSTING = 1;
    private static final int FLAG_GLIDING = 1 << 1;
    private static final int FLAG_SURGING = 1 << 2;
    private static final int FLAG_COASTING = 1 << 3;
    private static final int FLAG_OVERHEATED = 1 << 4;

    public static final StreamCodec<RegistryFriendlyByteBuf, JetpackAnimSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, JetpackAnimSyncPayload::entityId,
            ByteBufCodecs.BYTE, JetpackAnimSyncPayload::flags,
            ByteBufCodecs.FLOAT, JetpackAnimSyncPayload::heat,
            JetpackAnimSyncPayload::unpack);

    private byte flags() {
        return (byte) ((this.thrusting ? FLAG_THRUSTING : 0)
                | (this.gliding ? FLAG_GLIDING : 0)
                | (this.surging ? FLAG_SURGING : 0)
                | (this.coasting ? FLAG_COASTING : 0)
                | (this.overheated ? FLAG_OVERHEATED : 0));
    }

    private static JetpackAnimSyncPayload unpack(int entityId, byte flags, float heat) {
        return new JetpackAnimSyncPayload(entityId,
                (flags & FLAG_THRUSTING) != 0,
                (flags & FLAG_GLIDING) != 0,
                (flags & FLAG_SURGING) != 0,
                (flags & FLAG_COASTING) != 0,
                heat,
                (flags & FLAG_OVERHEATED) != 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
