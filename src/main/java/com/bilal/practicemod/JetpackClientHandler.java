package com.bilal.practicemod;

import org.lwjgl.glfw.GLFW;

import com.bilal.practicemod.network.JetpackInputPayload;
import com.bilal.practicemod.network.OpenJetpackMenuPayload;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// This class only loads on the physical client (see the Dist restriction below), so touching
// Minecraft/LocalPlayer here is safe. It's the only place that reads the actual jump key state,
// since that's not information vanilla otherwise sends to the server for an on-foot player.
@EventBusSubscriber(modid = PracticeMod.MODID, value = Dist.CLIENT)
public class JetpackClientHandler {

    // Opens the fueling menu for the WORN jetpack. Registered in PracticeModClient's
    // RegisterKeyMappingsEvent, so it's rebindable in the vanilla Controls screen.
    public static final KeyMapping OPEN_MENU_KEY = new KeyMapping(
            "key.practicemod.open_jetpack_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.practicemod");

    // Runs before the player's own tick, so JetpackFlightHandler's PlayerTickEvent.Post sees the
    // up-to-date value in the same tick rather than one tick stale.
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        // consumeClick must drain even when not wearing the jetpack, or presses would queue up
        // and the menu would pop open the moment one is equipped.
        while (OPEN_MENU_KEY.consumeClick()) {
            if (JetpackFlightHandler.isWearingJetpack(player)) {
                PacketDistributor.sendToServer(OpenJetpackMenuPayload.INSTANCE);
            }
        }

        if (!JetpackFlightHandler.isWearingJetpack(player)) {
            return;
        }

        boolean jumping = player.input.jumping;
        float forward = player.input.forwardImpulse;
        float strafe = player.input.leftImpulse;
        // Read the sprint key directly: vanilla clears the sprint FLAG when a player is airborne
        // without forward input, which would make it a flaky overdrive trigger mid-flight.
        boolean boost = Minecraft.getInstance().options.keySprint.isDown();
        player.setData(PracticeMod.JETPACK_THRUSTING, jumping);
        player.setData(PracticeMod.JETPACK_FORWARD, forward);
        player.setData(PracticeMod.JETPACK_STRAFE, strafe);
        player.setData(PracticeMod.JETPACK_OVERDRIVE, boost);
        PacketDistributor.sendToServer(new JetpackInputPayload(jumping, forward, strafe, boost));
    }
}
