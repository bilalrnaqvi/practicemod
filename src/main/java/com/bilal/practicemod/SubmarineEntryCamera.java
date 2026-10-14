package com.bilal.practicemod;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/** Authored first-person boarding and separate third-person inspection shots. */
@EventBusSubscriber(modid = PracticeMod.MODID, value = Dist.CLIENT)
public final class SubmarineEntryCamera {
    private SubmarineEntryCamera() {}
    public static boolean interior(float t) { return t >= SubmarineEntryAnimation.CABIN_TICK; }

    static SubmarineChaseCamera.View view(BlockGetter level, Player player, SubmarineEntity sub, float partial) {
        float t = sub.entryTime(partial);
        Vec3 eye = reviewEye(t);
        Vec3 target = sub.entryEye(partial).add(0,-.6,0);
        Vec3 position = SubmarineHeadlight.modelPosition(sub,partial,eye);
        Vec3 focus = SubmarineHeadlight.modelPosition(sub,partial,target);
        Vec3 boom = position.subtract(focus);
        double safe = SubmarineCameraCollision.clearBoom(level,player,focus,boom.normalize(),boom.length());
        if (safe < .5) return firstPerson(sub,partial);
        position = focus.add(boom.normalize().scale(safe));
        Vec3 look = focus.subtract(position);
        float yaw = (float)Math.atan2(-look.x,look.z)*Mth.RAD_TO_DEG;
        float pitch = (float)-Math.atan2(look.y,look.horizontalDistance())*Mth.RAD_TO_DEG;
        return new SubmarineChaseCamera.View(position,yaw,pitch,false);
    }

    /** Keep the chair approach inside the aisle, clear of the starboard cabin frame. */
    static Vec3 reviewEye(float t) {
        if (t < SubmarineEntryAnimation.CABIN_TICK) return new Vec3(6.2,8.0,6.8);
        Vec3 ladder = new Vec3(0,3.65,-3.4);
        Vec3 chair = new Vec3(.65,3.65,-3.5).lerp(new Vec3(.65,3.65,-5.9),
                SubmarineEntryAnimation.ease(t,82,100));
        return ladder.lerp(chair,SubmarineEntryAnimation.ease(t,60,66));
    }

    static SubmarineChaseCamera.View firstPerson(SubmarineEntity sub, float partial) {
        float t=sub.entryTime(partial);
        return new SubmarineChaseCamera.View(SubmarineHeadlight.modelPosition(sub,partial,sub.isReturningToChair() ? sub.entryEye(partial) : SubmarineEntryAnimation.cameraEye(t)),
                sub.getYRot()-SubmarineEntryAnimation.cameraHeading(t),SubmarineEntryAnimation.cameraPitch(t),true);
    }

    /** Render the same skin and limb poses in world space so the hull occludes them correctly. */
    @SubscribeEvent
    static void body(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage()!=net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        var mc=Minecraft.getInstance();
        var player=mc.player;
        if (player==null || mc.gameRenderer.getMainCamera().isDetached()
                || !(player.getVehicle() instanceof SubmarineEntity sub) || (!sub.isBoarding() && !sub.isCabinTransition())) return;
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var renderer=(net.minecraft.client.renderer.entity.player.PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(player);
        var model=renderer.getModel();
        var pose=event.getPoseStack();
        SubmarinePilotPose.restore(model);
        float animationTime = sub.isCabinTransition() ? sub.cabinAnimationTime(partial) : sub.entryTime(partial);
        SubmarinePilotPose.boarding(model,animationTime);
        pose.pushPose();
        try {
            Vec3 origin=SubmarineHeadlight.modelPosition(sub,partial,sub.isCabinTransition()
                    ? SubmarineEntryAnimation.eye(sub.entryOrigin(), animationTime) : sub.entryEye(partial)).subtract(event.getCamera().getPosition());
            pose.translate(origin.x,origin.y,origin.z);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-Mth.rotLerp(partial,sub.yRotO,sub.getYRot())));
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(SubmarineRenderer.visualRoll(sub,partial)));
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-Mth.lerp(partial,sub.xRotO,sub.getXRot())));
            pose.translate(0,-1.62,0);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(SubmarineEntryAnimation.heading(sub.entryOrigin(),animationTime)));
            pose.scale(-.9375F,-.9375F,.9375F);
            pose.translate(0,-1.501,0);
            var buffer=mc.renderBuffers().bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(player.getSkin().texture()));
            int light=mc.getEntityRenderDispatcher().getPackedLightCoords(player,partial);
            for (var part:java.util.List.of(model.rightArm,model.leftArm,model.rightLeg,model.leftLeg,
                    model.rightSleeve,model.leftSleeve,model.rightPants,model.leftPants))
                part.render(pose,buffer,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            BoardingFirstPersonArmor.draw(renderer,player,pose,light,partial);
        } finally {
            pose.popPose();
            SubmarinePilotPose.restore(model);
        }
    }

    @SubscribeEvent
    static void hands(RenderHandEvent event) {
        var player=Minecraft.getInstance().player;
        if (player!=null && player.getVehicle() instanceof SubmarineEntity sub && (sub.isBoarding() || sub.isCabinTransition()))
            event.setCanceled(true);
    }
}
