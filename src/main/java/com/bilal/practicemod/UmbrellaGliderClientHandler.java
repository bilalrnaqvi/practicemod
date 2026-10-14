package com.bilal.practicemod;

import com.bilal.practicemod.network.UmbrellaGliderInputPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Owner-side glue for the held glider: it forwards movement impulses to the server, and hides the
 * first-person held model while the canopy is open overhead. There is no deploy key - the canopy
 * state is derived in {@link UmbrellaGliderHandler} from what the player is holding.
 */
@EventBusSubscriber(modid = PracticeMod.MODID, value = Dist.CLIENT)
public final class UmbrellaGliderClientHandler {
    private UmbrellaGliderClientHandler() {
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        boolean holdingUmbrella = UmbrellaGliderHandler.isHoldingUmbrella(player);
        boolean deployed = UmbrellaGliderHandler.isDeployed(player);
        if (!holdingUmbrella && !deployed) {
            player.setData(PracticeMod.UMBRELLA_GLIDER_FORWARD, 0.0F);
            player.setData(PracticeMod.UMBRELLA_GLIDER_STRAFE, 0.0F);
            return;
        }

        float forward = player.input.forwardImpulse;
        float strafe = player.input.leftImpulse;
        player.setData(PracticeMod.UMBRELLA_GLIDER_FORWARD, forward);
        player.setData(PracticeMod.UMBRELLA_GLIDER_STRAFE, strafe);

        // Resent every tick while the item is relevant so a missed or stale impulse self-heals.
        PacketDistributor.sendToServer(new UmbrellaGliderInputPayload(forward, strafe));
    }

    // The deployed canopy is drawn overhead by UmbrellaGliderLayer, so leaving the held model in
    // place would show the player two gliders at once. Third person is handled by PlayerItemInHandLayerMixin.
    @SubscribeEvent
    static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null
                && event.getItemStack().is(PracticeMod.UMBRELLA_GLIDER.get())
                && (UmbrellaGliderHandler.isDeployed(player)
                        || UmbrellaGliderAnimationController.isAnimating(player))) {
            event.setCanceled(true);
        }
    }
}
