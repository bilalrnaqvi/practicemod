package com.bilal.practicemod;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
public class PreviewDeepseekerExpedition {
 static String path="output/blockbench/deepseeker-expedition/";
 record Model(float[][] data,boolean[] glass){}
 static Model load(String file)throws Exception{
  try(var in=new DataInputStream(new FileInputStream(path+file+".bin"))){int n=in.readInt();float[][] d=new float[n][];boolean[] g=new boolean[n];for(int i=0;i<n;i++){g[i]=in.readBoolean();d[i]=new float[in.readInt()];for(int j=0;j<d[i].length;j++)d[i][j]=in.readFloat();}return new Model(d,g);}
 }
 public static void main(String[] args)throws Exception{
  var tex=new BufferedImage(4096,2048,BufferedImage.TYPE_INT_ARGB);var tg=tex.createGraphics();tg.drawImage(ImageIO.read(new File(path+"preview-texture-0.png")),0,0,null);tg.drawImage(ImageIO.read(new File(path+"preview-texture-1.png")),2048,0,null);tg.dispose();PreviewDeepseeker.texture=tex;
  var full=load("full");var inside=load("interior");var plan=load("plan");
  ImageIO.write(PreviewDeepseeker.renderGeometry(new double[]{0,51,-63},new double[]{0,47,2},680,1600,1000,full.data,full.glass),"png",new File(path+"interior-refined.png"));
  ImageIO.write(PreviewDeepseeker.renderGeometry(new double[]{24,51,-34},new double[]{0,38,12},600,900,850,full.data,full.glass),"png",new File(path+"storage-refined.png"));
  int W=1600,H=1160;var out=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);var g=out.createGraphics();g.setColor(new Color(0x152536));g.fillRect(0,0,W,H);g.setColor(Color.WHITE);g.setFont(new Font("SansSerif",Font.BOLD,24));g.drawString("DEEPSEEKER / BUILT EXPEDITION INTERIOR",24,34);
  g.drawImage(PreviewDeepseeker.renderGeometry(new double[]{0,51,-63},new double[]{0,47,2},430,1050,660,full.data,full.glass),0,50,null);
  g.drawImage(PreviewDeepseeker.renderGeometry(new double[]{0,180,-20},new double[]{0,20,-19},680,550,660,plan.data,plan.glass),1050,50,null);
  g.drawImage(PreviewDeepseeker.renderGeometry(new double[]{105,110,-120},new double[]{0,51,-23},440,800,400,inside.data,inside.glass),0,735,null);
  g.drawImage(PreviewDeepseeker.renderGeometry(new double[]{190,150,-230},new double[]{0,48,-40},580,800,400,full.data,full.glass),800,735,null);
  g.setFont(new Font("SansSerif",Font.PLAIN,18));g.setColor(Color.WHITE);g.drawString("Interior looking aft — starboard entry on left",24,710);g.drawString("Floor plan / hatch open",1080,710);g.drawString("Editable furniture cutaway",24,1150);g.drawString("Full submarine revision / hatch open",830,1150);g.dispose();ImageIO.write(out,"png",new File(path+"model-preview.png"));
 }
}
