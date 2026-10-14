package com.bilal.practicemod;

import com.bilal.practicemod.network.MechanicalDolphinInputPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = PracticeMod.MODID, value = Dist.CLIENT)
public final class MechanicalDolphinClientHandler {
    // The 5.3-block hull reaches almost four blocks aft of the pilot's eye.
    // Eight blocks leaves room for the flukes and surrounding water while steering.
    private static final float THIRD_PERSON_DISTANCE = 8.0F;

    @SubscribeEvent static void hands(RenderHandEvent event) {
        var viewer = Minecraft.getInstance().getCameraEntity();
        if (viewer != null && viewer.getVehicle() instanceof MechanicalDolphinEntity)
            event.setCanceled(true);
    }

    @SubscribeEvent static void cameraDistance(CalculateDetachedCameraDistanceEvent event) {
        var viewer = event.getCamera().getEntity();
        if (viewer == null || !(viewer.getVehicle() instanceof MechanicalDolphinEntity)) return;
        // Fit the vehicle independently of rider scale. Vanilla applies terrain
        // collision after this event, for both rear and front third-person views.
        event.setDistance(THIRD_PERSON_DISTANCE / event.getEntityScalingFactor());
    }

    @SubscribeEvent static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !(minecraft.player.getVehicle() instanceof MechanicalDolphinEntity dolphin)) return;
        boolean active = minecraft.screen == null && !minecraft.isPaused();
        PacketDistributor.sendToServer(new MechanicalDolphinInputPayload(dolphin.getId(),
                active ? minecraft.player.input.forwardImpulse : 0,
                minecraft.player.getYRot(), minecraft.player.getXRot(),
                active && minecraft.options.keyJump.isDown()));
    }

    @SubscribeEvent static void hud(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.AIR_LEVEL,
                ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "mechanical_dolphin"), (graphics, delta) -> {
                    var minecraft = Minecraft.getInstance();
                    if (minecraft.player == null || minecraft.options.hideGui
                            || !(minecraft.player.getVehicle() instanceof MechanicalDolphinEntity)) return;
                    graphics.drawCenteredString(minecraft.font, Component.translatable("hud.practicemod.mechanical_dolphin"),
                            graphics.guiWidth() / 2, graphics.guiHeight() - 65, 0xB6ECF2);
                });
    }
}
