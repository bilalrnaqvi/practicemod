package com.bilal.practicemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Prototype taut cable; synchronized anchors also render for remote players. */
@EventBusSubscriber(modid = PracticeMod.MODID, value = Dist.CLIENT)
public final class GrappleCableRenderer {
    private GrappleCableRenderer() {}
    private static GrappleState preview = GrappleState.IDLE;

    @SubscribeEvent
    static void preview(ClientTickEvent.Post event) {
        var player = Minecraft.getInstance().player;
        if (player == null || !GrappleHandler.available(player) || GrappleHandler.active(player)
                || !(player.getMainHandItem().is(PracticeMod.GRAPPLING_HOOK.get())
                || player.getOffhandItem().is(PracticeMod.GRAPPLING_HOOK.get()))) {
            preview = GrappleState.IDLE;
            return;
        }
        if (player.tickCount % 2 == 0) preview = GrappleTargeting.find(player);
    }

    static void ring(com.mojang.blaze3d.vertex.PoseStack poses,
            com.mojang.blaze3d.vertex.VertexConsumer lines, Vec3 anchor, Vec3 camera, boolean locked) {
        Vec3 facing = camera.subtract(anchor).normalize();
        Vec3 right = facing.cross(new Vec3(0, 1, 0)).normalize();
        if (right.lengthSqr() < 0.5) right = new Vec3(1, 0, 0);
        Vec3 up = right.cross(facing).normalize();
        Vec3 center = anchor.subtract(camera).add(facing.scale(0.06));
        double radius = Math.max(0.12, Math.min(0.6, camera.distanceTo(anchor) * 0.012));
        for (int i = 0; i < 32; i++) {
            double a = Math.PI * 2 * i / 32, b = Math.PI * 2 * (i + 1) / 32;
            Vec3 from = center.add(right.scale(Math.cos(a) * radius)).add(up.scale(Math.sin(a) * radius));
            Vec3 to = center.add(right.scale(Math.cos(b) * radius)).add(up.scale(Math.sin(b) * radius));
            Vec3 normal = to.subtract(from).normalize();
            for (Vec3 point : new Vec3[]{from, to}) {
                lines.addVertex(poses.last(), (float) point.x, (float) point.y, (float) point.z)
                        .setColor(1F, locked ? 0.70F : 0.94F, locked ? 0.25F : 0.78F, 1F)
                        .setNormal(poses.last(), (float) normal.x, (float) normal.y, (float) normal.z);
            }
        }
    }

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
            ring(poses, lines, state.anchor(), camera, true);
        }
        if (preview.active() && mc.player != null && !GrappleHandler.active(mc.player) && !mc.options.hideGui) {
            ring(poses, lines, preview.anchor(), camera, false);
        }
        source.endBatch(RenderType.lines());
    }
}
