package com.bilal.practicemod.mixin;

import com.bilal.practicemod.SubmarineEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The pilot breathes cabin air; normal water behavior resumes immediately on exit. */
@Mixin(Entity.class)
public abstract class SubmarinePassengerMixin {
    private boolean practicemod$insidePressureCabin() {
        return (Object) this instanceof Player player
                && player.getVehicle() instanceof SubmarineEntity sub && sub.getControllingPassenger() == player
                && sub.insideCabin();
    }

    @Inject(method = "getEyeInFluidType", at = @At("RETURN"), cancellable = true)
    private void practicemod$cabinAir(CallbackInfoReturnable<FluidType> cir) {
        if (cir.getReturnValue() == NeoForgeMod.WATER_TYPE.value() && practicemod$insidePressureCabin())
            cir.setReturnValue(NeoForgeMod.EMPTY_TYPE.value());
    }

    @Inject(method = "isUnderWater", at = @At("RETURN"), cancellable = true)
    private void practicemod$dryPilot(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && practicemod$insidePressureCabin()) cir.setReturnValue(false);
    }
}
