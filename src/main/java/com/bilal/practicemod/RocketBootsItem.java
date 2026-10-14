package com.bilal.practicemod;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Rocket boots equipment; flight behavior lives in RocketBootsFlightHandler. */
public final class RocketBootsItem extends ArmorItem {
    public RocketBootsItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }

    @Override
    public net.minecraft.resources.ResourceLocation getArmorTexture(ItemStack stack,
            net.minecraft.world.entity.Entity entity, net.minecraft.world.entity.EquipmentSlot slot,
            ArmorMaterial.Layer layer, boolean innerModel) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                PracticeMod.MODID, "textures/entity/rocket_boots_mk1.png");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        double boostSeconds = JetpackServerConfig.SPEC.isLoaded()
                ? JetpackServerConfig.ROCKET_BOOTS_BOOST_SECONDS.get()
                : 4.0;
        tooltip.add(Component.translatable("item.practicemod.rocket_boots.boost_hint", boostSeconds)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.practicemod.rocket_boots.cooldown_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
