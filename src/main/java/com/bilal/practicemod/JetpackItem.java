package com.bilal.practicemod;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

// The jetpack chestplate. Right-click keeps the plain vanilla equip behavior; refueling happens
// through the fueling menu, opened with the G keybind while worn (see JetpackClientHandler).
// Repurposes the vanilla durability bar as a fuel gauge: green when full, fading to red as fuel
// runs out. (Trade-off: the bar no longer shows armor damage - fine for v1, since the jetpack is
// a utility item rather than protective armor.)
public class JetpackItem extends ArmorItem {

    public JetpackItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        // Always visible so an empty tank reads as "empty bar", not "no bar".
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        // The bar is 13 pixels wide at full.
        return Math.round(13.0F * JetpackFuelHandler.getFuelFraction(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        // Hue 0.0 = red (empty) through 1/3 = green (full).
        return Mth.hsvToRgb(JetpackFuelHandler.getFuelFraction(stack) / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        // The tank capacity lives in the server config, which is only loaded while in a world.
        if (JetpackServerConfig.SPEC.isLoaded()) {
            tooltip.add(Component.translatable("item.practicemod.jetpack.fuel",
                    Math.round(JetpackFuelHandler.getFuel(stack)),
                    Math.round(JetpackFuelHandler.getFuelCapacity())).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.practicemod.jetpack.refuel_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
