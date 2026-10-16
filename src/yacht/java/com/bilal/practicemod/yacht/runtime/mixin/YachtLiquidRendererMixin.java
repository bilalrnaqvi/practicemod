package com.bilal.practicemod.yacht.runtime.mixin;

import com.bilal.practicemod.yacht.runtime.YachtAssets;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pool cells keep vanilla fluid mechanics but already have an authored water surface. */
@Mixin(LiquidBlockRenderer.class)
public abstract class YachtLiquidRendererMixin {
    @Inject(method="tesselate",at=@At("HEAD"),cancellable=true)
    private void whiteHorizonSurface(BlockAndTintGetter level,BlockPos pos,VertexConsumer buffer,BlockState block,FluidState fluid,CallbackInfo ci) {
        if(block.is(YachtAssets.HULL))ci.cancel();
    }
}
