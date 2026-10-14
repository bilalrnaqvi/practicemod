package com.bilal.practicemod;

/** Per-entity, tick-sampled 60 s history. NaN samples are explicit gaps. */
public final class SubmarineHistory {
    public static final int CAPACITY = 120;
    private final long[] times = new long[CAPACITY];
    private final float[] values = new float[CAPACITY];
    private int next, count;
    private float range = 10;
    private long shrinkSince = -1;
    public void clear() { next = count = 0; range = 10; shrinkSince = -1; }
    public int size() { return count; }
    private int index(int i) { return (next - count + i + CAPACITY) % CAPACITY; }
    public long time(int i) { return times[index(i)]; }
    public float value(int i) { return values[index(i)]; }
    public float range() { return range; }
    public void sample(long tick, float value) {
        if (count > 0 && tick < time(count - 1)) clear();
        if (count > 0 && tick - time(count - 1) < 10) return;
        times[next] = tick; values[next] = value;
        next = (next + 1) % CAPACITY; count = Math.min(CAPACITY, count + 1);
        float max = 0;
        for (int i = 0; i < count; i++)
            if (time(i) >= tick - 1200 && Float.isFinite(value(i))) max = Math.max(max, value(i));
        float desired = Math.max(10, (float) Math.ceil(max / 10) * 10);
        if (desired > range) { range = desired; shrinkSince = -1; }
        else if (desired <= range / 2) {
            if (shrinkSince < 0) shrinkSince = tick;
            if (tick - shrinkSince >= 200) { range = desired; shrinkSince = -1; }
        } else shrinkSince = -1;
    }
    public static float plotX(long sample, double now) { return (float) (122 - (now - sample) * 102 / 1200); }
    public static float plotY(float depth, float range) { return 17 + depth / range * 30; }
}
