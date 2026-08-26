#!/usr/bin/env node

// Converts the checked-in Blockbench source into the code-defined ModelPart tree used by
// Minecraft 1.21.1. The source asset is box-UV geometry, so every cube can be represented by a
// CubeListBuilder entry without a runtime model loader.

import fs from "node:fs";

const input = process.argv[2];
if (!input) {
    throw new Error("usage: node tools/generate_scavpack_geometry.mjs <model.bbmodel>");
}

const model = JSON.parse(fs.readFileSync(input, "utf8"));
const elements = new Map(model.elements.map((element) => [element.uuid, element]));

function groupByName(name) {
    const group = model.groups.find((candidate) => candidate.name === name);
    if (!group) {
        throw new Error(`missing Blockbench group: ${name}`);
    }
    return group;
}

function outlinerNode(uuid, nodes = model.outliner) {
    for (const node of nodes) {
        if (typeof node === "object") {
            if (node.uuid === uuid) {
                return node;
            }
            const nested = outlinerNode(uuid, node.children ?? []);
            if (nested) {
                return nested;
            }
        }
    }
    return null;
}

function directElements(groupName) {
    const group = groupByName(groupName);
    const node = outlinerNode(group.uuid);
    if (!node) {
        throw new Error(`missing outliner node: ${groupName}`);
    }
    return node.children.filter((child) => typeof child === "string").map((uuid) => elements.get(uuid));
}

function f(value) {
    const rounded = Math.abs(value) < 0.0000005 ? 0 : Math.round(value * 1000000) / 1000000;
    return `${Number.isInteger(rounded) ? rounded.toFixed(1) : rounded}F`;
}

function transformedPivot(sourcePivot) {
    return [sourcePivot[0], 24 - sourcePivot[1], sourcePivot[2]];
}

function relativePivot(sourcePivot, sourceParentPivot) {
    const pivot = transformedPivot(sourcePivot);
    const parent = transformedPivot(sourceParentPivot);
    return [pivot[0] - parent[0], pivot[1] - parent[1], pivot[2] - parent[2]];
}

function cubeLine(element, sourcePivot) {
    const pivot = transformedPivot(sourcePivot);
    const from = [element.from[0], 24 - element.to[1], element.from[2]];
    const size = element.to.map((value, index) => value - element.from[index]);
    const uv = element.uv_offset ?? [element.faces.east.uv[0], element.faces.up.uv[3]];
    return `                .texOffs(${Math.round(uv[0])}, ${Math.round(uv[1])}).addBox(${f(from[0] - pivot[0])}, ${f(from[1] - pivot[1])}, ${f(from[2] - pivot[2])}, ${f(size[0])}, ${f(size[1])}, ${f(size[2])})`;
}

function builder(cubes, sourcePivot) {
    if (cubes.length === 0) {
        return "CubeListBuilder.create()";
    }
    return `CubeListBuilder.create()\n${cubes.map((cube) => cubeLine(cube, sourcePivot)).join("\n")}`;
}

function pose(sourcePivot, sourceParentPivot, rotation = [0, 0, 0]) {
    const offset = relativePivot(sourcePivot, sourceParentPivot);
    const radians = [
        -rotation[0] * Math.PI / 180,
        rotation[1] * Math.PI / 180,
        -rotation[2] * Math.PI / 180,
    ];
    if (radians.every((value) => value === 0)) {
        return `PartPose.offset(${offset.map(f).join(", ")})`;
    }
    return `PartPose.offsetAndRotation(${offset.map(f).join(", ")}, ${radians.map(f).join(", ")})`;
}

function addPart(parentVariable, variable, partName, sourcePivot, sourceParentPivot, cubes, rotation = [0, 0, 0]) {
    return `        PartDefinition ${variable} = ${parentVariable}.addOrReplaceChild("${partName}", ${builder(cubes, sourcePivot)},\n                ${pose(sourcePivot, sourceParentPivot, rotation)});`;
}

const root = groupByName("root");
const leftTankGroup = groupByName("left_tank");
const rightTankGroup = groupByName("right_tank");
const leftThrusterGroup = groupByName("left_thruster");
const rightThrusterGroup = groupByName("right_thruster");
const leftEmitterGroup = groupByName("left_exhaust_emitter");
const rightEmitterGroup = groupByName("right_exhaust_emitter");
const centerGroup = groupByName("center_chassis");
const mountsGroup = groupByName("tank_mounts");
const tubingGroup = groupByName("tubing");
const detailsGroup = groupByName("details");

const leftThrusterCubes = directElements("left_thruster");
const rightThrusterCubes = directElements("right_thruster");
const leftExhaust = leftThrusterCubes.filter((cube) => cube.name.startsWith("left_exhaust_opening"));
const rightExhaust = rightThrusterCubes.filter((cube) => cube.name.startsWith("right_exhaust_opening"));

const centerCubes = directElements("center_chassis");
const energyCore = centerCubes.filter((cube) => cube.name === "central_energy_core" || cube.name.startsWith("energy_core_"));
const fanBlades = centerCubes.filter((cube) => cube.name.startsWith("fan_blade_"));
const fanShroud = centerCubes.filter((cube) => cube.name.startsWith("cooling_fan_"));
const leftLatch = centerCubes.filter((cube) => cube.name === "upper_latch_left");
const rightLatch = centerCubes.filter((cube) => cube.name === "upper_latch_right");
const animatedCenterNames = new Set([...energyCore, ...fanBlades, ...fanShroud, ...leftLatch, ...rightLatch].map((cube) => cube.name));
const staticCenter = centerCubes.filter((cube) => !animatedCenterNames.has(cube.name));

const fanPivot = [0, 12.85, 8.1];
const energyPivot = [0, 19.2, 8.4];
const leftLatchPivot = [-1.55, 24.5, 7.5];
const rightLatchPivot = [1.55, 24.25, 7.5];

const lines = [];
lines.push(`package com.bilal.practicemod;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.PartDefinition;

// Generated from src/main/blockbench/SCAVPACK_MK1.bbmodel by
// tools/generate_scavpack_geometry.mjs. Keep animation-specific grouping changes in the generator,
// then regenerate this file after editing the Blockbench source.
final class ScavpackMk1Geometry {
    private ScavpackMk1Geometry() {
    }

    static void addTo(PartDefinition body) {`);

lines.push(addPart("body", "pack", "pack", root.origin, [0, 24, 0], [], root.rotation));
lines.push(addPart("pack", "leftTank", "left_tank", leftTankGroup.origin, root.origin, directElements("left_tank"), leftTankGroup.rotation));
lines.push(addPart("leftTank", "leftThruster", "left_thruster", leftThrusterGroup.origin, leftTankGroup.origin,
    leftThrusterCubes.filter((cube) => !leftExhaust.includes(cube)), leftThrusterGroup.rotation));
lines.push(addPart("leftThruster", "leftExhaustEmitter", "left_exhaust_emitter", leftEmitterGroup.origin,
    leftThrusterGroup.origin, leftExhaust, leftEmitterGroup.rotation));

lines.push(addPart("pack", "rightTank", "right_tank", rightTankGroup.origin, root.origin, directElements("right_tank"), rightTankGroup.rotation));
lines.push(addPart("rightTank", "rightThruster", "right_thruster", rightThrusterGroup.origin, rightTankGroup.origin,
    rightThrusterCubes.filter((cube) => !rightExhaust.includes(cube)), rightThrusterGroup.rotation));
lines.push(addPart("rightThruster", "rightExhaustEmitter", "right_exhaust_emitter", rightEmitterGroup.origin,
    rightThrusterGroup.origin, rightExhaust, rightEmitterGroup.rotation));

lines.push(addPart("pack", "centerChassis", "center_chassis", centerGroup.origin, root.origin, staticCenter, centerGroup.rotation));
lines.push(addPart("centerChassis", "coolingFan", "cooling_fan", fanPivot, centerGroup.origin, fanBlades, [0, 0, 35]));
lines.push(addPart("centerChassis", "energyCore", "energy_core", energyPivot, centerGroup.origin, energyCore));
lines.push(addPart("centerChassis", "leftLatchPart", "left_latch", leftLatchPivot, centerGroup.origin, leftLatch));
lines.push(addPart("centerChassis", "rightLatchPart", "right_latch", rightLatchPivot, centerGroup.origin, rightLatch));

fanShroud.forEach((cube, index) => {
    lines.push(addPart("centerChassis", `fanShroud${index}`, `fan_shroud_${index}`, cube.origin, centerGroup.origin,
        [cube], cube.rotation));
});

lines.push(addPart("pack", "tankMounts", "tank_mounts", mountsGroup.origin, root.origin, directElements("tank_mounts"), mountsGroup.rotation));
lines.push(addPart("pack", "tubing", "tubing", tubingGroup.origin, root.origin, directElements("tubing"), tubingGroup.rotation));
lines.push(addPart("pack", "details", "details", detailsGroup.origin, root.origin, directElements("details"), detailsGroup.rotation));

lines.push(`    }
}
`);

process.stdout.write(lines.join("\n\n"));
