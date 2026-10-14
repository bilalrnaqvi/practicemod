package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

/** Authored per-face UV geometry, attached to the vanilla armor leg poses. */
public final class RocketBootsModel extends HumanoidModel<LivingEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "rocket_boots"), "main");
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            PracticeMod.MODID, "textures/entity/rocket_boots_mk1.png");

    public RocketBootsModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createLayer() {
        return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 512, 512);
    }

    @Override
    public void renderToBuffer(PoseStack poses, VertexConsumer vertices, int light, int overlay, int color) {
        poses.pushPose();
        if (this.young) {
            poses.scale(0.5F, 0.5F, 0.5F);
            poses.translate(0.0F, 24.0F / 16.0F, 0.0F);
        }
        renderLeg(poses, vertices, light, overlay, color, this.leftLeg, RocketBootsGeometry.LEFT);
        renderLeg(poses, vertices, light, overlay, color, this.rightLeg, RocketBootsGeometry.RIGHT);
        poses.popPose();
    }

    private static void renderLeg(PoseStack poses, VertexConsumer vertices, int light, int overlay,
            int color, ModelPart leg, float[][] cubes) {
        if (!leg.visible) return;
        poses.pushPose();
        leg.translateAndRotate(poses);
        renderGeometry(poses, vertices, light, overlay, color, cubes);
        poses.popPose();
    }

    static void renderGeometry(PoseStack poses, VertexConsumer vertices, int light, int overlay,
            int color, float[][] cubes) {
        PoseStack.Pose pose = poses.last();
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        for (float[] cube : cubes) {
            for (int i = 0; i < cube.length; i += 8) {
                pose.pose().transformPosition(cube[i], cube[i + 1], cube[i + 2], position);
                pose.transformNormal(cube[i + 5], cube[i + 6], cube[i + 7], normal);
                vertices.addVertex(position.x(), position.y(), position.z(), color,
                        cube[i + 3], cube[i + 4], overlay, light, normal.x(), normal.y(), normal.z());
            }
        }
    }
}
