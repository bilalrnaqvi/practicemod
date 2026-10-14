import com.bilal.practicemod.BlenderPlayerClip;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** Verify PAL evaluates the baked poses, including random seeks and remount-style restarts. */
public final class ValidateBlenderPlayback {
    public static void main(String[] args) throws Exception {
        Path asset = Path.of("src/main/resources/assets/practicemod/player_animations/deepseeker_entry.json");
        JsonArray moves;
        try (var reader = Files.newBufferedReader(asset)) {
            moves = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("emote").getAsJsonArray("moves");
        }
        try (var stream = Files.newInputStream(asset)) {
            var clip = new BlenderPlayerClip(stream);
            for (float tick = 0; tick <= (int) BlenderPlayerClip.LAST_TICK; tick += .125F) check(clip, moves, tick);
            var random = new Random(20261012);
            for (int i = 0; i < 1000; i++) check(clip, moves, random.nextFloat() * (int) BlenderPlayerClip.LAST_TICK);
            for (float tick : new float[]{(int) BlenderPlayerClip.LAST_TICK, 0, 70, 70, 20, (int) BlenderPlayerClip.LAST_TICK, 0, -10, BlenderPlayerClip.LAST_TICK + 24})
                check(clip, moves, tick);
        }
        System.out.println("PASS: PAL samples match baked rotations/bends; fractional time, repeat renders, reverse seeks and restart checked.");
    }

    private static void check(BlenderPlayerClip clip, JsonArray moves, float requested) {
        clip.sample(requested);
        float time = Math.clamp(requested, 0, (int) BlenderPlayerClip.LAST_TICK);
        int a = (int) time, b = Math.min(a + 1, (int) BlenderPlayerClip.LAST_TICK);
        for (String name : new String[]{"head", "rightArm", "leftArm", "rightLeg", "leftLeg"}) {
            String boneName = name.replaceAll("([A-Z])", "_$1").toLowerCase();
            var bone = clip.bone(boneName);
            near(name + " pitch", requested, bone.getRotX(), expected(moves, name, "pitch", a, b, time - a));
            near(name + " yaw", requested, bone.getRotY(), expected(moves, name, "yaw", a, b, time - a));
            near(name + " roll", requested, bone.getRotZ(), expected(moves, name, "roll", a, b, time - a));
            if (!name.equals("head"))
                near(name + " bend", requested, bone.getBend(), expected(moves, name, "bend", a, b, time - a));
        }
        near("torso bend", requested, clip.bone("torso").getBend(),
                expected(moves, "torso", "bend", a, b, time - a));
        // PAL's legacy body channel carries the exported hip rotation.
        var body = clip.bone("body");
        near("body x", requested, body.getRotX(), -expected(moves, "torso", "pitch", a, b, time - a));
        near("body y", requested, body.getRotY(), -expected(moves, "torso", "yaw", a, b, time - a));
        near("body z", requested, body.getRotZ(), expected(moves, "torso", "roll", a, b, time - a));
    }

    private static float expected(JsonArray moves, String part, String channel, int a, int b, float f) {
        float start = moves.get(a).getAsJsonObject().getAsJsonObject(part).get(channel).getAsFloat();
        float end = moves.get(b).getAsJsonObject().getAsJsonObject(part).get(channel).getAsFloat();
        return start + (end - start) * f;
    }

    private static void near(String channel, float tick, float actual, float expected) {
        if (!Float.isFinite(actual) || Math.abs(actual - expected) > .0001F)
            throw new AssertionError(channel + " at " + tick + ": " + actual + " != " + expected);
    }
}
