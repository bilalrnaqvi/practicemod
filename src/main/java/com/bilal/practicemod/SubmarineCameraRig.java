package com.bilal.practicemod;

/** Pure perspective fitting; input points are right/up/forward offsets from the hull center. */
final class SubmarineCameraRig {
    static final double HEIGHT_FRACTION = .325;
    static final double WIDTH_LIMIT = .70;
    static final double CENTER_NDC_Y = -.24; // Center of the submarine at 62% down the screen.
    record Fit(double distance, double minimumDistance, double lowerLift, double upperLift,
               double tangent, double ndcBottom, double ndcTop) {
        double lift(double atDistance) {
            return (lowerLift + upperLift - (ndcTop + ndcBottom)*tangent*atDistance) / 2;
        }
    }

    static Fit fit(double[][] points, double fovDegrees, double aspect) {
        double tangent = Math.tan(Math.toRadians(Math.clamp(fovDegrees, 20, 150))/2);
        aspect = Math.max(.3, aspect);
        double bottom = CENTER_NDC_Y - HEIGHT_FRACTION, top = CENTER_NDC_Y + HEIGHT_FRACTION;
        double a = -Double.MAX_VALUE, b = Double.MAX_VALUE, horizontal = 0, near = 1;
        for (double[] p : points) {
            a = Math.max(a, p[1] - top*tangent*p[2]);
            b = Math.min(b, p[1] - bottom*tangent*p[2]);
            horizontal = Math.max(horizontal, Math.abs(p[0])/(WIDTH_LIMIT*tangent*aspect) - p[2]);
            near = Math.max(near, 1 - p[2]);
        }
        double distance = Math.max(near, Math.max(horizontal, (a-b)/((top-bottom)*tangent)));
        return new Fit(distance, near, a, b, tangent, bottom, top);
    }

    static double chaseDistance(Fit fit) {
        return Math.max(fit.minimumDistance()+2, fit.distance()*.95);
    }

    /** Frame-rate independent easing. Safety collision clamps are applied after this. */
    static double ease(double current, double target, double seconds) {
        return current + (target-current)*(-Math.expm1(-6*Math.clamp(seconds, 0, .1)));
    }

    static boolean cockpitFallback(boolean alreadyInside, double safeDistance, double minimumDistance) {
        return safeDistance < minimumDistance + (alreadyInside ? 2 : .5);
    }

    private SubmarineCameraRig() {}
}
