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

// The jetpack's on-screen readout: a prominent heat bar (the skill layer the player is actively
// managing) with a thinner fuel bar beneath it, centered above the hotbar. Renders only while a
// jetpack is worn. Heat comes from the client-predicted attachment (updated every tick by the
// flight sim, so the bar is live with zero network traffic); fuel comes from the item component
// that vanilla syncs on equipped armor. Zone thresholds come from the server config, which is
// synced to clients on world join.
public final class JetpackHudOverlay implements LayeredDraw.Layer {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "jetpack_status");

    private static final int WIDTH = 91;
    private static final int HEAT_HEIGHT = 5;
    private static final int FUEL_HEIGHT = 2;
    private static final int GAP = 2;

    private static final int BACKGROUND = 0xC0000000;
    private static final int HEAT_NORMAL = 0xFF40C040;   // green: standard thrust
    private static final int HEAT_OPTIMAL = 0xFFF0C020;  // yellow: afterburner band
    private static final int HEAT_CRITICAL = 0xFFE04030; // red: next stop is a stall
    private static final int FUEL_COLOR = 0xFF3898D8;    // blue: distinct from every heat zone

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
        float fuelFraction = JetpackFuelHandler.getFuelFraction(chest);

        int x = graphics.guiWidth() / 2 - WIDTH / 2;
        // Stack above whatever vanilla drew on the status rows (health, armor, food, air, ...);
        // leftHeight/rightHeight are NeoForge-patched running totals, final by the time this layer
        // renders because it's registered above AIR_LEVEL, the last status row.
        int base = graphics.guiHeight() - Math.max(minecraft.gui.leftHeight, minecraft.gui.rightHeight) - 2;
        int fuelY = base - FUEL_HEIGHT;
        int heatY = fuelY - GAP - HEAT_HEIGHT;

        // Heat bar: fill color tracks the zone the needle is currently in.
        int heatColor = heat >= JetpackServerConfig.OPTIMAL_ZONE_END.get() ? HEAT_CRITICAL
                : heat >= JetpackServerConfig.OPTIMAL_ZONE_START.get() ? HEAT_OPTIMAL
                : HEAT_NORMAL;
        graphics.fill(x - 1, heatY - 1, x + WIDTH + 1, heatY + HEAT_HEIGHT + 1, BACKGROUND);
        int heatFill = Math.round(WIDTH * Mth.clamp(heat / heatMax, 0.0F, 1.0F));
        if (heatFill > 0) {
            graphics.fill(x, heatY, x + heatFill, heatY + HEAT_HEIGHT, heatColor);
        }

        // Fuel bar: thinner, secondary.
        graphics.fill(x - 1, fuelY - 1, x + WIDTH + 1, fuelY + FUEL_HEIGHT + 1, BACKGROUND);
        int fuelFill = Math.round(WIDTH * fuelFraction);
        if (fuelFill > 0) {
            graphics.fill(x, fuelY, x + fuelFill, fuelY + FUEL_HEIGHT, FUEL_COLOR);
        }
    }
}
