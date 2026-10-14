package com.bilal.practicemod;

import java.util.Map;
import java.util.WeakHashMap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** Board-relative foot targets keep the planted foot still throughout the push. */
public final class SkateboardPoseRenderer {
    private static final Map<PlayerModel<?>, SkateboardKinematics.Pose> POSES = new WeakHashMap<>();
    static SkateboardKinematics.Pose poseFor(PlayerModel<?> model) { return POSES.get(model); }
    // Player renderers reuse a single rig for many players and first-person hands.
    // Restore precisely what we changed before vanilla animates the next render.
    private static final Map<PlayerModel<?>, Saved[]> SAVED = new WeakHashMap<>();
    private record Saved(ModelPart part, PartPose pose, float x, float y, float z, boolean skipDraw) {
        Saved(ModelPart part) { this(part, part.storePose(), part.xScale, part.yScale, part.zScale, part.skipDraw); }
        void restore() { part.loadPose(pose); part.xScale = x; part.yScale = y; part.zScale = z; part.skipDraw = skipDraw; }
    }

    public static void restore(PlayerModel<?> model) {
        POSES.remove(model);
        Saved[] saved = SAVED.remove(model);
        if (saved != null) for (Saved part : saved) part.restore();
    }

    private static boolean eligible(LivingEntity player) {
        return player.getVehicle() instanceof SkateboardEntity && player.isAlive()
                && !player.isSleeping() && !player.isFallFlying() && !player.isAutoSpinAttack();
    }

    public static void applyBodyRotation(AbstractClientPlayer player, PoseStack poses,
            float bodyYaw, float partialTick) {
        if (!eligible(player)) return;
        SkateboardEntity board = (SkateboardEntity) player.getVehicle();
        var sample = board.animation().sample(partialTick);
        float weight = sample.ride();
        float stance = SkateboardKinematics.pose(sample).stance();
        float boardYaw = Mth.rotLerp(partialTick, board.yRotO, board.getYRot());
        // Replace the look-driven body yaw with the board heading plus a sideways stance.
        poses.mulPose(Axis.YP.rotationDegrees(Mth.wrapDegrees(bodyYaw - boardYaw) * weight - stance - board.visualSpin(partialTick)));
        if (board.skateMode() == SkateboardEntity.GRIND_STATE && board.grindStyle() == 1)
            poses.mulPose(Axis.YP.rotationDegrees(90));
    }

    public static void applyLimbPose(PlayerModel<?> model, LivingEntity entity, float ageInTicks) {
        if (!eligible(entity)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (entity == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson()) return;
        SkateboardEntity board = (SkateboardEntity) entity.getVehicle();
        float partial = Mth.clamp(ageInTicks - entity.tickCount, 0, 1);
        SkateboardAnimation.Sample sample = board.animation().sample(partial);
        float weight = sample.ride();
        if (weight < 0.001F) return;
        ModelPart[] parts = {model.head, model.hat, model.body, model.jacket,
                model.leftLeg, model.rightLeg, model.leftPants, model.rightPants,
                model.leftArm, model.rightArm, model.leftSleeve, model.rightSleeve};
        Saved[] saved = new Saved[parts.length];
        for (int i = 0; i < parts.length; i++) saved[i] = new Saved(parts[i]);
        SAVED.put(model, saved);

        var rig = SkateboardKinematics.pose(sample, board.animation().crouch(partial), board.animation().pitch(partial));
        POSES.put(model, rig);
        var pelvis = rig.pelvis();
        float counter = rig.armSwing();
        fixedLeg(model.leftLeg, rig.left(), weight, SkateboardLegLayer.canRender(entity));
        fixedLeg(model.rightLeg, rig.right(), weight, SkateboardLegLayer.canRender(entity));
        // ModelPart's torso pivot is at the neck. Reposition that pivot around the
        // actual hip center, so leaning cannot pull the waist away from the legs.
        Quaternionf torso = new Quaternionf().rotationYXZ(rig.torsoYaw(), rig.lean(), 0);
        Vector3f neck = new Vector3f(0, -12, 0).rotate(torso)
                .add(pelvis.x(), pelvis.y() + 12, pelvis.z());
        model.body.setPos(neck.x, neck.y, neck.z);
        model.body.xRot = rig.lean();
        model.body.yRot = rig.torsoYaw();
        model.body.zRot = 0;
        model.head.setPos(neck.x, neck.y, neck.z);
        float boardYaw = Mth.rotLerp(partial, board.yRotO, board.getYRot());
        float look = Mth.rotLerp(partial, entity.yHeadRotO, entity.yHeadRot);
        model.head.yRot = Mth.lerp(weight, model.head.yRot,
                Mth.clamp(Mth.wrapDegrees(look - boardYaw - rig.stance()), -85, 85) * Mth.DEG_TO_RAD);
        arm(model.leftArm, pelvis.y(), -0.12F - counter, -0.08F, weight);
        arm(model.rightArm, pelvis.y(), 0.06F + counter, 0.10F, weight);
        if (board.balancing() || board.skateMode() == SkateboardEntity.AIR) {
            model.leftArm.zRot = -0.6F - board.balance() * 0.25F;
            model.rightArm.zRot = 0.6F - board.balance() * 0.25F;
        }
        if (board.currentTrick().grab()) {
            model.rightArm.xRot = -0.8F;
            model.rightArm.zRot = -0.25F;
        }
        // Both shoulder sockets follow the same torso transform. Keep item-use
        // rotations but move their sockets with the chest, avoiding detached arms.
        if (entity.isUsingItem() || entity.swinging) {
            for (int i : new int[] {8, 9}) saved[i].restore();
        }
        shoulder(model.leftArm, saved[8].pose(), torso, neck);
        shoulder(model.rightArm, saved[9].pose(), torso, neck);
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        // Armor uses its own rig. Keep its normal solid legs instead of clipping
        // third-party armor models into the articulated skin layer.
        if (SkateboardLegLayer.canRender(entity)) {
            model.leftLeg.skipDraw = model.rightLeg.skipDraw = true;
            model.leftPants.skipDraw = model.rightPants.skipDraw = true;
        }
    }

    private static void shoulder(ModelPart arm, PartPose original, Quaternionf torso, Vector3f neck) {
        Vector3f socket = new Vector3f(original.x, original.y, 0).rotate(torso).add(neck);
        arm.setPos(socket.x, socket.y, socket.z);
    }

    private static void arm(ModelPart arm, float compression, float pitch, float roll, float weight) {
        arm.y += compression * weight;
        arm.xRot = Mth.lerp(weight, arm.xRot, pitch);
        arm.yRot = Mth.lerp(weight, arm.yRot, 0);
        arm.zRot = Mth.lerp(weight, arm.zRot, roll);
    }

    private static void fixedLeg(ModelPart leg, SkateboardKinematics.Leg rig, float weight, boolean articulated) {
        var hip = rig.hip(); var foot = rig.foot();
        if (articulated) {
            // Vanilla/custom boots attach to the shin, with their local y=12 at the sole.
            var knee = rig.knee();
            hip = new SkateboardKinematics.Point(2 * knee.x() - foot.x(),
                    2 * knee.y() - foot.y(), 2 * knee.z() - foot.z());
        }
        float dx = foot.x() - hip.x(), dy = foot.y() - hip.y(), dz = foot.z() - hip.z();
        leg.x = Mth.lerp(weight, leg.x, hip.x());
        leg.y = Mth.lerp(weight, leg.y, hip.y());
        leg.z = Mth.lerp(weight, leg.z, hip.z());
        leg.xRot = (float) Math.atan2(dz, Math.sqrt(dx * dx + dy * dy));
        leg.zRot = (float) Math.atan2(-dx, dy);
        leg.yRot = 0;
        leg.xScale = leg.yScale = leg.zScale = 1;
    }

    private SkateboardPoseRenderer() {}
}
