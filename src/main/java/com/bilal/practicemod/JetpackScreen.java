package com.bilal.practicemod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

// The fueling menu's screen: the pre-drawn 176x166 panel plus a fuel gauge. The gauge recess is
// baked into the background art; the orange fill strip is parked in the texture's off-panel area
// (below y=166) and blitted over the recess cropped to the synced tank fraction.
public class JetpackScreen extends AbstractContainerScreen<JetpackMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "textures/gui/jetpack_fueling.png");

    // Gauge geometry, measured from the texture: recess interior at (44, 48)-(131, 55), fill art
    // 88x6 at (0, 167) in the off-panel strip.
    private static final int GAUGE_X = 44;
    private static final int GAUGE_Y = 49;
    private static final int GAUGE_WIDTH = 88;
    private static final int GAUGE_HEIGHT = 6;
    private static final int GAUGE_FILL_V = 167;

    public JetpackScreen(JetpackMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        int fill = Math.round(GAUGE_WIDTH * this.menu.getFuelFractionDisplay());
        if (fill > 0) {
            graphics.blit(TEXTURE, x + GAUGE_X, y + GAUGE_Y, 0, GAUGE_FILL_V, fill, GAUGE_HEIGHT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // The panel art carries its own labeling; the default title/inventory text would overlap it.
    }
}
