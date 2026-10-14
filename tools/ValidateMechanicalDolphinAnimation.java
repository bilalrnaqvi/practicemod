package com.bilal.practicemod;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/** Phase continuity, water transitions, fin bounds and reproducible artist-clip samples. */
public class ValidateMechanicalDolphinAnimation {
    static float[] angles(MechanicalDolphinAnimation.Pose p) {
        return new float[]{p.tail(), p.flukes(), p.portPitch(), p.portRoll(),
                p.starboardPitch(), p.starboardRoll(), p.dorsalYaw(), p.dorsalRoll()};
    }
    static void near(MechanicalDolphinAnimation.Pose a, MechanicalDolphinAnimation.Pose b, float tolerance) {
        float[] aa = angles(a), bb = angles(b);
        for (int i = 0; i < aa.length; i++) if (Math.abs(aa[i] - bb[i]) > tolerance)
            throw new AssertionError("Pose discontinuity on channel " + i + ": " + aa[i] + " / " + bb[i]);
    }
    public static void main(String[] args) throws Exception {
        var animation = new MechanicalDolphinAnimation();
        animation.tick(0, true, 0, 0);
        var previous = animation.sample(1);
        float biggestStep = 0;
        // Accelerate, coast, turn across yaw wrap (caller supplies wrapped rate),
        // breach, settle on land, re-enter, reverse, then come to rest.
        for (int tick = 0; tick < 900; tick++) {
            double speed = tick < 100 || tick >= 750 ? 0 : tick < 650 ? .82 : .3;
            boolean wet = tick < 300 || tick >= 500;
            animation.tick(speed, wet, tick >= 180 && tick < 220 ? 7 : 0,
                    tick >= 230 && tick < 270 ? -5 : 0);
            near(previous, animation.sample(0), 1e-5F);
            var a = animation.sample(0);
            for (int frame = 1; frame <= 12; frame++) {
                var b = animation.sample(frame / 12F);
                float[] av = angles(a), bv = angles(b);
                for (int i = 0; i < av.length; i++) {
                    if (!Float.isFinite(bv[i])) throw new AssertionError("Non-finite animation");
                    biggestStep = Math.max(biggestStep, Math.abs(bv[i] - av[i]));
                }
                if (Math.abs(b.tail()) > 21 || Math.abs(b.flukes()) > 10.01
                        || Math.abs(b.dorsalRoll()) > 2.5 || Math.abs(b.portRoll()) > 10.1)
                    throw new AssertionError("Animation exceeds joint limits");
                a = b;
            }
            previous = animation.sample(1);
            if (tick == 499) for (float value : angles(previous))
                if (Math.abs(value) > .001) throw new AssertionError("Swimming continues on land");
        }
        if (biggestStep > 1) throw new AssertionError("Visual snap: " + biggestStep);
        var first = new MechanicalDolphinAnimation();
        var second = new MechanicalDolphinAnimation();
        for (int tick = 0; tick < 500; tick++) {
            first.tick(.5, true, 0, 0); second.tick(.5, true, 0, 0);
            for (int f = 0; f < 17; f++) first.sample(f / 17F);
            near(first.sample(.5F), second.sample(.5F), 0);
        }
        Files.createDirectories(Path.of("build/dolphin-animation"));
        try (var out = new PrintWriter("build/dolphin-animation/clips.csv")) {
            out.println("clip,time,tail,flukes,portPitch,portRoll,starboardPitch,starboardRoll,dorsalYaw,dorsalRoll");
            for (int moving = 0; moving < 2; moving++) {
                double period = 2 * Math.PI / ((.075 + .285 * moving) * 20);
                float[] min = new float[8], max = new float[8];
                java.util.Arrays.fill(min, Float.POSITIVE_INFINITY);
                java.util.Arrays.fill(max, Float.NEGATIVE_INFINITY);
                for (int sample = 0; sample <= 128; sample++) {
                    var pose = MechanicalDolphinAnimation.cycle(sample * 2 * Math.PI / 128, moving, 1, 0, 0);
                    out.print((moving == 0 ? "idle" : "swim") + "," + period * sample / 128);
                    float[] values = angles(pose);
                    for (int i = 0; i < values.length; i++) {
                        out.print("," + values[i]); min[i] = Math.min(min[i], values[i]); max[i] = Math.max(max[i], values[i]);
                    }
                    out.println();
                }
                for (int index : new int[]{0, 1, 3, 5, 7})
                    if (max[index] - min[index] < .5) throw new AssertionError("Missing fin/tail motion " + index);
                if (moving == 0 && max[0] > 2.01 || moving == 1 && max[0] < 15)
                    throw new AssertionError("Rest/swim tail amplitude is wrong");
                near(MechanicalDolphinAnimation.cycle(0, moving, 1, 0, 0),
                        MechanicalDolphinAnimation.cycle(2 * Math.PI, moving, 1, 0, 0), 1e-5F);
            }
        }
        System.out.println("PASS: 10,800 interpolated transition poses, dry settling, phase continuity, frame-rate independence, five animated joints and loop closure; max 240-fps angular step " + biggestStep);
    }
}
