package com.bilal.practicemod;

/** Fixed-length thigh/shin solution, expressed in vanilla player model pixels. */
public final class SkateboardKinematics {
    public record Point(float x, float y, float z) {
        Point add(Point p) { return new Point(x + p.x, y + p.y, z + p.z); }
        Point sub(Point p) { return new Point(x - p.x, y - p.y, z - p.z); }
        Point mul(float s) { return new Point(x * s, y * s, z * s); }
        float dot(Point p) { return x * p.x + y * p.y + z * p.z; }
        float length() { return (float) Math.sqrt(dot(this)); }
    }
    public record Leg(Point hip, Point knee, Point foot) {}
    public record Pose(float stance, Point pelvis, float lean, float torsoYaw, float armSwing, Leg left, Leg right) {}
    private static float mix(float t, float a, float b) { return a + (b - a) * t; }
    private static Point rotate(float x, float y, float z, float radians) {
        float c = (float) Math.cos(radians), s = (float) Math.sin(radians);
        return new Point(c * x - s * z, y, s * x + c * z);
    }

    public static Pose pose(SkateboardAnimation.Sample sample) {
        return pose(sample, 0, 0);
    }

    public static Pose pose(SkateboardAnimation.Sample sample, float crouch, float pitch) {
        float[] stroke = SkateboardAnimation.stroke(sample.phase());
        float ride = sample.ride(), push = sample.push();
        float load = rotationLoad(sample.phase()) * push;
        // Pelvis opens gradually, while the shoulders lead slightly. The feet stay
        // in board space, so this rotates through the hips instead of spinning the stance.
        float stance = mix(load, 66, 24) * ride;
        float shoulderLoad = rotationLoad((sample.phase() + 0.035F) % 1) * push;
        float torsoYaw = (float) Math.toRadians(-14 * shoulderLoad * ride);
        float radians = (float) Math.toRadians(stance);
        Point pelvis = rotate(0, (mix(push, 1.3F, stroke[3]) + crouch) * ride, -5.5F * load * ride, radians);
        Point leftHip = pelvis.add(new Point(1.9F, 12, 0));
        Point rightHip = pelvis.add(new Point(-1.9F, 12, 0));
        Point front = rotate(0, 24, -5, radians);
        Point rear = rotate(stroke[0] * push, 24 + stroke[1] * push, mix(push, 5, stroke[2]), radians);
        float tilt = 5 * (float) Math.sin(Math.toRadians(pitch));
        front = new Point(mix(ride, 1.9F, front.x), 24 - tilt, front.z * ride);
        rear = new Point(mix(ride, -1.9F, rear.x), mix(ride, 24, rear.y) + tilt, rear.z * ride);
        Point forward = rotate(0, 0, -1, radians);
        return new Pose(stance, pelvis, (0.025F + 0.07F * load) * ride, torsoYaw, stroke[4] * push * ride,
                solve(leftHip, front, forward), solve(rightHip, rear, forward));
    }

    // Quintic easing has zero velocity AND acceleration at each end: no visible
    // mechanical catch as the hips enter/leave the loaded part of the stroke.
    private static float ease(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static float rotationLoad(float phase) {
        if (phase < 0.28F) return ease(phase / 0.28F);
        if (phase < 0.64F) return 1;
        return 1 - ease((phase - 0.64F) / 0.36F);
    }

    private static Leg solve(Point hip, Point target, Point forward) {
        Point delta = target.sub(hip);
        float reach = delta.length();
        float distance = Math.min(11.98F, Math.max(0.01F, reach));
        Point axis = delta.mul(1 / Math.max(0.01F, reach));
        Point foot = hip.add(axis.mul(distance));
        Point bend = forward.sub(axis.mul(forward.dot(axis)));
        float bendLength = bend.length();
        if (bendLength < 0.001F) bend = new Point(1, 0, 0);
        else bend = bend.mul(1 / bendLength);
        float height = (float) Math.sqrt(36 - distance * distance / 4);
        Point knee = hip.add(axis.mul(distance / 2)).add(bend.mul(height));
        return new Leg(hip, knee, foot);
    }

    private SkateboardKinematics() {}
}
