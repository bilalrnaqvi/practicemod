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
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Matrix4f;

/** Opt-in actual Minecraft line-shader/depth check, without opening or modifying a saved world. */
@EventBusSubscriber(modid = PracticeMod.MODID, value = Dist.CLIENT)
public final class GrappleRenderCheck {
    private static boolean finished;
    private GrappleRenderCheck() {}

    @SubscribeEvent
    static void render(RenderFrameEvent.Post event) {
        String output = System.getProperty("practicemod.grappleRenderCheck");
        var mc = Minecraft.getInstance();
        if (output == null || finished || !(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
        finished = true;
        Path directory = Path.of(output);
        try {
            Files.createDirectories(directory);
            for (boolean locked : new boolean[]{false, true}) {
                draw(mc, directory.resolve(locked ? "locked.png" : "target.png"), locked);
            }
            Files.writeString(directory.resolve("result.txt"), "PASS: actual Minecraft block and circle renderer completed. Inspect the images for appearance.\n");
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(GrappleRenderCheck.class).error("Grapple render check failed", ex);
            try { Files.writeString(directory.resolve("result.txt"), "FAIL: " + ex); } catch (Exception ignored) {}
        } finally { mc.stop(); }
    }

    private static void draw(Minecraft mc, Path output, boolean locked) throws Exception {
        var target = new TextureTarget(1000, 750, true, Minecraft.ON_OSX);
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var view = RenderSystem.getModelViewStack();
        view.pushMatrix();
        try {
            target.setClearColor(.075F, .11F, .16F, 1);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            Vec3 camera = new Vec3(3, 2.7, 6), anchor = new Vec3(.5, 1, 1);
            RenderSystem.setProjectionMatrix(new Matrix4f().setPerspective((float)Math.toRadians(65), 4F/3, .05F, 100), VertexSorting.DISTANCE_TO_ORIGIN);
            view.identity().lookAt((float)camera.x, (float)camera.y, (float)camera.z, .5F, .8F, .5F, 0, 1, 0);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
            mc.gameRenderer.lightTexture().turnOnLightLayer();
            var buffers = mc.renderBuffers().bufferSource();
            mc.getBlockRenderer().renderSingleBlock(Blocks.STONE.defaultBlockState(), new PoseStack(), buffers,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            buffers.endBatch();
            var poses = new PoseStack();
            poses.translate(camera.x, camera.y, camera.z);
            GrappleCableRenderer.ring(poses, buffers.getBuffer(RenderType.lines()), anchor, camera, locked);
            buffers.endBatch();
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
