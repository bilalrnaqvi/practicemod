package com.bilal.practicemod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

// The jetpack's on-screen readout: a segmented, industrial half-ring centered above the hotbar.
// Total fuel is intentionally absent from the HUD; it is a long-lived consumable whose depletion is
// announced by JetpackClientHandler instead of a second resource bar. The ring visualizes the
// client-predicted boost heat (live with zero network traffic). Red is reserved for the latched
// overheat flash; inactive segments stay neutral so the compact ring reads as one cohesive frame.
public final class JetpackHudOverlay implements LayeredDraw.Layer {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "jetpack_status");

    private static final ResourceLocation METER_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            PracticeMod.MODID, "textures/gui/jetpack_boost_meter.png");

    private static final int TEXTURE_WIDTH = 112;
    private static final int TEXTURE_LAYER_HEIGHT = 58;
    private static final int SEGMENT_COUNT = 13;
    private static final int TEXTURE_LAYER_COUNT = SEGMENT_COUNT + 2;
    private static final int TEXTURE_HEIGHT = TEXTURE_LAYER_HEIGHT * TEXTURE_LAYER_COUNT;
    private static final int OVERHEAT_LAYER_V = TEXTURE_LAYER_HEIGHT * (SEGMENT_COUNT + 1);

    // Roughly 38x20 GUI pixels. At this size the two feet land beside the innermost vanilla status
    // icons while the arc rises clearly above them, matching the intended heart-meter-food cluster.
    private static final float HUD_SCALE = 0.34F;
    private static final int WIDTH = Math.round(TEXTURE_WIDTH * HUD_SCALE);
    private static final int HEIGHT = Math.round(TEXTURE_LAYER_HEIGHT * HUD_SCALE);
    private static final int STATUS_ROW_TOP_MARGIN = 39;
    private static final int STATUS_ROW_GAP = 2;

    // Hover borrows the reference's cyan lift language: stacked arrows and expanding floor rings.
    // Armed stays subdued; engaged runs a stepped upward pulse that suits the pixel-art meter.
    private static final int HOVER_ARMED = 0xFF17646D;
    private static final int HOVER_DIM = 0xFF12818B;
    private static final int HOVER_MID = 0xFF20C9D3;
    private static final int HOVER_BRIGHT = 0xFF43F6FF;
    private static final int OVERHEAT_LAMP = 0xFFFF3A21;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || !JetpackServerConfig.SPEC.isLoaded()) {
            return;
        }
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof JetpackItem)) {
            return;
        }

        float heat = player.getData(PracticeMod.JETPACK_HEAT);
        float heatMax = (float) (double) JetpackServerConfig.HEAT_MAX.get();

        int x = graphics.guiWidth() / 2 - WIDTH / 2;
        // Vanilla's complete heart and food groups leave too little horizontal room for this meter
        // at their exact height. Preserve both rows and keep a deliberate two-pixel breathing gap,
        // while anchoring the bottom so future authored-size changes do not disturb the spacing.
        int y = graphics.guiHeight() - STATUS_ROW_TOP_MARGIN - STATUS_ROW_GAP - HEIGHT;

        blitLayer(graphics, x, y, 0);

        float heatFraction = Mth.clamp(heat / heatMax, 0.0F, 1.0F);
        int filledSegments = heatFraction <= 0.0F ? 0
                : Math.min(SEGMENT_COUNT, (int) Math.ceil(heatFraction * SEGMENT_COUNT));
        if (filledSegments > 0) {
            // Every atlas layer is a pre-rendered cumulative state. Selecting the exact state keeps
            // the angled segment faces whole; a rectangular scissor would cut through those faces.
            blitLayer(graphics, x, y, TEXTURE_LAYER_HEIGHT * filledSegments);
        }

        // A latched overheat alternates the whole ring between its current gold/orange reading and
        // the red flash layer. player.tickCount keeps the cadence stable even while the game is paused.
        boolean overheated = Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_OVERHEATED));
        boolean overheatFlash = overheated && (player.tickCount / 4 & 1) == 0;
        if (overheatFlash) {
            blitLayer(graphics, x, y, OVERHEAT_LAYER_V);
        }

        if (overheatFlash) {
            drawBaseLamps(graphics, x, y, OVERHEAT_LAMP);
        } else if (Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_HOVER_MODE))) {
            boolean engaged = Boolean.TRUE.equals(player.getData(PracticeMod.JETPACK_HOVERING));
            drawHoverIndicator(graphics, x, y, player.tickCount, engaged);
            drawBaseLamps(graphics, x, y, engaged ? HOVER_BRIGHT : HOVER_ARMED);
        }
    }

    private static void blitLayer(GuiGraphics graphics, int x, int y, int textureV) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(HUD_SCALE, HUD_SCALE, 1.0F);
        graphics.blit(METER_TEXTURE, 0, 0, 0.0F, textureV,
                TEXTURE_WIDTH, TEXTURE_LAYER_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        graphics.pose().popPose();
    }

    private static void drawBaseLamps(GuiGraphics graphics, int x, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(HUD_SCALE, HUD_SCALE, 1.0F);
        graphics.fill(12, 48, 18, 50, color);
        graphics.fill(94, 48, 100, 50, color);
        graphics.pose().popPose();
    }

    private static void drawHoverIndicator(GuiGraphics graphics, int x, int y,
            int tickCount, boolean engaged) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(HUD_SCALE, HUD_SCALE, 1.0F);

        // Quiet backplates mask the normal gold boost chevrons and keep cyan readable over terrain.
        graphics.fill(47, 18, 65, 40, 0xC8000000);
        graphics.fill(38, 37, 74, 45, 0xC8000000);

        int brightArrow = engaged ? 2 - (tickCount / 3 % 3) : -1;
        for (int arrow = 0; arrow < 3; arrow++) {
            int color;
            if (!engaged) {
                color = HOVER_ARMED;
            } else if (arrow == brightArrow) {
                color = HOVER_BRIGHT;
            } else if (Math.abs(arrow - brightArrow) == 1) {
                color = HOVER_MID;
            } else {
                color = HOVER_DIM;
            }
            drawHoverChevron(graphics, 19 + arrow * 7, color);
        }

        boolean outerPulse = engaged && (tickCount / 3 & 1) == 0;
        drawHoverRing(graphics, 45, 67, 36,
                engaged ? (outerPulse ? HOVER_MID : HOVER_BRIGHT) : HOVER_ARMED);
        drawHoverRing(graphics, 38, 74, 40,
                engaged ? (outerPulse ? HOVER_BRIGHT : HOVER_MID) : HOVER_ARMED);
        graphics.pose().popPose();
    }

    private static void drawHoverChevron(GuiGraphics graphics, int top, int color) {
        graphics.fill(49, top + 4, 53, top + 7, color);
        graphics.fill(52, top + 2, 56, top + 5, color);
        graphics.fill(55, top, 58, top + 3, color);
        graphics.fill(57, top + 2, 61, top + 5, color);
        graphics.fill(60, top + 4, 64, top + 7, color);
    }

    private static void drawHoverRing(GuiGraphics graphics, int left, int right, int top, int color) {
        graphics.fill(left, top, left + 4, top + 2, color);
        graphics.fill(right - 4, top, right, top + 2, color);
        graphics.fill(left + 3, top + 2, right - 3, top + 4, color);
    }
}
