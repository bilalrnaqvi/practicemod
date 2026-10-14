package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Third-person opening, deployed, and closing umbrella rendering. */
public final class UmbrellaGliderLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final UmbrellaGliderModel model = new UmbrellaGliderModel();

    public UmbrellaGliderLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
            EntityModelSet modelSet) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
            AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick,
            float ageInTicks, float netHeadYaw, float headPitch) {
        UmbrellaGliderVisualState state = UmbrellaGliderAnimationController.get(player.getId());
        if (state == null || !state.shouldRender() || player.isInvisible()) {
            return;
        }

        poseStack.pushPose();
        // The animated right palm carries the umbrella through reach, catch and steady glide.
        UmbrellaGliderPoseRenderer.applyGliderAttitude(poseStack, state, getParentModel());
        this.model.render(poseStack, index -> bufferSource.getBuffer(
                        RenderType.entityCutoutNoCull(UmbrellaGliderModel.TEXTURES[index])), packedLight,
                OverlayTexture.NO_OVERLAY, state.sectorRotationDegrees(partialTick),
                state.sectorScale(partialTick), state.canopySpinDegrees(partialTick));
        poseStack.popPose();
    }
}
