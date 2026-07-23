package com.bilal.practicemod.network;

import com.bilal.practicemod.PracticeMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Client -> server request to open the fueling menu for the WORN jetpack (sent by the G keybind,
// see JetpackClientHandler). Carries no data; the server validates that a jetpack is actually
// worn before opening (JetpackNetworking.handleOpenJetpackMenu).
public record OpenJetpackMenuPayload() implements CustomPacketPayload {

    public static final OpenJetpackMenuPayload INSTANCE = new OpenJetpackMenuPayload();

    public static final CustomPacketPayload.Type<OpenJetpackMenuPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "open_jetpack_menu"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenJetpackMenuPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
