package com.bilal.practicemod;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

/** Software model preview for geometry/UV inspection; not a substitute for in-game transparency QA. */
public class PreviewJetski {
    static BufferedImage texture;
    static double ambient = .68;
    static final int W=600,H=500;
    record V(double x,double y,double z,double u,double v) { }
    record T(V a,V b,V c,boolean glass,double shade) { }
    static double[] norm(double[] a) {double n=Math.sqrt(dot(a,a));return new double[]{a[0]/n,a[1]/n,a[2]/n};}
    static double dot(double[] a,double[] b) {return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    static double[] cross(double[] a,double[] b) {return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    static BufferedImage render(double[] eye,double[] target,double focal) {
        return render(eye,target,focal,W,H);
    }
    static BufferedImage render(double[] eye,double[] target,double focal,int W,int H) {
        return renderGeometry(eye,target,focal,W,H,JetskiGeometry.CUBES,new boolean[JetskiGeometry.CUBES.length]);
    }
    static BufferedImage renderGeometry(double[] eye,double[] target,double focal,int W,int H,float[][] geometry,boolean[] glass) {
        double[] forward=norm(new double[]{target[0]-eye[0],target[1]-eye[1],target[2]-eye[2]});
        double[] right=norm(cross(forward,new double[]{0,1,0})),up=cross(right,forward);
        var image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);int[] color=new int[W*H];Arrays.fill(color,0x152536);
        double[] depth=new double[W*H];Arrays.fill(depth,Double.POSITIVE_INFINITY);
        var faces=new ArrayList<T>();
        for(int c=0;c<geometry.length;c++) {
            float[] cube=geometry[c];
            for(int i=0;i<cube.length;i+=32) {
                V[] v=new V[4];boolean valid=true;
                for(int j=0;j<4;j++) {
                    int k=i+j*8;double[] p={cube[k]*16-eye[0],cube[k+1]*16-eye[1],cube[k+2]*16-eye[2]};
                    double z=dot(p,forward);if(z<=.1){valid=false;break;}
                    v[j]=new V(W/2+focal*dot(p,right)/z,H/2-focal*dot(p,up)/z,z,cube[k+3],cube[k+4]);
                }
                if(!valid)continue;
                double shade=ambient+(1-ambient)*Math.max(0,(cube[i+5]*.3+cube[i+6]*.7-cube[i+7]*.6));
                faces.add(new T(v[0],v[1],v[2],glass[c],shade));
                faces.add(new T(v[0],v[2],v[3],glass[c],shade));
            }
        }
        faces.sort(Comparator.<T>comparingInt(t->t.glass?1:0).thenComparingDouble(t->-(t.a.z+t.b.z+t.c.z)));
        for(T t:faces) {
            V a=t.a,b=t.b,c=t.c;double den=(b.y-c.y)*(a.x-c.x)+(c.x-b.x)*(a.y-c.y);
            if(Math.abs(den)<1e-9)continue;
            int x0=Math.max(0,(int)Math.floor(Math.min(a.x,Math.min(b.x,c.x)))),x1=Math.min(W-1,(int)Math.ceil(Math.max(a.x,Math.max(b.x,c.x))));
            int y0=Math.max(0,(int)Math.floor(Math.min(a.y,Math.min(b.y,c.y)))),y1=Math.min(H-1,(int)Math.ceil(Math.max(a.y,Math.max(b.y,c.y))));
            for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++) {
                double aa=((b.y-c.y)*(x+.5-c.x)+(c.x-b.x)*(y+.5-c.y))/den;
                double bb=((c.y-a.y)*(x+.5-c.x)+(a.x-c.x)*(y+.5-c.y))/den,cc=1-aa-bb;
                if(aa<0||bb<0||cc<0)continue;
                double z=1/(aa/a.z+bb/b.z+cc/c.z);int index=y*W+x;if(z>depth[index])continue;
                double u=z*(aa*a.u/a.z+bb*b.u/b.z+cc*c.u/c.z),v=z*(aa*a.v/a.z+bb*b.v/b.z+cc*c.v/c.z);
                int pixel=texture.getRGB(Math.max(0,Math.min(texture.getWidth()-1,(int)(u*texture.getWidth()))),Math.max(0,Math.min(texture.getHeight()-1,(int)(v*texture.getHeight()))));
                double alpha=t.glass?((pixel>>>24)&255)/255.:(((pixel>>>24)&255)>25?1:0);
                if(alpha==0)continue;
                int out=0;for(int shift:new int[]{16,8,0}) {
                    int channel=(int)Math.min(255,((pixel>>shift)&255)*t.shade*alpha+((color[index]>>shift)&255)*(1-alpha));out|=channel<<shift;
                }
                color[index]=out;if(!t.glass)depth[index]=z;
            }
        }
        image.setRGB(0,0,W,H,color,0,W);return image;
    }
    public static void main(String[] args)throws Exception {
        texture=ImageIO.read(new File("src/main/resources/assets/practicemod/textures/entity/compact_racer.png"));
        var out=new BufferedImage(W*2,H*2,BufferedImage.TYPE_INT_RGB);var g=out.createGraphics();
        double[][] eyes={{42,34,54},{-42,29,-52},{48,17,0},{0,17,60}};
        for(int i=0;i<4;i++) g.drawImage(render(eyes[i],new double[]{0,7,0},i<2?1050:650),i%2*W,i/2*H,null);
        g.dispose();ImageIO.write(out,"png",new File("build/compact-racer-preview.png"));
    }
}
