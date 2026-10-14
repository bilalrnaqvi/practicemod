package com.bilal.practicemod;

public class ValidateSubmarineJoystick {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var stick = new SubmarineJoystickAnimation();
        check(stick.pitch(1) == 0 && stick.roll(1) == 0, "Neutral on spawn");
        for (int i = 0; i < 80; i++) {
            float previous = stick.pitch(1);
            stick.tick(0, 0, .35, 0, 0);
            check(stick.pitch(0) == previous, "Continuous tick interpolation");
        }
        check(stick.pitch(1) < -13.9, "Push forward with forward movement");
        var rotated = new SubmarineJoystickAnimation();
        for (int i = 0; i < 80; i++) rotated.tick(-.35, 0, 0, 90, 0);
        check(Math.abs(rotated.pitch(1) - stick.pitch(1)) < .001, "Travel uses local heading");
        for (int i = 0; i < 80; i++) stick.tick(0, 0, -.35, 0, 0);
        check(stick.pitch(1) > 13.9, "Pull back when reversing");
        for (int i = 0; i < 80; i++) stick.tick(0, .18, 0, 0, 4);
        check(stick.pitch(1) > 11.9 && stick.roll(1) < -17.9, "Climb and left turn combine");
        for (int i = 0; i < 80; i++) stick.tick(0, -.18, 0, 0, -4);
        check(stick.pitch(1) < -11.9 && stick.roll(1) > 17.9, "Dive and right turn combine");
        var wrap = new SubmarineJoystickAnimation();
        wrap.tick(0, 0, 0, -179, -358);
        check(wrap.roll(1) < 0, "Heading wrap preserves left turn");
        var teleport = new SubmarineJoystickAnimation();
        teleport.tick(100, 0, 0, 0, 0);
        check(teleport.pitch(1) == 0, "Teleport ignored");
        for (int i = 0; i < 100; i++) stick.tick(0, 0, 0, 0, 0);
        check(stick.pitch(1) == 0 && stick.roll(1) == 0, "Settles exactly to neutral");
        check(DeepseekerGeometry.JOYSTICK.length == DeepseekerGeometry.CUBES.length,
                "Membership matches exported geometry");
        int members = 0;
        for (int c = 0; c < DeepseekerGeometry.CUBES.length; c++) {
            if (!DeepseekerGeometry.JOYSTICK[c]) continue;
            members++;
            check(!DeepseekerGeometry.GLASS[c] && DeepseekerGeometry.ROTORS[c] == -1,
                    "Controls are opaque and separate from rotors");
            float[] cube = DeepseekerGeometry.CUBES[c], pivot = DeepseekerGeometry.JOYSTICK_ORIGIN;
            for (int i = 0; i < cube.length; i += 8)
                check(Math.abs(cube[i] - pivot[0]) < .15 && Math.abs(cube[i + 1] - pivot[1]) < .3
                        && Math.abs(cube[i + 2] - pivot[2]) < .15, "Pivot stays at the joystick foot");
        }
        check(members == 2, "Only shaft and grip move; boot remains fixed");
        System.out.println("Submarine joystick motion and geometry checks passed");
    }
}
