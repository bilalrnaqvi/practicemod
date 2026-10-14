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

final class JetskiItemRenderer extends BlockEntityWithoutLevelRenderer {
    JetskiItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) { super(dispatcher, models); }
    @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
            MultiBufferSource buffers, int light, int overlay) {
        poses.pushPose();
        poses.translate(0.5, 0.2, 0.5);
        JetskiModel.render(poses, ItemRenderer.getFoilBufferDirect(buffers,
                RenderType.entityCutoutNoCull(JetskiModel.TEXTURE), false, stack.hasFoil()), light, overlay);
        poses.popPose();
    }
}
