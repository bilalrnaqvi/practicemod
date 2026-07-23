package com.bilal.practicemod;

import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

// Fuel bookkeeping for the jetpack: a single float tank stored on the jetpack ItemStack itself
// (JETPACK_FUEL), so fuel travels with the item. Drained by thrust and slowly regenerated while
// worn-but-idle (JetpackFlightHandler), topped up from blaze powder (JetpackItem#use). The server
// owns all writes; the client reads the copy that vanilla inventory syncing delivers.
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
}
