package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** Render the seated pilot in the same interpolated frame as the pressure hull. */
public final class SubmarinePilotPose {
    private static final java.util.Map<PlayerModel<?>, java.util.List<SavedPart>> SAVED_POSES = new java.util.WeakHashMap<>();
    private record SavedPart(net.minecraft.client.model.geom.ModelPart part,
            net.minecraft.client.model.geom.PartPose pose, float xScale, float yScale, float zScale) {
        SavedPart(net.minecraft.client.model.geom.ModelPart part) {
            this(part, part.storePose(), part.xScale, part.yScale, part.zScale);
        }
        void restore() {
            part.loadPose(pose);
            part.xScale=xScale; part.yScale=yScale; part.zScale=zScale;
        }
    }
    private SubmarinePilotPose() {}

    public static boolean active(LivingEntity player) {
        return player.isAlive() && player.getVehicle() instanceof SubmarineEntity sub
                && sub.getControllingPassenger() == player && (!sub.isParked() || sub.isCabinTransition());
    }

    public static void body(AbstractClientPlayer player, PoseStack pose, float partial, float scale) {
        var sub = (SubmarineEntity) player.getVehicle();
        // The remote rider and boat can interpolate independently. Anchor the rendered eye
        // to the hull reference without moving the gameplay entity or the first-person camera.
        var targetEye = sub.isCabinTransition() ? sub.modelToWorld(SubmarineEntryAnimation.eye(sub.entryOrigin(), sub.cabinAnimationTime(partial)), partial)
                : sub.isBoarding() ? SubmarineHeadlight.modelPosition(sub, partial, sub.entryEye(partial))
                : sub.getPosition(partial).add(0, sub.pilotEyeOffset(), 0);
        var delta = targetEye
                .subtract(player.getPosition(partial).add(0, player.getEyeHeight(), 0));
        pose.translate(delta.x / scale, delta.y / scale, delta.z / scale);
        pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, sub.yRotO, sub.getYRot())));
        float eye = player.getEyeHeight() / scale;
        pose.translate(0, eye, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(SubmarineRenderer.visualRoll(sub, partial)));
        pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partial, sub.xRotO, sub.getXRot())));
        pose.translate(0, -eye, 0);
        if (sub.isCabinTransition()) pose.mulPose(Axis.YP.rotationDegrees(
                SubmarineEntryAnimation.heading(sub.entryOrigin(), sub.cabinAnimationTime(partial))));
        else if (sub.isBoarding()) pose.mulPose(Axis.YP.rotationDegrees(
                SubmarineEntryAnimation.heading(sub.entryOrigin(), sub.entryTime(partial))));
    }

    public static void prepare(PlayerModel<?> model, LivingEntity player) {
        restore(model);
        if (!active(player)) return;
        model.riding = !((SubmarineEntity) player.getVehicle()).isBoarding();
        model.crouching = false;
        model.swimAmount = 0;
    }

    public static void finish(PlayerModel<?> model, LivingEntity player, float ageInTicks) {
        if (player.getVehicle() instanceof SubmarineEntity cabin && cabin.isParked()
                && !cabin.isCabinTransition() && !player.isSleeping()) {
            // Vanilla suppresses limb swing for passengers even when shouldRiderSit is false.
            float swing = Mth.cos(cabin.cabinWalkPhase * .6662F) * .7F * cabin.cabinWalkSpeed;
            model.rightLeg.xRot = swing;
            model.leftLeg.xRot = -swing;
            if (model.rightArmPose == net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY) model.rightArm.xRot = -swing;
            if (model.leftArmPose == net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY) model.leftArm.xRot = swing;
            model.rightPants.copyFrom(model.rightLeg); model.leftPants.copyFrom(model.leftLeg);
            model.rightSleeve.copyFrom(model.rightArm); model.leftSleeve.copyFrom(model.leftArm);
            return;
        }
        if (!active(player)) return;
        var sub = (SubmarineEntity) player.getVehicle();
        float partial = Mth.clamp(ageInTicks - player.tickCount, 0, 1);
        if (sub.isCabinTransition()) { boarding(model, sub.cabinAnimationTime(partial)); return; }
        if (sub.isBoarding()) {
            boarding(model, sub.entryTime(partial));
            return;
        }
        // Keep the authored bent-knee seated pose through the control handoff.
        BlenderBoardingPlayback.apply(model, SubmarineEntryAnimation.DURATION);
        model.rightArm.xRot = model.leftArm.xRot = -.75F;
        model.rightSleeve.copyFrom(model.rightArm); model.leftSleeve.copyFrom(model.leftArm);
        model.head.yRot = Mth.wrapDegrees(Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot)
                - Mth.rotLerp(partial, sub.yRotO, sub.getYRot())) * Mth.DEG_TO_RAD;
        model.hat.copyFrom(model.head);
    }

    /** Shared with the mesh-clearance harness; every transform is applied to skin layers too. */
    public static void boarding(PlayerModel<?> model, float time) {
        BlenderBoardingPlayback.apply(model,time);
    }

    public static void restore(PlayerModel<?> model) {
        BlenderPlayerPose.restore(model);
        var saved = SAVED_POSES.remove(model);
        if (saved != null) saved.forEach(SavedPart::restore);
    }

    private static void aim(net.minecraft.client.model.geom.ModelPart arm, net.minecraft.world.phys.Vec3 target,
            float t, float weight) {
        var eye=SubmarineEntryAnimation.eye(SubmarineEntryAnimation.LADDER_START,t);
        double a=Math.toRadians(SubmarineEntryAnimation.heading(SubmarineEntryAnimation.LADDER_START,t));
        var d=target.subtract(eye.add(0,-1.62,0));
        // Invert the player's authored heading, scale, Y flip and Minecraft model translation.
        double x=-(d.x*Math.cos(a)-d.z*Math.sin(a))*16/.9375-arm.x;
        double y=(1.501-d.y/.9375)*16-arm.y;
        double z=(d.x*Math.sin(a)+d.z*Math.cos(a))*16/.9375-arm.z;
        double length=Math.sqrt(x*x+y*y+z*z);
        float xr=(float)Math.asin(z/Math.max(length,1e-6));
        float zr=(float)Math.atan2(-x,y);
        arm.xRot=Mth.rotLerp(weight,arm.xRot*Mth.RAD_TO_DEG,xr*Mth.RAD_TO_DEG)*Mth.DEG_TO_RAD;
        arm.zRot=zr*weight;
        arm.yScale=Mth.lerp(weight,1,Mth.clamp((float)length/10,.65F,1.08F));
    }

    static void seatedLegs(PlayerModel<?> model) {
        // Reassert vanilla's seated limbs after other equipment layers, including pants.
        model.rightLeg.setPos(-1.9F, 12, 0);
        model.leftLeg.setPos(1.9F, 12, 0);
        model.rightLeg.xRot = model.leftLeg.xRot = -1.4137167F;
        model.rightLeg.yRot = Mth.PI / 10;
        model.leftLeg.yRot = -Mth.PI / 10;
        model.rightLeg.zRot = .07853982F;
        model.leftLeg.zRot = -.07853982F;
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
    }
}
