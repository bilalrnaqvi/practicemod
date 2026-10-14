import com.bilal.practicemod.SubmarineDepth;
import com.bilal.practicemod.SubmarineHistory;
import com.bilal.practicemod.SubmarineAttitude;

/** Run with javac/java; no client, world, or third-party test runtime needed. */
public class ValidateSubmarineInstruments {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static final SubmarineDepth.Cell AIR = new SubmarineDepth.Cell(true, false, true, false, 0);
    private static final SubmarineDepth.Cell WATER = new SubmarineDepth.Cell(true, true, false, false, 8F / 9);
    private static final SubmarineDepth.Cell UNKNOWN = new SubmarineDepth.Cell(false, false, true, false, 0);
    private static final SubmarineDepth.Cell SOLID = new SubmarineDepth.Cell(true, false, true, true, 0);
    public static void main(String[] args) {
        check(Math.abs(SubmarineDepth.measure(10.25, y -> y <= 12 ? WATER : AIR) - (12 + 8F/9 - 10.25)) < .0001,
                "fractional fluid surface, measured from eye");
        check(SubmarineDepth.measure(10.95, y -> y == 10 ? WATER : AIR) == 0, "eye above fractional surface");
        check(SubmarineDepth.measure(10.95, y -> y == 10 ? WATER : SOLID) == 0, "dry fraction below a roof");
        check(SubmarineDepth.measure(10, y -> AIR) == 0, "out of water");
        check(SubmarineDepth.measure(10, y -> new SubmarineDepth.Cell(true, false, false, false, 1)) == 0, "lava is not water");
        check(Float.isNaN(SubmarineDepth.measure(10, y -> UNKNOWN)), "unloaded eye");
        check(Float.isNaN(SubmarineDepth.measure(10, y -> y < 12 ? WATER : UNKNOWN)), "unloaded surface");
        check(Float.isNaN(SubmarineDepth.measure(10, y -> y < 12 ? WATER : SOLID)), "solid roof is not water surface");
        check(Float.isNaN(SubmarineDepth.measure(10, y -> new SubmarineDepth.Cell(true, true, false, true, 1))), "waterlogged solid unknown");
        int[] queries = {0};
        check(Float.isNaN(SubmarineDepth.measure(-50, y -> { queries[0]++; return WATER; })), "capped scan unknown");
        check(queries[0] == SubmarineDepth.MAX_BLOCKS + 1, "strict query bound");
        check(SubmarineDepth.measure(-20.5, y -> y < -19 ? WATER : AIR) > 0, "negative world coordinates");
        SubmarineHistory a = new SubmarineHistory(), b = new SubmarineHistory();
        check(a.size() == 0, "no invented history");
        a.sample(0, 5); a.sample(5, 100); a.sample(10, 5);
        check(a.size() == 2, "2 Hz maximum");
        check(SubmarineHistory.plotY(a.value(0), a.range()) == SubmarineHistory.plotY(a.value(1), a.range()), "constant depth horizontal");
        check(SubmarineHistory.plotY(8, 10) > SubmarineHistory.plotY(5, 10), "descent moves down");
        check(SubmarineHistory.plotY(3, 10) < SubmarineHistory.plotY(5, 10), "ascent moves up");
        check(SubmarineHistory.plotY(0, 10) == 17, "surface at top");
        a.sample(20, Float.NaN); a.sample(30, 7);
        check(Float.isNaN(a.value(2)), "unknown retained as gap");
        check(SubmarineHistory.plotX(0, 600) == 71, "real timestamps preserve partial history");
        a.sample(100, 33);
        check(a.range() == 40, "immediate rounded expansion");
        b.sample(100, 2);
        check(b.range() == 10 && b.size() == 1 && a.size() == 5, "independent vehicle buffers");
        for (int t = 110; t <= 1300; t += 10) a.sample(t, 2);
        check(a.size() == 120 && a.range() == 40, "bounded history and delayed shrink");
        for (int t = 1310; t <= 1520; t += 10) a.sample(t, 2);
        check(a.range() == 10, "sustained smaller window shrinks");
        a.clear(); check(a.size() == 0 && a.range() == 10, "dismount/reset discards history");
        b.sample(0, 0); check(b.size() == 1 && b.value(0) == 0, "dimension clock reset");
        check(SubmarineAttitude.horizon(0) == 32, "level horizon");
        check(SubmarineAttitude.horizon(-20) > 32, "nose-up horizon down");
        check(SubmarineAttitude.horizon(20) < 32, "nose-down horizon up");
        check(SubmarineAttitude.cardRoll(20) == -20, "right bank counter-rotates");
        check(SubmarineAttitude.cardRoll(-20) == 20, "left bank counter-rotates");
        check(SubmarineAttitude.cardRoll(0) == 0, "no invented roll");
        System.out.println("Passed " + checks + " submarine instrument checks");
    }
}
