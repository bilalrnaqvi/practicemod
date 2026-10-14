package com.bilal.practicemod;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import java.nio.file.*;
import java.util.*;

/** Exports the actual Minecraft meshes, and optionally a migration/reference pose capture. */
public final class ExportBoardingPreview {
    public static void main(String[] args) throws Exception {
        var gson = new GsonBuilder().create();
        var scene = new LinkedHashMap<String,Object>();
        scene.put("hull", DeepseekerGeometry.CUBES);
        scene.put("names", DeepseekerGeometry.NAMES);
        scene.put("parts", DeepseekerGeometry.ENTRY_PARTS);
        scene.put("glass", DeepseekerGeometry.GLASS);
        scene.put("hatchPivot", new double[]{DeepseekerCabinRig.HATCH_X,DeepseekerCabinRig.HATCH_Y,DeepseekerCabinRig.HATCH_Z});
        scene.put("chairZ", DeepseekerPilot.Z);
        var meshes = new LinkedHashMap<String,Object>();
        for (boolean slim : new boolean[]{false,true}) {
            var root = PlayerModel.createMesh(CubeDeformation.NONE,slim).getRoot().bake(64,64);
            var bones = new LinkedHashMap<String,Object>();
            root.visit(new PoseStack(), (pose,path,index,cube) -> {
                if (path.equals("/ear") || path.equals("/cloak")) return;
                var vertices = new ArrayList<Float>();
                cube.compile(new PoseStack().last(), new VertexConsumer() {
                    public VertexConsumer addVertex(float x,float y,float z) { vertices.add(x);vertices.add(y);vertices.add(z);return this; }
                    public VertexConsumer setColor(int r,int g,int b,int a) { return this; }
                    public VertexConsumer setUv(float u,float v) { return this; }
                    public VertexConsumer setUv1(int u,int v) { return this; }
                    public VertexConsumer setUv2(int u,int v) { return this; }
                    public VertexConsumer setNormal(float x,float y,float z) { return this; }
                },0,0,-1);
                bones.put(path.substring(1),vertices);
            });
            meshes.put(slim ? "slim" : "wide",bones);
        }
        scene.put("player",meshes);
        Path out = Path.of("output/animation/boarding"); Files.createDirectories(out);
        Files.writeString(out.resolve("scene.json"),gson.toJson(scene));
        if (args.length == 0) return;
        var root = PlayerModel.createMesh(CubeDeformation.NONE,false).getRoot().bake(64,64);
        var model = new PlayerModel<>(root,false);
        var tracks = new LinkedHashMap<String,List<float[]>>();
        for (String name : List.of("eye","heading","hatch","door","chair","bend","camera_pitch",
                "body","head","right_arm","left_arm","right_leg","left_leg")) tracks.put(name,new ArrayList<>());
        for (int frame=0;frame<=SubmarineEntryAnimation.DURATION*2;frame++) {
            float t=frame*.5F;
            SubmarinePilotPose.boarding(model,t);
            var eye=SubmarineEntryAnimation.eye(SubmarineEntryAnimation.LADDER_START,t);
            tracks.get("eye").add(new float[]{t,(float)eye.x,(float)eye.y,(float)eye.z});
            tracks.get("heading").add(new float[]{t,SubmarineEntryAnimation.heading(SubmarineEntryAnimation.LADDER_START,t)});
            tracks.get("hatch").add(new float[]{t,SubmarineEntryAnimation.hatch(t)});
            tracks.get("door").add(new float[]{t,SubmarineEntryAnimation.door(t)});
            tracks.get("chair").add(new float[]{t,SubmarineEntryAnimation.chair(t)});
            tracks.get("bend").add(new float[]{t,SubmarineEntryAnimation.bend(t)});
            tracks.get("camera_pitch").add(new float[]{t,25*SubmarineEntryAnimation.ease(t,58,78)*(1-SubmarineEntryAnimation.ease(t,128,146))});
            for (String name : List.of("body","head","right_arm","left_arm","right_leg","left_leg")) {
                ModelPart p=root.getChild(name);
                tracks.get(name).add(new float[]{t,p.x,p.y,p.z,p.xRot,p.yRot,p.zRot,p.xScale,p.yScale,p.zScale});
            }
            SubmarinePilotPose.restore(model);
        }
        Files.writeString(Path.of(args[0]),gson.toJson(tracks));
    }
}
