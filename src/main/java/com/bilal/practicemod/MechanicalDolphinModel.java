package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;

/** Panorama shell with independent stabilizing fins and a two-joint vertical tail stroke. */
final class MechanicalDolphinModel {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            PracticeMod.MODID, "textures/entity/mechanical_dolphin.png");

    static void renderOpaque(PoseStack poses, VertexConsumer out, int light, int overlay, float age, float motion) {
        renderOpaque(poses, out, light, overlay, MechanicalDolphinAnimation.steady(age, motion));
    }
    static void renderOpaque(PoseStack poses, VertexConsumer out, int light, int overlay, MechanicalDolphinAnimation.Pose animation) {
        mesh(poses, out, light, overlay, MechanicalDolphinGeometry.HULL);
        mesh(poses, out, LightTexture.FULL_BRIGHT, overlay, MechanicalDolphinGeometry.LIGHTS);
        joint(poses, out, light, overlay,
                MechanicalDolphinGeometry.PORT_FIN_X, MechanicalDolphinGeometry.PORT_FIN_Y, MechanicalDolphinGeometry.PORT_FIN_Z,
                animation.portPitch(), 0, animation.portRoll(), MechanicalDolphinGeometry.PORT_FIN, MechanicalDolphinGeometry.PORT_FIN_LIGHTS);
        joint(poses, out, light, overlay,
                MechanicalDolphinGeometry.STARBOARD_FIN_X, MechanicalDolphinGeometry.STARBOARD_FIN_Y, MechanicalDolphinGeometry.STARBOARD_FIN_Z,
                animation.starboardPitch(), 0, animation.starboardRoll(), MechanicalDolphinGeometry.STARBOARD_FIN, MechanicalDolphinGeometry.STARBOARD_FIN_LIGHTS);
        joint(poses, out, light, overlay,
                MechanicalDolphinGeometry.DORSAL_FIN_X, MechanicalDolphinGeometry.DORSAL_FIN_Y, MechanicalDolphinGeometry.DORSAL_FIN_Z,
                0, animation.dorsalYaw(), animation.dorsalRoll(), MechanicalDolphinGeometry.DORSAL_FIN, MechanicalDolphinGeometry.DORSAL_FIN_LIGHTS);
        poses.pushPose();
        rotateJoint(poses, MechanicalDolphinGeometry.TAIL_X, MechanicalDolphinGeometry.TAIL_Y, MechanicalDolphinGeometry.TAIL_Z,
                animation.tail(), 0, 0);
        mesh(poses, out, light, overlay, MechanicalDolphinGeometry.TAIL);
        mesh(poses, out, LightTexture.FULL_BRIGHT, overlay, MechanicalDolphinGeometry.TAIL_LIGHTS);
        joint(poses, out, light, overlay,
                MechanicalDolphinGeometry.FLUKES_X, MechanicalDolphinGeometry.FLUKES_Y, MechanicalDolphinGeometry.FLUKES_Z,
                animation.flukes(), 0, 0, MechanicalDolphinGeometry.FLUKES, MechanicalDolphinGeometry.FLUKES_LIGHTS);
        poses.popPose();
    }
    private static void joint(PoseStack poses, VertexConsumer out, int light, int overlay,
            float x, float y, float z, float pitch, float yaw, float roll, float[][] solid, float[][] lamps) {
        poses.pushPose();
        rotateJoint(poses, x, y, z, pitch, yaw, roll);
        mesh(poses, out, light, overlay, solid);
        mesh(poses, out, LightTexture.FULL_BRIGHT, overlay, lamps);
        poses.popPose();
    }
    private static void rotateJoint(PoseStack poses, float x, float y, float z, float pitch, float yaw, float roll) {
        poses.translate(x, y, z);
        poses.mulPose(Axis.ZP.rotationDegrees(roll));
        poses.mulPose(Axis.YP.rotationDegrees(yaw));
        poses.mulPose(Axis.XP.rotationDegrees(pitch));
        poses.translate(-x, -y, -z);
    }
    static void renderGlass(PoseStack poses, VertexConsumer out, int light, int overlay) {
        mesh(poses, out, light, overlay, MechanicalDolphinGeometry.GLASS);
    }
    static void render(PoseStack poses, VertexConsumer out, int light, int overlay, float age, float motion) {
        renderOpaque(poses, out, light, overlay, age, motion);
        renderGlass(poses, out, light, overlay);
    }
    private static void mesh(PoseStack poses, VertexConsumer out, int light, int overlay, float[][] parts) {
        for (float[] part : parts) for (int i=0; i<part.length; i+=8) {
            out.addVertex(poses.last(), part[i], part[i+1], part[i+2]).setColor(-1)
                    .setUv(part[i+3], part[i+4]).setOverlay(overlay).setLight(light)
                    .setNormal(poses.last(), part[i+5], part[i+6], part[i+7]);
        }
    }
}
