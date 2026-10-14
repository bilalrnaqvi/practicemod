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

/** Displays the same alien skateboard in hands, inventory, frames, and dropped items. */
final class SkateboardItemRenderer extends BlockEntityWithoutLevelRenderer {
    SkateboardItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
            MultiBufferSource buffers, int light, int overlay) {
        poses.pushPose();
        poses.translate(0.5F, 0.375F, 0.5F);
        var vertices = ItemRenderer.getFoilBufferDirect(buffers,
                RenderType.entityCutoutNoCull(SkateboardModel.TEXTURE), false, stack.hasFoil());
        SkateboardModel.render(poses, vertices, light, overlay);
        poses.popPose();
    }
}
