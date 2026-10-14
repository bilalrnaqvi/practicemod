package com.bilal.practicemod;

import com.bilal.practicemod.network.SubmarineInputPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = PracticeMod.MODID, value = Dist.CLIENT)
public final class SubmarineClientHandler {
    public static final KeyMapping CABIN_KEY = new KeyMapping("key.practicemod.submarine_cabin",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.practicemod");
    public static final KeyMapping EXIT_KEY = new KeyMapping("key.practicemod.exit_submarine",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.practicemod");

    public static final KeyMapping LIGHT_KEY = new KeyMapping("key.practicemod.submarine_light",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L, "key.categories.practicemod");

    @SubscribeEvent
    static void onBoardingClick(net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        var minecraft = Minecraft.getInstance();
        if (!event.isUseItem() || minecraft.screen != null || minecraft.player == null) return;
        if (minecraft.player.getVehicle() instanceof SubmarineEntity sub && sub.isParked()) {
            event.setCanceled(true); event.setSwingHand(false);
            if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND)
                PacketDistributor.sendToServer(new com.bilal.practicemod.network.SubmarineCabinPayload(sub.getId(), 2, 0, 0));
            return;
        }
        var target = SubmarineBoarding.pick(minecraft.player);
        if (target == null) return;
        // Cancel both hands so a missed vanilla boat hit does not use/place the held item.
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND)
            PacketDistributor.sendToServer(new com.bilal.practicemod.network.SubmarineBoardPayload(target.getId()));
    }

    private static boolean lightHeld;

    @SubscribeEvent
    static void onClientTickPre(ClientTickEvent.Pre event) {
        var minecraft = Minecraft.getInstance();
        while (CABIN_KEY.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null
                    && minecraft.player.getVehicle() instanceof SubmarineEntity sub)
                PacketDistributor.sendToServer(new com.bilal.practicemod.network.SubmarineCabinPayload(sub.getId(), 0, 0, 0));
        }
        boolean clicked = false;
        while (LIGHT_KEY.consumeClick()) clicked = true;
        boolean toggle = clicked && !lightHeld;
        lightHeld = LIGHT_KEY.isDown();
        if (minecraft.screen != null || minecraft.player == null
                || !(minecraft.player.getVehicle() instanceof SubmarineEntity submarine)) return;
        // L also opens vanilla advancements. Reserve the shared binding only while piloting.
        if (LIGHT_KEY.getKey().equals(minecraft.options.keyAdvancements.getKey())) {
            while (minecraft.options.keyAdvancements.consumeClick()) { }
        }
        if (toggle) PacketDistributor.sendToServer(
                new com.bilal.practicemod.network.SubmarineLightPayload(submarine.getId()));
    }

    /** Register client telemetry without introducing gameplay systems or network traffic. */
    public static void setInstrumentProvider(SubmarineInstrumentProvider provider) {
        SubmarineInstruments.provider = java.util.Objects.requireNonNull(provider);
    }

    private static SubmarineEntity lastVehicle;

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        var current = minecraft.player != null && minecraft.player.getVehicle() instanceof SubmarineEntity submarine
                ? submarine : null;
        if (current != null && current != lastVehicle) {
            minecraft.gui.setOverlayMessage(Component.translatable("message.practicemod.submarine_controls",
                    minecraft.options.keyJump.getTranslatedKeyMessage(),
                    minecraft.options.keyShift.getTranslatedKeyMessage(), EXIT_KEY.getTranslatedKeyMessage(),
                    LIGHT_KEY.getTranslatedKeyMessage(), CABIN_KEY.getTranslatedKeyMessage()), false);
        }
        if (lastVehicle != current && lastVehicle != null) lastVehicle.clearInstruments();
        if (lastVehicle != current && current != null) current.clearInstruments();
        lastVehicle = current;
        SubmarineHeadlight.update(minecraft);
    }

    @SubscribeEvent
    static void onMovementInput(MovementInputUpdateEvent event) {
        boolean exit = false;
        while (EXIT_KEY.consumeClick()) exit = true;
        if (!(event.getEntity().getVehicle() instanceof SubmarineEntity submarine)) return;
        var minecraft = Minecraft.getInstance();
        var input = event.getInput();
        boolean active = minecraft.screen == null;
        if (submarine.isParked()) {
            submarine.acceptCabinInput(event.getEntity(), active ? input.forwardImpulse : 0, active ? input.leftImpulse : 0);
            PacketDistributor.sendToServer(new com.bilal.practicemod.network.SubmarineCabinPayload(submarine.getId(), 1,
                    active ? input.forwardImpulse : 0, active ? input.leftImpulse : 0));
            input.forwardImpulse = input.leftImpulse = 0;
            input.up = input.down = input.left = input.right = false;
        }
        boolean rise = active && input.jumping;
        boolean descend = active && input.shiftKeyDown;
        // Capture sneak first, then clear vanilla's dismount bit before it reaches either side.
        input.shiftKeyDown = false;
        input.jumping = false;
        submarine.acceptInput(event.getEntity(), rise, descend, false);
        PacketDistributor.sendToServer(new SubmarineInputPayload(submarine.getId(), rise, descend, active && exit));
    }
}
