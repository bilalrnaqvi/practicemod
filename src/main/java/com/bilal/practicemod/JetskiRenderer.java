package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class JetskiRenderer extends EntityRenderer<JetskiEntity> {
    public JetskiRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.65F * JetskiEntity.HULL_SCALE;
    }
    @Override public void render(JetskiEntity ski, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partial, ski.xRotO, ski.getXRot())));
        pose.mulPose(Axis.ZP.rotationDegrees(-ski.roll(partial)));
        pose.scale(JetskiEntity.HULL_SCALE, JetskiEntity.HULL_SCALE, JetskiEntity.HULL_SCALE);
        JetskiModel.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(JetskiModel.TEXTURE)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(ski, yaw, partial, pose, buffers, light);
    }
    @Override public ResourceLocation getTextureLocation(JetskiEntity ski) { return JetskiModel.TEXTURE; }
}
