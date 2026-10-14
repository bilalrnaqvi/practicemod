package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Renders the authored alien skateboard with its nose facing the travel direction. */
public class SkateboardRenderer extends EntityRenderer<SkateboardEntity> {

    public SkateboardRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.4F;
    }

    @Override
    public void render(SkateboardEntity entity, float yaw, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        pose.mulPose(Axis.YP.rotationDegrees(-entity.visualSpin(partialTick)));
        if (entity.skateMode() == SkateboardEntity.GRIND_STATE && entity.grindStyle() == 1)
            pose.mulPose(Axis.YP.rotationDegrees(90));
        pose.translate(0, 0.2, 0);
        pose.mulPose(Axis.XP.rotationDegrees(entity.animation().pitch(partialTick)));
        pose.mulPose(Axis.ZP.rotationDegrees(entity.animation().roll(partialTick)));
        var trick = entity.currentTrick();
        float progress = entity.trickProgress(partialTick);
        if (trick == SkateboardTricks.Trick.KICKFLIP || trick == SkateboardTricks.Trick.HEELFLIP || trick == SkateboardTricks.Trick.TRE_FLIP)
            pose.mulPose(Axis.ZP.rotationDegrees(progress * (trick == SkateboardTricks.Trick.HEELFLIP ? -360 : 360)));
        if (trick == SkateboardTricks.Trick.SHUVIT || trick == SkateboardTricks.Trick.TRE_FLIP)
            pose.mulPose(Axis.YP.rotationDegrees(progress * (trick == SkateboardTricks.Trick.TRE_FLIP ? 360 : 180)));
        pose.translate(0, -0.2, 0);
        SkateboardModel.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(SkateboardModel.TEXTURE)),
                light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SkateboardEntity entity) {
        return SkateboardModel.TEXTURE;
    }
}
