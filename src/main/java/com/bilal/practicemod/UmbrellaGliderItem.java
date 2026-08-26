package com.bilal.practicemod;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The held form of the umbrella glider. All deployment and movement rules intentionally live in
 * {@link UmbrellaGliderHandler}, which keys off the hand slots rather than any equipment slot; that
 * keeps the eventual Blockbench asset swap entirely separate from gameplay.
 */
public final class UmbrellaGliderItem extends Item {
    public UmbrellaGliderItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.practicemod.umbrella_glider.deploy_hint")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.practicemod.umbrella_glider.inventory_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
