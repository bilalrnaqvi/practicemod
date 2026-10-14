package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Two six-pixel bones per leg: knee flexion without rubber-leg scaling. */
public final class SkateboardLegLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final ModelPart[] left = segments(16, 48, 0);
    private final ModelPart[] right = segments(0, 16, 0);
    private final ModelPart[] leftPants = segments(0, 48, 0.25F);
    private final ModelPart[] rightPants = segments(0, 32, 0.25F);

    public SkateboardLegLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    static boolean canRender(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.LEGS).isEmpty();
    }

    private static ModelPart[] segments(int u, int v, float inflate) {
        ModelPart[] parts = new ModelPart[2];
        for (int i = 0; i < 2; i++) {
            // Crop the original twelve-pixel skin strip into two six-pixel strips.
            // End caps keep the original UVs, including the shoe sole (not v+6).
            var sides = new ModelPart.Cube(u, v + i * 6, -2, 0, -2, 4, 6, 4,
                    inflate, inflate, inflate, false, 64, 64,
                    EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST));
            var caps = new ModelPart.Cube(u, v, -2, 0, -2, 4, 6, 4,
                    inflate, inflate, inflate, false, 64, 64, EnumSet.of(Direction.UP, Direction.DOWN));
            parts[i] = new ModelPart(List.of(sides, caps), Map.of());
        }
        return parts;
    }

    @Override
    public void render(PoseStack poses, MultiBufferSource buffers, int light, AbstractClientPlayer player,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        var rig = SkateboardPoseRenderer.poseFor(getParentModel());
        if (rig == null || !canRender(player) || player.isInvisible() || player.isSpectator()) return;
        var vertices = buffers.getBuffer(RenderType.entityTranslucent(player.getSkin().texture()));
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0);
        draw(poses, vertices, light, overlay, rig.left(), left);
        draw(poses, vertices, light, overlay, rig.right(), right);
        if (getParentModel().leftPants.visible) draw(poses, vertices, light, overlay, rig.left(), leftPants);
        if (getParentModel().rightPants.visible) draw(poses, vertices, light, overlay, rig.right(), rightPants);
    }

    private static void draw(PoseStack poses, VertexConsumer vertices, int light, int overlay,
            SkateboardKinematics.Leg leg, ModelPart[] parts) {
        segment(poses, vertices, light, overlay, leg.hip(), leg.knee(), parts[0]);
        segment(poses, vertices, light, overlay, leg.knee(), leg.foot(), parts[1]);
    }

    private static void segment(PoseStack poses, VertexConsumer vertices, int light, int overlay,
            SkateboardKinematics.Point from, SkateboardKinematics.Point to, ModelPart part) {
        poses.pushPose();
        poses.translate(from.x() / 16, from.y() / 16, from.z() / 16);
        Vector3f direction = new Vector3f(to.x() - from.x(), to.y() - from.y(), to.z() - from.z()).normalize();
        poses.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), direction));
        part.render(poses, vertices, light, overlay);
        poses.popPose();
    }
}
