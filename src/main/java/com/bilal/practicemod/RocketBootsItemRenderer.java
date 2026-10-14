package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Uses the same pair of textured boots in the inventory, hand, and world. */
final class RocketBootsItemRenderer extends BlockEntityWithoutLevelRenderer {
    RocketBootsItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
            MultiBufferSource buffers, int light, int overlay) {
        poses.pushPose();
        poses.translate(0.5F, 0.15F, 0.5F);
        poses.scale(1.0F, -1.0F, -1.0F);
        poses.translate(0.0F, -12.0F / 16.0F, 0.0F);
        var vertices = ItemRenderer.getFoilBufferDirect(buffers,
                RenderType.entityCutoutNoCull(RocketBootsModel.TEXTURE), false, stack.hasFoil());
        poses.pushPose();
        poses.translate(2.0F / 16.0F, 0.0F, 0.0F);
        RocketBootsModel.renderGeometry(poses, vertices, light, overlay, -1, RocketBootsGeometry.LEFT);
        poses.popPose();
        poses.translate(-2.0F / 16.0F, 0.0F, 0.0F);
        RocketBootsModel.renderGeometry(poses, vertices, light, overlay, -1, RocketBootsGeometry.RIGHT);
        poses.popPose();
    }
}
