package com.bilal.practicemod;

public class ValidateSubmarineDynamics {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var up = new SubmarineDynamics.State(0, 0, 0);
        var down = up;
        var stationary = up;
        for (int i = 0; i < 160; i++) {
            var next = SubmarineDynamics.step(up.pitch(), up.pitchRate(), up.verticalSpeed(), .35, 1);
            check(Math.abs(next.pitch() - up.pitch()) <= .851, "bounded angular speed");
            check(Math.abs(next.verticalSpeed() - up.verticalSpeed()) <= .00401, "bounded vertical acceleration");
            up = next;
            down = SubmarineDynamics.step(down.pitch(), down.pitchRate(), down.verticalSpeed(), .35, -1);
            stationary = SubmarineDynamics.step(stationary.pitch(), stationary.pitchRate(), stationary.verticalSpeed(), 0, 1);
            check(Math.abs(up.pitch() + down.pitch()) < .0001, "symmetric dive/climb trim");
            check(Math.abs(up.verticalSpeed() + down.verticalSpeed()) < .0001, "symmetric dive/climb motion");
        }
        check(up.pitch() < -23 && up.verticalSpeed() > .15, "forward ascent is nose-up and diagonal");
        check(stationary.pitch() < -7 && stationary.verticalSpeed() < .036, "ballast-only ascent is slower");
        var reverse = SubmarineDynamics.step(up.pitch(), up.pitchRate(), up.verticalSpeed(), .35, -1);
        check(reverse.verticalSpeed() > 0 && reverse.pitch() < 0, "reversing input cannot instantly reverse heavy hull");
        for (int i = 0; i < 200; i++) up = SubmarineDynamics.step(up.pitch(), up.pitchRate(), up.verticalSpeed(), .35, 0);
        check(up.pitch() == 0 && up.verticalSpeed() == 0, "release settles without indefinite drift");
        var backing = new SubmarineDynamics.State(0, 0, 0);
        for (int i = 0; i < 160; i++) backing = SubmarineDynamics.step(backing.pitch(), backing.pitchRate(), backing.verticalSpeed(), -.15, 1);
        check(backing.pitch() > 0 && backing.verticalSpeed() > 0, "reverse ascent raises stern with correct plane response");
        System.out.println("Submarine dynamics checks passed");
    }
}
