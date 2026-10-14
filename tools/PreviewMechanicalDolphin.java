package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.zip.ZipFile;

/** Actual exported runtime mesh and full-size vanilla pilot, including translucent glass. */
public class PreviewMechanicalDolphin {
    static VertexConsumer capture(List<float[]> vertices, boolean player) {
        return new VertexConsumer() {
            float[] v;
            public VertexConsumer addVertex(float x,float y,float z) { v=new float[]{x,y,z,0,0,0,0,0}; vertices.add(v);return this; }
            public VertexConsumer setColor(int r,int g,int b,int a) {return this;}
            public VertexConsumer setUv(float u,float w) {v[3]=player?(1024+u*64)/1088:u*1024/1088;v[4]=player?w*64/1024:w;return this;}
            public VertexConsumer setUv1(int u,int v) {return this;}
            public VertexConsumer setUv2(int u,int v) {return this;}
            public VertexConsumer setNormal(float x,float y,float z) {v[5]=x;v[6]=y;v[7]=z;return this;}
        };
    }
    static List<float[]> pilot(boolean slim,List<String> report) {
        var root=PlayerModel.createMesh(CubeDeformation.NONE,slim).getRoot().bake(64,64);
        var model=new PlayerModel<>(root,slim);
        var original=model.body.storePose();
        MechanicalDolphinRiderPose.prone(model);
        var p=new PoseStack();p.translate(0,1.05-1.62,.9);
        p.mulPose(Axis.YP.rotationDegrees(180));p.scale(-.9375F,-.9375F,.9375F);p.translate(0,-1.501,0);
        var result=new ArrayList<float[]>();
        root.visit(p,(pose,path,index,cube)->{
            if(path.equals("/ear")||path.equals("/cloak"))return;
            var part=new ArrayList<float[]>();cube.compile(pose,capture(part,true),0,0,-1);result.addAll(part);
            for(var v:part)report.add((slim?"slim":"wide")+path+","+v[0]+","+v[1]+","+v[2]);
        });
        MechanicalDolphinRiderPose.restore(model);
        if(model.body.y!=original.y||model.body.z!=original.z||model.body.xRot!=original.xRot)
            throw new AssertionError("Prone pose leaks into the next rendered player");
        return result;
    }
    static float[][] quads(List<float[]> list) {
        var q=new float[list.size()/4][32];
        for(int i=0;i<list.size();i++)System.arraycopy(list.get(i),0,q[i/4],i%4*8,8);
        return q;
    }
    public static void main(String[] args)throws Exception {
        var opaque=new ArrayList<float[]>();var glass=new ArrayList<float[]>();
        MechanicalDolphinModel.renderOpaque(new PoseStack(),capture(opaque,false),0,0,0,0);
        MechanicalDolphinModel.renderGlass(new PoseStack(),capture(glass,false),0,0);
        var report=new ArrayList<String>();var rider=pilot(false,report);pilot(true,report);
        Files.write(Path.of("build/dolphin-rider-points.csv"),report);
        var atlas=new BufferedImage(1088,1024,BufferedImage.TYPE_INT_ARGB);var ag=atlas.createGraphics();
        ag.drawImage(ImageIO.read(Path.of("src/main/resources/assets/practicemod/textures/entity/mechanical_dolphin.png").toFile()),0,0,null);
        try(var jar=new ZipFile("build/moddev/artifacts/neoforge-21.1.235-client-extra-aka-minecraft-resources.jar")) {
            ag.drawImage(ImageIO.read(jar.getInputStream(jar.getEntry("assets/minecraft/textures/entity/player/wide/steve.png"))),1024,0,null);
        }
        ag.dispose();PreviewDeepseeker.texture=atlas;
        var all=new ArrayList<>(opaque);all.addAll(rider);int glassStart=all.size()/4;all.addAll(glass);
        var q=quads(all);var flags=new boolean[q.length];Arrays.fill(flags,glassStart,flags.length,true);
        var out=new BufferedImage(1600,1000,BufferedImage.TYPE_INT_RGB);var g=out.createGraphics();
        double[][] eyes={{85,62,110},{180,24,0},{0,29,115},{-90,53,-115}};
        for(int i=0;i<4;i++)g.drawImage(PreviewDeepseeker.renderGeometry(eyes[i],new double[]{0,12,-6},i==1?1300:820,800,500,q,flags),i%2*800,i/2*500,null);
        g.dispose();Files.createDirectories(Path.of("output/mechanical-dolphin"));ImageIO.write(out,"png",Path.of("output/mechanical-dolphin/runtime-preview.png").toFile());
        System.out.println("Runtime preview: "+opaque.size()/4+" solid quads, "+glass.size()/4+" glass quads; wide/slim pose restoration passed.");
    }
}
