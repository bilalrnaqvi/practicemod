package com.bilal.practicemod;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
/** Uses the exported geometry to inspect the actual hinged hatch and sliding door. */
public class PreviewDeepseekerEntry {
    public static void main(String[] args) throws Exception {
        PreviewDeepseeker.texture=ImageIO.read(new File("src/main/resources/assets/practicemod/textures/entity/deepseeker.png"));
        var out=new BufferedImage(1200,540,BufferedImage.TYPE_INT_RGB);
        var g=out.createGraphics(); g.setColor(new Color(0x152536));g.fillRect(0,0,1200,540);
        for(int c=0;c<DeepseekerGeometry.CUBES.length;c++) {
            float[] v=DeepseekerGeometry.CUBES[c];
            int part=DeepseekerGeometry.ENTRY_PARTS[c];
            for(int i=0;i<v.length;i+=8) {
                if(part==1) {
                    double a=Math.toRadians(105),y=v[i+1]-87.9/16,z=v[i+2]+14.85/16;
                    v[i+1]=(float)(87.9/16+Math.cos(a)*y-Math.sin(a)*z);
                    v[i+2]=(float)(-14.85/16+Math.sin(a)*y+Math.cos(a)*z);
                } else if(part==2) v[i]+=1.25F;
            }
        }
        g.drawImage(PreviewDeepseeker.render(new double[]{90,160,-150},new double[]{0,70,-27},600),0,40,null);
        g.drawImage(PreviewDeepseeker.render(new double[]{0,48,-88},new double[]{0,43,-40},330),600,40,null);
        g.setColor(Color.WHITE);g.drawString("Hatch open (actual exported mesh)",20,25);
        g.drawString("Door open behind pilot chair",620,25);g.dispose();
        ImageIO.write(out,"png",new File("build/deepseeker-entry-preview.png"));
    }
}
