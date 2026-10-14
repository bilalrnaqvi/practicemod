import com.zigythebird.playeranimcore.loading.UniversalAnimLoader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Exercise the real 1.21.1 PAL parser, not a parallel JSON validator. */
public final class ValidateBlenderPlayerClip {
    public static void main(String[] args) throws Exception {
        try (var input = Files.newInputStream(Path.of(args[0]))) {
            var animations = UniversalAnimLoader.loadAnimations(input);
            if (animations.size() != 1) throw new AssertionError("Expected one player clip");
            var animation = animations.values().iterator().next();
            for (String bone : new String[]{"head","torso","right_arm","left_arm","right_leg","left_leg"})
                if (animation.getBone(bone) == null) throw new AssertionError("Missing bone: " + bone);
            if (animation.length() < 96 || animation.length() > 97)
                throw new AssertionError("Unexpected duration: " + animation.length());
            for (String bone : new String[]{"torso","right_arm","left_arm","right_leg","left_leg"})
                if (animation.getBone(bone).bendKeyFrames().isEmpty())
                    throw new AssertionError("Missing articulated joint: " + bone);
            System.out.println("PAL 1.1.6 / MC 1.21.1 parsed " + animation.getNameOrId()
                    + ": " + animation.length() + " ticks; six body parts and five bend tracks present.");
        }
    }
}
