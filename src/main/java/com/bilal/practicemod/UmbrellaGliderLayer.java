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
    private static final float PIXEL = 1.0F / 16.0F;
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
        // The Blockbench hub is at y=0 and the grip extends down to y=-19.1. This places that hub
        // above the head and the grip beside the right hand, matching the previous gameplay layer.
        poseStack.translate(-4.0F * PIXEL, -25.0F * PIXEL, 0.0F);
        this.model.render(poseStack, index -> bufferSource.getBuffer(
                        RenderType.entityCutoutNoCull(UmbrellaGliderModel.TEXTURES[index])), packedLight,
                OverlayTexture.NO_OVERLAY, state.sectorRotationDegrees(partialTick),
                state.sectorScale(partialTick));
        poseStack.popPose();
    }
}
