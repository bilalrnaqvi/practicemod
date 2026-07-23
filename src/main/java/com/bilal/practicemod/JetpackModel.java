package com.bilal.practicemod;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

// The worn jetpack model: a back-mounted plate with two tanks and downward nozzles, replacing the
// vanilla chestplate shape (which would wrap the whole torso and arms). All geometry hangs off the
// "body" part so it follows torso animation; every other humanoid part exists but is empty, because
// HumanoidModel requires the full part set and the armor layer marks the arms visible for chest gear.
//
// The tanks, nozzles, and vent flaps are separate child parts of "body" so applyAnimation can pose
// them independently. NeoForge's armor pose copy (ClientHooks.copyModelProperties) only writes the
// seven humanoid root parts, so child poses survive it - but nothing else resets them either, and
// this model instance is shared between all wearers, so applyAnimation writes every animated value
// absolutely each frame (via resetPose + explicit sets), never incrementally.
//
// Coordinates are in body-part space: y grows downward from the neck (torso spans y 0..12), +z is
// behind the player (torso back face at z = 2, outer skin layer at z = 2.25). Boxes deliberately
// intersect rather than sharing faces - coplanar overlapping faces z-fight.
public class JetpackModel extends HumanoidModel<LivingEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(PracticeMod.MODID, "jetpack"), "main");

    private final ModelPart rightTank;
    private final ModelPart leftTank;
    private final ModelPart rightNozzle;
    private final ModelPart leftNozzle;
    private final ModelPart rightVent;
    private final ModelPart leftVent;

    public JetpackModel(ModelPart root) {
        super(root);
        this.rightTank = this.body.getChild("right_tank");
        this.leftTank = this.body.getChild("left_tank");
        this.rightNozzle = this.body.getChild("right_nozzle");
        this.leftNozzle = this.body.getChild("left_nozzle");
        this.rightVent = this.body.getChild("right_vent");
        this.leftVent = this.body.getChild("left_vent");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Required by the HumanoidModel constructor; empty so nothing covers the head, arms, or legs.
        root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));

        // UV offsets land on regions of jetpack_layer_1.png that are opaque in the current art, so
        // the model is visible with today's texture; art/ART_PIPELINE.md documents the layout for
        // repainting. Texture x 32-64 / y 0-16 is transparent and must stay unused.
        //
        // Boxes are authored relative to each part's pivot so the assembled rest pose is identical
        // to the original single-part model. Pivots are chosen for the motion each part performs:
        // tanks pivot at their centers (vibration reads as shaking, not swinging), nozzles at their
        // top edge (the hinge they sag from), vents along their top edge on the tanks' outer faces.
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                // Mounting plate, flush across the back.
                .texOffs(0, 16).addBox(-4.0F, 0.0F, 2.5F, 8.0F, 11.0F, 1.0F),
                PartPose.ZERO);

        // Fuel tanks (right = player's right = -x), embedded 0.5 into the plate.
        body.addOrReplaceChild("right_tank", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1.5F, -5.0F, -1.5F, 3.0F, 10.0F, 3.0F),
                PartPose.offset(-2.0F, 5.5F, 4.5F));
        body.addOrReplaceChild("left_tank", CubeListBuilder.create()
                .texOffs(16, 0).addBox(-1.5F, -5.0F, -1.5F, 3.0F, 10.0F, 3.0F),
                PartPose.offset(2.0F, 5.5F, 4.5F));

        // Nozzles, sunk 0.5 into the tank bottoms and reaching one unit below the waist.
        body.addOrReplaceChild("right_nozzle", CubeListBuilder.create()
                .texOffs(20, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F),
                PartPose.offset(-2.0F, 10.0F, 4.75F));
        body.addOrReplaceChild("left_nozzle", CubeListBuilder.create()
                .texOffs(32, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F),
                PartPose.offset(2.0F, 10.0F, 4.75F));

        // Overheat vent flaps on the tanks' outer faces, hinged along their top edge so they swing
        // outward when the jetpack locks out. They sample the plate's opaque UV region; dedicated
        // vent art can be added later through the art pipeline without touching this geometry.
        body.addOrReplaceChild("right_vent", CubeListBuilder.create()
                .texOffs(10, 16).addBox(-1.0F, 0.0F, -1.5F, 1.0F, 4.0F, 3.0F),
                PartPose.offset(-3.55F, 2.5F, 4.5F));
        body.addOrReplaceChild("left_vent", CubeListBuilder.create()
                .texOffs(10, 16).addBox(0.0F, 0.0F, -1.5F, 1.0F, 4.0F, 3.0F),
                PartPose.offset(3.55F, 2.5F, 4.5F));

        return LayerDefinition.create(mesh, 64, 32);
    }

    // Poses the animated child parts from the wearer's animation state. Called from
    // IClientItemExtensions#setupModelAnimations after NeoForge copies the torso pose onto this
    // model and immediately before it renders, once per wearer per frame. Only reads state and
    // writes part fields - pure readout, no simulation. No allocations: a handful of float ops.
    public void applyAnimation(JetpackAnimationState state, float partialTick) {
        resetAnimationPose();

        float flare = state.flare(partialTick);
        float vibration = state.vibration(partialTick);
        float ventOpen = state.ventOpen(partialTick);
        float sag = state.sag(partialTick);
        float glide = state.glide(partialTick);
        float shudder = state.shudder(partialTick);
        float breathe = state.breathe(partialTick);
        float shimmer = state.shimmer(partialTick);
        float cough = state.coughTwitch(partialTick);
        float time = state.time(partialTick);
        float noiseA = JetpackAnimationState.noise(time);
        float noiseB = JetpackAnimationState.noise(time + 7.31F);

        // Nozzles: stretch and widen with flare (the "bloom"), hinge open/outward when sagging,
        // flare into the outward/swept-back airbrake pose while gliding, rattle with shudder, and
        // twitch one side per sputter cough. The glide tilt is deliberately OPPOSITE the sag droop
        // direction so a glide never reads as out-of-fuel; sag and glide stay additive so the
        // fuel-dies-mid-glide handoff blends.
        float stretch = 1.0F + 0.35F * flare - 0.1F * sag - 0.06F * glide;
        float widen = 1.0F + 0.12F * flare + 0.08F * glide;
        float droop = 0.45F * sag;
        float glideTilt = -0.40F * glide;
        float splay = 0.25F * sag + 0.35F * glide;
        float rattle = 0.06F * shudder;

        this.rightNozzle.yScale = stretch;
        this.rightNozzle.xScale = widen;
        this.rightNozzle.zScale = widen;
        this.rightNozzle.xRot = droop + glideTilt + rattle * noiseA + (state.coughSide() < 0 ? cough : 0.0F);
        this.rightNozzle.zRot = splay;

        this.leftNozzle.yScale = stretch;
        this.leftNozzle.xScale = widen;
        this.leftNozzle.zScale = widen;
        this.leftNozzle.xRot = droop + glideTilt + rattle * noiseB + (state.coughSide() > 0 ? cough : 0.0F);
        this.leftNozzle.zRot = -splay;

        // Tanks: idle breathing (fades out under thrust), continuous jitter scaled by vibration,
        // plus a share of any shutdown shudder. Different noise phases so they don't move in lockstep.
        float breatheScale = 1.0F + 0.008F * breathe * (1.0F - Math.min(1.0F, flare));
        this.rightTank.zRot = 0.02F * vibration * noiseA + 0.03F * shudder * noiseB;
        this.rightTank.xRot = 0.015F * vibration * noiseB;
        this.rightTank.xScale = breatheScale;
        this.rightTank.zScale = breatheScale;
        this.leftTank.zRot = 0.02F * vibration * noiseB + 0.03F * shudder * noiseA;
        this.leftTank.xRot = 0.015F * vibration * noiseA;
        this.leftTank.xScale = breatheScale;
        this.leftTank.zScale = breatheScale;

        // Vent flaps: swing outward with ventOpen, with a slow shimmer pulse layered on while open.
        float ventAngle = ventOpen * (1.0F + 0.05F * shimmer);
        this.rightVent.zRot = ventAngle;
        this.leftVent.zRot = -ventAngle;
    }

    // Returns the animated parts to their baked rest pose (loadPose also resets scale to 1). Used
    // both as the base of applyAnimation and on its own when a wearer has no animation state (e.g.
    // armor stands), so the shared model instance never leaks a pose from one wearer to the next.
    public void resetAnimationPose() {
        this.rightTank.resetPose();
        this.leftTank.resetPose();
        this.rightNozzle.resetPose();
        this.leftNozzle.resetPose();
        this.rightVent.resetPose();
        this.leftVent.resetPose();
    }
}
