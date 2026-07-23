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
public record JetpackAnimSyncPayload(int entityId, boolean thrusting, boolean gliding, float heat, boolean overheated)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<JetpackAnimSyncPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "jetpack_anim_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JetpackAnimSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, JetpackAnimSyncPayload::entityId,
            ByteBufCodecs.BOOL, JetpackAnimSyncPayload::thrusting,
            ByteBufCodecs.BOOL, JetpackAnimSyncPayload::gliding,
            ByteBufCodecs.FLOAT, JetpackAnimSyncPayload::heat,
            ByteBufCodecs.BOOL, JetpackAnimSyncPayload::overheated,
            JetpackAnimSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
