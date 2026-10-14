package com.bilal.practicemod;

import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/** Local rendering review; does not move the player or change server boarding state. */
@EventBusSubscriber(modid = PracticeMod.MODID, value = Dist.CLIENT)
public final class BlenderAnimationPreview {
    private static BlenderPlayerClip clip;
    private static LivingEntity subject;
    private static long start;
    private static float fixedTick;
    private static boolean playing;

    private BlenderAnimationPreview() {}

    @SubscribeEvent
    static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("deepseekerpose")
                .then(Commands.literal("play").executes(context -> begin(-1)))
                .then(Commands.literal("stop").executes(context -> { stop(); return 1; }))
                .then(Commands.argument("tick", FloatArgumentType.floatArg(0, BlenderPlayerClip.LAST_TICK))
                        .executes(context -> begin(FloatArgumentType.getFloat(context, "tick")))));
    }

    private static int begin(float time) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return 0;
        if (mc.player.isPassenger()) {
            mc.player.displayClientMessage(Component.literal("Leave the vehicle before previewing the player rig."), false);
            return 0;
        }
        try (var input = mc.getResourceManager().open(ResourceLocation.fromNamespaceAndPath(
                PracticeMod.MODID, "player_animations/deepseeker_entry.json"))) {
            clip = new BlenderPlayerClip(input);
            subject = mc.player;
            start = mc.level.getGameTime();
            playing = time < 0;
            fixedTick = Math.max(0, time);
            mc.player.displayClientMessage(Component.literal(
                    "Blender pose preview. Use third person; /deepseekerpose stop restores normal motion."), false);
            return 1;
        } catch (Exception ex) {
            stop();
            org.slf4j.LoggerFactory.getLogger(BlenderAnimationPreview.class).error("Cannot preview Blender clip", ex);
            mc.player.displayClientMessage(Component.literal("Could not load the Blender clip; see the game log."), false);
            return 0;
        }
    }

    public static void apply(PlayerModel<?> model, LivingEntity entity, float ageInTicks) {
        var mc = Minecraft.getInstance();
        if (subject != null && (subject != mc.player || !subject.isAlive() || subject.isPassenger())) stop();
        if (entity != subject || clip == null || mc.level == null) return;
        float time = playing ? mc.level.getGameTime() - start
                + Math.clamp(ageInTicks - entity.tickCount, 0, 1) : fixedTick;
        if (playing && time > BlenderPlayerClip.LAST_TICK) { stop(); return; }
        clip.sample(time);
        BlenderPlayerPose.apply(model, clip);
    }

    private static void stop() { subject = null; clip = null; playing = false; }

    @SubscribeEvent
    static void afterPlayer(RenderPlayerEvent.Post event) {
        BlenderPlayerPose.restore(event.getRenderer().getModel());
    }
}
