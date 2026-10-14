package com.bilal.practicemod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import java.nio.file.*;
import java.util.*;

/** Uses Minecraft's actual baked player cubes, layer inflation, and seated leg transforms. */
public class ValidateSubmarinePlayerMesh {
    public static void main(String[] args) throws Exception {
        for (boolean slim : new boolean[]{false, true}) {
            var root = PlayerModel.createMesh(CubeDeformation.NONE, slim).getRoot().bake(64, 64);
            var model = new PlayerModel<>(root, slim);
            SubmarinePilotPose.seatedLegs(model);
            var pose = new PoseStack();
            pose.translate(DeepseekerPilot.X, DeepseekerPilot.Y - 1.62, DeepseekerPilot.Z);
            pose.scale(-.9375F, -.9375F, .9375F);
            pose.translate(0, -1.501, 0);
            List<String> points = new ArrayList<>();
            double[] minimum = {Double.POSITIVE_INFINITY};
            VertexConsumer vertices = new VertexConsumer() {
                public VertexConsumer addVertex(float x, float y, float z) {
                    minimum[0] = Math.min(minimum[0], y - DeepseekerPilot.FLOOR_Y);
                    double xx=x*16/DeepseekerPilot.HULL_SCALE, yy=y*16/DeepseekerPilot.HULL_SCALE-15, zz=z*16/DeepseekerPilot.HULL_SCALE;
                    double r=zz < -20.8 ? 8.45+3.2*Math.clamp((zz+38.5)/17.7,0,1) : 10.2;
                    if (!(zz > -38.5 && zz < 32.4 && Math.max(Math.abs(xx),Math.abs(yy)) < r
                            && Math.abs(xx)+Math.abs(yy) < r*Math.sqrt(2)))
                        throw new AssertionError("Player vertex outside pressure hull: " + x+","+y+","+z);
                    points.add("["+x+","+y+","+z+"]");
                    return this;
                }
                public VertexConsumer setColor(int r,int g,int b,int a) { return this; }
                public VertexConsumer setUv(float u,float v) { return this; }
                public VertexConsumer setUv1(int u,int v) { return this; }
                public VertexConsumer setUv2(int u,int v) { return this; }
                public VertexConsumer setNormal(float x,float y,float z) { return this; }
            };
            root.visit(pose, (p, path, index, cube) -> {
                if (!path.equals("/ear") && !path.equals("/cloak")) cube.compile(p, vertices, 0, 0, -1);
            });
            if (minimum[0] < .5) throw new AssertionError("Insufficient actual player mesh floor clearance: "+minimum[0]);
            Files.writeString(Path.of("build/deepseeker-player-"+(slim?"slim":"wide")+".json"),"["+String.join(",",points)+"]\n");
            System.out.printf("PASS: %s player mesh (%d vertices), %.3f blocks above deck%n",slim?"slim":"wide",points.size(),minimum[0]);
        }
    }
}
