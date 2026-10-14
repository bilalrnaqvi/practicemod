package com.bilal.practicemod.mixin;

import com.bilal.practicemod.SubmarineEntity;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace only the private boat behaviors that conflict with a submerged vehicle. */
@Mixin(Boat.class)
public abstract class BoatMixin {
    @Shadow private float outOfControlTicks;
    @Shadow private float deltaRotation;

    @Inject(method = "tick", at = @At("HEAD"))
    private void practicemod$keepSubmarineMounted(CallbackInfo ci) {
        if ((Object) this instanceof SubmarineEntity) outOfControlTicks = 0;
    }

    @Inject(method = "floatBoat", at = @At("HEAD"), cancellable = true)
    private void practicemod$submarineBuoyancy(CallbackInfo ci) {
        if ((Object) this instanceof SubmarineEntity submarine) {
            submarine.applyDepthControl();
            deltaRotation *= 0.9F;
            ci.cancel();
        }
    }

    @Inject(method = "controlBoat", at = @At("HEAD"), cancellable = true)
    private void practicemod$waitForPilot(CallbackInfo ci) {
        if ((Object) this instanceof SubmarineEntity submarine && (submarine.isBoarding() || submarine.isParked())) {
            deltaRotation = 0;
            submarine.setPaddleState(false, false);
            ci.cancel();
        }
    }

    @Inject(method = "tickBubbleColumn", at = @At("HEAD"), cancellable = true)
    private void practicemod$ignoreSubmarineBubbleEjection(CallbackInfo ci) {
        if ((Object) this instanceof SubmarineEntity) ci.cancel();
    }
}
