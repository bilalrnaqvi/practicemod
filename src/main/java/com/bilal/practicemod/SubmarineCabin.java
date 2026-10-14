package com.bilal.practicemod;

import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

import java.util.List;

/** Dry pilot camera and render-only water exclusion; ocean blocks are never removed. */
@EventBusSubscriber(modid = PracticeMod.MODID, value = Dist.CLIENT)
public final class SubmarineCabin {
    private SubmarineCabin() { }

    public static boolean dryCamera(Camera camera) {
        if (!(camera.getEntity() instanceof Player player)
                || !(player.getVehicle() instanceof SubmarineEntity submarine)
                || submarine.getControllingPassenger() != player) return false;
        if (submarine.isBoarding()) {
            if (camera.isDetached()) return SubmarineEntryCamera.interior(submarine.entryTime(0));
            float bend=SubmarineEntryAnimation.bend(submarine.entryTime(0));
            double eyeY=submarine.entryEye(0).y-(12-12*Math.cos(bend))*.9375/16;
            return eyeY < 5.1;
        }
        return !camera.isDetached() && submarine.insideCabin();
    }

    private static boolean underwaterCabin(Camera camera) {
        var level = Minecraft.getInstance().level;
        return dryCamera(camera) && level != null && level.getFluidState(camera.getBlockPosition()).is(FluidTags.WATER);
    }

    @SubscribeEvent
    static void fog(ViewportEvent.RenderFog event) {
        if (!underwaterCabin(event.getCamera())) return;
        if (event.getCamera().getEntity() instanceof net.minecraft.world.entity.LivingEntity entity
                && (entity.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
                || entity.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS))) return;
        // Air inside the pressure hull, underwater visibility beyond the windows.
        event.setNearPlaneDistance(4);
        event.setFarPlaneDistance(64);
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void fogColor(ViewportEvent.ComputeFogColor event) {
        if (!underwaterCabin(event.getCamera())) return;
        int color = Minecraft.getInstance().level.getBiome(event.getCamera().getBlockPosition()).value().getWaterFogColor();
        event.setRed(((color >> 16) & 255) / 255F);
        event.setGreen(((color >> 8) & 255) / 255F);
        event.setBlue((color & 255) / 255F);
    }

    static int prepare(ShaderInstance shader, Camera camera, float partialTick, List<SubmarineEntity> vehicles) {
        int count = Math.min(4, vehicles.size());
        shader.getUniform("CabinCount").set(count);
        for (int i = 0; i < count; i++) {
            var sub = vehicles.get(i);
            var offset = sub.getPosition(partialTick).subtract(camera.getPosition());
            var inverse = new Matrix4f().translation((float) offset.x, (float) offset.y, (float) offset.z)
                    .rotateY((180 - Mth.rotLerp(partialTick, sub.yRotO, sub.getYRot())) * Mth.DEG_TO_RAD)
                    .translate(0, (float) sub.pilotEyeOffset(), 0)
                    .rotateZ(SubmarineRenderer.visualRoll(sub, partialTick) * Mth.DEG_TO_RAD)
                    .rotateX(-Mth.lerp(partialTick, sub.xRotO, sub.getXRot()) * Mth.DEG_TO_RAD)
                    .translate(-DeepseekerPilot.X, -DeepseekerPilot.Y, -DeepseekerPilot.Z).scale(DeepseekerPilot.HULL_SCALE / 16).invert();
            shader.getUniform("CabinTransform" + i).set(inverse);
        }
        var atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        String[] sprites = {"water_still", "water_flow", "water_overlay"};
        for (int i = 0; i < sprites.length; i++) {
            var sprite = atlas.apply(ResourceLocation.withDefaultNamespace("block/" + sprites[i]));
            shader.getUniform("WaterSprite" + i).set(sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());
        }
        return count;
    }
}
