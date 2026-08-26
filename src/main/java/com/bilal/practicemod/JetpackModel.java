package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

// Worn SCAVPACK MK1 model. The imported Blockbench geometry remains a code-defined layer so it uses
// vanilla's armor rendering path without a runtime model dependency. The intentionally separated
// tank, thruster, fan, core, latch, and exhaust parts turn the asset's mechanical construction into
// animation controls instead of carrying over the old pack's generic tank/nozzle/flap motions.
public class JetpackModel extends HumanoidModel<LivingEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "jetpack"), "main");

    // The Blockbench source is intentionally imposing, but its authored entity scale is a little
    // too large on the vanilla player rig. Scale around the pack's central mounting pivot so its
    // proportions, clearances, and all nested animation pivots remain unchanged.
    static final float PACK_SCALE = 0.81F;
    static final float PACK_PIVOT_Y = 8.0F;
    static final float PACK_PIVOT_Z = 4.0F;

    // Bottom face of the exhaust-emitter boxes, in emitter-local model units. Rendering from this
    // exact point (after every parent ModelPart transform) is what keeps the effect welded to a bell
    // while tanks bloom, thrusters telescope/splay, and the whole player pitches into boost flight.
    private static final float EXHAUST_OUTLET_Y = 1.63F / 16.0F;
    private static final int EFFECT_FULL_BRIGHT = 0x00F000F0;
    private static final int HALO_SEGMENTS = 16;

    private final ModelPart pack;
    private final ModelPart leftTank;
    private final ModelPart rightTank;
    private final ModelPart leftThruster;
    private final ModelPart rightThruster;
    private final ModelPart leftExhaustEmitter;
    private final ModelPart rightExhaustEmitter;
    private final ModelPart coolingFan;
    private final ModelPart energyCore;
    private final ModelPart leftLatch;
    private final ModelPart rightLatch;

    public JetpackModel(ModelPart root) {
        super(root);
        this.pack = this.body.getChild("pack");
        this.leftTank = this.pack.getChild("left_tank");
        this.rightTank = this.pack.getChild("right_tank");
        this.leftThruster = this.leftTank.getChild("left_thruster");
        this.rightThruster = this.rightTank.getChild("right_thruster");
        this.leftExhaustEmitter = this.leftThruster.getChild("left_exhaust_emitter");
        this.rightExhaustEmitter = this.rightThruster.getChild("right_exhaust_emitter");
        ModelPart centerChassis = this.pack.getChild("center_chassis");
        this.coolingFan = centerChassis.getChild("cooling_fan");
        this.energyCore = centerChassis.getChild("energy_core");
        this.leftLatch = centerChassis.getChild("left_latch");
        this.rightLatch = centerChassis.getChild("right_latch");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // HumanoidModel requires all seven vanilla roots. Only body has geometry; the others exist
        // so NeoForge can copy the wearer's pose and visibility exactly like ordinary chest armor.
        root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);

        ScavpackMk1Geometry.addTo(body);
        return LayerDefinition.create(mesh, 128, 128);
    }

    // Reads the existing flight animation state into the SCAVPACK's mechanisms. At overheat the
    // pack performs a staged thermal bloom: crown latches release, both tank pods shift and cant
    // away from the chassis, the bells splay farther, the core pushes out and pulses, and the fan
    // spins at emergency speed. This exposes the cooling stack while preserving a heavy, scavenged
    // machine feel appropriate to the model.
    public void applyAnimation(JetpackAnimationState state, float partialTick) {
        resetAnimationPose();

        float flare = state.flare(partialTick);
        float vibration = state.vibration(partialTick);
        float thermalBloom = state.ventOpen(partialTick);
        float sag = state.sag(partialTick);
        float glide = state.glide(partialTick);
        float shudder = state.shudder(partialTick);
        float breathe = state.breathe(partialTick);
        float shimmer = state.shimmer(partialTick);
        float cough = state.coughTwitch(partialTick);
        float hitch = state.hitchTwitch(partialTick);
        float time = state.time(partialTick);
        float noiseA = JetpackAnimationState.noise(time);
        float noiseB = JetpackAnimationState.noise(time + 7.31F);

        // The long scavenged tank pods hum independently. During emergency cooling they unlock from
        // the fixed mount rails, separate slightly, and cant their lower ends outward like radiators.
        float breatheScale = 1.0F + 0.006F * breathe * (1.0F - Math.min(1.0F, flare));
        float tankBloom = thermalBloom * (0.19F + 0.008F * shimmer);
        this.leftTank.x -= 0.45F * thermalBloom;
        this.leftTank.z += 0.18F * thermalBloom;
        this.leftTank.zRot = tankBloom + 0.014F * vibration * noiseA + 0.025F * shudder * noiseB;
        this.leftTank.xRot = 0.012F * vibration * noiseB;
        this.leftTank.xScale = breatheScale;
        this.leftTank.zScale = breatheScale;

        this.rightTank.x += 0.45F * thermalBloom;
        this.rightTank.z += 0.18F * thermalBloom;
        this.rightTank.zRot = -tankBloom + 0.014F * vibration * noiseB + 0.025F * shudder * noiseA;
        this.rightTank.xRot = 0.012F * vibration * noiseA;
        this.rightTank.xScale = breatheScale;
        this.rightTank.zScale = breatheScale;

        // The nested bell assemblies telescope under thrust, droop on a dry stall, sweep into the
        // hover-boot airbrake pose, and join the tank bloom during thermal lockout. Impulses still
        // affect the physical left/right side selected by the synchronized animation state.
        float stretch = 1.0F + 0.18F * flare - 0.05F * sag - 0.04F * glide;
        float widen = 1.0F + 0.07F * flare + 0.06F * glide + 0.04F * thermalBloom;
        float droop = 0.34F * sag;
        float glideTilt = -0.32F * glide;
        float splay = 0.16F * sag + 0.28F * glide + 0.17F * thermalBloom;
        float rattle = 0.045F * shudder;

        this.leftThruster.yScale = stretch;
        this.leftThruster.xScale = widen;
        this.leftThruster.zScale = widen;
        this.leftThruster.xRot = droop + glideTilt + rattle * noiseA
                + (state.coughSide() < 0 ? cough : 0.0F)
                + (state.hitchSide() < 0 ? hitch : 0.0F);
        this.leftThruster.zRot = splay;

        this.rightThruster.yScale = stretch;
        this.rightThruster.xScale = widen;
        this.rightThruster.zScale = widen;
        this.rightThruster.xRot = droop + glideTilt + rattle * noiseB
                + (state.coughSide() > 0 ? cough : 0.0F)
                + (state.hitchSide() > 0 ? hitch : 0.0F);
        this.rightThruster.zRot = -splay;

        // The dark exhaust throats bloom independently of the heavy bells, making ignition and the
        // low hover pilot visually readable without rubber-scaling the whole tank.
        float emitterBloom = 1.0F + 0.16F * flare + 0.08F * glide;
        this.leftExhaustEmitter.xScale = emitterBloom;
        this.leftExhaustEmitter.zScale = emitterBloom;
        this.rightExhaustEmitter.xScale = emitterBloom;
        this.rightExhaustEmitter.zScale = emitterBloom;

        // The exposed fan has its own integrated phase, so changing heat changes speed without an
        // angular jump. Overheat also pushes the energy cell out of the chassis and gives it a slow,
        // uneven warning heartbeat while the release latches stand open.
        this.coolingFan.zRot += state.coolingFanSpin(partialTick);
        float corePulse = 1.0F + 0.012F * breathe + 0.07F * thermalBloom * (0.65F + 0.35F * shimmer);
        this.energyCore.xScale = corePulse;
        this.energyCore.yScale = corePulse;
        this.energyCore.z += 0.32F * thermalBloom;
        this.leftLatch.yRot = -0.95F * thermalBloom;
        this.leftLatch.xRot = -0.16F * thermalBloom;
        this.rightLatch.yRot = 0.95F * thermalBloom;
        this.rightLatch.xRot = -0.16F * thermalBloom;
    }

    // Model instances are shared across wearers. Every animated part must return to its baked pose
    // before another entity is rendered or a hot/flying player's pose can leak onto the next one.
    public void resetAnimationPose() {
        this.pack.resetPose();
        this.pack.xScale = PACK_SCALE;
        this.pack.yScale = PACK_SCALE;
        this.pack.zScale = PACK_SCALE;
        this.leftTank.resetPose();
        this.rightTank.resetPose();
        this.leftThruster.resetPose();
        this.rightThruster.resetPose();
        this.leftExhaustEmitter.resetPose();
        this.rightExhaustEmitter.resetPose();
        this.coolingFan.resetPose();
        this.energyCore.resetPose();
        this.leftLatch.resetPose();
        this.rightLatch.resetPose();
    }

    /**
     * Renders both engine effects through the same model hierarchy as the physical booster bells.
     * The caller has already copied the current player model pose and called {@link #applyAnimation},
     * so there are no separately approximated world coordinates anywhere in this path.
     */
    public void renderFlightPlumes(PoseStack poseStack, VertexConsumer plumeConsumer,
            JetpackAnimationState state, float partialTick,
            boolean thrusting, boolean surging) {
        poseStack.pushPose();
        this.body.translateAndRotate(poseStack);
        this.pack.translateAndRotate(poseStack);

        renderBoosterPlume(poseStack, plumeConsumer, state, partialTick,
                this.leftTank, this.leftThruster, this.leftExhaustEmitter, thrusting, surging);
        renderBoosterPlume(poseStack, plumeConsumer, state, partialTick,
                this.rightTank, this.rightThruster, this.rightExhaustEmitter, thrusting, surging);
        poseStack.popPose();
    }

    public void renderFlightHalos(PoseStack poseStack, VertexConsumer haloConsumer,
            JetpackAnimationState state, float partialTick, boolean thrusting, boolean hovering) {
        poseStack.pushPose();
        this.body.translateAndRotate(poseStack);
        this.pack.translateAndRotate(poseStack);

        renderBoosterHalos(poseStack, haloConsumer, state, partialTick,
                this.leftTank, this.leftThruster, this.leftExhaustEmitter, thrusting, hovering);
        renderBoosterHalos(poseStack, haloConsumer, state, partialTick,
                this.rightTank, this.rightThruster, this.rightExhaustEmitter, thrusting, hovering);
        poseStack.popPose();
    }

    /** Renders the single cohesive overheat treatment from both real exhaust throats. */
    public void renderOverheatPlumes(PoseStack poseStack, VertexConsumer thermalConsumer,
            JetpackAnimationState state, float partialTick) {
        poseStack.pushPose();
        this.body.translateAndRotate(poseStack);
        this.pack.translateAndRotate(poseStack);

        renderBoosterOverheat(poseStack, thermalConsumer, state, partialTick,
                this.leftTank, this.leftThruster, this.leftExhaustEmitter, 0.0F);
        renderBoosterOverheat(poseStack, thermalConsumer, state, partialTick,
                this.rightTank, this.rightThruster, this.rightExhaustEmitter, Mth.PI);
        poseStack.popPose();
    }

    private static void renderBoosterPlume(PoseStack poseStack, VertexConsumer plumeConsumer,
            JetpackAnimationState state, float partialTick,
            ModelPart tank, ModelPart thruster, ModelPart emitter,
            boolean thrusting, boolean surging) {
        poseStack.pushPose();
        tank.translateAndRotate(poseStack);
        thruster.translateAndRotate(poseStack);
        emitter.translateAndRotate(poseStack);

        float time = state.time(partialTick);
        if (thrusting) {
            float flicker = 1.0F + 0.035F * Mth.sin(time * 2.7F) + 0.018F * Mth.sin(time * 5.1F);
            float width = (surging ? 0.38F : 0.27F) * flicker;
            float length = (surging ? 1.42F : 0.68F) * flicker;
            int alpha = surging ? 255 : 230;

            // The sprite begins one hundredth of a block inside the throat. The separate halo pass
            // draws its mouth glow after the plume buffer has been finalized.
            renderCrossedPlume(poseStack, plumeConsumer, EXHAUST_OUTLET_Y - 0.012F,
                    width, length, alpha);
        }

        poseStack.popPose();
    }

    private static void renderBoosterHalos(PoseStack poseStack, VertexConsumer haloConsumer,
            JetpackAnimationState state, float partialTick,
            ModelPart tank, ModelPart thruster, ModelPart emitter,
            boolean thrusting, boolean hovering) {
        poseStack.pushPose();
        tank.translateAndRotate(poseStack);
        thruster.translateAndRotate(poseStack);
        emitter.translateAndRotate(poseStack);

        if (thrusting) {
            // Hides the sprite's one-pixel tip inside the glowing exhaust throat, eliminating the
            // last possible seam without shifting the concept plume away from the bell.
            renderHalo(poseStack.last().pose(), haloConsumer, EXHAUST_OUTLET_Y - 0.006F,
                    0.105F, 0.023F, 0.82F);
        } else if (hovering) {
            float time = state.time(partialTick);
            float phaseA = positiveModulo(time * 0.095F, 1.0F);
            float phaseB = positiveModulo(phaseA + 0.5F, 1.0F);
            renderHoverPulse(poseStack.last().pose(), haloConsumer, phaseA);
            renderHoverPulse(poseStack.last().pose(), haloConsumer, phaseB);
        }

        poseStack.popPose();
    }

    private static void renderBoosterOverheat(PoseStack poseStack, VertexConsumer thermalConsumer,
            JetpackAnimationState state, float partialTick,
            ModelPart tank, ModelPart thruster, ModelPart emitter, float phaseOffset) {
        poseStack.pushPose();
        tank.translateAndRotate(poseStack);
        thruster.translateAndRotate(poseStack);
        emitter.translateAndRotate(poseStack);

        float bloom = state.ventOpen(partialTick);
        float time = state.time(partialTick);
        // A slow shared breath gives the thermal discharge weight; a small opposite-phase flutter
        // stops the two failing engines looking stamped, without adding any extra particle textures.
        float breath = 1.0F + 0.055F * Mth.sin(time * 0.72F);
        float flutter = 1.0F + 0.035F * Mth.sin(time * 2.35F + phaseOffset);
        float width = (0.17F + 0.09F * bloom) * breath;
        float length = (0.30F + 0.34F * bloom) * flutter;
        int alpha = (int) Mth.lerp(bloom, 185.0F, 255.0F);
        renderCrossedPlume(poseStack, thermalConsumer, EXHAUST_OUTLET_Y - 0.010F,
                width, length, alpha);

        poseStack.popPose();
    }

    private static void renderCrossedPlume(PoseStack poseStack, VertexConsumer consumer,
            float rootY, float width, float length, int alpha) {
        float halfWidth = width * 0.5F;
        float tipY = rootY + length;
        int color = alpha << 24 | 0x00FFFFFF;
        PoseStack.Pose pose = poseStack.last();

        texturedVertex(consumer, pose, -halfWidth, rootY, 0.0F, color, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
        texturedVertex(consumer, pose, halfWidth, rootY, 0.0F, color, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F);
        texturedVertex(consumer, pose, halfWidth, tipY, 0.0F, color, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F);
        texturedVertex(consumer, pose, -halfWidth, tipY, 0.0F, color, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F);

        texturedVertex(consumer, pose, 0.0F, rootY, -halfWidth, color, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F);
        texturedVertex(consumer, pose, 0.0F, rootY, halfWidth, color, 1.0F, 0.0F, 1.0F, 0.0F, 0.0F);
        texturedVertex(consumer, pose, 0.0F, tipY, halfWidth, color, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F);
        texturedVertex(consumer, pose, 0.0F, tipY, -halfWidth, color, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F);
    }

    private static void texturedVertex(VertexConsumer consumer, PoseStack.Pose pose,
            float x, float y, float z, int color, float u, float v,
            float normalX, float normalY, float normalZ) {
        consumer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(0)
                .setLight(EFFECT_FULL_BRIGHT)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    private static void renderHoverPulse(Matrix4f matrix, VertexConsumer consumer, float phase) {
        float eased = phase * phase * (3.0F - 2.0F * phase);
        float y = EXHAUST_OUTLET_Y + 0.045F + 0.31F * eased;
        float radius = 0.105F + 0.075F * eased;
        float alpha = 0.72F * (1.0F - phase) + 0.12F;
        renderHalo(matrix, consumer, y, radius, 0.014F, alpha);
    }

    private static void renderHalo(Matrix4f matrix, VertexConsumer consumer,
            float y, float radius, float thickness, float alpha) {
        float inner = Math.max(0.0F, radius - thickness);
        for (int segment = 0; segment < HALO_SEGMENTS; segment++) {
            float angleA = Mth.TWO_PI * segment / HALO_SEGMENTS;
            float angleB = Mth.TWO_PI * (segment + 1) / HALO_SEGMENTS;
            float cosA = Mth.cos(angleA);
            float sinA = Mth.sin(angleA);
            float cosB = Mth.cos(angleB);
            float sinB = Mth.sin(angleB);

            consumer.addVertex(matrix, cosA * inner, y, sinA * inner).setColor(0.08F, 0.68F, 1.0F, alpha);
            consumer.addVertex(matrix, cosA * radius, y, sinA * radius).setColor(0.28F, 0.88F, 1.0F, alpha);
            consumer.addVertex(matrix, cosB * radius, y, sinB * radius).setColor(0.28F, 0.88F, 1.0F, alpha);
            consumer.addVertex(matrix, cosB * inner, y, sinB * inner).setColor(0.08F, 0.68F, 1.0F, alpha);
        }
    }

    private static float positiveModulo(float value, float modulus) {
        float result = value % modulus;
        return result < 0.0F ? result + modulus : result;
    }
}
