package com.bilal.practicemod.mixin;

import com.bilal.practicemod.SubmarineCabin;
import net.minecraft.client.Camera;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class SubmarineCameraMixin {
    @org.spongepowered.asm.mixin.Shadow private boolean detached;
    @org.spongepowered.asm.mixin.Shadow protected abstract void setPosition(net.minecraft.world.phys.Vec3 position);
    @org.spongepowered.asm.mixin.Shadow protected abstract void setRotation(float yaw, float pitch, float roll);

    @Inject(method = "setup", at = @At("RETURN"))
    private void practicemod$submarineChaseCamera(net.minecraft.world.level.BlockGetter level,
            net.minecraft.world.entity.Entity entity, boolean detached, boolean reverse, float partialTick,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!detached && entity.getVehicle() instanceof com.bilal.practicemod.MechanicalDolphinEntity dolphin)
            setPosition(com.bilal.practicemod.MechanicalDolphinCockpit.cameraPosition(dolphin, partialTick));
        var view = com.bilal.practicemod.SubmarineChaseCamera.update((Camera)(Object)this, level, entity, detached, reverse, partialTick);
        if (view == null) return;
        setRotation(view.yaw(), view.pitch(), 0);
        setPosition(view.position());
        if (entity.getVehicle() instanceof com.bilal.practicemod.SubmarineEntity sub && sub.isBoarding())
            this.detached = !view.cockpit();
        else if (view.cockpit()) this.detached = false;
    }

    @Inject(method = "reset", at = @At("RETURN"))
    private void practicemod$resetChaseCamera(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.bilal.practicemod.SubmarineChaseCamera.reset();
    }

    @Inject(method = "getFluidInCamera", at = @At("RETURN"), cancellable = true)
    private void practicemod$dryPressureCabin(CallbackInfoReturnable<FogType> cir) {
        if (cir.getReturnValue() == FogType.WATER && SubmarineCabin.dryCamera((Camera) (Object) this))
            cir.setReturnValue(FogType.NONE);
    }
}
