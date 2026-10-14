package com.bilal.practicemod;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class PreviewSubmarineBoarding {
    static float[] color(int r,int g,int b){
        var im=PreviewDeepseeker.texture;double best=1e9;int bx=0,by=0;
        for(int y=0;y<im.getHeight();y+=2)for(int x=0;x<im.getWidth();x+=2){
            int p=im.getRGB(x,y);if((p>>>24)<250)continue;
            double d=Math.pow(((p>>16)&255)-r,2)+Math.pow(((p>>8)&255)-g,2)+Math.pow((p&255)-b,2);
            if(d<best){best=d;bx=x;by=y;}
        }return new float[]{(bx+.5F)/im.getWidth(),(by+.5F)/im.getHeight()};
    }
    public static void main(String[] args)throws Exception{
        PreviewDeepseeker.texture=ImageIO.read(new File("src/main/resources/assets/practicemod/textures/entity/deepseeker.png"));
        float[] skin=color(190,139,99),shirt=color(40,155,175),pants=color(45,60,91);
        int[] times={38,86,127,189,220,254};
        var out=new BufferedImage(1800,1080,BufferedImage.TYPE_INT_RGB);var g=out.createGraphics();
        g.setColor(new Color(0x152536));g.fillRect(0,0,1800,1080);
        var root=PlayerModel.createMesh(CubeDeformation.NONE,false).getRoot().bake(64,64);var model=new PlayerModel<>(root,false);
        for(int shot=0;shot<times.length;shot++){
            int t=times[shot];var meshes=new ArrayList<float[]>(Arrays.asList(ValidateSubmarineEntryMesh.hull(t)));
            SubmarinePilotPose.boarding(model,t);var eye=SubmarineEntryAnimation.eye(SubmarineEntryAnimation.LADDER_START,t);
            var pose=new PoseStack();pose.translate(eye.x,eye.y-1.62,eye.z);
            pose.mulPose(Axis.YP.rotationDegrees(SubmarineEntryAnimation.heading(SubmarineEntryAnimation.LADDER_START,t)));
            pose.scale(-.9375F,-.9375F,.9375F);pose.translate(0,-1.501,0);
            root.visit(pose,(p,path,index,cube)->{
                if(path.equals("/ear")||path.equals("/cloak"))return;
                var pts=new ArrayList<float[]>();float[] uv=path.contains("leg")||path.contains("pants")?pants:path.contains("body")||path.contains("jacket")?shirt:skin;
                cube.compile(p,new VertexConsumer(){
                    public VertexConsumer addVertex(float x,float y,float z){pts.add(new float[]{x,y,z,uv[0],uv[1],0,1,0});return this;}
                    public VertexConsumer setColor(int r,int g,int b,int a){return this;}public VertexConsumer setUv(float u,float v){return this;}
                    public VertexConsumer setUv1(int u,int v){return this;}public VertexConsumer setUv2(int u,int v){return this;}
                    public VertexConsumer setNormal(float x,float y,float z){var v=pts.getLast();v[5]=x;v[6]=y;v[7]=z;return this;}
                },0,0,-1);
                float[] data=new float[pts.size()*8];for(int i=0;i<pts.size();i++)System.arraycopy(pts.get(i),0,data,i*8,8);meshes.add(data);
            });
            boolean[] glass=Arrays.copyOf(DeepseekerGeometry.GLASS,meshes.size());
            double[] camera=t<112?new double[]{6.2,7.5,-5.8}:t<180?new double[]{0,3.75,-1.8}:new double[]{1.1,4.3,-6.15};
            for(int i=0;i<3;i++)camera[i]*=16;
            var im=PreviewDeepseeker.renderGeometry(camera,new double[]{eye.x*16,(eye.y-.6)*16,eye.z*16},420,600,500,meshes.toArray(float[][]::new),glass);
            int x=shot%3*600,y=shot/3*540;g.drawImage(im,x,y+40,null);g.setColor(Color.WHITE);g.drawString("Tick "+t,x+20,y+25);
        }
        g.dispose();ImageIO.write(out,"png",new File("build/submarine-boarding-poses.png"));
    }
}
