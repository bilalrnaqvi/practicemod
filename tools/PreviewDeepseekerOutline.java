package com.bilal.practicemod;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
public class PreviewDeepseekerOutline {
 public static void main(String[] args) throws Exception {
  String path="output/blockbench/deepseeker-outline-restored/";
  PreviewDeepseekerExpedition.path=path;
  var atlas=new BufferedImage(4096,2048,BufferedImage.TYPE_INT_ARGB);
  var a=atlas.createGraphics();
  a.drawImage(ImageIO.read(new File(path+"preview-texture-0.png")),0,0,null);
  a.drawImage(ImageIO.read(new File(path+"preview-texture-1.png")),2048,0,null);a.dispose();
  PreviewDeepseeker.texture=atlas;
  var model=PreviewDeepseekerExpedition.load("full");
  var sheet=new BufferedImage(1500,850,BufferedImage.TYPE_INT_RGB);var g=sheet.createGraphics();
  g.setColor(new Color(0x152536));g.fillRect(0,0,1500,850);
  g.drawImage(PreviewDeepseeker.renderGeometry(new double[]{190,140,-235},new double[]{0,46,-29},750,900,710,model.data(),model.glass()),0,90,null);
  g.drawImage(PreviewDeepseeker.renderGeometry(new double[]{115,125,-95},new double[]{13,76,-30},790,600,620,model.data(),model.glass()),900,155,null);
  g.setColor(Color.WHITE);g.setFont(new Font("SansSerif",Font.BOLD,28));g.drawString("DEEPSEEKER / RESTORED EXTERIOR",26,43);
  g.setFont(new Font("SansSerif",Font.PLAIN,20));g.drawString("Original low round hatch and painted ladder outline",26,75);
  g.drawString("Current cabin furniture and swivel chair retained",26,818);
  g.drawString("Hatch / painted guide detail",965,818);g.dispose();
  ImageIO.write(sheet,"png",new File(path+"model-preview.png"));
 }
}
