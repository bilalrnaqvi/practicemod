package com.bilal.practicemod.mixin;

import com.bilal.practicemod.SubmarineEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Swimming/crouching dimensions must not move the hull reference while piloting. */
@Mixin(Player.class)
public abstract class SubmarinePlayerMixin {
    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void practicemod$seatedCabinPose(CallbackInfo ci) {
        var player = (Player) (Object) this;
        if (player.isAlive() && ((player.getVehicle() instanceof SubmarineEntity sub
                && sub.getControllingPassenger() == player)
                || player.getVehicle() instanceof com.bilal.practicemod.MechanicalDolphinEntity)) {
            player.setSwimming(false);
            player.stopFallFlying();
            player.setPose(player.isSleeping() ? Pose.SLEEPING : Pose.STANDING);
            ci.cancel();
        }
    }
}
