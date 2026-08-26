package com.bilal.practicemod;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.PartDefinition;

// Generated from src/main/blockbench/SCAVPACK_MK1.bbmodel by
// tools/generate_scavpack_geometry.mjs. Keep animation-specific grouping changes in the generator,
// then regenerate this file after editing the Blockbench source.
final class ScavpackMk1Geometry {
    private ScavpackMk1Geometry() {
    }

    static void addTo(PartDefinition body) {

        PartDefinition pack = body.addOrReplaceChild("pack", CubeListBuilder.create(),
                PartPose.offset(0.0F, 8.0F, 4.0F));

        PartDefinition leftTank = pack.addOrReplaceChild("left_tank", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.0F, -8.0F, -2.16F, 6.0F, 16.0F, 4.32F)
                .texOffs(0, 0).addBox(-2.16F, -8.0F, -3.0F, 4.32F, 16.0F, 6.0F)
                .texOffs(0, 0).addBox(-2.85F, -9.0F, -2.052F, 5.7F, 1.5F, 4.104F)
                .texOffs(0, 0).addBox(-2.052F, -9.0F, -2.85F, 4.104F, 1.5F, 5.7F)
                .texOffs(64, 0).addBox(-3.0F, 7.6F, -2.16F, 6.0F, 1.4F, 4.32F)
                .texOffs(64, 0).addBox(-2.16F, 7.6F, -3.0F, 4.32F, 1.4F, 6.0F)
                .texOffs(64, 0).addBox(-3.175F, 3.5F, -2.286F, 6.35F, 1.0F, 4.572F)
                .texOffs(64, 0).addBox(-2.286F, 3.5F, -3.175F, 4.572F, 1.0F, 6.35F)
                .texOffs(64, 0).addBox(-3.175F, -4.5F, -2.286F, 6.35F, 1.0F, 4.572F)
                .texOffs(64, 0).addBox(-2.286F, -4.5F, -3.175F, 4.572F, 1.0F, 6.35F)
                .texOffs(64, 0).addBox(-3.19F, -2.025F, -2.2968F, 6.38F, 1.65F, 4.5936F)
                .texOffs(64, 0).addBox(-2.2968F, -2.025F, -3.19F, 4.5936F, 1.65F, 6.38F)
                .texOffs(64, 0).addBox(-2.55F, -9.6F, -1.836F, 5.1F, 1.0F, 3.672F)
                .texOffs(64, 0).addBox(-1.836F, -9.6F, -2.55F, 3.672F, 1.0F, 5.1F)
                .texOffs(64, 0).addBox(-2.875F, 8.85F, -2.07F, 5.75F, 1.0F, 4.14F)
                .texOffs(64, 0).addBox(-2.07F, 8.85F, -2.875F, 4.14F, 1.0F, 5.75F)
                .texOffs(64, 0).addBox(-2.575F, 10.15F, -1.854F, 5.15F, 1.0F, 3.708F)
                .texOffs(64, 0).addBox(-1.854F, 10.15F, -2.575F, 3.708F, 1.0F, 5.15F)
                .texOffs(64, 0).addBox(-2.675F, 12.25F, -1.926F, 5.35F, 1.0F, 3.852F)
                .texOffs(64, 0).addBox(-1.926F, 12.25F, -2.675F, 3.852F, 1.0F, 5.35F)
                .texOffs(0, 32).addBox(-1.5F, -10.5F, -1.08F, 3.0F, 1.0F, 2.16F)
                .texOffs(0, 32).addBox(-1.08F, -10.5F, -1.5F, 2.16F, 1.0F, 3.0F)
                .texOffs(0, 32).addBox(-2.725F, 8.65F, -1.962F, 5.45F, 1.0F, 3.924F)
                .texOffs(0, 32).addBox(-1.962F, 8.65F, -2.725F, 3.924F, 1.0F, 5.45F),
                PartPose.offset(-7.0F, -2.0F, 0.0F));

        PartDefinition leftThruster = leftTank.addOrReplaceChild("left_thruster", CubeListBuilder.create()
                .texOffs(96, 0).addBox(-2.7F, -0.75F, -1.944F, 5.4F, 1.2F, 3.888F)
                .texOffs(96, 0).addBox(-1.944F, -0.75F, -2.7F, 3.888F, 1.2F, 5.4F)
                .texOffs(96, 0).addBox(-2.35F, 0.5F, -1.692F, 4.7F, 1.3F, 3.384F)
                .texOffs(96, 0).addBox(-1.692F, 0.5F, -2.35F, 3.384F, 1.3F, 4.7F)
                .texOffs(96, 0).addBox(-2.5F, 1.75F, -1.8F, 5.0F, 1.0F, 3.6F)
                .texOffs(96, 0).addBox(-1.8F, 1.75F, -2.5F, 3.6F, 1.0F, 5.0F),
                PartPose.offset(0.0F, 10.0F, 0.0F));

        PartDefinition leftExhaustEmitter = leftThruster.addOrReplaceChild("left_exhaust_emitter", CubeListBuilder.create()
                .texOffs(32, 32).addBox(-1.7F, 0.65F, -1.224F, 3.4F, 1.0F, 2.448F)
                .texOffs(32, 32).addBox(-1.224F, 0.65F, -1.7F, 2.448F, 1.0F, 3.4F),
                PartPose.offset(0.0F, 2.5F, 0.0F));

        PartDefinition rightTank = pack.addOrReplaceChild("right_tank", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.0F, -8.0F, -2.16F, 6.0F, 16.0F, 4.32F)
                .texOffs(0, 0).addBox(-2.16F, -8.0F, -3.0F, 4.32F, 16.0F, 6.0F)
                .texOffs(0, 0).addBox(-2.85F, -9.0F, -2.052F, 5.7F, 1.5F, 4.104F)
                .texOffs(0, 0).addBox(-2.052F, -9.0F, -2.85F, 4.104F, 1.5F, 5.7F)
                .texOffs(64, 0).addBox(-3.0F, 7.6F, -2.16F, 6.0F, 1.4F, 4.32F)
                .texOffs(64, 0).addBox(-2.16F, 7.6F, -3.0F, 4.32F, 1.4F, 6.0F)
                .texOffs(64, 0).addBox(-3.175F, 3.5F, -2.286F, 6.35F, 1.0F, 4.572F)
                .texOffs(64, 0).addBox(-2.286F, 3.5F, -3.175F, 4.572F, 1.0F, 6.35F)
                .texOffs(64, 0).addBox(-3.175F, -4.5F, -2.286F, 6.35F, 1.0F, 4.572F)
                .texOffs(64, 0).addBox(-2.286F, -4.5F, -3.175F, 4.572F, 1.0F, 6.35F)
                .texOffs(64, 0).addBox(-3.19F, -2.425F, -2.2968F, 6.38F, 1.65F, 4.5936F)
                .texOffs(64, 0).addBox(-2.2968F, -2.425F, -3.19F, 4.5936F, 1.65F, 6.38F)
                .texOffs(64, 0).addBox(-2.55F, -9.6F, -1.836F, 5.1F, 1.0F, 3.672F)
                .texOffs(64, 0).addBox(-1.836F, -9.6F, -2.55F, 3.672F, 1.0F, 5.1F)
                .texOffs(64, 0).addBox(-2.875F, 8.85F, -2.07F, 5.75F, 1.0F, 4.14F)
                .texOffs(64, 0).addBox(-2.07F, 8.85F, -2.875F, 4.14F, 1.0F, 5.75F)
                .texOffs(64, 0).addBox(-2.575F, 10.15F, -1.854F, 5.15F, 1.0F, 3.708F)
                .texOffs(64, 0).addBox(-1.854F, 10.15F, -2.575F, 3.708F, 1.0F, 5.15F)
                .texOffs(64, 0).addBox(-2.675F, 12.25F, -1.926F, 5.35F, 1.0F, 3.852F)
                .texOffs(64, 0).addBox(-1.926F, 12.25F, -2.675F, 3.852F, 1.0F, 5.35F)
                .texOffs(0, 32).addBox(-1.5F, -10.5F, -1.08F, 3.0F, 1.0F, 2.16F)
                .texOffs(0, 32).addBox(-1.08F, -10.5F, -1.5F, 2.16F, 1.0F, 3.0F)
                .texOffs(0, 32).addBox(-2.725F, 8.65F, -1.962F, 5.45F, 1.0F, 3.924F)
                .texOffs(0, 32).addBox(-1.962F, 8.65F, -2.725F, 3.924F, 1.0F, 5.45F),
                PartPose.offset(7.0F, -2.0F, 0.0F));

        PartDefinition rightThruster = rightTank.addOrReplaceChild("right_thruster", CubeListBuilder.create()
                .texOffs(96, 0).addBox(-2.7F, -0.75F, -1.944F, 5.4F, 1.2F, 3.888F)
                .texOffs(96, 0).addBox(-1.944F, -0.75F, -2.7F, 3.888F, 1.2F, 5.4F)
                .texOffs(96, 0).addBox(-2.35F, 0.5F, -1.692F, 4.7F, 1.3F, 3.384F)
                .texOffs(96, 0).addBox(-1.692F, 0.5F, -2.35F, 3.384F, 1.3F, 4.7F)
                .texOffs(96, 0).addBox(-2.5F, 1.75F, -1.8F, 5.0F, 1.0F, 3.6F)
                .texOffs(96, 0).addBox(-1.8F, 1.75F, -2.5F, 3.6F, 1.0F, 5.0F),
                PartPose.offset(0.0F, 10.0F, 0.0F));

        PartDefinition rightExhaustEmitter = rightThruster.addOrReplaceChild("right_exhaust_emitter", CubeListBuilder.create()
                .texOffs(32, 32).addBox(-1.7F, 0.65F, -1.224F, 3.4F, 1.0F, 2.448F)
                .texOffs(32, 32).addBox(-1.224F, 0.65F, -1.7F, 2.448F, 1.0F, 3.4F),
                PartPose.offset(0.0F, 2.5F, 0.0F));

        PartDefinition centerChassis = pack.addOrReplaceChild("center_chassis", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-2.8F, -9.0F, -1.2F, 0.65F, 18.0F, 3.9F)
                .texOffs(64, 0).addBox(2.15F, -9.0F, -1.2F, 0.65F, 18.0F, 3.9F)
                .texOffs(64, 0).addBox(-2.8F, -9.0F, -1.2F, 5.6F, 1.0F, 3.9F)
                .texOffs(64, 0).addBox(-2.8F, -4.5F, -1.0F, 5.6F, 0.7F, 3.8F)
                .texOffs(64, 0).addBox(-2.8F, 1.6F, -1.0F, 5.6F, 0.7F, 3.8F)
                .texOffs(64, 0).addBox(-2.8F, 8.0F, -1.2F, 5.6F, 1.0F, 4.0F)
                .texOffs(96, 0).addBox(-2.05F, -8.0F, -1.0F, 4.1F, 16.0F, 1.1F)
                .texOffs(96, 0).addBox(-1.9F, -7.7F, 0.1F, 3.8F, 3.1F, 3.1F)
                .texOffs(96, 0).addBox(-2.05F, -3.8F, 0.2F, 4.1F, 4.7F, 2.9F)
                .texOffs(96, 0).addBox(-2.25F, 2.4F, 0.1F, 4.5F, 5.4F, 3.0F)
                .texOffs(64, 0).addBox(-1.25F, -3.7F, 3.0F, 2.5F, 5.0F, 1.15F)
                .texOffs(64, 0).addBox(-1.45F, -3.9F, 3.2F, 2.9F, 0.6F, 1.25F)
                .texOffs(64, 0).addBox(-1.45F, 0.9F, 3.2F, 2.9F, 0.6F, 1.25F)
                .texOffs(64, 0).addBox(-1.65F, 7.0F, 3.0F, 3.3F, 1.3F, 1.0F)
                .texOffs(64, 0).addBox(-2.0F, -6.0F, -2.2F, 0.8F, 12.0F, 1.2F)
                .texOffs(64, 0).addBox(1.2F, -6.0F, -2.2F, 0.8F, 12.0F, 1.2F)
                .texOffs(0, 64).addBox(-0.45F, 7.35F, 3.98F, 0.9F, 0.7F, 0.22F)
                .texOffs(32, 0).addBox(-1.55F, -7.45F, 3.15F, 3.1F, 2.45F, 0.4F)
                .texOffs(32, 0).addBox(-0.95F, -6.55F, 3.53F, 1.9F, 1.1F, 0.19F)
                .texOffs(0, 32).addBox(-2.0F, 2.8F, 3.1F, 4.0F, 0.4F, 0.55F)
                .texOffs(0, 32).addBox(-2.0F, 6.9F, 3.1F, 4.0F, 0.4F, 0.55F),
                PartPose.offset(0.0F, -2.0F, 0.0F));

        PartDefinition coolingFan = centerChassis.addOrReplaceChild("cooling_fan", CubeListBuilder.create()
                .texOffs(96, 0).addBox(0.15F, -0.5F, -0.1F, 1.0F, 0.45F, 0.2F)
                .texOffs(96, 0).addBox(-1.15F, 0.05F, -0.1F, 1.0F, 0.45F, 0.2F)
                .texOffs(96, 0).addBox(-0.45F, -1.1F, -0.1F, 0.45F, 1.0F, 0.2F)
                .texOffs(96, 0).addBox(0.0F, 0.1F, -0.1F, 0.45F, 1.0F, 0.2F),
                PartPose.offsetAndRotation(0.0F, 5.15F, 4.1F, 0.0F, 0.0F, -0.610865F));

        PartDefinition energyCore = centerChassis.addOrReplaceChild("energy_core", CubeListBuilder.create()
                .texOffs(0, 64).addBox(-0.62F, -1.9F, -0.28F, 1.24F, 3.8F, 0.4F)
                .texOffs(0, 64).addBox(-0.82F, -1.6F, -0.27F, 1.64F, 0.3F, 0.45F)
                .texOffs(0, 64).addBox(-0.82F, 1.2F, -0.27F, 1.64F, 0.3F, 0.45F),
                PartPose.offset(0.0F, -1.2F, 4.4F));

        PartDefinition leftLatchPart = centerChassis.addOrReplaceChild("left_latch", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-0.35F, -0.5F, -0.38F, 0.35F, 1.0F, 0.73F),
                PartPose.offset(-1.55F, -6.5F, 3.5F));

        PartDefinition rightLatchPart = centerChassis.addOrReplaceChild("right_latch", CubeListBuilder.create()
                .texOffs(0, 32).addBox(0.0F, -0.45F, -0.38F, 0.35F, 1.2F, 0.73F),
                PartPose.offset(1.55F, -6.25F, 3.5F));

        PartDefinition fanShroud0 = centerChassis.addOrReplaceChild("fan_shroud_0", CubeListBuilder.create()
                .texOffs(96, 0).addBox(-1.825F, -0.5F, -1.314F, 3.65F, 1.0F, 2.628F),
                PartPose.offsetAndRotation(0.0F, 5.15F, 3.5F, -1.570796F, 0.0F, 0.0F));

        PartDefinition fanShroud1 = centerChassis.addOrReplaceChild("fan_shroud_1", CubeListBuilder.create()
                .texOffs(96, 0).addBox(-1.314F, -0.5F, -1.825F, 2.628F, 1.0F, 3.65F),
                PartPose.offsetAndRotation(0.0F, 5.15F, 3.5F, -1.570796F, 0.0F, 0.0F));

        PartDefinition fanShroud2 = centerChassis.addOrReplaceChild("fan_shroud_2", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-1.325F, -0.5F, -0.954F, 2.65F, 1.0F, 1.908F),
                PartPose.offsetAndRotation(0.0F, 5.15F, 4.0F, -1.570796F, 0.0F, 0.0F));

        PartDefinition fanShroud3 = centerChassis.addOrReplaceChild("fan_shroud_3", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-0.954F, -0.5F, -1.325F, 1.908F, 1.0F, 2.65F),
                PartPose.offsetAndRotation(0.0F, 5.15F, 4.0F, -1.570796F, 0.0F, 0.0F));

        PartDefinition fanShroud4 = centerChassis.addOrReplaceChild("fan_shroud_4", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-0.5F, -0.5F, -0.36F, 1.0F, 1.0F, 0.72F),
                PartPose.offsetAndRotation(0.0F, 5.15F, 4.55F, -1.570796F, 0.0F, 0.0F));

        PartDefinition fanShroud5 = centerChassis.addOrReplaceChild("fan_shroud_5", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-0.36F, -0.5F, -0.5F, 0.72F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, 5.15F, 4.55F, -1.570796F, 0.0F, 0.0F));

        PartDefinition tankMounts = pack.addOrReplaceChild("tank_mounts", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-4.35F, -6.4F, -0.8F, 1.7F, 0.9F, 3.0F)
                .texOffs(0, 32).addBox(2.65F, -6.4F, -0.8F, 1.7F, 0.9F, 3.0F)
                .texOffs(0, 32).addBox(-4.25F, -0.4F, -0.6F, 1.6F, 0.9F, 2.6F)
                .texOffs(0, 32).addBox(2.65F, -0.4F, -0.6F, 1.6F, 0.9F, 2.6F)
                .texOffs(0, 32).addBox(-4.1F, 5.4F, -0.5F, 1.45F, 0.9F, 2.3F)
                .texOffs(0, 32).addBox(2.65F, 5.4F, -0.5F, 1.45F, 0.9F, 2.3F)
                .texOffs(64, 0).addBox(-4.55F, -6.65F, -1.0F, 0.5F, 1.4F, 3.4F)
                .texOffs(64, 0).addBox(4.05F, -6.65F, -1.0F, 0.5F, 1.4F, 3.4F)
                .texOffs(64, 0).addBox(-4.4F, 5.15F, -0.8F, 0.5F, 1.4F, 2.9F)
                .texOffs(64, 0).addBox(3.9F, 5.15F, -0.8F, 0.5F, 1.4F, 2.9F)
                .texOffs(64, 0).addBox(-2.6F, -8.25F, -1.6F, 5.2F, 0.65F, 0.8F)
                .texOffs(64, 0).addBox(-2.45F, 8.15F, -1.7F, 4.9F, 0.65F, 0.9F)
                .texOffs(64, 32).addBox(-4.65F, -6.35F, 1.95F, 0.45F, 0.8F, 0.6F)
                .texOffs(64, 32).addBox(4.2F, -6.35F, 1.95F, 0.45F, 0.8F, 0.6F)
                .texOffs(64, 32).addBox(-4.5F, 5.45F, 1.75F, 0.45F, 0.8F, 0.6F)
                .texOffs(64, 32).addBox(4.05F, 5.45F, 1.75F, 0.45F, 0.8F, 0.6F),
                PartPose.offset(0.0F, -2.0F, 0.0F));

        PartDefinition tubing = pack.addOrReplaceChild("tubing", CubeListBuilder.create()
                .texOffs(32, 32).addBox(-7.28F, -11.55F, -0.28F, 0.56F, 1.2F, 0.56F)
                .texOffs(32, 32).addBox(-7.0F, -11.84F, -0.28F, 2.45F, 0.56F, 0.56F)
                .texOffs(32, 32).addBox(-4.83F, -11.55F, -0.28F, 0.56F, 2.55F, 0.56F)
                .texOffs(32, 32).addBox(6.72F, -11.7F, -0.08F, 0.56F, 1.35F, 0.56F)
                .texOffs(32, 32).addBox(4.35F, -11.98F, -0.08F, 2.65F, 0.56F, 0.56F)
                .texOffs(32, 32).addBox(4.07F, -11.7F, -0.08F, 0.56F, 3.5F, 0.56F)
                .texOffs(32, 32).addBox(-3.45F, 8.6F, 0.85F, 0.5F, 3.3F, 0.5F)
                .texOffs(32, 32).addBox(2.95F, 8.6F, 0.85F, 0.5F, 3.3F, 0.5F)
                .texOffs(32, 32).addBox(-3.2F, 11.6F, 0.85F, 6.4F, 0.5F, 0.5F)
                .texOffs(32, 32).addBox(-4.4F, 7.45F, 1.15F, 1.65F, 0.45F, 0.5F)
                .texOffs(32, 32).addBox(2.75F, 7.45F, 1.15F, 1.65F, 0.45F, 0.5F)
                .texOffs(32, 32).addBox(-3.5F, -3.8F, 1.45F, 0.45F, 7.0F, 0.5F)
                .texOffs(32, 32).addBox(3.05F, -1.7F, 1.45F, 0.45F, 5.2F, 0.5F)
                .texOffs(32, 32).addBox(-4.4F, -3.4F, 1.45F, 1.2F, 0.45F, 0.5F)
                .texOffs(32, 32).addBox(3.2F, -1.3F, 1.45F, 1.2F, 0.45F, 0.5F)
                .texOffs(32, 64).addBox(-10.18F, -3.8F, 0.55F, 0.46F, 6.3F, 0.5F)
                .texOffs(32, 64).addBox(-10.0F, -4.0F, 0.55F, 1.25F, 0.45F, 0.5F)
                .texOffs(32, 64).addBox(9.7F, -3.6F, -0.55F, 0.45F, 4.8F, 0.5F)
                .texOffs(32, 64).addBox(8.8F, 1.0F, -0.55F, 1.15F, 0.45F, 0.5F)
                .texOffs(64, 32).addBox(-4.95F, -9.15F, -0.4F, 0.7F, 0.7F, 0.8F)
                .texOffs(64, 32).addBox(4.05F, -8.7F, -0.2F, 0.7F, 0.75F, 0.8F)
                .texOffs(64, 32).addBox(-3.1F, -4.6F, 2.8F, 0.28F, 5.3F, 0.35F)
                .texOffs(64, 32).addBox(2.82F, -6.0F, 2.75F, 0.28F, 4.5F, 0.35F)
                .texOffs(64, 32).addBox(-2.95F, -4.62F, 2.8F, 1.45F, 0.32F, 0.35F)
                .texOffs(64, 32).addBox(1.45F, -6.02F, 2.75F, 1.5F, 0.32F, 0.35F),
                PartPose.offset(0.0F, -2.0F, 0.0F));

        PartDefinition details = pack.addOrReplaceChild("details", CubeListBuilder.create()
                .texOffs(32, 0).addBox(-8.55F, -0.25F, 2.85F, 3.1F, 2.25F, 0.35F)
                .texOffs(32, 0).addBox(5.65F, -7.6F, 2.85F, 2.8F, 2.45F, 0.35F)
                .texOffs(32, 0).addBox(-10.1F, -6.3F, -1.0F, 0.38F, 3.3F, 2.0F)
                .texOffs(32, 0).addBox(9.72F, 1.6F, -0.8F, 0.38F, 3.2F, 2.1F)
                .texOffs(96, 32).addBox(6.0F, -0.2F, 2.86F, 2.1F, 2.2F, 0.37F)
                .texOffs(96, 32).addBox(-8.7F, -5.45F, 2.86F, 3.35F, 0.45F, 0.38F)
                .texOffs(96, 32).addBox(8.45F, 6.0F, 2.3F, 0.75F, 0.5F, 0.45F)
                .texOffs(96, 32).addBox(8.1F, 5.3F, 2.55F, 0.75F, 0.5F, 0.37F)
                .texOffs(96, 32).addBox(7.75F, 4.6F, 2.72F, 0.75F, 0.5F, 0.3F)
                .texOffs(64, 64).addBox(8.5F, -4.0F, 2.15F, 0.85F, 1.7F, 0.6F)
                .texOffs(64, 64).addBox(8.35F, -3.2F, 2.7F, 1.15F, 0.4F, 0.3F)
                .texOffs(64, 32).addBox(-8.8F, -0.6F, 3.15F, 0.45F, 0.8F, 0.35F)
                .texOffs(64, 32).addBox(-9.95F, 3.5F, -0.5F, 0.4F, 0.9F, 1.1F)
                .texOffs(64, 32).addBox(9.55F, -4.5F, -0.6F, 0.4F, 0.9F, 1.3F)
                .texOffs(64, 32).addBox(9.9F, 2.3F, -0.5F, 0.45F, 1.7F, 0.5F)
                .texOffs(64, 0).addBox(-8.25F, 0.25F, 3.18F, 0.2F, 1.4F, 0.24F)
                .texOffs(64, 0).addBox(-7.85F, 0.25F, 3.18F, 0.2F, 1.4F, 0.24F)
                .texOffs(64, 0).addBox(-7.45F, 0.25F, 3.18F, 0.2F, 1.4F, 0.24F)
                .texOffs(64, 0).addBox(-7.05F, 0.25F, 3.18F, 0.2F, 1.4F, 0.24F)
                .texOffs(64, 0).addBox(6.9F, 0.55F, 3.2F, 0.3F, 1.1F, 0.23F)
                .texOffs(64, 0).addBox(6.9F, 0.05F, 3.2F, 0.3F, 0.3F, 0.23F),
                PartPose.offset(0.0F, -2.0F, 0.0F));

    }
}
