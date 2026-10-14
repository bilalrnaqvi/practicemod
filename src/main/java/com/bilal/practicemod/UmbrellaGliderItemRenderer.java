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

/** Renders every item context from the authored closed rig pose, including the inventory GUI. */
final class UmbrellaGliderItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final float CLOSED_ROTATION = -72.0F;
    private static final float CLOSED_SCALE = 0.28F;
    private final UmbrellaGliderModel model = new UmbrellaGliderModel();

    UmbrellaGliderItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) {
        super(dispatcher, modelSet);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        // Built-in entity item models use the same y-down convention as ModelPart geometry.
        poseStack.scale(1.0F, -1.0F, -1.0F);
        this.model.render(poseStack, index -> ItemRenderer.getFoilBufferDirect(bufferSource,
                        RenderType.entityCutoutNoCull(UmbrellaGliderModel.TEXTURES[index]),
                        false, stack.hasFoil()), packedLight, packedOverlay,
                CLOSED_ROTATION, CLOSED_SCALE, 0.0F);
        poseStack.popPose();
    }
}
