package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.resources.ResourceLocation;

/** CRAG Blockbench mesh with independent wheel travel and a sprung cockpit. */
final class AtvModel {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "textures/entity/crag.png");

    static void render(PoseStack pose, VertexConsumer vertices, int light, int overlay, AtvEntity atv, float partial) {
        float heave = atv == null ? 0 : atv.visual(0, partial);
        float steer = atv == null ? 0 : atv.visual(2, partial) * 26;
        pose.pushPose();
        pose.translate(0, heave, 0);
        if (atv != null) {
            pose.mulPose(Axis.XP.rotationDegrees(atv.visual(7, partial)));
            pose.mulPose(Axis.ZP.rotationDegrees(-atv.visual(1, partial)));
        }
        mesh(pose, vertices, AtvGeometry.BODY, light, overlay);
        pose.pushPose();
        pose.translate(AtvGeometry.STEERING_X, AtvGeometry.STEERING_Y, AtvGeometry.STEERING_Z);
        pose.mulPose(Axis.XP.rotationDegrees(AtvGeometry.STEERING_TILT));
        pose.mulPose(Axis.ZP.rotationDegrees(-steer * 1.6F));
        pose.mulPose(Axis.XP.rotationDegrees(-AtvGeometry.STEERING_TILT));
        pose.translate(-AtvGeometry.STEERING_X, -AtvGeometry.STEERING_Y, -AtvGeometry.STEERING_Z);
        mesh(pose, vertices, AtvGeometry.STEERING, light, overlay);
        pose.popPose();
        for (int i = 0; i < 4; i++) {
            float travel = atv == null ? 0 : atv.visual(i + 3, partial);
            double x = AtvGeometry.WHEEL_X[i], z = AtvGeometry.WHEEL_Z[i];
            pose.pushPose();
            pose.translate(x, AtvGeometry.WHEEL_Y + travel, z);
            if (i < 2) pose.mulPose(Axis.YP.rotationDegrees(steer));
            pose.mulPose(Axis.XP.rotationDegrees(atv == null ? 0 : atv.wheelAngle(partial)));
            pose.translate(-x, -AtvGeometry.WHEEL_Y, -z);
            mesh(pose, vertices, AtvGeometry.WHEELS[i], light, overlay);
            pose.popPose();
        }
        pose.popPose();
    }

    private static void mesh(PoseStack pose, VertexConsumer vertices, float[][] cubes, int light, int overlay) {
        for (float[] cube : cubes) for (int i = 0; i < cube.length; i += 8) {
            vertices.addVertex(pose.last(), cube[i], cube[i + 1], cube[i + 2])
                    .setColor(0xFFFFFFFF).setUv(cube[i + 3], cube[i + 4]).setOverlay(overlay).setLight(light)
                    .setNormal(pose.last(), cube[i + 5], cube[i + 6], cube[i + 7]);
        }
    }
}
