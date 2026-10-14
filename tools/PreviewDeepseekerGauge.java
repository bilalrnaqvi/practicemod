package com.bilal.practicemod;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.*;
import javax.imageio.metadata.*;
import javax.imageio.stream.*;
public class PreviewDeepseekerGauge {
 public static void main(String[] args)throws Exception{
  String p=PreviewDeepseekerExpedition.path;
  var tex=new BufferedImage(4096,2048,BufferedImage.TYPE_INT_ARGB);var tg=tex.createGraphics();tg.drawImage(ImageIO.read(new File(p+"preview-texture-0.png")),0,0,null);tg.drawImage(ImageIO.read(new File(p+"preview-texture-1.png")),2048,0,null);tg.dispose();PreviewDeepseeker.texture=tex;
  ImageWriter writer=ImageIO.getImageWritersByFormatName("gif").next();
  try(var output=new FileImageOutputStream(new File(p+"air-gauge-bobble.gif"))){
   writer.setOutput(output);writer.prepareWriteSequence(null);
   for(int i=0;i<24;i++){
    var model=PreviewDeepseekerExpedition.load(String.format("gauge%02d",i));
    var img=PreviewDeepseeker.renderGeometry(new double[]{5,61,1.5},new double[]{-19,61,1.5},1800,520,520,model.data(),model.glass());
    var param=writer.getDefaultWriteParam();var meta=writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(img),param);
    String format=meta.getNativeMetadataFormatName();var root=(IIOMetadataNode)meta.getAsTree(format);
    var control=(IIOMetadataNode)root.getElementsByTagName("GraphicControlExtension").item(0);control.setAttribute("disposalMethod","none");control.setAttribute("userInputFlag","FALSE");control.setAttribute("transparentColorFlag","FALSE");control.setAttribute("delayTime","10");control.setAttribute("transparentColorIndex","0");
    if(i==0){var apps=new IIOMetadataNode("ApplicationExtensions");var app=new IIOMetadataNode("ApplicationExtension");app.setAttribute("applicationID","NETSCAPE");app.setAttribute("authenticationCode","2.0");app.setUserObject(new byte[]{1,0,0});apps.appendChild(app);root.appendChild(apps);}
    meta.setFromTree(format,root);writer.writeToSequence(new IIOImage(img,null,meta),param);
   }
   writer.endWriteSequence();
  }finally{writer.dispose();}
 }
}
