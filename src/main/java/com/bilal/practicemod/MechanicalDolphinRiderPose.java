package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** Full-length prone pilot, anchored to the cockpit through dives and breaches. */
public final class MechanicalDolphinRiderPose {
    private static final java.util.Map<PlayerModel<?>, java.util.List<SavedPart>> SAVED = new java.util.WeakHashMap<>();
    private record SavedPart(ModelPart part, PartPose pose) {
        SavedPart(ModelPart part) { this(part, part.storePose()); }
        void restore() { part.loadPose(pose); }
    }
    public static void prepare(PlayerModel<?> model, LivingEntity rider) {
        restore(model);
        if (!(rider.getVehicle() instanceof MechanicalDolphinEntity)) return;
        model.riding=false; model.crouching=false; model.swimAmount=0;
    }
    public static void restore(PlayerModel<?> model) {
        var saved=SAVED.remove(model);
        if (saved!=null) saved.forEach(SavedPart::restore);
    }
    public static void limbs(PlayerModel<?> model, LivingEntity rider) {
        if (rider.getVehicle() instanceof MechanicalDolphinEntity) prone(model);
    }
    /** Shared with the actual wide/slim player mesh clearance check. */
    public static void prone(PlayerModel<?> model) {
        SAVED.computeIfAbsent(model, key -> java.util.List.of(model.body, model.head, model.hat,
                model.jacket, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg,
                model.rightSleeve, model.leftSleeve, model.rightPants, model.leftPants)
                .stream().map(SavedPart::new).toList());
        part(model.body,0,0,4,Mth.HALF_PI);
        part(model.head,0,2.6F,0,0);
        part(model.rightArm,-5,1,5,-Mth.HALF_PI);
        part(model.leftArm,5,1,5,-Mth.HALF_PI);
        part(model.rightLeg,-1.9F,0,16,Mth.HALF_PI);
        part(model.leftLeg,1.9F,0,16,Mth.HALF_PI);
        model.hat.copyFrom(model.head); model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm); model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg); model.leftPants.copyFrom(model.leftLeg);
    }
    private static void part(ModelPart part,float x,float y,float z,float rotation) {
        part.setPos(x,y,z); part.xRot=rotation; part.yRot=part.zRot=0;
    }
    public static void body(AbstractClientPlayer rider, PoseStack pose, float partial, float scale) {
        var dolphin = (MechanicalDolphinEntity) rider.getVehicle();
        float yaw=Mth.rotLerp(partial,dolphin.yRotO,dolphin.getYRot());
        float pitch=Mth.lerp(partial,dolphin.xRotO,dolphin.getXRot());
        var targetEye=dolphin.getPosition(partial).add(MechanicalDolphinEntity.pilotEyeOffset(yaw,pitch));
        var delta=targetEye.subtract(rider.getPosition(partial).add(0,rider.getEyeHeight(),0));
        pose.translate(delta.x/scale,delta.y/scale,delta.z/scale);
        pose.mulPose(Axis.YP.rotationDegrees(180-yaw));
        float eye=rider.getEyeHeight()/scale;
        pose.translate(0,eye,0);
        pose.mulPose(Axis.XP.rotationDegrees(-pitch));
        pose.translate(0,-eye,0);
    }
}
