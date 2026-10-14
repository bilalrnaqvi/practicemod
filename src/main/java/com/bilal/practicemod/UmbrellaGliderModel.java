package com.bilal.practicemod;

import java.util.function.IntFunction;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.resources.ResourceLocation;

/** Renders the handed-off Blockbench cuboids and free-mesh canopy through its eight-bone rig. */
public final class UmbrellaGliderModel {
    private static final float PIXEL = 1.0F / 16.0F;
    private static final float SOURCE_UV_PIXEL = 1.0F / 16.0F;
    private static final float SECTOR_REST_ROTATION = 14.0F;
    private static final float[] SECTOR_Y_ROTATIONS = {
            67.5F, 22.5F, -22.5F, -67.5F, -112.5F, -157.5F, -202.5F, -247.5F
    };

    static final ResourceLocation[] TEXTURES = java.util.Arrays.stream(UmbrellaGliderGeometry.TEXTURES)
            .map(name -> ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID,
                    "textures/entity/umbrella_glider/" + name + ".png"))
            .toArray(ResourceLocation[]::new);

    private final Vector3f transformedPosition = new Vector3f();
    private final Vector3f transformedNormal = new Vector3f();

    void render(PoseStack poseStack, IntFunction<VertexConsumer> consumers, int packedLight,
            int packedOverlay, float sectorRotationOffset, float sectorScale, float canopySpinDegrees) {
        renderElements(poseStack, consumers, packedLight, packedOverlay, -1);

        // Spin the canopy and its ribs about the local shaft axis after the grip/bank
        // transform. The fixed shaft and handle above remain planted in the hands.
        poseStack.pushPose();
        poseStack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(canopySpinDegrees)));
        for (int sector = 0; sector < SECTOR_Y_ROTATIONS.length; sector++) {
            poseStack.pushPose();
            poseStack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(SECTOR_Y_ROTATIONS[sector])));
            poseStack.mulPose(new Quaternionf().rotationZ(
                    (float) Math.toRadians(SECTOR_REST_ROTATION - sectorRotationOffset)));
            poseStack.scale(1.0F, 1.0F, sectorScale);
            renderElements(poseStack, consumers, packedLight, packedOverlay, sector);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private void renderElements(PoseStack poseStack, IntFunction<VertexConsumer> consumers,
            int packedLight, int packedOverlay, int sector) {
        for (UmbrellaGliderGeometry.Cube cube : UmbrellaGliderGeometry.CUBES) {
            if (cube.sector() == sector) {
                renderCube(poseStack, consumers.apply(cube.texture()), packedLight, packedOverlay, cube);
            }
        }
        for (UmbrellaGliderGeometry.Mesh mesh : UmbrellaGliderGeometry.MESHES) {
            if (mesh.sector() == sector) {
                renderMesh(poseStack, consumers, packedLight, packedOverlay, mesh);
            }
        }
    }

    private void renderCube(PoseStack poseStack, VertexConsumer consumer, int packedLight,
            int packedOverlay, UmbrellaGliderGeometry.Cube cube) {
        poseStack.pushPose();
        applyElementTransform(poseStack, cube.pivotX(), cube.pivotY(), cube.pivotZ(),
                cube.rotationX(), cube.rotationY(), cube.rotationZ());

        float x0 = (cube.fromX() - cube.pivotX()) * PIXEL;
        float x1 = (cube.toX() - cube.pivotX()) * PIXEL;
        float y0 = (cube.pivotY() - cube.toY()) * PIXEL;
        float y1 = (cube.pivotY() - cube.fromY()) * PIXEL;
        float z0 = (cube.fromZ() - cube.pivotZ()) * PIXEL;
        float z1 = (cube.toZ() - cube.pivotZ()) * PIXEL;
        PoseStack.Pose pose = poseStack.last();

        quad(pose, consumer, packedLight, packedOverlay, 0.0F, 0.0F, -1.0F,
                x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
        quad(pose, consumer, packedLight, packedOverlay, 0.0F, 0.0F, 1.0F,
                x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(pose, consumer, packedLight, packedOverlay, -1.0F, 0.0F, 0.0F,
                x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(pose, consumer, packedLight, packedOverlay, 1.0F, 0.0F, 0.0F,
                x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
        quad(pose, consumer, packedLight, packedOverlay, 0.0F, -1.0F, 0.0F,
                x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(pose, consumer, packedLight, packedOverlay, 0.0F, 1.0F, 0.0F,
                x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        poseStack.popPose();
    }

    private void renderMesh(PoseStack poseStack, IntFunction<VertexConsumer> consumers,
            int packedLight, int packedOverlay, UmbrellaGliderGeometry.Mesh mesh) {
        poseStack.pushPose();
        applyElementTransform(poseStack, mesh.pivotX(), mesh.pivotY(), mesh.pivotZ(),
                mesh.rotationX(), mesh.rotationY(), mesh.rotationZ());
        PoseStack.Pose pose = poseStack.last();

        for (UmbrellaGliderGeometry.Face face : mesh.faces()) {
            VertexConsumer consumer = consumers.apply(face.texture());
            pose.transformNormal(face.normalX(), face.normalY(), face.normalZ(), this.transformedNormal);
            for (int corner = 0; corner < 4; corner++) {
                int vertex = face.vertices()[corner] * 3;
                addVertex(pose, consumer, packedLight, packedOverlay,
                        (mesh.vertices()[vertex] - mesh.pivotX()) * PIXEL,
                        (mesh.vertices()[vertex + 1] + mesh.pivotY()) * PIXEL,
                        (mesh.vertices()[vertex + 2] - mesh.pivotZ()) * PIXEL,
                        face.uv()[corner * 2], face.uv()[corner * 2 + 1]);
            }
        }
        poseStack.popPose();
    }

    private static void applyElementTransform(PoseStack poseStack, float pivotX, float pivotY,
            float pivotZ, float rotationX, float rotationY, float rotationZ) {
        poseStack.translate(pivotX * PIXEL, -pivotY * PIXEL, pivotZ * PIXEL);
        if (rotationX != 0.0F || rotationY != 0.0F || rotationZ != 0.0F) {
            poseStack.mulPose(new Quaternionf().rotationZYX(
                    (float) Math.toRadians(rotationZ),
                    (float) Math.toRadians(rotationY),
                    (float) Math.toRadians(rotationX)));
        }
    }

    private void quad(PoseStack.Pose pose, VertexConsumer consumer, int packedLight,
            int packedOverlay, float normalX, float normalY, float normalZ, float... positions) {
        pose.transformNormal(normalX, normalY, normalZ, this.transformedNormal);
        for (int corner = 0; corner < 4; corner++) {
            int index = corner * 3;
            float u = corner == 0 || corner == 3 ? 0.0F : SOURCE_UV_PIXEL;
            float v = corner < 2 ? 0.0F : SOURCE_UV_PIXEL;
            addVertex(pose, consumer, packedLight, packedOverlay,
                    positions[index], positions[index + 1], positions[index + 2], u, v);
        }
    }

    private void addVertex(PoseStack.Pose pose, VertexConsumer consumer, int packedLight,
            int packedOverlay, float x, float y, float z, float u, float v) {
        pose.pose().transformPosition(x, y, z, this.transformedPosition);
        consumer.addVertex(this.transformedPosition.x(), this.transformedPosition.y(),
                this.transformedPosition.z(), -1, u, v, packedOverlay, packedLight,
                this.transformedNormal.x(), this.transformedNormal.y(), this.transformedNormal.z());
    }
}
