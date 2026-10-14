#!/usr/bin/env node

// Converts the handed-off free-format Blockbench model into compact render data and extracts its
// embedded PNGs unchanged. Minecraft's ModelPart baker only accepts cuboids, while this asset's
// canopy is made from true triangular/quadrilateral meshes, so those faces are retained for the
// small custom renderer instead of being approximated with replacement boxes.

import fs from "node:fs";
import path from "node:path";

const [input, textureOutput] = process.argv.slice(2);
if (!input || !textureOutput) {
    throw new Error("usage: node tools/generate_umbrella_glider_geometry.mjs <model.bbmodel> <texture-output-dir>");
}

const model = JSON.parse(fs.readFileSync(input, "utf8"));
const groups = new Map(model.groups.map((group) => [group.uuid, group]));
const elements = new Map(model.elements.map((element) => [element.uuid, element]));
const elementSector = new Map();

function visit(node, sector = -1) {
    if (typeof node === "string") {
        elementSector.set(node, sector);
        return;
    }
    const group = groups.get(node.uuid);
    if (!group) {
        throw new Error(`missing group ${node.uuid}`);
    }
    const match = /^sector_(\d\d)$/.exec(group.name);
    const childSector = match ? Number(match[1]) - 1 : sector;
    for (const child of node.children ?? []) {
        visit(child, childSector);
    }
}
for (const node of model.outliner) {
    visit(node);
}

const expectedAnimations = new Map([
    ["animation.glider.opening", 0.8],
    ["animation.glider.closing", 0.8],
    ["animation.glider.closed", 1.0],
]);
for (const [name, length] of expectedAnimations) {
    const animation = model.animations.find((candidate) => candidate.name === name);
    if (!animation || animation.length !== length) {
        throw new Error(`missing or changed animation ${name}`);
    }
}

const textureNames = model.textures.map((texture) => texture.name);
fs.mkdirSync(textureOutput, {recursive: true});
for (const texture of model.textures) {
    const match = /^data:image\/png;base64,(.+)$/.exec(texture.source ?? "");
    if (!match) {
        throw new Error(`texture ${texture.name} is not an embedded PNG`);
    }
    fs.writeFileSync(path.join(textureOutput, `${texture.name}.png`), Buffer.from(match[1], "base64"));
}

function f(value) {
    const rounded = Math.abs(value) < 0.0000005 ? 0 : Math.round(value * 1000000) / 1000000;
    return `${Number.isInteger(rounded) ? rounded.toFixed(1) : rounded}F`;
}

function transformedRotation(rotation = [0, 0, 0]) {
    return [-rotation[0], rotation[1], -rotation[2]];
}

function cubeLine(element) {
    const rotation = transformedRotation(element.rotation);
    const textureIds = [...new Set(Object.values(element.faces).map((face) => face.texture))];
    if (textureIds.length !== 1 || Object.keys(element.faces).length !== 6) {
        throw new Error(`cube ${element.name} must have six faces sharing one texture`);
    }
    return `        new Cube(${elementSector.get(element.uuid) ?? -1}, ${textureIds[0]}, `
        + `${[...element.from, ...element.to, ...element.origin, ...rotation].map(f).join(", ")})`;
}

function meshLine(element) {
    const vertexEntries = Object.entries(element.vertices);
    const vertexIndices = new Map(vertexEntries.map(([uuid], index) => [uuid, index]));
    const vertices = vertexEntries.flatMap(([, vertex]) => [vertex[0], -vertex[1], vertex[2]]);
    const faces = Object.values(element.faces).map((face) => {
        // Reflecting Y changes handedness. Reverse the face and its UVs together so
        // the winding and lighting normals still point out of the canopy.
        const corners = [...face.vertices].reverse();
        const indices = corners.map((uuid) => vertexIndices.get(uuid));
        if (indices.some((index) => index === undefined) || (indices.length !== 3 && indices.length !== 4)) {
            throw new Error(`unsupported face in mesh ${element.name}`);
        }
        while (indices.length < 4) {
            indices.push(indices[indices.length - 1]);
        }
        const uv = corners.flatMap((uuid) => face.uv[uuid].map((value) => value / 16));
        while (uv.length < 8) {
            uv.push(uv[uv.length - 2], uv[uv.length - 1]);
        }

        const a = element.vertices[corners[0]];
        const b = element.vertices[corners[1]];
        const c = element.vertices[corners[2]];
        const ta = [a[0], -a[1], a[2]];
        const tb = [b[0], -b[1], b[2]];
        const tc = [c[0], -c[1], c[2]];
        const ab = tb.map((value, index) => value - ta[index]);
        const ac = tc.map((value, index) => value - ta[index]);
        const normal = [
            ab[1] * ac[2] - ab[2] * ac[1],
            ab[2] * ac[0] - ab[0] * ac[2],
            ab[0] * ac[1] - ab[1] * ac[0],
        ];
        const length = Math.hypot(...normal);
        const normalized = normal.map((value) => value / length);
        return `new Face(${face.texture}, new int[] {${indices.join(", ")}}, `
            + `new float[] {${uv.map(f).join(", ")}}, ${normalized.map(f).join(", ")})`;
    });
    const rotation = transformedRotation(element.rotation);
    return `        new Mesh(${elementSector.get(element.uuid) ?? -1}, `
        + `${[...element.origin, ...rotation].map(f).join(", ")}, `
        + `new float[] {${vertices.map(f).join(", ")}},\n`
        + `                new Face[] {${faces.join(", ")}})`;
}

const cubes = model.elements.filter((element) => element.from).map(cubeLine);
const meshes = model.elements.filter((element) => element.vertices).map(meshLine);

const source = `package com.bilal.practicemod;

// Generated from src/main/blockbench/mechanical_octagonal_glider.bbmodel by
// tools/generate_umbrella_glider_geometry.mjs. The generated data preserves all source cuboids,
// free-mesh canopy faces, pivots, rotations, UVs, texture assignments, and animation bone routing.
final class UmbrellaGliderGeometry {
    record Cube(int sector, int texture, float fromX, float fromY, float fromZ,
            float toX, float toY, float toZ, float pivotX, float pivotY, float pivotZ,
            float rotationX, float rotationY, float rotationZ) {
    }

    record Face(int texture, int[] vertices, float[] uv, float normalX, float normalY, float normalZ) {
    }

    record Mesh(int sector, float pivotX, float pivotY, float pivotZ,
            float rotationX, float rotationY, float rotationZ, float[] vertices, Face[] faces) {
    }

    static final String[] TEXTURES = {${textureNames.map((name) => `"${name}"`).join(", ")}};

    static final Cube[] CUBES = {
${cubes.join(",\n")}
    };

    static final Mesh[] MESHES = {
${meshes.join(",\n")}
    };

    private UmbrellaGliderGeometry() {
    }
}
`;

process.stdout.write(source);
