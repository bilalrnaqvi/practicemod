package com.bilal.practicemod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Remaining boost charge in the same half-ring footprint as the jetpack HUD. */
public final class JetskiHudOverlay implements LayeredDraw.Layer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            PracticeMod.MODID, "textures/gui/jetski_boost_meter.png");
    private static final int TEXTURE_WIDTH = 112;
    private static final int LAYER_HEIGHT = 58;
    private static final int SEGMENTS = 13;
    private static final int TEXTURE_HEIGHT = LAYER_HEIGHT * (SEGMENTS + 2);
    private static final float SCALE = 0.34F;
    private static final int WIDTH = Math.round(TEXTURE_WIDTH * SCALE);
    private static final int HEIGHT = Math.round(LAYER_HEIGHT * SCALE);

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        var mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null
                || !(mc.player.getVehicle() instanceof JetskiEntity ski)) return;

        int x = graphics.guiWidth() / 2 - WIDTH / 2;
        int y = graphics.guiHeight() - 39 - 2 - HEIGHT;
        float charge = Mth.clamp(ski.boostCharge(), 0, 1);
        int filled = charge <= 0 ? 0 : Math.min(SEGMENTS, (int) Math.ceil(charge * SEGMENTS));

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(SCALE, SCALE, 1);
        blitLayer(graphics, 0);
        // Cumulative atlas layers preserve the complete angled faces as charge drains/refills.
        if (filled > 0) blitLayer(graphics, filled);

        // Keep the charge readable during lockout: only the two base lamps turn coral.
        // No jetpack heat flash or text instructions on the vehicle meter.
        if (ski.isBoostExhausted() || ski.isBoosting()) {
            int lamp = ski.isBoostExhausted() ? 0xFFFF7757 : 0xFFD8FCFF;
            graphics.fill(12, 48, 18, 50, lamp);
            graphics.fill(94, 48, 100, 50, lamp);
        }
        graphics.pose().popPose();
    }

    private static void blitLayer(GuiGraphics graphics, int layer) {
        graphics.blit(TEXTURE, 0, 0, 0.0F, layer * LAYER_HEIGHT,
                TEXTURE_WIDTH, LAYER_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }
}
