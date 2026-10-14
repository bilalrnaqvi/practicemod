package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class AtvRenderer extends EntityRenderer<AtvEntity> {
    public AtvRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = .8F; }
    @Override public void render(AtvEntity atv, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose(); pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        AtvModel.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(AtvModel.TEXTURE)), light, OverlayTexture.NO_OVERLAY, atv, partial);
        pose.popPose();
        super.render(atv, yaw, partial, pose, buffers, light);
    }
    @Override public ResourceLocation getTextureLocation(AtvEntity atv) { return AtvModel.TEXTURE; }
}
