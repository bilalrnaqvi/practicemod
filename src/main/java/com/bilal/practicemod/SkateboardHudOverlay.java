package com.bilal.practicemod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** All score and balance values are replicated from the server. */
public final class SkateboardHudOverlay implements LayeredDraw.Layer {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "skateboard");
    private static final int GOLD = 0xFFFFD34D, WHITE = 0xFFF4F4ED, RED = 0xFFFF665C;
    @Override public void render(GuiGraphics g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !(mc.player.getVehicle() instanceof SkateboardEntity board)) return;
        var font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight(), center = w / 2;
        int accent = board.specialMeter() >= 750 ? GOLD : 0xFF65D4CA;
        g.fill(8, 8, 151, 52, 0xC0182028);
        g.fill(8, 8, 11, 52, accent);
        g.drawString(font, "SCORE  " + board.totalScore(), 17, 14, WHITE);
        g.drawString(font, "BEST  " + board.bestCombo(), 17, 26, 0xFFACB9C3);
        g.drawString(font, "SPECIAL", 17, 39, accent);
        g.fill(64, 40, 143, 46, 0xFF38424A);
        g.fill(64, 40, 64 + Math.round(79 * board.specialMeter() / 1000F), 46, accent);
        String speed = Math.round(board.speed() * 20) + " b/s";
        g.drawString(font, speed, w - font.width(speed) - 10, 12, WHITE);

        int y = h - 108;
        String line = board.trickLine();
        var trick = board.currentTrick();
        if (trick != SkateboardTricks.Trick.NONE) line = line.isEmpty() ? trick.label + "..." : line + " + " + trick.label + "...";
        if (board.multiplier() > 0) {
            String score = board.comboBase() + " x " + board.multiplier();
            g.pose().pushPose(); g.pose().translate(center, y - 17, 0); g.pose().scale(1.5F, 1.5F, 1);
            g.drawCenteredString(font, score, 0, 0, GOLD); g.pose().popPose();
        }
        if (!line.isEmpty()) {
            line = font.plainSubstrByWidth(line, Math.max(80, w - 24));
            int color = board.lastResult() < 0 ? RED : board.lastResult() > 0 ? 0xFF8DF09B : WHITE;
            int half = font.width(line) / 2 + 6;
            g.fill(center - half, y - 2, center + half, y + 12, 0xBA141C24);
            g.drawCenteredString(font, line, center, y, color);
        }
        if (board.balancing()) {
            int by = h / 2 + 30;
            boolean grind = board.skateMode() == SkateboardEntity.GRIND_STATE;
            String keys = grind ? mc.options.keyLeft.getTranslatedKeyMessage().getString() + " / " + mc.options.keyRight.getTranslatedKeyMessage().getString()
                    : mc.options.keyUp.getTranslatedKeyMessage().getString() + " / " + mc.options.keyDown.getTranslatedKeyMessage().getString();
            g.drawCenteredString(font, (grind ? "GRIND  " : "MANUAL  ") + keys, center, by - 13, WHITE);
            g.fill(center - 62, by - 2, center + 62, by + 8, 0xCF141C24);
            g.fill(center - 58, by, center + 58, by + 4, RED);
            g.fill(center - 40, by, center + 40, by + 4, GOLD);
            g.fill(center - 18, by, center + 18, by + 4, 0xFF70DDB4);
            int needle = center + Math.round(Mth.clamp(board.balance(), -1, 1) * 58);
            g.fill(needle - 1, by - 4, needle + 2, by + 9, WHITE);
        }
        if (board.charge() > 0) {
            g.fill(center - 31, h - 85, center + 31, h - 79, 0xCC141C24);
            g.fill(center - 30, h - 84, center - 30 + 60 * board.charge() / SkateboardDynamics.MAX_CHARGE, h - 80, GOLD);
        }
        if (board.speed() < 0.04 && board.skateMode() == SkateboardEntity.GROUND && w >= 300) {
            String help = key(SkateboardClientHandler.FLIP_KEY) + " flip   " + key(SkateboardClientHandler.GRAB_KEY)
                    + " grab   " + key(SkateboardClientHandler.GRIND_KEY) + " grind   " + key(SkateboardClientHandler.MANUAL_KEY) + " manual";
            g.drawCenteredString(font, help, center, h - 68, 0xFFB7C3CD);
            g.drawCenteredString(font, "Hold " + key(mc.options.keyJump) + ", release to ollie", center, h - 57, WHITE);
        }
    }
    private static String key(net.minecraft.client.KeyMapping key) { return key.getTranslatedKeyMessage().getString(); }
}
