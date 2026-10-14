package com.bilal.practicemod;

import com.google.gson.Gson;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Arrays;

/** Textured review renderer for the authoring study, not a capture from Minecraft. */
public final class RenderBoardingStudy {
    record Frame(float time,float[][] geometry) {}
    record Scene(float[][] hull,boolean[] glass,Frame[] frames) {}
    public static void main(String[] args) throws Exception {
        Path dir=Path.of("build/boarding-study");
        Scene scene=new Gson().fromJson(Files.readString(dir.resolve("render-data.json")),Scene.class);
        PreviewDeepseeker.texture=ImageIO.read(dir.resolve("atlas.png").toFile());
        int w=620,h=560;
        for(int f=0;f<scene.frames.length;f++) {
            Frame frame=scene.frames[f];
            float[][] geometry=Arrays.copyOf(scene.hull,scene.hull.length+frame.geometry.length);
            System.arraycopy(frame.geometry,0,geometry,scene.hull.length,frame.geometry.length);
            boolean[] glass=Arrays.copyOf(scene.glass,geometry.length);
            BufferedImage image=PreviewDeepseeker.renderGeometry(new double[]{102,103,-53},new double[]{43,83,0},780,w,h,geometry,glass);
            var g=image.createGraphics();
            g.setColor(new Color(0x152536));g.fillRect(0,0,w,42);g.fillRect(0,h-34,w,34);
            g.setFont(new Font("SansSerif",Font.PLAIN,16));g.setColor(new Color(0xE2E8EA));
            g.drawString("DEEPSEEKER / ladder contact study",18,27);
            g.setFont(new Font("SansSerif",Font.PLAIN,13));g.setColor(new Color(0xB6C9D5));
            g.drawString(String.format("Authoring preview · %.2f s · fixed-length articulated rig",frame.time),18,h-12);
            g.dispose();ImageIO.write(image,"png",dir.resolve(String.format("frame-%03d.png",f)).toFile());
        }
    }
}
