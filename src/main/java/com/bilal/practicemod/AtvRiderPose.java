package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** The seated rider follows the sprung chassis and reaches for the CRAG steering wheel. */
public final class AtvRiderPose {
    public static void limbs(PlayerModel<?> model, LivingEntity rider) {
        if (!(rider.getVehicle() instanceof AtvEntity)) return;
        seatedLimbs(model);
    }
    static void seatedLimbs(PlayerModel<?> model) {
        model.rightArm.xRot = model.leftArm.xRot = -1.15F;
        model.rightArm.yRot = -.30F; model.leftArm.yRot = .30F;
        model.rightArm.zRot = model.leftArm.zRot = 0;
        model.rightLeg.xRot = model.leftLeg.xRot = -1.12F;
        model.rightLeg.yRot = .12F; model.leftLeg.yRot = -.12F;
        model.rightLeg.zRot = .08F; model.leftLeg.zRot = -.08F;
        model.rightSleeve.copyFrom(model.rightArm); model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg); model.leftPants.copyFrom(model.leftLeg);
    }
    public static void body(AbstractClientPlayer rider, PoseStack pose, float partial, float scale) {
        var ski = (AtvEntity) rider.getVehicle();
        pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, ski.yRotO, ski.getYRot())));
        float hips = (float) AtvEntity.HIP_OFFSET / scale;
        pose.translate(0, hips, 0);
        pose.mulPose(Axis.XP.rotationDegrees(-ski.visual(7, partial)));
        pose.mulPose(Axis.ZP.rotationDegrees(ski.visual(1, partial)));
        pose.translate(0, -hips, 0);
    }
}
