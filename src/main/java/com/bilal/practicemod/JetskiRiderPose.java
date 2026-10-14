package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** The seated rider shares the hull attitude and reaches toward the Compact Racer handlebars. */
public final class JetskiRiderPose {
    public static void limbs(PlayerModel<?> model, LivingEntity rider) {
        if (!(rider.getVehicle() instanceof JetskiEntity)) return;
        model.rightArm.xRot = model.leftArm.xRot = -1.0F;
        model.rightArm.yRot = -.12F; model.leftArm.yRot = .12F;
        model.rightArm.zRot = model.leftArm.zRot = 0;
        model.rightLeg.xRot = model.leftLeg.xRot = -1.25F;
        model.rightLeg.yRot = .28F; model.leftLeg.yRot = -.28F;
        model.rightLeg.zRot = .08F; model.leftLeg.zRot = -.08F;
        model.rightSleeve.copyFrom(model.rightArm); model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg); model.leftPants.copyFrom(model.leftLeg);
    }
    public static void body(AbstractClientPlayer rider, PoseStack pose, float partial, float scale) {
        var ski = (JetskiEntity) rider.getVehicle();
        pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, ski.yRotO, ski.getYRot())));
        float hips = .65F / scale;
        pose.translate(0, hips, 0);
        pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partial, ski.xRotO, ski.getXRot())));
        pose.mulPose(Axis.ZP.rotationDegrees(ski.roll(partial)));
        pose.translate(0, -hips, 0);
    }
}
