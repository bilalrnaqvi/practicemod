package com.bilal.practicemod;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Static staged geometry, not boarding footage or a gameplay camera. */
public class PreviewDeepseekerRearEntry {
 public static void main(String[] args) throws Exception {
  String base="output/blockbench/deepseeker-rear-entry/";
  var atlas=new BufferedImage(4096,2048,BufferedImage.TYPE_INT_ARGB);var a=atlas.createGraphics();
  a.drawImage(ImageIO.read(new File(base+"closed/preview-texture-0.png")),0,0,null);
  a.drawImage(ImageIO.read(new File(base+"closed/preview-texture-1.png")),2048,0,null);a.dispose();PreviewDeepseeker.texture=atlas;
  PreviewDeepseekerExpedition.path=base+"closed/";var closed=PreviewDeepseekerExpedition.load("full");
  PreviewDeepseekerExpedition.path=base+"open/";var open=PreviewDeepseekerExpedition.load("full");
  var plan=PreviewDeepseekerExpedition.load("plan");
  var sheet=new BufferedImage(1600,1180,BufferedImage.TYPE_INT_RGB);var g=sheet.createGraphics();
  g.setColor(new Color(0x152536));g.fillRect(0,0,1600,1180);
  g.drawImage(PreviewDeepseeker.renderGeometry(new double[]{160,140,165},new double[]{0,62,-18},610,800,510,closed.data(),closed.glass()),0,85,null);
  g.drawImage(PreviewDeepseeker.renderGeometry(new double[]{100,138,115},new double[]{19,94,-1},530,800,510,open.data(),open.glass()),800,85,null);
  g.drawImage(PreviewDeepseeker.renderGeometry(new double[]{0,51,-64},new double[]{3,51,0},410,950,500,open.data(),open.glass()),0,640,null);
  g.drawImage(PreviewDeepseeker.renderGeometry(new double[]{0,180,-20},new double[]{0,20,-20.01},820,650,500,plan.data(),plan.glass()),950,640,null);
  g.setColor(Color.WHITE);g.setFont(new Font("SansSerif",Font.BOLD,26));g.drawString("DEEPSEEKER / REAR-CORNER ENTRY",24,36);
  g.setFont(new Font("SansSerif",Font.PLAIN,18));g.drawString("Staged Blockbench model • furniture retained • model inspection views, not gameplay",24,65);
  g.drawString("Closed hatch / fitted rear shoulder",24,614);g.drawString("Forward-opening lid / stern starting tread",824,614);
  g.drawString("Cabin view / bed, desk and storage fronts accessible",24,1162);
  g.drawString("Floor plan / cockpit ↑ / rear ↓",995,1162);
  g.dispose();ImageIO.write(sheet,"png",new File(base+"model-review.png"));
  ImageIO.write(PreviewDeepseeker.renderGeometry(new double[]{0,51,-64},new double[]{3,51,0},620,1400,950,open.data(),open.glass()),"png",new File(base+"cabin-access.png"));
 }
}
