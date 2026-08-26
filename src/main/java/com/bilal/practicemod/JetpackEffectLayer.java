package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Renders the concept-art plume and hover pulses in the animated jetpack model's local space. */
public final class JetpackEffectLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation BOOST_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            PracticeMod.MODID, "textures/entity/jetpack_boost.png");
    private static final ResourceLocation OVERHEAT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            PracticeMod.MODID, "textures/entity/jetpack_overheat.png");

    private final JetpackModel effectModel;

    public JetpackEffectLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
            EntityModelSet modelSet) {
        super(parent);
        this.effectModel = new JetpackModel(modelSet.bakeLayer(JetpackModel.LAYER));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
            AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick,
            float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof JetpackItem) || player.isInvisible()) {
            return;
        }

        JetpackAnimationState state = JetpackAnimationController.get(player.getId());
        if (state == null) {
            return;
        }

        boolean overheated = Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_OVERHEATED));
        boolean hovering = Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_HOVERING));
        boolean surging = Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_SURGING));
        boolean thrusting = !overheated && !hovering && JetpackFuelHandler.hasFuelSupply(chest)
                && Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_THRUSTING));
        if (!thrusting && !hovering && !overheated) {
            return;
        }

        // Match the armor layer's setup, then drive our private model copy with the same animation
        // sample. The two flight-effect passes traverse its real nested parts but draw only exhaust.
        // The pack is a body child; only the body's current pose/scale participates in its parent
        // transform. Copying that part directly also avoids pretending the LivingEntity armor model
        // and AbstractClientPlayer skin model have the same generic parameter.
        this.effectModel.body.copyFrom(this.getParentModel().body);
        this.effectModel.applyAnimation(state, partialTick);

        if (overheated) {
            // One authored texture replaces the old fire + dust + smoke stack. Like the blue plume,
            // it is rendered through each exhaust emitter's actual animated hierarchy.
            VertexConsumer thermalFlare = bufferSource.getBuffer(
                    RenderType.entityTranslucentEmissive(OVERHEAT_TEXTURE, false));
            this.effectModel.renderOverheatPlumes(poseStack, thermalFlare, state, partialTick);
            return;
        }

        // Hover deliberately has no miniature boost plume: its only steady engine effect is the
        // pair of cyan halo pulses below each exhaust. Normal and sprint thrust keep the authored
        // plume treatment.
        if (thrusting) {
            VertexConsumer plume = bufferSource.getBuffer(
                    RenderType.entityTranslucentEmissive(BOOST_TEXTURE, false));
            this.effectModel.renderFlightPlumes(
                    poseStack, plume, state, partialTick, thrusting, surging);
        }

        // When thrusting, requesting the halo buffer finalizes the preceding plume buffer, so these
        // remain ordered passes rather than consumers acquired up front and written interleaved.
        VertexConsumer halo = bufferSource.getBuffer(RenderType.lightning());
        this.effectModel.renderFlightHalos(poseStack, halo, state, partialTick, thrusting, hovering);
    }
}
