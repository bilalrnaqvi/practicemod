package com.bilal.practicemod.mixin;

import com.bilal.practicemod.PracticeMod;
import com.bilal.practicemod.UmbrellaGliderHandler;
import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

// A deployed canopy is drawn overhead by UmbrellaGliderLayer, so the third-person held model has to
// drop out or the player is holding a second glider. No NeoForge event covers this layer in 1.21.1,
// hence the mixin. PlayerItemInHandLayer - not its ItemInHandLayer base - is the layer PlayerRenderer
// actually installs, and it overrides renderArmWithItem, so the concrete class is the target. That
// method is the per-arm overload, leaving an item in the other hand untouched, and the state check
// reads the synchronized attachment so it is correct for remote players too.
@Mixin(PlayerItemInHandLayer.class)
public abstract class PlayerItemInHandLayerMixin {

    @Inject(method = "renderArmWithItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void practicemod$hideDeployedUmbrella(LivingEntity livingEntity, ItemStack itemStack,
            ItemDisplayContext displayContext, HumanoidArm arm, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (livingEntity instanceof Player player
                && itemStack.is(PracticeMod.UMBRELLA_GLIDER.get())
                && UmbrellaGliderHandler.isDeployed(player)) {
            ci.cancel();
        }
    }
}
