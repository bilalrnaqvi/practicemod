package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/** Authored Y-up geometry; same exporter conventions as the skateboard. */
public class SubmarineRenderer extends EntityRenderer<SubmarineEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            PracticeMod.MODID, "textures/entity/deepseeker.png");

    public SubmarineRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.8F;
    }

    static float visualRoll(SubmarineEntity entity, float partialTick) {
        float hurt = entity.getHurtTime() - partialTick;
        return hurt > 0 ? Mth.sin(hurt) * hurt * Math.max(0, entity.getDamage() - partialTick)
                / 10 * entity.getHurtDir() : 0;
    }

    @Override
    public boolean shouldRender(SubmarineEntity entity, Frustum frustum, double x, double y, double z) {
        // Hull is larger than the original boat collision box. Vanilla also explicitly renders
        // the camera player's vehicle, including first person: no second cockpit/HUD pass.
        return entity.shouldRender(x, y, z) && frustum.isVisible(entity.getBoundingBox().inflate(6 * DeepseekerPilot.HULL_SCALE));
    }

    @Override
    public void render(SubmarineEntity entity, float yaw, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        // Align the authored canopy pilot reference to the raised passenger eye.
        pose.translate(0, entity.pilotEyeOffset(), 0);
        pose.mulPose(Axis.ZP.rotationDegrees(visualRoll(entity, partialTick)));
        pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
        pose.translate(-DeepseekerPilot.X, -DeepseekerPilot.Y, -DeepseekerPilot.Z);
        // The pilot's chase view gets consistent fill even with the headlights off.
        // Preserve environmental sky light, brighter light, and directional shading.
        var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
        boolean chaseView = camera.isDetached() && camera.getEntity() != null
                && camera.getEntity().getVehicle() == entity;
        int fill = chaseView ? 14 : entity.headlightsOn() ? 10 : 0;
        int hullLight = (light & 0xFFFF0000) | Math.max(light & 0xFFFF, fill << 4);
        // Omit the small rear fittings beyond 48 blocks. They share the hull atlas/pass,
        // but skipping submission also avoids their CPU transforms and GPU work.
        boolean rearCabin = entity.position().distanceToSqr(net.minecraft.client.Minecraft.getInstance()
                .gameRenderer.getMainCamera().getPosition()) <= 48 * 48;
        renderHull(pose, buffers, hullLight, false, entity.headlightsOn(), rearCabin,
                entity.fanAnimation.angle(partialTick), entity.joystickAnimation.pitch(partialTick),
                entity.joystickAnimation.roll(partialTick), SubmarineEntryAnimation.hatch(entity.entryTime(partialTick)),
                SubmarineEntryAnimation.door(entity.entryTime(partialTick)), entity.chairAngle(partialTick),
                SubmarineEntryAnimation.gauge(entity.tickCount + partialTick));
        SubmarineInstruments.render(entity, partialTick, pose, buffers);
        renderHull(pose, buffers, hullLight, true);
        if (entity.headlightsOn()) SubmarineLampEffects.render(entity, partialTick, pose, buffers);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    static void renderHull(PoseStack poses, MultiBufferSource buffers, int light, boolean glass) {
        renderHull(poses, buffers, light, glass, false);
    }

    static void renderHull(PoseStack poses, MultiBufferSource buffers, int light, boolean glass, boolean headlights) {
        // The inventory miniature does not need the rear compartment fittings.
        renderHull(poses, buffers, light, glass, headlights, false);
    }

    static void renderHull(PoseStack poses, MultiBufferSource buffers, int light, boolean glass,
            boolean headlights, boolean rearCabin) {
        renderHull(poses, buffers, light, glass, headlights, rearCabin, 0, 0, 0, 0, 0, 0, 0);
    }

    static void renderHull(PoseStack poses, MultiBufferSource buffers, int light, boolean glass,
            boolean headlights, boolean rearCabin, float fanAngle, float stickPitch, float stickRoll, float hatch, float door, float chair, float gauge) {
        var vertices = buffers.getBuffer(glass ? RenderType.entityTranslucent(TEXTURE)
                : RenderType.entityCutoutNoCull(TEXTURE));
        PoseStack.Pose pose = poses.last();
        Vector3f position = new Vector3f(), normal = new Vector3f();
        for (int c = 0; c < DeepseekerGeometry.CUBES.length; c++) {
            if (DeepseekerGeometry.GLASS[c] != glass) continue;
            if (!rearCabin && DeepseekerGeometry.REAR_CABIN[c]) continue;
            int entryPart = DeepseekerGeometry.ENTRY_PARTS[c];
            if (entryPart != 0) {
                poses.pushPose();
                if (entryPart == 1) {
                    poses.translate(DeepseekerCabinRig.HATCH_X, DeepseekerCabinRig.HATCH_Y, DeepseekerCabinRig.HATCH_Z);
                    poses.mulPose(Axis.XP.rotationDegrees(hatch));
                    poses.translate(-DeepseekerCabinRig.HATCH_X, -DeepseekerCabinRig.HATCH_Y, -DeepseekerCabinRig.HATCH_Z);
                } else if (entryPart == 2) poses.translate(door * 1.25, 0, 0);
                else {
                    poses.translate(0, 0, DeepseekerPilot.Z);
                    poses.mulPose(Axis.YP.rotationDegrees(chair));
                    poses.translate(0, 0, -DeepseekerPilot.Z);
                }
                pose = poses.last();
            }
            int rotor = DeepseekerGeometry.ROTORS[c];
            if (rotor >= 0) {
                float[] pivot = DeepseekerGeometry.ROTOR_ORIGINS[rotor];
                poses.pushPose();
                poses.translate(pivot[0], pivot[1], pivot[2]);
                poses.mulPose(Axis.ZP.rotationDegrees(fanAngle));
                poses.translate(-pivot[0], -pivot[1], -pivot[2]);
                pose = poses.last();
            }
            if (DeepseekerGeometry.JOYSTICK[c]) {
                float[] pivot = DeepseekerGeometry.JOYSTICK_ORIGIN;
                poses.pushPose();
                poses.translate(pivot[0], pivot[1], pivot[2]);
                poses.mulPose(Axis.ZP.rotationDegrees(stickRoll));
                poses.mulPose(Axis.XP.rotationDegrees(stickPitch));
                poses.translate(-pivot[0], -pivot[1], -pivot[2]);
                pose = poses.last();
            }
            if (DeepseekerGeometry.GAUGE[c]) {
                poses.pushPose();
                poses.translate(DeepseekerCabinRig.GAUGE_X, DeepseekerCabinRig.GAUGE_Y, DeepseekerCabinRig.GAUGE_Z);
                poses.mulPose(Axis.XP.rotationDegrees(gauge));
                poses.translate(-DeepseekerCabinRig.GAUGE_X, -DeepseekerCabinRig.GAUGE_Y, -DeepseekerCabinRig.GAUGE_Z);
                pose = poses.last();
            }
            float[] cube = DeepseekerGeometry.CUBES[c];
            for (int i = 0; i < cube.length; i += 8) {
                pose.pose().transformPosition(cube[i], cube[i + 1], cube[i + 2], position);
                pose.transformNormal(cube[i + 5], cube[i + 6], cube[i + 7], normal);
                vertices.addVertex(position.x(), position.y(), position.z(), -1,
                        cube[i + 3], cube[i + 4], OverlayTexture.NO_OVERLAY, (headlights && DeepseekerGeometry.LAMPS[c]) || DeepseekerGeometry.CABIN_LAMPS[c]
                                ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : light,
                        normal.x(), normal.y(), normal.z());
            }
            if (DeepseekerGeometry.GAUGE[c]) {
                poses.popPose();
                pose = poses.last();
            }
            if (DeepseekerGeometry.JOYSTICK[c]) {
                poses.popPose();
                pose = poses.last();
            }
            if (rotor >= 0) {
                poses.popPose();
                pose = poses.last();
            }
            if (entryPart != 0) {
                poses.popPose();
                pose = poses.last();
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(SubmarineEntity entity) { return TEXTURE; }
}
