package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import javax.imageio.ImageIO;

/** Captures the actual textured CRAG renderer, then uses the existing software rasterizer. */
public class PreviewAtv {
    public static void main(String[] args) throws Exception {
        // Neutral daylight makes the authored paint readable without the old muddy preview shadows.
        PreviewJetski.ambient = .84;
        var vertices = new ArrayList<float[]>();

        AtvModel.render(new PoseStack(), new VertexConsumer() {
            float[] current;
            public VertexConsumer addVertex(float x,float y,float z) {
                current=new float[]{x,y,z,0,0,0,0,0}; vertices.add(current); return this;
            }
            public VertexConsumer setColor(int r,int g,int b,int a) {
                return this;
            }
            public VertexConsumer setUv(float u,float v) { current[3]=u;current[4]=v;return this; }
            public VertexConsumer setUv1(int u,int v) { return this; }
            public VertexConsumer setUv2(int u,int v) { return this; }
            public VertexConsumer setNormal(float x,float y,float z) { current[5]=x;current[6]=y;current[7]=z;return this; }
        }, 0, 0, null, 0);
        PreviewJetski.texture=ImageIO.read(new File("src/main/resources/assets/practicemod/textures/entity/crag.png"));
        float[][] quads=new float[vertices.size()/4][32];
        for (int i=0;i<vertices.size();i++) {
            var v=vertices.get(i);
            System.arraycopy(v,0,quads[i/4],(i%4)*8,8);
        }
        var out=new BufferedImage(1200,1000,BufferedImage.TYPE_INT_RGB);var g=out.createGraphics();
        double[][] eyes={{40,32,50},{-40,29,-50},{52,19,0},{0,24,58}};
        for (int i=0;i<4;i++) g.drawImage(PreviewJetski.renderGeometry(eyes[i],new double[]{0,14,0},i==2 ? 550 : 665,600,500,
                quads,new boolean[quads.length]),i%2*600,i/2*500,null);
        g.dispose();new File("output/crag").mkdirs();ImageIO.write(out,"png",new File("output/crag/model-preview.png"));
        var detail = new BufferedImage(1500,1000,BufferedImage.TYPE_INT_RGB);
        var dg=detail.createGraphics();
        dg.setColor(new java.awt.Color(0x152536));dg.fillRect(0,0,1500,1000);
        dg.drawImage(PreviewJetski.renderGeometry(new double[]{43,31,53},new double[]{0,14,0},1250,1000,1000,
                quads,new boolean[quads.length]),0,0,null);
        dg.drawImage(PreviewJetski.renderGeometry(new double[]{-44,29,-53},new double[]{0,14,0},540,500,470,
                quads,new boolean[quads.length]),1000,30,null);
        dg.drawImage(PreviewJetski.renderGeometry(new double[]{58,18,0},new double[]{0,14,0},520,500,470,
                quads,new boolean[quads.length]),1000,500,null);
        dg.dispose();ImageIO.write(detail,"png",new File("output/crag/detail-preview.png"));
        System.out.println("Rendered " + quads.length + " actual model quads to output/crag/model-preview.png");
    }
}
