package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.ZipFile;

/** Actual cockpit/skin meshes at the first-person eye and 70-degree vertical FOV. */
public class PreviewMechanicalDolphinCockpit {
    public static void main(String[] args) throws Exception {
        var result = new BufferedImage(1600, 900, BufferedImage.TYPE_INT_RGB);
        var g = result.createGraphics();
        try (var jar = new ZipFile("build/moddev/artifacts/neoforge-21.1.235-client-extra-aka-minecraft-resources.jar")) {
            for (boolean slim : new boolean[]{false, true}) {
                var atlas = new BufferedImage(1088, 1024, BufferedImage.TYPE_INT_ARGB);
                var ag = atlas.createGraphics();
                ag.drawImage(ImageIO.read(Path.of("src/main/resources/assets/practicemod/textures/entity/mechanical_dolphin.png").toFile()), 0, 0, null);
                String skin = slim ? "slim/alex" : "wide/steve";
                ag.drawImage(ImageIO.read(jar.getInputStream(jar.getEntry("assets/minecraft/textures/entity/player/" + skin + ".png"))), 1024, 0, null);
                ag.dispose();
                PreviewDeepseeker.texture = atlas;
                var model = new PlayerModel<>(PlayerModel.createMesh(CubeDeformation.NONE, slim).getRoot().bake(64, 64), slim);
                MechanicalDolphinCockpit.gripPose(model, slim);
                var hull = new ArrayList<float[]>();
                var hands = new ArrayList<float[]>();
                var glass = new ArrayList<float[]>();
                MechanicalDolphinModel.renderOpaque(new PoseStack(), PreviewMechanicalDolphin.capture(hull, false), 0, 0, 0, 0);
                MechanicalDolphinCockpit.renderArms(model, new PoseStack(), PreviewMechanicalDolphin.capture(hands, true), 0, true, true);
                MechanicalDolphinModel.renderGlass(new PoseStack(), PreviewMechanicalDolphin.capture(glass, false), 0, 0);
                // Check the actual skin mesh's final three pixels surround each authored grip.
                for (int sign : new int[]{-1, 1}) {
                    double[] low = {100, 100, 100}, high = {-100, -100, -100};
                    for (var v : hands) if (v[0] * sign > 0) {
                        for (int a = 0; a < 3; a++) { low[a] = Math.min(low[a], v[a]); high[a] = Math.max(high[a], v[a]); }
                    }
                    low[2] = high[2] - 3 * .9375 / 16;
                    double[] grip = {sign * 4.85 / 16, 11.7 / 16, 19.7 / 16};
                    for (int a = 0; a < 3; a++) if (grip[a] < low[a] || grip[a] > high[a])
                        throw new AssertionError(skin + " hand does not enclose grip axis " + a);
                    var eye = MechanicalDolphinCockpit.EYE;
                    double depth = grip[2] - eye.z;
                    double vertical = (grip[1] - eye.y) / depth / Math.tan(Math.toRadians(35));
                    double horizontal = (grip[0] - eye.x) / depth / Math.tan(Math.toRadians(35)) / (16.0 / 9);
                    if (Math.abs(vertical) > .85 || Math.abs(horizontal) > .85)
                        throw new AssertionError("Grip is too close to the screen edge at 70 FOV");
                }
                for (int row = 0; row < 2; row++) {
                    var mesh = new ArrayList<>(hull);
                    if (row == 0) mesh.addAll(hands);
                    int glassStart = mesh.size() / 4;
                    mesh.addAll(glass);
                    var quads = PreviewMechanicalDolphin.quads(mesh);
                    var transparent = new boolean[quads.length];
                    Arrays.fill(transparent, glassStart, transparent.length, true);
                    var eye = MechanicalDolphinCockpit.EYE;
                    var image = PreviewDeepseeker.renderGeometry(new double[]{eye.x * 16, eye.y * 16, eye.z * 16},
                            new double[]{eye.x * 16, eye.y * 16, eye.z * 16 + 16},
                            225 / Math.tan(Math.toRadians(35)), 800, 450, quads, transparent);
                    int x = slim ? 800 : 0, y = row * 450;
                    g.drawImage(image, x, y, null);
                    g.setColor(Color.WHITE);
                    g.drawString(skin + (row == 0 ? " — cockpit hands" : " — handle positions without hands"), x + 20, y + 25);
                }
            }
        }
        g.dispose();
        Files.createDirectories(Path.of("output/mechanical-dolphin"));
        ImageIO.write(result, "png", Path.of("output/mechanical-dolphin/cockpit-hands-preview.png").toFile());
        System.out.println("PASS: wide/slim hand mesh encloses both grips; both contacts visible at 70 FOV. Offline preview written.");
    }
}
