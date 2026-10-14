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

public class MechanicalDolphinRenderer extends EntityRenderer<MechanicalDolphinEntity> {
    private final MechanicalDolphinCockpit cockpit;
    public MechanicalDolphinRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 1.1F;
        cockpit = new MechanicalDolphinCockpit(context);
    }
    @Override public void render(MechanicalDolphinEntity dolphin, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partial, dolphin.xRotO, dolphin.getXRot())));
        MechanicalDolphinModel.renderOpaque(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(MechanicalDolphinModel.TEXTURE)),
                light, OverlayTexture.NO_OVERLAY, dolphin.animationPose(partial));
        cockpit.render(dolphin, pose, buffers, light);
        MechanicalDolphinModel.renderGlass(pose, buffers.getBuffer(RenderType.entityTranslucent(MechanicalDolphinModel.TEXTURE)),
                light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(dolphin, yaw, partial, pose, buffers, light);
    }
    @Override public boolean shouldRender(MechanicalDolphinEntity dolphin, net.minecraft.client.renderer.culling.Frustum frustum,
            double x, double y, double z) {
        return dolphin.shouldRender(x, y, z) && frustum.isVisible(dolphin.getBoundingBox().inflate(3.2));
    }
    @Override public ResourceLocation getTextureLocation(MechanicalDolphinEntity dolphin) { return MechanicalDolphinModel.TEXTURE; }
}
