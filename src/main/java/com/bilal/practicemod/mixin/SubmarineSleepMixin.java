package com.bilal.practicemod.mixin;

import com.bilal.practicemod.SubmarineEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep vanilla sleep timers and night voting, with an entity bunk instead of a world bed. */
@Mixin(LivingEntity.class)
public abstract class SubmarineSleepMixin {
    private boolean practicemod$cabinBed() {
        return (Object) this instanceof Player p && p.getVehicle() instanceof SubmarineEntity sub
                && sub.isParked() && !sub.isCabinTransition();
    }
    @Inject(method = "startSleeping", at = @At("HEAD"), cancellable = true)
    private void practicemod$lieDown(BlockPos pos, CallbackInfo ci) {
        if (!practicemod$cabinBed()) return;
        var self = (LivingEntity) (Object) this;
        self.setPose(Pose.SLEEPING);
        self.setSleepingPos(pos);
        self.setDeltaMovement(Vec3.ZERO);
        ci.cancel();
    }
    @Inject(method = "setPosToBed", at = @At("HEAD"), cancellable = true)
    private void practicemod$keepBunkPosition(BlockPos pos, CallbackInfo ci) {
        if (practicemod$cabinBed()) ci.cancel();
    }
    @Inject(method = "checkBedExists", at = @At("HEAD"), cancellable = true)
    private void practicemod$entityBedExists(CallbackInfoReturnable<Boolean> ci) {
        if (practicemod$cabinBed()) ci.setReturnValue(true);
    }
    @Inject(method = "getBedOrientation", at = @At("HEAD"), cancellable = true)
    private void practicemod$bunkDirection(CallbackInfoReturnable<Direction> ci) {
        if (practicemod$cabinBed()) ci.setReturnValue(Direction.fromYRot(
                ((LivingEntity) (Object) this).getVehicle().getYRot()));
    }
    @Inject(method = "stopSleeping", at = @At("HEAD"), cancellable = true)
    private void practicemod$wakeInCabin(CallbackInfo ci) {
        if (!practicemod$cabinBed()) return;
        var self = (LivingEntity) (Object) this;
        self.clearSleepingPos();
        self.setPose(Pose.STANDING);
        self.getVehicle().positionRider(self);
        ci.cancel();
    }
}
