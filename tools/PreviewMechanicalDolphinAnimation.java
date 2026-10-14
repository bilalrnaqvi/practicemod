package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

/** Render the actual runtime joint hierarchy, including attached emissive details. */
public class PreviewMechanicalDolphinAnimation {
    public static void main(String[] args) throws Exception {
        var atlas = new BufferedImage(1088, 1024, BufferedImage.TYPE_INT_ARGB);
        var ag = atlas.createGraphics();
        ag.drawImage(ImageIO.read(Path.of("src/main/resources/assets/practicemod/textures/entity/mechanical_dolphin.png").toFile()), 0, 0, null);
        ag.dispose();
        PreviewDeepseeker.texture = atlas;
        for (int moving = 0; moving < 2; moving++) {
            String label = moving == 0 ? "idle" : "swim";
            Path folder = Path.of("build/dolphin-animation/" + label);
            Files.createDirectories(folder);
            for (int frame = 0; frame < 32; frame++) {
                var vertices = new ArrayList<float[]>();
                var pose = MechanicalDolphinAnimation.cycle(frame * 2 * Math.PI / 32, moving, 1, 0, 0);
                MechanicalDolphinModel.renderOpaque(new PoseStack(), PreviewMechanicalDolphin.capture(vertices, false), 0, 0, pose);
                int glassStart = vertices.size() / 4;
                MechanicalDolphinModel.renderGlass(new PoseStack(), PreviewMechanicalDolphin.capture(vertices, false), 0, 0);
                if (vertices.size() != 1604 * 4) throw new AssertionError("Geometry lost or duplicated");
                if (frame % 8 == 0) try (var csv = new PrintWriter(folder.resolve("mesh-" + frame + ".csv").toFile())) {
                    for (var v : vertices) csv.println(v[0] + "," + v[1] + "," + v[2]);
                }
                var quads = PreviewMechanicalDolphin.quads(vertices);
                var glass = new boolean[quads.length];
                Arrays.fill(glass, glassStart, glass.length, true);
                var image = new BufferedImage(800, 680, BufferedImage.TYPE_INT_RGB);
                var g = image.createGraphics();
                g.drawImage(PreviewDeepseeker.renderGeometry(new double[]{95, 60, 115}, new double[]{0, 10, -8},
                        1150, 800, 390, quads, glass), 0, 0, null);
                g.drawImage(PreviewDeepseeker.renderGeometry(new double[]{180, 20, -8}, new double[]{0, 10, -8},
                        1200, 800, 290, quads, glass), 0, 390, null);
                g.setColor(Color.WHITE);
                g.drawString(label.toUpperCase() + " — actual runtime mesh / " + (moving == 0 ? "4.19 s cycle" : "0.87 s cycle"), 20, 25);
                g.drawString("Side view: tail root + feathering flukes", 20, 415);
                g.dispose();
                ImageIO.write(image, "png", folder.resolve(String.format("%02d.png", frame)).toFile());
            }
            System.out.println("Rendered 32 " + label + " frames.");
        }
    }
}
