package com.bilal.practicemod.network;

import com.bilal.practicemod.PracticeMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Sent client -> server every client tick while the jetpack or rocket boots are equipped, since vanilla only syncs
// the jump-key-held state (and WASD impulses) for passengers steering a vehicle, not for a normal
// on-foot player. forward/strafe are the raw movement impulses in [-1, 1]; the flight sim rotates
// them by yaw so WASD steers relative to where the player faces. boost is the sprint-key-held state
// (vanilla drops the sprint FLAG mid-air, so we read the key directly) - the jetpack OVERDRIVE surge
// input. Rocket boots reuse jumping/forward/strafe/sneaking and always send boost/hover false.
//
// sneaking is here for the same reason boost is, even though vanilla does sync the entity's shift
// flag: that flag is gated (LocalPlayer.aiStep suppresses it while swimming, riding, or where the
// crouch pose won't fit), and hover uses sneak as altitude trim, which has to answer to the key
// itself. hoverMode is the H toggle's latched state, resent every tick rather than as an edge so a
// dropped packet self-heals on the next one instead of leaving the two sides disagreeing forever.
//
// hitch is the odd one out: a one-shot COSMETIC event, not an input. Hitches are rolled and applied
// only on the owning client (JetpackHoverHitch); this bit rides the tick packet that was being sent
// anyway so the server can relay the twitch to other viewers through JetpackAnimSyncPayload. The
// server never rolls one and never applies the impulse.
public record JetpackInputPayload(boolean jumping, boolean sneaking, boolean hoverMode, boolean boost,
        boolean hitch, float forward, float strafe) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<JetpackInputPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "jetpack_input"));

    // The five booleans travel packed into one byte, the same way JetpackAnimSyncPayload packs its
    // flags: StreamCodec.composite tops out at six components, and five BOOLs plus two floats would
    // sit right on that ceiling with no room for the next flag. The bitmask is smaller on the wire too.
    private static final int FLAG_JUMPING = 1;
    private static final int FLAG_SNEAKING = 1 << 1;
    private static final int FLAG_HOVER_MODE = 1 << 2;
    private static final int FLAG_BOOST = 1 << 3;
    private static final int FLAG_HITCH = 1 << 4;

    public static final StreamCodec<RegistryFriendlyByteBuf, JetpackInputPayload> STREAM_CODEC = StreamCodec
            .composite(ByteBufCodecs.BYTE, JetpackInputPayload::flags,
                    ByteBufCodecs.FLOAT, JetpackInputPayload::forward,
                    ByteBufCodecs.FLOAT, JetpackInputPayload::strafe,
                    JetpackInputPayload::unpack);

    private byte flags() {
        return (byte) ((this.jumping ? FLAG_JUMPING : 0)
                | (this.sneaking ? FLAG_SNEAKING : 0)
                | (this.hoverMode ? FLAG_HOVER_MODE : 0)
                | (this.boost ? FLAG_BOOST : 0)
                | (this.hitch ? FLAG_HITCH : 0));
    }

    private static JetpackInputPayload unpack(byte flags, float forward, float strafe) {
        return new JetpackInputPayload((flags & FLAG_JUMPING) != 0,
                (flags & FLAG_SNEAKING) != 0,
                (flags & FLAG_HOVER_MODE) != 0,
                (flags & FLAG_BOOST) != 0,
                (flags & FLAG_HITCH) != 0,
                forward,
                strafe);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
