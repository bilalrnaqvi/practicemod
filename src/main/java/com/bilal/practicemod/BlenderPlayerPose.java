package com.bilal.practicemod;

import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import io.github.kosmx.bendylib.ModelPartAccessor;
import io.github.kosmx.bendylib.impl.BendableCuboid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/** Scoped bridge from PAL's evaluated bones to bendy-lib's skin/armor cubes. */
public final class BlenderPlayerPose {
    private static final String MUTATOR = "practicemod:blender";
    private static final Map<PlayerModel<?>, List<SavedPart>> SAVED = new WeakHashMap<>();
    private record SavedPart(ModelPart part, PartPose pose, float sx, float sy, float sz,
                             String previousMutator) {
        SavedPart(ModelPart part) {
            this(part, part.storePose(), part.xScale, part.yScale, part.zScale,
                    ModelPartAccessor.optionalGetCuboid(part, 0).map(cube -> {
                        var active = cube.getActiveMutator();
                        return active == null ? null : active.getA();
                    }).orElse(null));
        }
        void restore() {
            part.loadPose(pose);
            part.xScale = sx; part.yScale = sy; part.zScale = sz;
            ModelPartAccessor.optionalGetCuboid(part, 0).ifPresent(cube -> {
                var active = cube.getActiveMutator();
                if (active != null && MUTATOR.equals(active.getA()))
                    cube.getAndActivateMutator(previousMutator);
            });
        }
    }

    private BlenderPlayerPose() {}

    public static void restore(PlayerModel<?> model) {
        var saved = SAVED.remove(model);
        if (saved != null) saved.forEach(SavedPart::restore);
    }

    public static void apply(PlayerModel<?> model, BlenderPlayerClip clip) {
        restore(model);
        var saved = new ArrayList<SavedPart>();
        for (var part : List.of(model.body, model.head, model.hat, model.jacket,
                model.rightArm, model.leftArm, model.rightLeg, model.leftLeg,
                model.rightSleeve, model.leftSleeve, model.rightPants, model.leftPants))
            saved.add(new SavedPart(part));
        SAVED.put(model, saved);
        float bend = clip.bone("torso").getBend();
        part(model.body, clip.bone("torso"), Direction.DOWN);
        part(model.head, clip.bone("head"), null);
        part(model.rightArm, clip.bone("right_arm"), Direction.UP);
        part(model.leftArm, clip.bone("left_arm"), Direction.UP);
        part(model.rightLeg, clip.bone("right_leg"), Direction.UP);
        part(model.leftLeg, clip.bone("left_leg"), Direction.UP);
        // The upper torso turns about its middle. Compose rotations instead of adding
        // Euler angles so turned heads and reaching shoulders remain attached.
        for (var upper : List.of(model.head, model.rightArm, model.leftArm)) {
            Vector3f position = new Vector3f(upper.x, upper.y - 6, upper.z).rotateX(bend);
            upper.setPos(position.x, position.y + 6, position.z);
            var rotation = new Matrix3f().rotationX(bend).rotateZYX(upper.zRot, upper.yRot, upper.xRot);
            upper.yRot = (float) Math.asin(Math.clamp(-rotation.m02(), -1, 1));
            if (Math.abs(Math.cos(upper.yRot)) > 1e-6) {
                upper.xRot = (float) Math.atan2(rotation.m12(), rotation.m22());
                upper.zRot = (float) Math.atan2(rotation.m01(), rotation.m00());
            } else {
                upper.xRot = (float) Math.atan2(-rotation.m21(), rotation.m11());
                upper.zRot = 0;
            }
        }
        // The legacy exporter puts the hip rotation on PAL's body channel.
        // Apply it to the entire authored hierarchy about the vanilla hip pivot.
        var body = clip.bone("body");
        var hip = new Matrix3f().rotationZYX(body.getRotZ(), body.getRotY(), body.getRotX());
        for (var part : List.of(model.body, model.head, model.rightArm, model.leftArm,
                model.rightLeg, model.leftLeg)) {
            var position = hip.transform(new Vector3f(part.x, part.y - 12, part.z));
            part.setPos(position.x, position.y + 12, position.z);
            var rotation = new Matrix3f(hip).rotateZYX(part.zRot, part.yRot, part.xRot);
            part.yRot = (float) Math.asin(Math.clamp(-rotation.m02(), -1, 1));
            if (Math.abs(Math.cos(part.yRot)) > 1e-6) {
                part.xRot = (float) Math.atan2(rotation.m12(), rotation.m22());
                part.zRot = (float) Math.atan2(rotation.m01(), rotation.m00());
            } else {
                part.xRot = (float) Math.atan2(-rotation.m21(), rotation.m11());
                part.zRot = 0;
            }
        }
        // copyFrom transfers bend state only when the target has a registered builder.
        register(model.jacket, Direction.DOWN);
        for (var layer : List.of(model.rightSleeve, model.leftSleeve, model.rightPants, model.leftPants))
            register(layer, Direction.UP);
        model.hat.copyFrom(model.head); model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm); model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg); model.leftPants.copyFrom(model.leftLeg);
    }

    private static void part(ModelPart part, PlayerAnimBone bone, Direction direction) {
        var initial = part.getInitialPose();
        part.setPos(initial.x + bone.getPosX(), initial.y - bone.getPosY(), initial.z + bone.getPosZ());
        part.xRot = bone.getRotX(); part.yRot = initial.yRot + bone.getRotY(); part.zRot = bone.getRotZ();
        part.xScale = bone.getScaleX(); part.yScale = bone.getScaleY(); part.zScale = bone.getScaleZ();
        if (direction == null) return;
        register(part, direction);
        ModelPartAccessor.optionalGetCuboid(part, 0).ifPresent(cube -> {
            ((BendableCuboid) cube.getAndActivateMutator(MUTATOR)).applyBend(0, bone.getBend());
        });
    }

    private static void register(ModelPart part, Direction direction) {
        ModelPartAccessor.optionalGetCuboid(part, 0).ifPresent(cube -> {
            if (!cube.hasMutator(MUTATOR))
                cube.registerMutator(MUTATOR, data -> new BendableCuboid.Builder().setDirection(direction).build(data));
        });
    }

    public static void prepareArmor(net.minecraft.client.model.HumanoidModel<?> source,
                                    net.minecraft.client.model.HumanoidModel<?> target) {
        var parts = List.of(source.body, source.rightArm, source.leftArm, source.rightLeg, source.leftLeg);
        var targets = List.of(target.body, target.rightArm, target.leftArm, target.rightLeg, target.leftLeg);
        for (int i = 0; i < parts.size(); i++) {
            var active = ModelPartAccessor.optionalGetCuboid(parts.get(i), 0)
                    .map(cube -> cube.getActiveMutator()).orElse(null);
            if (active != null && MUTATOR.equals(active.getA()))
                register(targets.get(i), i == 0 ? Direction.DOWN : Direction.UP);
        }
    }
}
