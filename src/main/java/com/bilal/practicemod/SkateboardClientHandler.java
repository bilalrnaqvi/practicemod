package com.bilal.practicemod;

import static com.bilal.practicemod.SkateboardDynamics.*;
import com.bilal.practicemod.network.SkateboardInputPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = PracticeMod.MODID, value = Dist.CLIENT)
public final class SkateboardClientHandler {
    private static final IKeyConflictContext CONTEXT = new IKeyConflictContext() {
        public boolean isActive() {
            var mc = Minecraft.getInstance();
            return mc.screen == null && mc.player != null && mc.player.getVehicle() instanceof SkateboardEntity;
        }
        public boolean conflicts(IKeyConflictContext other) { return this == other; }
    };
    public static final KeyMapping FLIP_KEY = key("flip", GLFW.GLFW_KEY_Z);
    public static final KeyMapping GRAB_KEY = key("grab", GLFW.GLFW_KEY_X);
    public static final KeyMapping GRIND_KEY = key("grind", GLFW.GLFW_KEY_C);
    public static final KeyMapping MANUAL_KEY = key("manual", GLFW.GLFW_KEY_V);
    public static final KeyMapping REVERT_KEY = key("revert", GLFW.GLFW_KEY_R);
    public static final KeyMapping SPECIAL_KEY = key("special", GLFW.GLFW_KEY_B);
    public static final KeyMapping[] TRICK_KEYS = {FLIP_KEY, GRAB_KEY, GRIND_KEY, MANUAL_KEY, REVERT_KEY, SPECIAL_KEY};
    private static final int[] BUTTONS = {FLIP, GRAB, GRIND, MANUAL, REVERT, SPECIAL};
    private static KeyMapping key(String name, int key) {
        return new KeyMapping("key.practicemod.skate_" + name, CONTEXT, InputConstants.Type.KEYSYM, key,
                "key.categories.practicemod.skateboard");
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || !(player.getVehicle() instanceof SkateboardEntity board)) return;
        boolean active = mc.screen == null && !mc.isPaused();
        int buttons = 0;
        float steer = 0;
        if (active) {
            buttons = ACTIVE;
            if (mc.options.keyUp.isDown()) buttons |= PUSH;
            if (mc.options.keyDown.isDown()) buttons |= BRAKE;
            if (mc.options.keySprint.isDown()) buttons |= SPRINT;
            if (mc.options.keyJump.isDown()) buttons |= OLLIE;
            steer = (mc.options.keyRight.isDown() ? 1 : 0) - (mc.options.keyLeft.isDown() ? 1 : 0);
            // Ground turns also turn the view, so releasing A/D does not steer back to an old heading.
            if (board.skateMode() == SkateboardEntity.GROUND) player.setYRot(player.getYRot() + steer * 5);
            for (int i = 0; i < TRICK_KEYS.length; i++) if (TRICK_KEYS[i].isDown()) buttons |= BUTTONS[i];
        }
        PacketDistributor.sendToServer(new SkateboardInputPayload(board.getId(), buttons, steer, player.getYRot()));
    }
    private SkateboardClientHandler() {}
}
