package com.bilal.practicemod;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
public class PreviewDeepseekerRefinement {
 public static void main(String[] args) throws Exception {
  PreviewDeepseeker.texture=ImageIO.read(new File("src/main/resources/assets/practicemod/textures/entity/deepseeker.png"));
  var out=new BufferedImage(1600,1000,BufferedImage.TYPE_INT_RGB);var g=out.createGraphics();
  double[][] eyes={{190,142,-220},{130,109,22},{95,98,-135},{-130,109,22}};
  double[][] targets={{0,56,-34},{0,101,22},{26.4,73,-52.8},{0,101,22}};
  String[] labels={"Updated model","Tail: starboard / full 03","Recessed shoulder lamp","Tail: port / full 03"};
  for(int i=0;i<4;i++){int x=(i%2)*800,y=(i/2)*500;g.drawImage(PreviewDeepseeker.render(eyes[i],targets[i],i==0?730:i==2?1250:1250,800,500),x,y,null);g.setColor(Color.WHITE);g.drawString(labels[i],x+20,y+25);}
  g.dispose();ImageIO.write(out,"png",new File("build/deepseeker-refinement-preview.png"));
  ImageIO.write(PreviewDeepseeker.render(new double[]{95,98,-135},new double[]{26,76.6,-57.8},
          1650,1000,800),"png",new File("build/deepseeker-lamp-closeup.png"));
  var lamps=new BufferedImage(1600,800,BufferedImage.TYPE_INT_RGB);var lg=lamps.createGraphics();
  lg.drawImage(PreviewDeepseeker.render(new double[]{0,95,-235},new double[]{0,68,-60},1200,800,800),0,0,null);
  lg.drawImage(PreviewDeepseeker.render(new double[]{95,98,-135},new double[]{26,76,-60},1400,800,800),800,0,null);
  lg.setColor(Color.WHITE);lg.drawString("Front: both lamps face forward",20,30);lg.drawString("Rebuilt shoulder frame and tapered socket",820,30);
  lg.dispose();ImageIO.write(lamps,"png",new File("build/deepseeker-forward-lamps.png"));
 }
}
