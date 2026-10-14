package com.bilal.practicemod;

/** Bounded water-column semantics, independent of the renderer and testable without a world. */
public final class SubmarineDepth {
    public static final int MAX_BLOCKS = 64;
    public record Cell(boolean loaded, boolean water, boolean emptyFluid, boolean obstructed, float height) {}
    public interface Column { Cell at(int y); }

    public static float measure(double eyeY, Column column) {
        int start = (int) Math.floor(eyeY);
        Cell current = column.at(start);
        if (!current.loaded()) return Float.NaN;
        if (!current.water()) return 0;
        // Waterlogged solids do not establish an unobstructed instrument column.
        if (current.obstructed()) return Float.NaN;
        for (int y = start; y < start + MAX_BLOCKS; y++) {
            Cell above = column.at(y + 1);
            if (!above.loaded()) return Float.NaN;
            if (above.water()) {
                if (above.obstructed()) return Float.NaN;
                current = above;
                continue;
            }
            // The pilot can occupy the dry fraction of the water cell itself.
            if (y == start && eyeY >= y + current.height()) return 0;
            if (above.obstructed() || !above.emptyFluid()) return Float.NaN;
            return (float) Math.max(0, y + current.height() - eyeY);
        }
        return Float.NaN;
    }
    private SubmarineDepth() {}
}
