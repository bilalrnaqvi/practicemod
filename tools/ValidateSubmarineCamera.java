package com.bilal.practicemod;

import java.util.*;

/** Exact screen projection of the exported model, independent of Minecraft/GL. */
public class ValidateSubmarineCamera {
    static final double[][] POINTS;
    static final double[] CENTER = new double[3];
    static {
        var points=new ArrayList<double[]>();
        double[] lo={1e9,1e9,1e9},hi={-1e9,-1e9,-1e9};
        for(float[] cube:DeepseekerGeometry.CUBES) for(int i=0;i<cube.length;i+=8) {
            double[] p={cube[i],cube[i+1],cube[i+2]}; points.add(p);
            for(int a=0;a<3;a++){lo[a]=Math.min(lo[a],p[a]);hi[a]=Math.max(hi[a],p[a]);}
        }
        POINTS=points.toArray(double[][]::new);
        for(int a=0;a<3;a++)CENTER[a]=(lo[a]+hi[a])/2;
    }
    static double[] forward(double yaw,double pitch) {
        double y=Math.toRadians(yaw),p=Math.toRadians(pitch);
        return new double[]{Math.sin(y)*Math.cos(p),-Math.sin(p),-Math.cos(y)*Math.cos(p)};
    }
    static double[] right(double yaw) {return new double[]{Math.cos(Math.toRadians(yaw)),0,Math.sin(Math.toRadians(yaw))};}
    static double[] cross(double[] a,double[] b) {return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    static double dot(double[] a,double[] b) {return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    static double[][] view(double yaw,double pitch,double hullPitch) {
        double[] f=forward(yaw,pitch),r=right(yaw),u=cross(r,f);
        double a=Math.toRadians(hullPitch),c=Math.cos(a),s=Math.sin(a);
        double[][] result=new double[POINTS.length][3];
        for(int i=0;i<POINTS.length;i++) {
            double[] p=POINTS[i];double x=p[0]-CENTER[0],y=p[1]-CENTER[1],z=p[2]-CENTER[2];
            double[] v={x,c*y-s*z,s*y+c*z};result[i]=new double[]{dot(v,r),dot(v,u),dot(v,f)};
        }
        return result;
    }
    public static void main(String[] args) {
        int checks=0, widthLimited=0;
        double minimum=1,maximum=0;
        for(double aspect:new double[]{4./3,16./9,21./9}) for(double fov:new double[]{45,60,70,90,110})
        for(int yaw=-180;yaw<=180;yaw+=30) for(int pitch:new int[]{-80,-60,-35,0,18,45,70}) for(int trim:new int[]{-24,0,24}) {
            double[][] points=view(yaw,pitch,trim);var fit=SubmarineCameraRig.fit(points,fov,aspect);
            double d=fit.distance(),lift=fit.lift(d),tan=fit.tangent();
            double chase=SubmarineCameraRig.chaseDistance(fit);
            if(Math.abs(chase-Math.max(fit.minimumDistance()+2,d*.95))>1e-9)
                throw new AssertionError("Expected 5% closer with hull clearance");
            for(double[] point:points) {
                double depth=chase+point[2];
                if(depth<1 || Math.abs(point[0]/depth/tan/aspect)>=1
                        || Math.abs((point[1]-fit.lift(chase))/depth/tan)>=1)
                    throw new AssertionError("Closer chase camera clips the hull");
            }
            double low=1e9,high=-1e9,width=0;
            for(double[] p:points) {
                if(d+p[2]<.999)throw new AssertionError("Camera entered the mesh");
                double y=(p[1]-lift)/(d+p[2])/tan;
                low=Math.min(low,y);high=Math.max(high,y);
                width=Math.max(width,Math.abs(p[0]/(d+p[2])/tan/aspect));
            }
            double height=(high-low)/2;
            if(height>.325001 || width>.700001 || low<-.565001 || high>.085001)throw new AssertionError("Framing overflow");
            if(height<.30){widthLimited++;if(width<.6999)throw new AssertionError("Unexpected undersized framing "+height);}
            minimum=Math.min(minimum,height);maximum=Math.max(maximum,height);checks++;
        }
        double a=10,b=10;
        for(int i=0;i<30;i++)a=SubmarineCameraRig.ease(a,30,1./30);
        for(int i=0;i<144;i++)b=SubmarineCameraRig.ease(b,30,1./144);
        if(Math.abs(a-b)>1e-8)throw new AssertionError("Frame-dependent easing");
        if(!SubmarineCameraRig.cockpitFallback(false,5,5) || !SubmarineCameraRig.cockpitFallback(true,6,5)
                || SubmarineCameraRig.cockpitFallback(true,8,5))throw new AssertionError("Fallback hysteresis");
        System.out.printf("PASS: %,d framing poses; height %.2f–%.2f%% (%d narrow/wide-side cases limited by 70%% width)%n",checks,100*minimum,100*maximum,widthLimited);
        System.out.println("PASS: frame-rate-independent easing and tight-space fallback hysteresis");
        for(int yaw:new int[]{0,45,90,180}) {
            var fit=SubmarineCameraRig.fit(view(yaw,18,0),60,16./9);
            System.out.printf("Orbit %d: %.2f-block boom, %.2f-block lift%n",yaw,fit.distance(),fit.lift(fit.distance()));
        }
    }
}
