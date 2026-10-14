package com.bilal.practicemod;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.LightTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Matrix4f;

/** Opt-in model review through the real vehicle renderer; does not change a world. */
@EventBusSubscriber(modid = PracticeMod.MODID, value = Dist.CLIENT)
public final class SubmarineModelReview {
    private static boolean finished;
    @SubscribeEvent
    static void render(RenderFrameEvent.Post event) {
        String output = System.getProperty("practicemod.submarineModelReview");
        var mc = Minecraft.getInstance();
        if (output == null || finished || !(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
        finished = true;
        Path directory = Path.of(output);
        try {
            Files.createDirectories(directory);
            draw(mc, directory.resolve("closed.png"), 0, -1, false);
            draw(mc, directory.resolve("open.png"), DeepseekerCabinRig.HATCH_OPEN_DEGREES, -1, false);
            for (boolean firstPerson : new boolean[]{false,true}) {
                var motion=directory.resolve(firstPerson ? "first-person" : "outside");
                Files.createDirectories(motion);
                for (int tick=0;tick<=SubmarineEntryAnimation.DURATION;tick+=2)
                    draw(mc,motion.resolve(String.format("%03d.png",tick)),SubmarineEntryAnimation.hatch(tick),tick,firstPerson);
            }
            Files.writeString(directory.resolve("result.txt"), "PASS: rear-entry full hull, outside motion and first-person motion rendered by Minecraft.\n");
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(SubmarineModelReview.class).error("Model review failed", ex);
            try { Files.writeString(directory.resolve("result.txt"), "FAIL: " + ex); } catch (Exception ignored) {}
        } finally { mc.stop(); }
    }
    private static void draw(Minecraft mc, Path output, float hatch, float tick, boolean firstPerson) throws Exception {
        var target = new TextureTarget(1200, 900, true, Minecraft.ON_OSX);
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var view = RenderSystem.getModelViewStack();
        view.pushMatrix();
        try {
            target.setClearColor(.075F, .11F, .16F, 1);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(-7,7,-5.25F,5.25F,.1F,100), VertexSorting.ORTHOGRAPHIC_Z);
            view.identity().lookAt(12,13,16,0,4,0,0,1,0);
            if (tick>=SubmarineEntryAnimation.CABIN_TICK) {
                var anchor=SubmarineEntryAnimation.eye(SubmarineEntryAnimation.LADDER_START,tick);
                var camera=SubmarineEntryCamera.reviewEye(tick);
                view.identity().lookAt((float)camera.x,(float)camera.y,(float)camera.z,
                    (float)anchor.x,(float)anchor.y-.6F,(float)anchor.z,0,1,0);
                RenderSystem.setProjectionMatrix(new Matrix4f().setPerspective((float)Math.toRadians(85),4F/3,.025F,100),VertexSorting.DISTANCE_TO_ORIGIN);
            }
            if (firstPerson) {
                var eye=SubmarineEntryAnimation.cameraEye(tick);
                double yaw=Math.toRadians(SubmarineEntryAnimation.cameraHeading(tick));
                double pitch=Math.toRadians(SubmarineEntryAnimation.cameraPitch(tick));
                var direction=new net.minecraft.world.phys.Vec3(-Math.sin(yaw)*Math.cos(pitch),-Math.sin(pitch),-Math.cos(yaw)*Math.cos(pitch));
                var focus=eye.add(direction);
                view.identity().lookAt((float)eye.x,(float)eye.y,(float)eye.z,(float)focus.x,(float)focus.y,(float)focus.z,0,1,0);
                RenderSystem.setProjectionMatrix(new Matrix4f().setPerspective((float)Math.toRadians(75),4F/3,.025F,100),VertexSorting.DISTANCE_TO_ORIGIN);
            }
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1,1,1,1);
            com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
            mc.gameRenderer.lightTexture().turnOnLightLayer();
            var buffers = mc.renderBuffers().bufferSource();
            for (boolean glass : new boolean[]{false,true}) {
                SubmarineRenderer.renderHull(new PoseStack(), buffers, LightTexture.FULL_BRIGHT,
                        glass, false, true, 0,0,0,hatch,0,tick<0 ? 0 : SubmarineEntryAnimation.chair(tick),0);
                buffers.endBatch();
            }
            if (tick>=0) {
                var model=new net.minecraft.client.model.PlayerModel<net.minecraft.client.player.AbstractClientPlayer>(
                    mc.getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER),false);
                model.young=false;
                model.head.visible=model.hat.visible=!firstPerson;
                model.body.visible=model.jacket.visible=!firstPerson;
                BlenderBoardingPlayback.apply(model,tick);
                var anchor=SubmarineEntryAnimation.eye(SubmarineEntryAnimation.LADDER_START,tick);
                var pose=new PoseStack();pose.translate(anchor.x,anchor.y-1.62,anchor.z);
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(SubmarineEntryAnimation.heading(SubmarineEntryAnimation.LADDER_START,tick)));
                pose.scale(-.9375F,-.9375F,.9375F);pose.translate(0,-1.501,0);
                model.renderToBuffer(pose,buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png"))),
                    LightTexture.FULL_BRIGHT,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,-1);
                buffers.endBatch();BlenderPlayerPose.restore(model);
            }
            try (var pixels = Screenshot.takeScreenshot(target)) { pixels.writeToFile(output); }
        } finally {
            view.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            target.destroyBuffers();
            mc.getMainRenderTarget().bindWrite(true);
        }
    }
}
