package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;

/** Renders a yellow crossed plume from each boot's side booster outlet. */
public final class RocketBootsEffectLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation BOOST_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            PracticeMod.MODID, "textures/entity/rocket_boots_boost.png");
    // Blockbench side-thruster locators (+/-5.63, 1.2, 0.85), relative to leg pivots (+/-2, 12, 0).
    private static final float EXHAUST_X = 5.63F - 2.0F;
    private static final float EXHAUST_Y = (12.0F - 1.2F) / 16.0F;
    private static final float EXHAUST_Z = 0.85F / 16.0F;

    public RocketBootsEffectLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
            AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick,
            float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()
                || !(player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof RocketBootsItem)
                || JetpackFlightHandler.isWearingJetpack(player)
                || !Boolean.TRUE.equals(player.getData(PracticeMod.ROCKET_BOOTS_THRUSTING))) {
            return;
        }

        float time = ageInTicks;
        float flicker = 1.0F + 0.035F * Mth.sin(time * 2.7F)
                + 0.018F * Mth.sin(time * 5.1F);
        VertexConsumer plume = bufferSource.getBuffer(
                RenderType.entityTranslucentEmissive(BOOST_TEXTURE, false));
        renderBootPlume(poseStack, plume, this.getParentModel().leftLeg, EXHAUST_X, flicker);
        renderBootPlume(poseStack, plume, this.getParentModel().rightLeg, -EXHAUST_X, flicker);
    }

    private static void renderBootPlume(PoseStack poseStack, VertexConsumer plume,
            ModelPart leg, float exhaustX, float flicker) {
        poseStack.pushPose();
        leg.translateAndRotate(poseStack);
        poseStack.translate(exhaustX / 16.0F, 0.0F, EXHAUST_Z);
        // The width, length, alpha, crossed planes, UVs, and full-bright render path are exactly the
        // jetpack's normal (non-sprint) plume. Only the texture hue and emitter origin differ.
        JetpackModel.renderCrossedPlume(poseStack, plume, EXHAUST_Y,
                0.27F * flicker, 0.68F * flicker, 230);
        poseStack.popPose();
    }
}
