package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Held/inventory miniature uses the neutral authored instrument fallback tiles. */
final class SubmarineItemRenderer extends BlockEntityWithoutLevelRenderer {
    SubmarineItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) { super(dispatcher, models); }
    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(.5, .5, .5);
        float scale = .35F / DeepseekerPilot.HULL_SCALE;
        pose.scale(scale, scale, scale);
        pose.translate(0, -14.0 * DeepseekerPilot.HULL_SCALE / 16, 0);
        SubmarineRenderer.renderHull(pose, buffers, light, false);
        SubmarineRenderer.renderHull(pose, buffers, light, true);
        pose.popPose();
    }
}
