package com.bilal.practicemod;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Matrix4f;

/** Opt-in development render harness, using Minecraft's actual models, shaders and bend mixins. */
@EventBusSubscriber(modid = PracticeMod.MODID, value = Dist.CLIENT)
public final class BlenderAnimationRenderCheck {
    private static boolean finished;
    private BlenderAnimationRenderCheck() {}

    @SubscribeEvent
    static void render(RenderFrameEvent.Post event) {
        String output = System.getProperty("practicemod.animationRenderCheck");
        var mc = Minecraft.getInstance();
        if (output == null || finished || !(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
        finished = true;
        Path directory = Path.of(output);
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("result.txt"), "RUNNING\n");
            try (var stream = mc.getResourceManager().open(ResourceLocation.fromNamespaceAndPath(
                    PracticeMod.MODID, "player_animations/deepseeker_entry.json"))) {
                var clip = new BlenderPlayerClip(stream);
                for (boolean slim : new boolean[]{false, true}) {
                    var model = new PlayerModel<AbstractClientPlayer>(mc.getEntityModels().bakeLayer(
                            slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
                    model.young=false;
                    String prefix = slim ? "slim-" : "wide-";
                    if (!slim) compareContacts(clip, model, directory);
                    draw(mc, model, slim, directory.resolve(prefix + "baseline.png"), false);
                    for (int tick : new int[]{0, 12, 20, 28, 40, 54, 68, 84, SubmarineEntryAnimation.DURATION}) {
                        clip.sample(tick);
                        BlenderPlayerPose.apply(model, clip);
                        draw(mc, model, slim, directory.resolve(prefix + tick + ".png"), false);
                        if (tick == 0 || tick == SubmarineEntryAnimation.DURATION)
                            draw(mc, model, slim, directory.resolve(prefix + "armor-" + tick + ".png"), true);
                        BlenderPlayerPose.restore(model);
                    }
                    Path motion = directory.resolve(slim ? "slim-motion" : "wide-motion");
                    Files.createDirectories(motion);
                    for (int frame = 0; frame < 96; frame++) {
                        clip.sample(frame * (float)SubmarineEntryAnimation.DURATION / 95);
                        BlenderPlayerPose.apply(model, clip);
                        draw(mc, model, slim, motion.resolve(String.format("%03d.png", frame)), false);
                        BlenderPlayerPose.restore(model);
                    }
                    draw(mc, model, slim, directory.resolve(prefix + "restored.png"), false);
                    if (Files.mismatch(directory.resolve(prefix + "baseline.png"), directory.resolve(prefix + "restored.png")) != -1)
                        throw new IllegalStateException(prefix + "model did not restore to its original rendered appearance");
                }
            }
            Files.writeString(directory.resolve("result.txt"), "PASS: wide/slim skins, 192 motion frames, four armor poses; both models restore pixel-identically.\n");
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(BlenderAnimationRenderCheck.class).error("Animation render check failed", ex);
            try { Files.writeString(directory.resolve("result.txt"), "FAIL: " + ex); } catch (Exception ignored) {}
        } finally {
            mc.stop();
        }
    }

    private static void compareContacts(BlenderPlayerClip clip, PlayerModel<?> model, Path directory) throws Exception {
        var references = com.google.gson.JsonParser.parseString(Files.readString(
                directory.getParent().resolve("runtime-reference.json"))).getAsJsonArray();
        float maxError = 0;
        String worst = "";
        var parts = java.util.Map.of("rightArm", model.rightArm, "leftArm", model.leftArm,
                "rightLeg", model.rightLeg, "leftLeg", model.leftLeg);
        for (var reference : references) {
            var frame = reference.getAsJsonObject();
            int tick = frame.get("tick").getAsInt();
            clip.sample(tick);
            BlenderPlayerPose.apply(model, clip);
            for (var entry : parts.entrySet()) {
                var part = entry.getValue();
                var cube = (io.github.kosmx.bendylib.impl.BendableCuboid)
                        io.github.kosmx.bendylib.ModelPartAccessor.optionalGetCuboid(part, 0).orElseThrow()
                                .getActiveMutator().getB();
                var center = new org.joml.Vector3f();
                int[] count = {0};
                cube.iteratePositions(point -> {
                    if (Math.abs(point.getOriginalPos().y - cube.maxY) < .001) {
                        center.add(point.getPos()); count[0]++;
                    }
                });
                center.div(count[0]);
                new Matrix4f().translation(part.x, part.y, part.z)
                        .rotateZYX(part.zRot, part.yRot, part.xRot).transformPosition(center);
                var expected = frame.getAsJsonObject("tips").getAsJsonArray(entry.getKey());
                float error = center.distance(expected.get(0).getAsFloat(), expected.get(1).getAsFloat(), expected.get(2).getAsFloat());
                if (error > maxError) { maxError = error; worst = entry.getKey() + " at tick " + tick; }
            }
            BlenderPlayerPose.restore(model);
        }
        Files.writeString(directory.resolve("contact-comparison.txt"),
                (references.size()*4)+" rendered limb endpoints vs Blender. Maximum error: " + maxError + " model pixels; " + worst + "\n");
        // The legacy reference and vanilla legs differ by a tenth of a pixel at their pivots.
        if (maxError > .15F) throw new IllegalStateException("Blender contact mismatch: " + maxError + " pixels, " + worst);
    }

    private static void draw(Minecraft mc, PlayerModel<AbstractClientPlayer> model, boolean slim, Path output, boolean armored) throws Exception {
        var target = new TextureTarget(512, 512, true, Minecraft.ON_OSX);
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var view = RenderSystem.getModelViewStack();
        view.pushMatrix();
        try {
            target.setClearColor(.09F, .12F, .17F, 1);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(-1.05F, 1.05F, -1.05F, 1.05F, -10, 10),
                    VertexSorting.ORTHOGRAPHIC_Z);
            view.identity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            var pose = new PoseStack();
            pose.mulPose(Axis.XP.rotationDegrees(12));
            pose.mulPose(Axis.YP.rotationDegrees(-30));
            pose.scale(1, -1, -1);
            pose.translate(0, -.75, 0);
            mc.gameRenderer.lightTexture().turnOnLightLayer();
            var texture = ResourceLocation.withDefaultNamespace("textures/entity/player/"
                    + (slim ? "slim/alex.png" : "wide/steve.png"));
            var buffers = mc.renderBuffers().bufferSource();
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
            if (armored) {
                for (boolean inner : new boolean[]{false, true}) {
                    var armor = new HumanoidModel<AbstractClientPlayer>(mc.getEntityModels().bakeLayer(
                            inner ? ModelLayers.PLAYER_INNER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR));
                    model.copyPropertiesTo(armor);
                    armor.setAllVisible(!inner);
                    armor.rightLeg.visible = armor.leftLeg.visible = inner;
                    armor.renderToBuffer(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(
                                    ResourceLocation.withDefaultNamespace("textures/models/armor/diamond_layer_" + (inner ? 2 : 1) + ".png"))),
                            LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
                }
            }
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
