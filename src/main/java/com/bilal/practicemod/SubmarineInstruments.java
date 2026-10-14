package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Physical full-bright panel quads, never a HUD or a mutable shared atlas. */
final class SubmarineInstruments {
    static SubmarineInstrumentProvider provider = SubmarineInstrumentProvider.STANDBY;
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID,
            "textures/entity/deepseeker_pixel.png");
    private static final int DARK = 0xff031216, CYAN = 0xff72e9e6, GRID = 0xff163b42, GREEN = 0xff5bff91;

    static void render(SubmarineEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        double now = entity.level().getGameTime() + partialTick;
        for (int i = 0; i < DeepseekerPanels.PLANES.length; i++) {
            float[] p = DeepseekerPanels.PLANES[i];
            Vector3f right = new Vector3f(p[3], p[4], p[5]);
            Vector3f down = new Vector3f(p[6], p[7], p[8]);
            Vector3f normal = new Vector3f(down).cross(right).normalize();
            Matrix4f plane = new Matrix4f().setColumn(0, new org.joml.Vector4f(right, 0))
                    .setColumn(1, new org.joml.Vector4f(down, 0))
                    .setColumn(2, new org.joml.Vector4f(normal, 0))
                    .setColumn(3, new org.joml.Vector4f(p[0], p[1], p[2], 1));
            pose.pushPose();
            pose.mulPose(plane);
            Canvas c = new Canvas(pose, buffers, i == 0 ? 128 : 64, i == 2 || i == 3 ? 32 : 64);
            c.rect(0, 0, c.width, c.height, DARK, 0);
            switch (i) {
                case 0 -> depth(c, entity, now);
                case 1 -> sonar(c, entity, now);
                case 2 -> { c.text("POWER", 3, 3, CYAN); c.text(provider.power(entity), 3, 17, CYAN); }
                case 3 -> { c.text("SYSTEMS", 3, 3, CYAN); c.text(provider.systems(entity), 3, 17, CYAN); }
                case 4 -> attitude(c, entity, partialTick);
                default -> { }
            }
            pose.popPose();
        }
    }

    private static void depth(Canvas c, SubmarineEntity entity, double now) {
        var h = entity.instrumentHistory;
        float value = entity.instrumentDepth();
        c.text("DEPTH", 2, 2, CYAN);
        String current = Float.isFinite(value) ? Math.round(value) + " M" : "-- M";
        c.text(current, 126 - Minecraft.getInstance().font.width(current), 2, CYAN);
        for (int y = 17; y <= 47; y += 10) c.rect(20, y, 123, y + 1, GRID, 1);
        for (int x = 20; x <= 122; x += 17) c.rect(x, 17, x + 1, 48, GRID, 1);
        c.smallText("0", 1, 14, CYAN);
        c.smallText(Integer.toString(Math.round(h.range())), 1, 42, CYAN);
        c.text("-60S", 20, 54, CYAN); c.text("NOW", 104, 54, CYAN);
        for (int i = 0; i < h.size(); i++) {
            float x = SubmarineHistory.plotX(h.time(i), now);
            if (x < 20 || x > 122 || !Float.isFinite(h.value(i))) continue;
            float y = SubmarineHistory.plotY(h.value(i), h.range());
            c.rect(x, y, x + 1, y + 1, CYAN, 2);
            if (i == 0 || !Float.isFinite(h.value(i - 1)) || h.time(i) - h.time(i - 1) > 10) continue;
            float previousX = SubmarineHistory.plotX(h.time(i - 1), now);
            if (previousX < 20) continue;
            c.line(previousX, SubmarineHistory.plotY(h.value(i - 1), h.range()), x, y, CYAN);
        }
        if (!Float.isFinite(value)) c.text("NO DATA", 47, 28, CYAN);
    }

    private static void sonar(Canvas c, SubmarineEntity entity, double now) {
        c.text(provider.sonarOnline(entity) ? "SONAR" : "STBY", 3, 2, GREEN);
        for (int radius = 8; radius <= 24; radius += 8) {
            for (int a = 0; a < 120; a++) {
                double angle = a * Math.PI / 60;
                float x = 32 + (float) Math.cos(angle) * radius, y = 35 + (float) Math.sin(angle) * radius;
                c.rect(x, y, x + 1, y + 1, GRID, 1);
            }
        }
        double angle = now * Math.PI / 80;
        c.line(32, 35, 32 + (float) Math.cos(angle) * 23, 35 + (float) Math.sin(angle) * 23, GREEN);
        if (provider.sonarOnline(entity)) {
            int count = 0;
            for (var contact : provider.sonar(entity)) {
                if (++count > 128) break;
                if (!Float.isFinite(contact.right()) || !Float.isFinite(contact.forward())
                        || contact.right() * contact.right() + contact.forward() * contact.forward() > 1) continue;
                float x = 32 + contact.right() * 23, y = 35 - contact.forward() * 23;
                c.rect(x - 1, y - 1, x + 1, y + 1, GREEN, 2);
            }
        }
    }

    private static void attitude(Canvas c, SubmarineEntity entity, float partialTick) {
        float pitch = net.minecraft.util.Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        float horizon = SubmarineAttitude.horizon(pitch);
        // Convert local-Z card rotation to screen coordinates (screen Y points down).
        // The boat has no gameplay roll; only its existing damage wobble supplies visual roll.
        double angle = Math.toRadians(-SubmarineAttitude.cardRoll(SubmarineRenderer.visualRoll(entity, partialTick)));
        float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
        // Raster runs are masked to the circular aperture. Dark background fully covers the
        // neutral square enamel; the housing and fixed amber marker remain in front of it.
        for (int y = 0; y < 64; y++) {
            int run = 0, previous = 0;
            for (int x = 0; x <= 64; x++) {
                int color = 0;
                if (x < 64) {
                    float dx = x + .5F - 32, dy = y + .5F - 32;
                    if (dx * dx + dy * dy <= 31 * 31) {
                        float cardX = cos * dx + sin * dy;
                        float cardY = -sin * dx + cos * dy;
                        float relativeY = cardY - (horizon - 32);
                        color = relativeY < 0 ? 0xff226581 : 0xff71503a;
                        if (Math.abs(relativeY) < .65F || (Math.abs(cardX) < 8
                                && Math.abs(relativeY / 6 - Math.round(relativeY / 6)) < .10F))
                            color = 0xffd8f4ed;
                    }
                }
                if (color != previous) {
                    if (previous != 0) c.rect(run, y, x, y + 1, previous, 1);
                    run = x; previous = color;
                }
            }
        }
    }

    private static final class Canvas {
        final PoseStack pose;
        final MultiBufferSource buffers;
        final int width, height;
        Canvas(PoseStack pose, MultiBufferSource buffers, int width, int height) {
            this.pose = pose; this.buffers = buffers; this.width = width; this.height = height;
        }
        void rect(float x0, float y0, float x1, float y1, int color, int layer) {
            x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(width, x1); y1 = Math.min(height, y1);
            if (x1 <= x0 || y1 <= y0) return;
            var v = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
            var p = pose.last(); float z = layer * .00002F;
            v.addVertex(p, x0, y0, z).setColor(color).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0, 0, 1);
            v.addVertex(p, x0, y1, z).setColor(color).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0, 0, 1);
            v.addVertex(p, x1, y1, z).setColor(color).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0, 0, 1);
            v.addVertex(p, x1, y0, z).setColor(color).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0, 0, 1);
        }
        void line(float x0, float y0, float x1, float y1, int color) {
            int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))));
            for (int i = 0; i <= steps; i++) {
                float t = (float) i / steps, x = x0 + (x1 - x0) * t, y = y0 + (y1 - y0) * t;
                rect(x, y, x + 1, y + 1, color, 2);
            }
        }
        void text(String value, float x, float y, int color) {
            Font font = Minecraft.getInstance().font;
            String clipped = font.plainSubstrByWidth(value, Math.max(0, width - (int) x - 1));
            if (y < 0 || y + 8 > height) return;
            Matrix4f matrix = new Matrix4f(pose.last().pose()).translate(0, 0, .00008F);
            font.drawInBatch(clipped, x, y, color, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        }
        void smallText(String value, float x, float y, int color) {
            pose.pushPose(); pose.translate(x, y, 0); pose.scale(.65F, .65F, 1); text(value, 0, 0, color); pose.popPose();
        }
    }
    private SubmarineInstruments() {}
}
