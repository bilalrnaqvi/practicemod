package com.bilal.practicemod;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Closed machinery section rendered from the actual exported mesh and unchanged runtime atlas. */
public class PreviewDeepseekerCabin {
    public static void main(String[] args) throws Exception {
        PreviewDeepseeker.texture=ImageIO.read(new File("src/main/resources/assets/practicemod/textures/entity/deepseeker.png"));
        int w=1000,h=660;
        var out=new BufferedImage(w*2,h+40,BufferedImage.TYPE_INT_RGB);
        var g=out.createGraphics();g.setColor(new Color(0x152536));g.fillRect(0,0,out.getWidth(),out.getHeight());
        g.drawImage(PreviewDeepseeker.render(new double[]{24,61,-75},new double[]{0,51,-55},400,w,h),0,40,null);
        g.drawImage(PreviewDeepseeker.render(new double[]{0,61,-75},new double[]{0,52,-54},400,w,h),w,40,null);
        g.setColor(Color.WHITE);
        g.drawString("Compact machinery — over the pilot’s shoulder",18,26);
        g.drawString("Seated view aft — closed equipment, no rear room",w+18,26);
        g.dispose();ImageIO.write(out,"png",new File("build/deepseeker-rear-cabin-preview.png"));
    }
}
