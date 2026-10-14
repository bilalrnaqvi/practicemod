package com.bilal.practicemod;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Static model review only; not a gameplay or first-person animation capture. */
public class PreviewDeepseekerEntryRedesign {
 public static void main(String[] args) throws Exception {
  String base="output/blockbench/deepseeker-entry-redesign/";
  var atlas=new BufferedImage(4096,2048,BufferedImage.TYPE_INT_ARGB);
  var a=atlas.createGraphics();
  a.drawImage(ImageIO.read(new File(base+"closed/preview-texture-0.png")),0,0,null);
  a.drawImage(ImageIO.read(new File(base+"closed/preview-texture-1.png")),2048,0,null);
  a.dispose(); PreviewDeepseeker.texture=atlas;
  PreviewDeepseekerExpedition.path=base+"closed/";
  var closed=PreviewDeepseekerExpedition.load("full");
  PreviewDeepseekerExpedition.path=base+"open/";
  var open=PreviewDeepseekerExpedition.load("full");
  var inside=PreviewDeepseekerExpedition.load("interior");
  var sheet=new BufferedImage(1600,1160,BufferedImage.TYPE_INT_RGB);
  var g=sheet.createGraphics();g.setColor(new Color(0x152536));g.fillRect(0,0,1600,1160);
  g.drawImage(PreviewDeepseeker.renderGeometry(new double[]{140,150,-190},new double[]{2,60,-27},670,800,510,closed.data(),closed.glass()),0,85,null);
  g.drawImage(PreviewDeepseeker.renderGeometry(new double[]{80,135,-100},new double[]{7,94,-27},570,800,510,open.data(),open.glass()),800,85,null);
  g.drawImage(PreviewDeepseeker.renderGeometry(new double[]{-105,95,-115},new double[]{2,61,-27},510,800,500,inside.data(),inside.glass()),0,630,null);
  g.drawImage(PreviewDeepseeker.renderGeometry(new double[]{0,51,-64},new double[]{3,52,-22},440,800,500,open.data(),open.glass()),800,630,null);
  g.setColor(Color.WHITE);g.setFont(new Font("SansSerif",Font.BOLD,26));g.drawString("DEEPSEEKER / CONNECTED ENTRY — MODEL PASS 1",24,36);
  g.setFont(new Font("SansSerif",Font.PLAIN,18));g.drawString("Staged Blockbench geometry • outside and cutaway review • not first-person gameplay",24,65);
  g.setColor(new Color(0x152536));g.fillRect(0,1120,1600,40);
  g.setColor(Color.WHITE);
  g.drawString("Closed lid / low starting ledge",24,605);
  g.drawString("Open lid / side handle / ladder directly below",824,605);
  g.drawString("Cutaway / continuous ladder and floor landing",24,1145);
  g.drawString("Cabin inspection / bunk and chair retained",824,1145);
  g.dispose();ImageIO.write(sheet,"png",new File(base+"model-review.png"));
 }
}
