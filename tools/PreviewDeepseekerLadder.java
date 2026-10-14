package com.bilal.practicemod;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
public class PreviewDeepseekerLadder {
    public static void main(String[] args) throws Exception {
        PreviewDeepseeker.texture=ImageIO.read(new File("src/main/resources/assets/practicemod/textures/entity/deepseeker.png"));
        var out=new BufferedImage(1440,780,BufferedImage.TYPE_INT_RGB);
        var g=out.createGraphics();g.setColor(new Color(0x152536));g.fillRect(0,0,1440,780);
        g.drawImage(PreviewDeepseeker.render(new double[]{220,125,-140},new double[]{0,45,-35},710,720,660),0,70,null);
        g.drawImage(PreviewDeepseeker.render(new double[]{117,100,-56},new double[]{30,58,-27},650,720,660),720,70,null);
        g.setColor(new Color(0xF1D087));g.setFont(new Font("SansSerif",Font.BOLD,24));
        g.drawString("PAINTED BOARDING GUIDE",30,38);
        g.setColor(Color.WHITE);g.setFont(new Font("SansSerif",Font.PLAIN,16));
        g.drawString("Small ochre step marks • flat against the existing hull",30,63);
        g.drawString("Overall placement",30,753);g.drawString("Close-up: faint, broken stencil marks",750,753);
        g.dispose();ImageIO.write(out,"png",new File("build/deepseeker-ladder-concept.png"));
    }
}
