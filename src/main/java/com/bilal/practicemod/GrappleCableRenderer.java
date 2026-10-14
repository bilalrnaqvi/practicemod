package com.bilal.practicemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Prototype taut cable; synchronized anchors also render for remote players. */
@EventBusSubscriber(modid = PracticeMod.MODID, value = Dist.CLIENT)
public final class GrappleCableRenderer {
    private GrappleCableRenderer() {}

    @SubscribeEvent
    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var poses = event.getPoseStack();
        var source = mc.renderBuffers().bufferSource();
        var lines = source.getBuffer(RenderType.lines());
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        for (var player : mc.level.players()) {
            GrappleState state = player.getData(PracticeMod.GRAPPLE_STATE);
            if (!state.active()) continue;
            boolean main = player.getUsedItemHand() == InteractionHand.MAIN_HAND;
            boolean right = (player.getMainArm() == HumanoidArm.RIGHT) == main;
            Vec3 forward = player.getViewVector(partial);
            Vec3 side = new Vec3(-forward.z, 0, forward.x).normalize().scale(right ? -0.3 : 0.3);
            Vec3 start = player.getEyePosition(partial).add(0, -0.28, 0).add(side).add(forward.scale(0.5)).subtract(camera);
            Vec3 end = state.anchor().subtract(camera);
            Vec3 normal = end.subtract(start).normalize();
            var pose = poses.last();
            lines.addVertex(pose, (float) start.x, (float) start.y, (float) start.z)
                    .setColor(0.30F, 0.28F, 0.23F, 1F)
                    .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
            lines.addVertex(pose, (float) end.x, (float) end.y, (float) end.z)
                    .setColor(0.30F, 0.28F, 0.23F, 1F)
                    .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
            LevelRenderer.renderLineBox(poses, lines, new AABB(end, end).inflate(0.1), 0.85F, 0.65F, 0.25F, 1F);
        }
        source.endBatch(RenderType.lines());
    }
}
