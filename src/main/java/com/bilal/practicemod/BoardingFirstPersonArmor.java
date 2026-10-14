package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.EquipmentSlot;

/** Dedicated armor models keep first-person boarding from mutating shared layer state. */
final class BoardingFirstPersonArmor extends HumanoidArmorLayer<AbstractClientPlayer,
        PlayerModel<AbstractClientPlayer>, HumanoidModel<AbstractClientPlayer>> {
    private static final Map<PlayerRenderer, BoardingFirstPersonArmor> LAYERS = new WeakHashMap<>();

    private BoardingFirstPersonArmor(PlayerRenderer renderer) {
        super(renderer,
                new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                Minecraft.getInstance().getModelManager());
    }

    @Override
    protected void setPartVisibility(HumanoidModel<AbstractClientPlayer> model, EquipmentSlot slot) {
        super.setPartVisibility(model, slot);
        if (slot == EquipmentSlot.HEAD) model.setAllVisible(false);
        model.body.visible = false; // No collar/torso shell around the local eye.
    }

    static void draw(PlayerRenderer renderer, AbstractClientPlayer player, PoseStack pose, int light, float partial) {
        LAYERS.computeIfAbsent(renderer, BoardingFirstPersonArmor::new).render(pose,
                Minecraft.getInstance().renderBuffers().bufferSource(), light, player,
                0, 0, partial, player.tickCount + partial, 0, 0);
    }
}
