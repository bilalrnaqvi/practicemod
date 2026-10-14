package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/** Compact Racer Blockbench geometry. +Z is the bow; Y=0 is the keel. */
final class JetskiModel {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            PracticeMod.MODID, "textures/entity/compact_racer.png");

    static void render(PoseStack poses, VertexConsumer vertices, int light, int overlay) {
        PoseStack.Pose pose = poses.last();
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        for (float[] cube : JetskiGeometry.CUBES) {
            for (int i = 0; i < cube.length; i += 8) {
                pose.pose().transformPosition(cube[i], cube[i + 1], cube[i + 2], position);
                pose.transformNormal(cube[i + 5], cube[i + 6], cube[i + 7], normal);
                vertices.addVertex(position.x(), position.y(), position.z(), -1,
                        cube[i + 3], cube[i + 4], overlay, light, normal.x(), normal.y(), normal.z());
            }
        }
    }
}
