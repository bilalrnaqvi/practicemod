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

final class AtvItemRenderer extends BlockEntityWithoutLevelRenderer {
    AtvItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) { super(dispatcher, models); }
    @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        poses.pushPose(); poses.translate(.5, .05, .5);
        AtvModel.render(poses, ItemRenderer.getFoilBufferDirect(buffers, RenderType.entityCutoutNoCull(AtvModel.TEXTURE), false, stack.hasFoil()), light, overlay, null, 0);
        poses.popPose();
    }
}
