package com.bilal.practicemod;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Server-owned trick completion, repetition penalties, multiplier, and banked score. */
public final class SkateboardTricks {
    public enum Trick {
        NONE("", 0, 0), KICKFLIP("Kickflip", 100, 10), HEELFLIP("Heelflip", 100, 10),
        SHUVIT("Pop Shuvit", 150, 12), TRE_FLIP("360 Flip", 350, 16),
        INDY("Indy", 200, 7), MELON("Melon", 200, 7),
        NOSEGRAB("Nosegrab", 250, 7), TAILGRAB("Tailgrab", 250, 7),
        SPECIAL_900("The 900", 9000, 24);
        public final String label;
        public final int points, duration;
        Trick(String label, int points, int duration) {
            this.label = label; this.points = points; this.duration = duration;
        }
        public boolean grab() { return ordinal() >= INDY.ordinal() && ordinal() <= TAILGRAB.ordinal(); }
        public static Trick fromId(int id) { return id >= 0 && id < values().length ? values()[id] : NONE; }
    }

    private final Map<String, Integer> repetitions = new HashMap<>();
    private final ArrayDeque<String> names = new ArrayDeque<>();
    private int base, multiplier, special, resultTicks, lastResult, trickTicks, heldGrabTicks;
    private long total, best;
    private Trick trick = Trick.NONE;
    private String result = "";

    public int base() { return base; }
    public int multiplier() { return multiplier; }
    public int special() { return special; }
    public boolean specialReady() { return special >= 750; }
    public long total() { return total; }
    public long best() { return best; }
    public int lastResult() { return lastResult; }
    public String line() { return names.isEmpty() ? result : String.join(" + ", names); }
    public Trick trick() { return trick; }
    public int trickTicks() { return trickTicks; }
    public boolean active() { return multiplier > 0; }

    public void restore(long total, long best) {
        this.total = Math.max(0, total); this.best = Math.max(0, best);
    }

    public void tick(boolean grabHeld) {
        if (resultTicks > 0 && --resultTicks == 0) { result = ""; lastResult = 0; }
        if (!active() && special > 0) special = Math.max(0, special - 2);
        if (trick == Trick.NONE) return;
        if (trickTicks > 0) trickTicks--;
        if (trick.grab() && grabHeld && ++heldGrabTicks < 40) return;
        if (trickTicks == 0) {
            add(trick.label, trick.points + (trick.grab() ? heldGrabTicks * 8 : 0));
            trick = Trick.NONE;
            heldGrabTicks = 0;
        }
    }

    public boolean start(Trick next) {
        if (next == Trick.NONE || trick != Trick.NONE) return false;
        if (next == Trick.SPECIAL_900) {
            if (!specialReady()) return false;
            special = 0;
        }
        trick = next;
        trickTicks = next.duration;
        heldGrabTicks = 0;
        return true;
    }

    public void add(String name, int points) {
        int repetition = Math.min(4, repetitions.getOrDefault(name, 0));
        repetitions.put(name, repetition + 1);
        int awarded = Math.max(1, points >> repetition);
        base = Math.min(1_000_000, base + awarded);
        multiplier = Math.min(64, multiplier + 1);
        special = Math.min(1000, special + Math.min(200, awarded / 2 + 30));
        names.addLast(name);
        if (names.size() > 4) names.removeFirst();
        result = ""; lastResult = 0;
    }

    /** Sustain points do not create a fresh multiplier every tick. */
    public void sustain(int points) {
        if (active()) base = Math.min(1_000_000, base + Math.max(0, points));
    }

    public boolean land(float spin) {
        if (trick != Trick.NONE || !SkateboardDynamics.cleanLanding(spin)) { bail(); return false; }
        int turns = (int) ((Math.abs(spin) + 30) / 180);
        if (turns > 0) add((spin < 0 ? "FS " : "BS ") + turns * 180, 180 * turns);
        return true;
    }

    public void bank() {
        if (!active()) return;
        lastResult = base * multiplier;
        total = total > Long.MAX_VALUE - lastResult ? Long.MAX_VALUE : total + lastResult;
        best = Math.max(best, lastResult);
        result = "LANDED  +" + lastResult;
        resultTicks = 70;
        clearCombo();
    }

    public void bail() {
        lastResult = -1;
        result = "BAIL!";
        resultTicks = 50;
        special = 0;
        clearCombo();
    }

    private void clearCombo() {
        base = multiplier = trickTicks = heldGrabTicks = 0;
        trick = Trick.NONE;
        names.clear(); repetitions.clear();
    }
}
