package com.bilal.practicemod.mixin;

import com.bilal.practicemod.SubmarineHeadlight;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class SubmarineLightMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void practicemod$prepareHeadlights(DeltaTracker delta, boolean outline, Camera camera,
            GameRenderer renderer, LightTexture light, Matrix4f view, Matrix4f projection, CallbackInfo ci) {
        SubmarineHeadlight.beginFrame(camera, delta.getGameTimeDeltaPartialTick(false));
    }

    @Redirect(method = "renderSectionLayer", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;getShader()Lnet/minecraft/client/renderer/ShaderInstance;"))
    private ShaderInstance practicemod$terrainHeadlightShader() {
        return SubmarineHeadlight.terrainShader(RenderSystem.getShader());
    }
}
