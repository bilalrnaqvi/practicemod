package com.bilal.practicemod;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Same-lens side profile plus forward cockpit and the sealed aft equipment face. */
public class PreviewDeepseekerCompact {
    public static void main(String[] args) throws Exception {
        PreviewDeepseeker.texture=ImageIO.read(new File("src/main/resources/assets/practicemod/textures/entity/deepseeker.png"));
        int w=900,h=570;
        var out=new BufferedImage(w*2,(h+40)*2,BufferedImage.TYPE_INT_RGB);
        var g=out.createGraphics();g.setColor(new Color(0x152536));g.fillRect(0,0,out.getWidth(),out.getHeight());
        double[] eye={DeepseekerPilot.X*16,DeepseekerPilot.Y*16,DeepseekerPilot.Z*16};
        double[][] eyes={{300,95,0},{160,120,-235},eye,eye};
        double[][] targets={{0,45,0},{0,45,-40},{0,39,-102},{0,52,-54}};
        String[] labels={"Side profile — fixed lens / scale", "Front quarter", "Pilot controls — unchanged", "Behind pilot — sealed machinery face"};
        for(int i=0;i<4;i++) {
            int x=(i%2)*w,y=(i/2)*(h+40);
            g.drawImage(PreviewDeepseeker.render(eyes[i],targets[i],i<2?700:450,w,h),x,y+40,null);
            g.setColor(Color.WHITE);g.drawString(labels[i],x+18,y+26);
        }
        g.dispose();ImageIO.write(out,"png",new File(args[0]));
    }
}
