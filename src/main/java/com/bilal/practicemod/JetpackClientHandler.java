package com.bilal.practicemod;

import org.lwjgl.glfw.GLFW;

import com.bilal.practicemod.network.JetpackInputPayload;
import com.bilal.practicemod.network.OpenJetpackMenuPayload;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
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

    // A dedicated token prevents an empty-fuel alert from replacing an unrelated vanilla toast.
    private static final SystemToastId FUEL_EMPTY_TOAST = new SystemToastId();
    private static boolean trackingFuel;
    private static boolean hadFuel;
    private static boolean fuelEmptyAnnounced;

    // Opens the fueling menu for the WORN jetpack. Registered in PracticeModClient's
    // RegisterKeyMappingsEvent, so it's rebindable in the vanilla Controls screen.
    public static final KeyMapping OPEN_MENU_KEY = new KeyMapping(
            "key.practicemod.open_jetpack_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.practicemod");

    // Toggles HOVER MODE on the worn jetpack. Registered alongside OPEN_MENU_KEY in
    // PracticeModClient's RegisterKeyMappingsEvent, so it's rebindable too. H is unbound in vanilla.
    public static final KeyMapping HOVER_TOGGLE_KEY = new KeyMapping(
            "key.practicemod.toggle_jetpack_hover",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.practicemod");

    // Runs before the player's own tick, so JetpackFlightHandler's PlayerTickEvent.Post sees the
    // up-to-date value in the same tick rather than one tick stale.
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            trackingFuel = false;
            fuelEmptyAnnounced = false;
            return;
        }

        // Both consumeClick loops must drain even when not wearing the jetpack, or presses would
        // queue up and fire the moment one is equipped - the menu popping open, or hover silently
        // arming itself.
        boolean wearingJetpack = JetpackFlightHandler.isWearingJetpack(player);
        boolean wearingRocketBoots = RocketBootsFlightHandler.isWearingRocketBoots(player);
        boolean wearingFlightGear = wearingJetpack || wearingRocketBoots;
        updateFuelNotification(minecraft, player, wearingJetpack);

        while (OPEN_MENU_KEY.consumeClick()) {
            if (wearingJetpack) {
                PacketDistributor.sendToServer(OpenJetpackMenuPayload.INSTANCE);
            }
        }

        boolean hoverMode = Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_HOVER_MODE));
        while (HOVER_TOGGLE_KEY.consumeClick()) {
            if (wearingJetpack) {
                hoverMode = !hoverMode;
            }
        }
        if (!wearingJetpack) {
            hoverMode = false;
        }

        // Ticked unconditionally, BEFORE the wearing guard: a hitch in flight has to keep ageing out
        // even if the pack comes off mid-decay, or its clock freezes and the camera roll sticks
        // forever. It no-ops on its own whenever hover isn't engaged.
        boolean hitch = JetpackHoverHitch.tick(player);
        if (!wearingJetpack) {
            hitch = false;
        }

        if (!wearingFlightGear) {
            return;
        }

        boolean jumping = player.input.jumping;
        float forward = player.input.forwardImpulse;
        float strafe = player.input.leftImpulse;
        // Read the sprint and sneak keys directly rather than through the entity flags: vanilla
        // clears the sprint FLAG when a player is airborne without forward input, and gates the
        // crouch flag on whether the crouch pose actually fits. Both would make flaky flight inputs,
        // and hover leans on sneak as altitude trim.
        boolean boost = wearingJetpack && Minecraft.getInstance().options.keySprint.isDown();
        boolean sneaking = player.input.shiftKeyDown;

        player.setData(PracticeMod.JETPACK_THRUSTING, jumping);
        player.setData(PracticeMod.JETPACK_FORWARD, forward);
        player.setData(PracticeMod.JETPACK_STRAFE, strafe);
        player.setData(PracticeMod.JETPACK_OVERDRIVE, boost);
        player.setData(PracticeMod.JETPACK_SNEAKING, sneaking);
        player.setData(PracticeMod.JETPACK_HOVER_MODE, hoverMode);
        // hoverMode is resent every tick as latched state rather than as an edge, so a dropped
        // packet self-heals on the next one instead of leaving the two sides permanently disagreeing
        // about whether hover is armed.
        PacketDistributor.sendToServer(
                new JetpackInputPayload(jumping, sneaking, hoverMode, boost, hitch, forward, strafe));
    }

    // Fuel is deliberately not a HUD gauge. Watch the synchronized item component for the positive
    // -> empty edge and surface that event as Minecraft's standard top-right notification. Starting
    // to track an already-empty pack does not announce a stale depletion (for example on login or
    // equip), and refueling rearms the edge for the next tank.
    private static void updateFuelNotification(Minecraft minecraft, LocalPlayer player, boolean wearing) {
        if (!wearing || !JetpackServerConfig.SPEC.isLoaded()) {
            trackingFuel = false;
            fuelEmptyAnnounced = false;
            return;
        }

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        float fuel = JetpackFuelHandler.getFuel(chest);
        int storedPowder = JetpackFuelHandler.getStoredBlazePowder(chest);
        boolean hasFuel = JetpackFuelHandler.hasFuelSupply(chest);
        if (trackingFuel && hadFuel && !hasFuel && !fuelEmptyAnnounced) {
            SystemToast.add(minecraft.getToasts(), FUEL_EMPTY_TOAST,
                    Component.translatable("notification.practicemod.jetpack_fuel_empty"),
                    Component.translatable("notification.practicemod.jetpack_fuel_empty.description"));
            fuelEmptyAnnounced = true;
        } else if (fuelEmptyAnnounced && (storedPowder > 0 || fuel >= Math.min(
                JetpackFuelHandler.getFuelCapacity(),
                (float) (double) JetpackServerConfig.FUEL_PER_BLAZE_POWDER.get()))) {
            // Rearm after a real refill. This also prevents servers that opt into slow passive fuel
            // recovery from producing a toast loop as a tiny regenerated amount is burned again.
            fuelEmptyAnnounced = false;
        }
        trackingFuel = true;
        hadFuel = hasFuel;
    }
}
