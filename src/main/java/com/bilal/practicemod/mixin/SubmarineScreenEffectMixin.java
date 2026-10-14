package com.bilal.practicemod.mixin;

import com.bilal.practicemod.SubmarineCabin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class SubmarineScreenEffectMixin {
    @Inject(method = "renderWater", at = @At("HEAD"), cancellable = true)
    private static void practicemod$noWaterInsideCabin(Minecraft minecraft, PoseStack poses, CallbackInfo ci) {
        if (SubmarineCabin.dryCamera(minecraft.gameRenderer.getMainCamera())) ci.cancel();
    }
}
