package com.bilal.practicemod;

import com.bilal.practicemod.network.JetskiInputPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = PracticeMod.MODID, value = Dist.CLIENT)
public final class JetskiClientHandler {
    @SubscribeEvent static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !(minecraft.player.getVehicle() instanceof JetskiEntity ski)) return;
        boolean active = minecraft.screen == null && !minecraft.isPaused();
        PacketDistributor.sendToServer(new JetskiInputPayload(ski.getId(),
                active ? minecraft.player.input.forwardImpulse : 0,
                active ? minecraft.player.input.leftImpulse : 0,
                active && minecraft.options.keySprint.isDown()));
    }

    @SubscribeEvent static void hud(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.AIR_LEVEL, ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "jetski"),
                new JetskiHudOverlay());
    }
}
