package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.Vec3;

/** Skin arms drawn in hull space, so camera motion cannot pull hands off the controls. */
public final class MechanicalDolphinCockpit {
    static final Vec3 EYE = new Vec3(0, 1.05, .45);
    static final float PLAYER_SCALE = .9375F;
    // Centers of the two authored control_grip parts, converted from Blockbench to runtime.
    static final float GRIP_X = 4.85F / 16;
    static final float GRIP_Y = 11.7F / 16;
    static final float GRIP_Z = 19.7F / 16;
    private final PlayerModel<AbstractClientPlayer> wide;
    private final PlayerModel<AbstractClientPlayer> slim;

    MechanicalDolphinCockpit(EntityRendererProvider.Context context) {
        // Dedicated models avoid changing shared third-person or vanilla hand model state.
        wide = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
        slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        gripPose(wide, false);
        gripPose(slim, true);
    }

    public static Vec3 cameraPosition(MechanicalDolphinEntity dolphin, float partial) {
        float yaw = Mth.rotLerp(partial, dolphin.yRotO, dolphin.getYRot());
        float pitch = Mth.lerp(partial, dolphin.xRotO, dolphin.getXRot());
        return dolphin.getPosition(partial).add(EYE.xRot(-pitch * Mth.DEG_TO_RAD).yRot(-yaw * Mth.DEG_TO_RAD));
    }

    void render(MechanicalDolphinEntity dolphin, PoseStack pose, MultiBufferSource buffers, int light) {
        var mc = Minecraft.getInstance();
        if (!mc.options.getCameraType().isFirstPerson() || mc.gameRenderer.getMainCamera().isDetached()
                || !(mc.getCameraEntity() instanceof AbstractClientPlayer player)
                || player.getVehicle() != dolphin || !player.isAlive() || player.isInvisible()) return;
        var model = player.getSkin().model() == PlayerSkin.Model.SLIM ? slim : wide;
        var out = buffers.getBuffer(RenderType.entityCutoutNoCull(player.getSkin().texture()));
        renderArms(model, pose, out, light,
                player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE),
                player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
    }

    /** Normal-length vanilla arms; the final hand section encloses each horizontal grip. */
    static void gripPose(PlayerModel<?> model, boolean slim) {
        float units = 16 / PLAYER_SCALE;
        float centerX = slim ? .5F : 1;
        arm(model.rightArm, -GRIP_X * units + centerX, units);
        arm(model.leftArm, GRIP_X * units - centerX, units);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }

    private static void arm(ModelPart arm, float x, float units) {
        arm.setPos(x, -GRIP_Y * units, 8.5F - GRIP_Z * units);
        arm.xRot = -Mth.HALF_PI;
        arm.yRot = arm.zRot = 0;
    }

    static void renderArms(PlayerModel<?> model, PoseStack pose, VertexConsumer out, int light,
            boolean leftSleeve, boolean rightSleeve) {
        pose.pushPose();
        pose.scale(PLAYER_SCALE, -PLAYER_SCALE, -PLAYER_SCALE);
        model.rightArm.render(pose, out, light, OverlayTexture.NO_OVERLAY);
        model.leftArm.render(pose, out, light, OverlayTexture.NO_OVERLAY);
        if (rightSleeve) model.rightSleeve.render(pose, out, light, OverlayTexture.NO_OVERLAY);
        if (leftSleeve) model.leftSleeve.render(pose, out, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
