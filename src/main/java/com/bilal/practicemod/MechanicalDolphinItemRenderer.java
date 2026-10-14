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

final class MechanicalDolphinItemRenderer extends BlockEntityWithoutLevelRenderer {
    MechanicalDolphinItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) { super(dispatcher, models); }
    @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        poses.pushPose(); poses.translate(.5, .05, .5);
        poses.scale(.48F, .48F, .48F); poses.translate(0, 0, .4);
        MechanicalDolphinModel.renderOpaque(poses, ItemRenderer.getFoilBufferDirect(buffers,
                RenderType.entityCutoutNoCull(MechanicalDolphinModel.TEXTURE), false, stack.hasFoil()), light, overlay, 0, 0);
        MechanicalDolphinModel.renderGlass(poses, buffers.getBuffer(RenderType.entityTranslucent(MechanicalDolphinModel.TEXTURE)), light, overlay);
        poses.popPose();
    }
}
