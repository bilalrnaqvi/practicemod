package com.bilal.practicemod;

import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// Fuel bookkeeping for the jetpack: a single float tank stored on the jetpack ItemStack itself
// (JETPACK_FUEL), so fuel travels with the item. Drained by powered flight and topped up from Blaze
// Powder; servers can optionally enable worn-but-idle regeneration. The server owns all writes; the
// client reads the copy that vanilla inventory syncing delivers.
public class JetpackFuelHandler {

    public static float getFuel(ItemStack stack) {
        return stack.getOrDefault(PracticeMod.JETPACK_FUEL.get(), 0.0F);
    }

    // Clamps to [0, capacity]. An empty tank keeps an explicit 0.0 component (rather than removing
    // it) so the item bar reads "empty", not "uninitialized".
    public static void setFuel(ItemStack stack, float fuel) {
        stack.set(PracticeMod.JETPACK_FUEL.get(), Mth.clamp(fuel, 0.0F, getFuelCapacity()));
    }

    public static float getFuelCapacity() {
        // The capacity lives in the server config, which is only loaded while in a world.
        if (!JetpackServerConfig.SPEC.isLoaded()) {
            return 0.0F;
        }
        return (float) (double) JetpackServerConfig.FUEL_CAPACITY.get();
    }

    public static float getFuelFraction(ItemStack stack) {
        float capacity = getFuelCapacity();
        if (capacity <= 0.0F) {
            return 0.0F;
        }
        return Math.min(1.0F, getFuel(stack) / capacity);
    }

    public static int getStoredBlazePowder(ItemStack stack) {
        return stack.getOrDefault(PracticeMod.JETPACK_BLAZE_POWDER.get(), 0);
    }

    public static void setStoredBlazePowder(ItemStack stack, int count) {
        stack.set(PracticeMod.JETPACK_BLAZE_POWDER.get(), Mth.clamp(count, 0, Items.BLAZE_POWDER.getDefaultMaxStackSize()));
    }

    /** Current tank fuel plus at least one stored refill item. */
    public static boolean hasFuelSupply(ItemStack stack) {
        return getFuel(stack) > 0.0F || getStoredBlazePowder(stack) > 0;
    }

    /**
     * Burns fuel and automatically cracks stored Blaze Powder only when the current tank cannot pay
     * the requested amount. This keeps powder as a visible stack in the menu until flight actually
     * needs it, while allowing a stored reserve to carry the player through tank boundaries.
     */
    public static void consumeFuel(ItemStack stack, float amount) {
        if (amount <= 0.0F) {
            return;
        }

        float fuel = getFuel(stack);
        int powder = getStoredBlazePowder(stack);
        float fuelPerPowder = (float) (double) JetpackServerConfig.FUEL_PER_BLAZE_POWDER.get();
        while (fuel < amount && powder > 0) {
            fuel += fuelPerPowder;
            powder--;
        }

        setStoredBlazePowder(stack, powder);
        setFuel(stack, fuel - amount);
    }
}
