package com.bilal.practicemod.mixin;

import com.bilal.practicemod.BlenderPlayerPose;
import net.minecraft.client.model.HumanoidModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class BlenderArmorMixin {
    @Inject(method = "copyPropertiesTo(Lnet/minecraft/client/model/HumanoidModel;)V", at = @At("HEAD"))
    private void practicemod$prepareBendCopy(HumanoidModel<?> target, CallbackInfo ci) {
        BlenderPlayerPose.prepareArmor((HumanoidModel<?>) (Object) this, target);
    }
}
