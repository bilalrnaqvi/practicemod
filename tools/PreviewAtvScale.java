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

/** Actual ATV and vanilla Steve meshes at one world scale; no game window required. */
public final class PreviewAtvScale {
    static final List<float[]> vehicle = new ArrayList<>(), standing = new ArrayList<>(), seated = new ArrayList<>();
    static final Map<String, List<float[]>> riderParts = new LinkedHashMap<>();

    static VertexConsumer capture(List<float[]> vertices, boolean player) {
        return new VertexConsumer() {
            float[] v;
            public VertexConsumer addVertex(float x, float y, float z) {
                v = new float[]{x,y,z,0,0,0,0,0}; vertices.add(v); return this;
            }
            public VertexConsumer setColor(int r,int g,int b,int a) { return this; }
            public VertexConsumer setUv(float u,float w) {
                v[3] = player ? (1024+u*64)/1088 : u*1024/1088;
                v[4] = player ? w*64/1024 : w; return this;
            }
            public VertexConsumer setUv1(int u,int v) { return this; }
            public VertexConsumer setUv2(int u,int v) { return this; }
            public VertexConsumer setNormal(float x,float y,float z) { v[5]=x;v[6]=y;v[7]=z;return this; }
        };
    }

    static List<float[]> player(boolean sitting, boolean slim, int seat) {
        var root = PlayerModel.createMesh(CubeDeformation.NONE, slim).getRoot().bake(64,64);
        var model = new PlayerModel<>(root, slim);
        if (sitting) AtvRiderPose.seatedLimbs(model);
        var pose = new PoseStack();
        pose.translate(sitting ? (seat == 0 ? -AtvEntity.SEAT_X : AtvEntity.SEAT_X) : 2.1, sitting ? AtvEntity.SEAT_HEIGHT-AtvEntity.HIP_OFFSET : 0, sitting ? AtvEntity.SEAT_Z : 0);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(-.9375F,-.9375F,.9375F);
        pose.translate(0,-1.501,0);
        List<float[]> result = new ArrayList<>();
        root.visit(pose, (p,path,index,cube) -> {
            if (path.equals("/ear") || path.equals("/cloak")) return;
            var part = new ArrayList<float[]>();
            cube.compile(p, capture(part,true), 0,0,-1);
            result.addAll(part);
            if (sitting) riderParts.computeIfAbsent((slim ? "slim" : "wide")+(seat == 0 ? "-driver" : "-passenger")+path, k -> new ArrayList<>()).addAll(part);
        });
        return result;
    }

    static double[] bounds(List<float[]> points) {
        double[] b={1e9,1e9,1e9,-1e9,-1e9,-1e9};
        for (var p:points) for(int i=0;i<3;i++){b[i]=Math.min(b[i],p[i]);b[i+3]=Math.max(b[i+3],p[i]);}
        return b;
    }

    @SafeVarargs static float[][] quads(List<float[]>... lists) {
        var all = new ArrayList<float[]>();
        for (var list:lists) all.addAll(list);
        var q=new float[all.size()/4][32];
        for (int i=0;i<all.size();i++) System.arraycopy(all.get(i),0,q[i/4],(i%4)*8,8);
        return q;
    }

    static BufferedImage view(double[] eye, double[] target, double focal, int width, int height, float[][] q) {
        return PreviewJetski.renderGeometry(eye,target,focal,width,height,q,new boolean[q.length]);
    }

    public static void main(String[] args) throws Exception {
        AtvModel.render(new PoseStack(),capture(vehicle,false),0,0,null,0);
        standing.addAll(player(false,false,0));
        for (int seat=0;seat<2;seat++) { seated.addAll(player(true,false,seat)); player(true,true,seat); }
        System.out.println("ATV bounds "+Arrays.toString(bounds(vehicle)));
        for(var entry:riderParts.entrySet()) System.out.println(entry.getKey()+" "+Arrays.toString(bounds(entry.getValue())));
        var atlas = new BufferedImage(1088,1024,BufferedImage.TYPE_INT_ARGB);
        var ag=atlas.createGraphics();
        ag.drawImage(ImageIO.read(Path.of("src/main/resources/assets/practicemod/textures/entity/crag.png").toFile()),0,0,null);
        try (var jar=new ZipFile("build/moddev/artifacts/neoforge-21.1.235-client-extra-aka-minecraft-resources.jar")) {
            ag.drawImage(ImageIO.read(jar.getInputStream(jar.getEntry("assets/minecraft/textures/entity/player/wide/steve.png"))),1024,0,null);
        }
        ag.dispose(); PreviewJetski.texture=atlas; PreviewJetski.ambient=.84;
        var output=new BufferedImage(1600,1150,BufferedImage.TYPE_INT_RGB); var g=output.createGraphics();
        g.setColor(new Color(0x152536));g.fillRect(0,0,1600,1150);
        g.drawImage(view(new double[]{64,39,77},new double[]{10,17,0},850,1000,680,quads(vehicle,standing)),0,65,null);
        g.drawImage(view(new double[]{8,33,88},new double[]{0,18,0},710,600,600,quads(vehicle,seated)),1000,100,null);
        g.drawImage(view(new double[]{200,22,0},new double[]{0,18,0},1750,900,450,quads(vehicle,seated)),0,700,null);
        g.setColor(new Color(0xe4eee1));g.setFont(new Font("SansSerif",Font.BOLD,27));
        g.drawString("CRAG / compact two-seat buggy",35,43);
        g.setFont(new Font("SansSerif",Font.PLAIN,20));
        g.drawString("Standing Steve — same world scale",35,86);
        g.drawString("Driver + passenger",1040,87);
        g.drawString("Side profile / longer chassis",35,737);
        g.setFont(new Font("SansSerif",Font.PLAIN,23));
        double[] b=bounds(vehicle);
        g.drawString(String.format(Locale.ROOT,"%.2f blocks long × %.2f wide",b[5]-b[2],b[3]-b[0]),1000,817);
        g.drawString(String.format(Locale.ROOT,"Cage top: %.2f blocks",b[4]),1000,858);
        g.drawString("Narrower stance / two full-size seats",1000,925);
        g.drawString("Actual mod + vanilla player geometry",1000,1015);
        g.drawString("Software preview, not an in-game capture",1000,1048);
        g.dispose();
        Files.createDirectories(Path.of("output/crag"));
        ImageIO.write(output,"png",Path.of("output/crag/two-seat-preview.png").toFile());
        // Export the posed cubes for the geometry clearance audit.
        var lines=new ArrayList<String>();
        for(var entry:riderParts.entrySet()) for(var p:entry.getValue())
            lines.add(entry.getKey()+","+p[0]+","+p[1]+","+p[2]+","+p[5]+","+p[6]+","+p[7]);
        Files.write(Path.of("build/crag-rider-points.csv"),lines);
    }
}
