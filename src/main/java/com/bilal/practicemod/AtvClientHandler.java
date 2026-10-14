package com.bilal.practicemod;

import com.bilal.practicemod.network.AtvInputPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = PracticeMod.MODID, value = Dist.CLIENT)
public final class AtvClientHandler {
    @SubscribeEvent static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !(minecraft.player.getVehicle() instanceof AtvEntity ski)) return;
        if (!ski.isDriver(minecraft.player)) return;
        boolean active = minecraft.screen == null && !minecraft.isPaused();
        PacketDistributor.sendToServer(new AtvInputPayload(ski.getId(),
                active ? minecraft.player.input.forwardImpulse : 0,
                active ? minecraft.player.input.leftImpulse : 0));
    }

}
