package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;
import java.util.List;

/** Per-fragment, frame-interpolated searchlights. No block light edits or chunk rebuilds. */
public final class SubmarineHeadlight {
    private static final int SIZE = 32, MAX_LIGHTS = 4;
    private static ShaderInstance shader;
    private static DynamicTexture shadows;
    private static ClientLevel lastLevel;
    private static List<Source> previous = List.of();
    private static long shadowTick = Long.MIN_VALUE;
    private static int count;
    private static int cabinCount;
    private record Source(SubmarineEntity entity, Vec3 origin, Vec3 forward, Vec3 right, Vec3 up) { }

    private SubmarineHeadlight() { }

    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "submarine_headlight"),
                DefaultVertexFormat.BLOCK), loaded -> {
                    shader = loaded;
                    releaseShadows();
                });
    }

    private static void releaseShadows() {
        if (shadows != null) shadows.close();
        shadows = null;
        previous = List.of();
        count = 0;
        shadowTick = Long.MIN_VALUE;
    }

    /** Also called on disconnect, when no world render will run to release the texture. */
    public static void update(Minecraft minecraft) {
        if (lastLevel != minecraft.level) {
            releaseShadows();
            lastLevel = minecraft.level;
        }
    }

    public static void beginFrame(Camera camera, float partialTick) {
        var minecraft = Minecraft.getInstance();
        update(minecraft);
        count = 0;
        cabinCount = 0;
        if (shader == null || lastLevel == null || minecraft.player == null) return;
        var vehicles = lastLevel.getEntitiesOfClass(SubmarineEntity.class,
                minecraft.player.getBoundingBox().inflate(64));
        vehicles.sort(java.util.Comparator.comparingDouble(minecraft.player::distanceToSqr));
        cabinCount = SubmarineCabin.prepare(shader, camera, partialTick, vehicles);
        List<Source> sources = vehicles.stream().filter(SubmarineEntity::headlightsOn).limit(MAX_LIGHTS).map(sub -> source(sub, partialTick)).toList();
        if (sources.isEmpty()) {
            if (shadows != null) releaseShadows();
            shader.getUniform("LightCount").set(0);
            shader.setSampler("HeadlightShadow", minecraft.getTextureManager().getTexture(
                    net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS));
            return;
        }
        count = sources.size();
        if (shadows == null) {
            shadows = new DynamicTexture(SIZE, SIZE * MAX_LIGHTS, true);
            shadows.setFilter(false, false);
        }
        // Recast for every interpolated pose, but reuse stationary maps between terrain refreshes.
        long tick = lastLevel.getGameTime();
        if (!sources.equals(previous) || tick - shadowTick >= 4) {
            for (int i = 0; i < count; i++) writeShadow(i, sources.get(i));
            shadows.upload();
            previous = sources;
            shadowTick = tick;
        }
        shader.setSampler("HeadlightShadow", shadows);
        shader.getUniform("LightCount").set(count);
        shader.getUniform("LightIntensity").set(SubmarineLightField.INTENSITY);
        for (int i = 0; i < count; i++) {
            Source source = sources.get(i);
            uniform("LightOrigin" + i, source.origin.subtract(camera.getPosition()));
            uniform("LightForward" + i, source.forward);
            uniform("LightRight" + i, source.right);
            uniform("LightUp" + i, source.up);
        }
    }

    private static void uniform(String name, Vec3 value) {
        shader.getUniform(name).set((float) value.x, (float) value.y, (float) value.z);
    }

    static Vec3 modelDirection(SubmarineEntity sub, float partialTick, Vec3 direction) {
        float pitch = Mth.lerp(partialTick, sub.xRotO, sub.getXRot()) * Mth.DEG_TO_RAD;
        float yaw = (180 - Mth.rotLerp(partialTick, sub.yRotO, sub.getYRot())) * Mth.DEG_TO_RAD;
        float roll = -SubmarineRenderer.visualRoll(sub, partialTick) * Mth.DEG_TO_RAD;
        return direction.xRot(pitch).zRot(roll).yRot(yaw);
    }

    static Vec3 modelPosition(SubmarineEntity sub, float partialTick, Vec3 position) {
        return sub.getPosition(partialTick).add(0, sub.pilotEyeOffset(), 0)
                .add(modelDirection(sub, partialTick, position.add(-DeepseekerPilot.X, -DeepseekerPilot.Y, -DeepseekerPilot.Z)));
    }

    private static Source source(SubmarineEntity sub, float partialTick) {
        float[] left = DeepseekerGeometry.LAMP_ORIGINS[0], right = DeepseekerGeometry.LAMP_ORIGINS[1];
        Vec3 midpoint = new Vec3((left[0] + right[0]) / 2, (left[1] + right[1]) / 2,
                (left[2] + right[2]) / 2);
        return new Source(sub, modelPosition(sub, partialTick, midpoint),
                modelDirection(sub, partialTick, new Vec3(0, 0, -1)),
                modelDirection(sub, partialTick, new Vec3(1, 0, 0)),
                modelDirection(sub, partialTick, new Vec3(0, 1, 0)));
    }

    private static void writeShadow(int index, Source source) {
        var pixels = shadows.getPixels();
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            // Perspective frustum: +/- .8 tangent, wider than the spotlight's soft outer edge.
            double u = ((x + .5) / SIZE - .5) * 1.6, v = ((y + .5) / SIZE - .5) * 1.6;
            Vec3 ray = source.forward.add(source.right.scale(u)).add(source.up.scale(v)).normalize();
            double distance = SubmarineLightField.distance(lastLevel, source.entity, source.origin, ray)
                    * ray.dot(source.forward);
            int encoded = Mth.clamp((int) Math.round(distance / SubmarineLightField.SHADOW_RANGE * 65535), 0, 65535);
            // NativeImage is ABGR. Store 16-bit distance in R/G, sampled without filtering.
            pixels.setPixelRGBA(x, index * SIZE + y, 0xFF000000 | encoded);
        }
    }

    /** Only substitute vanilla terrain shaders inside LevelRenderer's terrain draw. */
    public static ShaderInstance terrainShader(ShaderInstance original) {
        if (shader == null || (count == 0 && cabinCount == 0)) return original;
        float cutoff = switch (original.getName()) {
            case "rendertype_solid", "rendertype_translucent" -> 0F;
            case "rendertype_cutout" -> .1F;
            case "rendertype_cutout_mipped" -> .5F;
            default -> -1F;
        };
        if (cutoff < 0) return original;
        shader.getUniform("WaterPass").set(original.getName().equals("rendertype_translucent") ? 1 : 0);
        shader.getUniform("AlphaCutoff").set(cutoff);
        return shader;
    }
}
