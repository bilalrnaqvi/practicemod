package com.bilal.practicemod;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Screen-space framing preview using the same fit as the runtime camera. */
public class PreviewSubmarineCamera {
    public static void main(String[] args) throws Exception {
        PreviewDeepseeker.texture=ImageIO.read(new File("src/main/resources/assets/practicemod/textures/entity/deepseeker.png"));
        int w=960,h=540,header=36;double fov=60;
        var out=new BufferedImage(w*2,(h+header)*2,BufferedImage.TYPE_INT_RGB);var g=out.createGraphics();
        g.setColor(new Color(0x152536));g.fillRect(0,0,out.getWidth(),out.getHeight());
        int[] angles={0,45,90,180};String[] names={"Rear chase (default)","Rear quarter / mouse look","Side orbit","Front view (F5)"};
        for(int i=0;i<4;i++) {
            int yaw=angles[i];double[] f=ValidateSubmarineCamera.forward(yaw,18),r=ValidateSubmarineCamera.right(yaw),u=ValidateSubmarineCamera.cross(r,f);
            var fit=SubmarineCameraRig.fit(ValidateSubmarineCamera.view(yaw,18,0),fov,(double)w/h);
            double[] eye=new double[3],target=new double[3];
            for(int a=0;a<3;a++) {eye[a]=16*(ValidateSubmarineCamera.CENTER[a]+u[a]*fit.lift(fit.distance())-f[a]*fit.distance());target[a]=eye[a]+f[a]*16;}
            int x=(i%2)*w,y=(i/2)*(h+header);
            g.drawImage(PreviewDeepseeker.render(eye,target,h/(2*Math.tan(Math.toRadians(fov/2))),w,h),x,y+header,null);
            g.setColor(new Color(0x708b9b));
            for(double fraction:new double[]{.4575,.7825})g.drawLine(x,y+header+(int)(h*fraction),x+w,y+header+(int)(h*fraction));
            g.setColor(Color.WHITE);g.drawString(names[i]+" — 32.5% height target",x+16,y+23);
        }
        g.dispose();ImageIO.write(out,"png",new File("build/deepseeker-third-person-preview.png"));
    }
}
