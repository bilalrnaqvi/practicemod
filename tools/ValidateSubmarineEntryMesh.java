package com.bilal.practicemod;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import java.util.*;
import java.nio.file.*;

/** Sweeps actual baked skin/clothing cubes against every exported hull triangle, including moving parts. */
public class ValidateSubmarineEntryMesh {
    static double[] sub(double[] a,double[] b){return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    static double[] cross(double[] a,double[] b){return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    static double[] norm(double[] a){double d=Math.sqrt(dot(a,a));return new double[]{a[0]/d,a[1]/d,a[2]/d};}
    static boolean separated(double[][] triangle,double[] axis,double[] extent){
        double len=Math.sqrt(dot(axis,axis));if(len<1e-7)return false;
        double lo=1e9,hi=-1e9;for(var p:triangle){double q=dot(p,axis);lo=Math.min(lo,q);hi=Math.max(hi,q);}
        double r=Math.abs(axis[0])*extent[0]+Math.abs(axis[1])*extent[1]+Math.abs(axis[2])*extent[2];
        return lo>=r-.008*len || hi<=-r+.008*len;
    }
    static boolean hits(List<double[]> points,float[] cube){
        double[] center=new double[3];for(var p:points)for(int i=0;i<3;i++)center[i]+=p[i]/points.size();
        double[][] axes={norm(sub(points.get(1),points.get(0))),norm(sub(points.get(3),points.get(0))),null};
        axes[2]=norm(cross(axes[0],axes[1]));double[] extent=new double[3];
        for(var p:points)for(int i=0;i<3;i++)extent[i]=Math.max(extent[i],Math.abs(dot(sub(p,center),axes[i])));
        for(int face=0;face<cube.length;face+=32)for(int[] indices:new int[][]{{0,8,16},{0,16,24}}){
            double[][] tri=new double[3][3];for(int k=0;k<3;k++){
                int j=face+indices[k];double[] d={cube[j]-center[0],cube[j+1]-center[1],cube[j+2]-center[2]};
                for(int i=0;i<3;i++)tri[k][i]=dot(d,axes[i]);
            }
            double[][] unit={{1,0,0},{0,1,0},{0,0,1}};boolean apart=false;
            for(var u:unit)apart|=separated(tri,u,extent);if(apart)continue;
            double[][] edges={sub(tri[1],tri[0]),sub(tri[2],tri[1]),sub(tri[0],tri[2])};
            if(separated(tri,cross(edges[0],edges[1]),extent))continue;
            for(var e:edges)for(var u:unit)apart|=separated(tri,cross(e,u),extent);
            if(!apart)return true;
        }return false;
    }
    static float[][] hull(float t){
        float[][] result=new float[DeepseekerGeometry.CUBES.length][];
        for(int c=0;c<result.length;c++){
            result[c]=DeepseekerGeometry.CUBES[c].clone();int part=DeepseekerGeometry.ENTRY_PARTS[c];
            double a=Math.toRadians(part==1?SubmarineEntryAnimation.hatch(t):SubmarineEntryAnimation.chair(t));
            for(int i=0;i<result[c].length;i+=8){float[] v=result[c];
                if(part==1){double y=v[i+1]-DeepseekerCabinRig.HATCH_Y,z=v[i+2]-DeepseekerCabinRig.HATCH_Z;v[i+1]=(float)(DeepseekerCabinRig.HATCH_Y+y*Math.cos(a)-z*Math.sin(a));v[i+2]=(float)(DeepseekerCabinRig.HATCH_Z+y*Math.sin(a)+z*Math.cos(a));}
                if(part==2)v[i]+=SubmarineEntryAnimation.door(t)*1.25F;
                if(part==3){double x=v[i],z=v[i+2]-DeepseekerPilot.Z;v[i]=(float)(x*Math.cos(a)+z*Math.sin(a));v[i+2]=(float)(DeepseekerPilot.Z-x*Math.sin(a)+z*Math.cos(a));}
            }
        }return result;
    }
    public static void main(String[] args)throws Exception{
        var reports=new TreeMap<String,int[]>();
        for(boolean slim:new boolean[]{false,true}){
            var root=PlayerModel.createMesh(CubeDeformation.NONE,slim).getRoot().bake(64,64);
            var model=new PlayerModel<>(root,slim);
            SubmarinePilotPose.boarding(model,110);
            SubmarinePilotPose.restore(model);
            if(model.rightArm.yScale!=1 || model.leftArm.yScale!=1
                    || model.rightSleeve.yScale!=1 || model.leftSleeve.yScale!=1 || model.body.z!=0 || model.body.xRot!=0)
                throw new AssertionError("Boarding scale leaked into another player's render");
            for(int tick=0;tick<=SubmarineEntryAnimation.DURATION*2;tick++){
                final int sample=tick; final float t=tick*.5F;SubmarinePilotPose.boarding(model,t);
                var eye=SubmarineEntryAnimation.eye(SubmarineEntryAnimation.LADDER_START,t);
                var pose=new PoseStack();pose.translate(eye.x,eye.y-1.62,eye.z);
                pose.mulPose(Axis.YP.rotationDegrees(SubmarineEntryAnimation.heading(SubmarineEntryAnimation.LADDER_START,t)));
                pose.scale(-.9375F,-.9375F,.9375F);pose.translate(0,-1.501,0);
                var geometry=hull(t);
                root.visit(pose,(p,path,index,cube)->{
                    if(path.equals("/ear")||path.equals("/cloak"))return;
                    var points=new ArrayList<double[]>();
                    cube.compile(p,new VertexConsumer(){
                        public VertexConsumer addVertex(float x,float y,float z){points.add(new double[]{x,y,z});return this;}
                        public VertexConsumer setColor(int r,int g,int b,int a){return this;}public VertexConsumer setUv(float u,float v){return this;}
                        public VertexConsumer setUv1(int u,int v){return this;}public VertexConsumer setUv2(int u,int v){return this;}
                        public VertexConsumer setNormal(float x,float y,float z){return this;}
                    },0,0,-1);
                    double[] lo={1e9,1e9,1e9},hi={-1e9,-1e9,-1e9};for(var v:points)for(int i=0;i<3;i++){lo[i]=Math.min(lo[i],v[i]);hi[i]=Math.max(hi[i],v[i]);}
                    for(int c=0;c<geometry.length;c++){
                        if(DeepseekerGeometry.NAMES[c].startsWith("boarding_paint"))continue;
                        float[] mesh=geometry[c];double[] a={1e9,1e9,1e9},b={-1e9,-1e9,-1e9};
                        for(int j=0;j<mesh.length;j+=8)for(int i=0;i<3;i++){a[i]=Math.min(a[i],mesh[j+i]);b[i]=Math.max(b[i],mesh[j+i]);}
                        if(hi[0]<a[0]||hi[1]<a[1]||hi[2]<a[2]||lo[0]>b[0]||lo[1]>b[1]||lo[2]>b[2])continue;
                        if(hits(points,mesh)){
                            String key=(slim?"slim":"wide")+path+" -> "+DeepseekerGeometry.NAMES[c];
                            var range=reports.computeIfAbsent(key,k->new int[]{sample,sample,0});range[1]=sample;range[2]++;
                        }
                    }
                });
            }
        }
        // Empty and occupied swivel travel must also keep the chair itself clear of fixed furniture.
        for(int sample=300;sample<=544;sample++) {
            float t=sample*.5F;var geometry=hull(t);
            for(int c=0;c<geometry.length;c++) {
                if(DeepseekerGeometry.ENTRY_PARTS[c]!=3)continue;
                var points=new ArrayList<double[]>();var mesh=geometry[c];
                double[] lo={1e9,1e9,1e9},hi={-1e9,-1e9,-1e9};
                for(int j=0;j<mesh.length;j+=8) {
                    points.add(new double[]{mesh[j],mesh[j+1],mesh[j+2]});
                    for(int k=0;k<3;k++){lo[k]=Math.min(lo[k],mesh[j+k]);hi[k]=Math.max(hi[k],mesh[j+k]);}
                }
                for(int other=0;other<geometry.length;other++) {
                    String name=DeepseekerGeometry.NAMES[other];
                    if(DeepseekerGeometry.ENTRY_PARTS[other]==3 || name.equals("seat_foot") || name.equals("seat_suspension"))continue;
                    float[] target=geometry[other];double[] a={1e9,1e9,1e9},b={-1e9,-1e9,-1e9};
                    for(int j=0;j<target.length;j+=8)for(int k=0;k<3;k++){a[k]=Math.min(a[k],target[j+k]);b[k]=Math.max(b[k],target[j+k]);}
                    if(hi[0]<a[0]||hi[1]<a[1]||hi[2]<a[2]||lo[0]>b[0]||lo[1]>b[1]||lo[2]>b[2])continue;
                    if(hits(points,target)) {
                        String key="chair/"+DeepseekerGeometry.NAMES[c]+" -> "+name;
                        int[] range=reports.get(key);
                        if(range==null){range=new int[]{sample,sample,0};reports.put(key,range);}
                        range[1]=sample;range[2]++;
                    }
                }
            }
        }
        var lines=new ArrayList<String>();reports.forEach((k,v)->lines.add(k+" ticks "+v[0]/2.0+".."+v[1]/2.0+" ("+v[2]+" frames)"));
        Files.write(Path.of("build/boarding-clearance.txt"),lines);lines.forEach(System.out::println);
        System.out.println("Swept 1,130 full player poses; intersecting part pairs: "+reports.size());
        if(!reports.isEmpty())System.exit(1);
    }
}
