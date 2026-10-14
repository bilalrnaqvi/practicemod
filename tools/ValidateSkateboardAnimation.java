import com.bilal.practicemod.SkateboardAnimation;
import com.bilal.practicemod.SkateboardKinematics;

/** Run with the compiled main classes on the classpath. */
public class ValidateSkateboardAnimation {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private record Riding(int strokes, int cruising) {}
    private static Riding riding(float speed, int mode) {
        var animation = new SkateboardAnimation();
        int strokes = 0, cruising = 0;
        float previous = 0;
        for (int tick = 0; tick < 2400; tick++) {
            animation.tick(true, mode, speed);
            var sample = animation.sample(1);
            if (sample.phase() < previous) strokes++;
            if (sample.phase() == 0 && sample.push() == 0) cruising++;
            previous = sample.phase();
        }
        return new Riding(strokes, cruising);
    }
    private static double distance(SkateboardKinematics.Point a, SkateboardKinematics.Point b) {
        return Math.sqrt(Math.pow(a.x() - b.x(), 2) + Math.pow(a.y() - b.y(), 2) + Math.pow(a.z() - b.z(), 2));
    }
    public static void main(String[] args) {
        var slow = riding(0.25F, 1);
        var normal = riding(1 / 1.3F, 1);
        var fast = riding(1, 2);
        check(slow.strokes() > normal.strokes() && normal.strokes() > fast.strokes() * 1.5,
                "faster travel needs fewer pushes");
        check(fast.cruising() > 1200 && fast.cruising() > normal.cruising()
                && normal.cruising() > slow.cruising(), "fast riders spend most of their time cruising");
        check(riding(1, 1).equals(fast), "actual speed determines cadence, not sprint intent");
        check(riding(0, 1).strokes() == 0, "stationary riders never push");
        var transition = new SkateboardAnimation();
        for (int tick = 0; tick < 34; tick++) {
            transition.tick(true, tick < 17 ? 1 : 2, tick < 17 ? 0.25F : 1);
            float expected = tick == 33 ? 0 : (tick + 1) / 34F;
            check(Math.abs(transition.sample(1).phase() - expected) < 0.00001,
                    "speed changes never jump or shorten an active stroke");
        }
        for (int tick = 0; tick < 50; tick++) {
            transition.tick(true, 2, 1);
            check(transition.sample(1).phase() == 0, "fast cruise keeps the foot on the deck");
        }
        for (int tick = 0; tick < 10; tick++) transition.tick(true, 1, 0.25F);
        check(transition.sample(1).phase() > 0, "slowing down brings the next push forward");
        transition.tick(true, 0, 0);
        float interrupted = transition.sample(1).phase();
        transition.tick(true, 1, 0.25F);
        check(Math.abs(transition.sample(1).phase() - interrupted - 1 / 34F) < 0.00001,
                "brief input release resumes the stroke without snapping");
        var a = new SkateboardAnimation();
        var b = new SkateboardAnimation();
        for (int tick = 0; tick < 5000; tick++) {
            int mode = tick % 120 < 60 ? 1 : 2;
            a.tick(true, mode, mode == 1 ? 1 / 1.3F : 1); b.tick(true, mode, mode == 1 ? 1 / 1.3F : 1);
            for (int frame = 0; frame < 16; frame++) a.sample(frame / 16F);
            check(a.sample(1).equals(b.sample(1)), "render frequency cannot advance the animation");
            var s = a.sample(0.5F);
            check(s.phase() >= 0 && s.phase() < 1, "phase stays wrapped");
            check(s.push() >= 0 && s.push() <= 1, "push blend stays bounded");
        }
        float[] start = SkateboardAnimation.stroke(0), end = SkateboardAnimation.stroke(1);
        for (int i = 0; i < start.length; i++) check(Math.abs(start[i] - end[i]) < 0.00001F, "loop closes");
        float lastZ = -9;
        for (float phase = 0.32F; phase <= 0.44F; phase += 0.001F) {
            float[] stroke = SkateboardAnimation.stroke(phase);
            check(Math.abs(stroke[1] - 4) < 0.00001F, "sole stays at ground height during power stroke");
            check(stroke[2] >= lastZ, "ground stroke only pushes backward");
            lastZ = stroke[2];
        }
        for (int frame = 0; frame <= 1000; frame++) {
            float phase = frame / 1000F;
            var pose = SkateboardKinematics.pose(new SkateboardAnimation.Sample(phase, 1, 1));
            check(pose.lean() <= Math.toRadians(5.5), "forward bend stays subtle");
            check(Math.abs(pose.torsoYaw()) <= Math.toRadians(14.1), "spinal twist stays modest");
            for (var leg : new SkateboardKinematics.Leg[] {pose.left(), pose.right()}) {
                check(Math.abs(distance(leg.hip(), leg.knee()) - 6) < 0.0001, "thigh length never changes");
                check(Math.abs(distance(leg.knee(), leg.foot()) - 6) < 0.0001, "shin length never changes");
            }
            check(Math.abs(pose.left().foot().y() - 24) < 0.01, "support foot stays planted");
            if (phase >= 0.32F && phase <= 0.44F) {
                check(Math.abs(pose.right().foot().y() - 28) < 0.15, "pushing sole reaches ground");
                check(pose.stance() + Math.toDegrees(pose.torsoYaw()) < 15, "chest faces forward while hips remain angled");
                check(pose.pelvis().z() < -5, "weight shifts over front foot");
            }
        }
        for (float boundary : new float[] {0, 0.28F, 0.64F, 1}) {
            float h = 0.0001F;
            var before = SkateboardKinematics.pose(new SkateboardAnimation.Sample((boundary - h + 1) % 1, 1, 1));
            var at = SkateboardKinematics.pose(new SkateboardAnimation.Sample(boundary % 1, 1, 1));
            var after = SkateboardKinematics.pose(new SkateboardAnimation.Sample((boundary + h) % 1, 1, 1));
            check(Math.abs(after.stance() - 2 * at.stance() + before.stance()) < 0.0001,
                    "hip rotation does not catch at transition boundaries");
        }
        check(SkateboardKinematics.pose(new SkateboardAnimation.Sample(0, 1, 1)).stance() > 65,
                "rider returns to sideways coasting stance");
        for (int i = 0; i < 12; i++) a.tick(true, 0, 0);
        check(a.sample(1).push() == 0 && a.sample(1).ride() == 1, "idle and airborne settle into riding stance");
        for (int i = 0; i < 10; i++) a.tick(false, 0, 0);
        check(a.sample(1).ride() == 0, "dismount clears animation weight");
        for (float crouch : new float[] {1.8F, 2.5F, 4, 6}) {
            for (float pitch : new float[] {-14, 0, 14}) {
                var action = SkateboardKinematics.pose(new SkateboardAnimation.Sample(0, 0, 1), crouch, pitch);
                for (var leg : new SkateboardKinematics.Leg[] {action.left(), action.right()}) {
                    check(Math.abs(distance(leg.hip(), leg.knee()) - 6) < 0.0001, "action poses preserve thigh length");
                    check(Math.abs(distance(leg.knee(), leg.foot()) - 6) < 0.0001, "action poses preserve shin length");
                }
                float tilt = 5 * (float) Math.sin(Math.toRadians(pitch));
                check(Math.abs(action.left().foot().y() - (24 - tilt)) < 0.01, "front foot follows raised truck");
                check(Math.abs(action.right().foot().y() - (24 + tilt)) < 0.01, "rear foot follows raised truck");
            }
        }
        System.out.println("PASS: speed-aware pushes and cruising, frame independence, long-running phase wrap, loop continuity, ground contact, recovery, and dismount");
    }
}
