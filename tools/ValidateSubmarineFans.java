package com.bilal.practicemod;

public class ValidateSubmarineFans {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static float step(SubmarineFanAnimation fan) {
        return fan.angle(1) - fan.angle(0);
    }

    public static void main(String[] args) {
        var fan = new SubmarineFanAnimation();
        check(step(fan) == 0, "Spawned fans must be still");
        for (int i = 0; i < 100; i++) {
            float previous = fan.angle(1);
            fan.tick(0, 0, .35, 0);
            check(Math.abs(Math.IEEEremainder(fan.angle(0) - previous, 360)) < .001,
                    "Tick boundaries must preserve rotor phase even when wrapping");
        }
        check(step(fan) > 39, "Forward movement spins fans up");
        check(new SubmarineFanAnimation().angle(1) == 0, "Vehicles have independent phases");
        for (int i = 0; i < 100; i++) fan.tick(0, 0, -.35, 0);
        check(step(fan) < -39, "Backing up reverses fans");
        for (int i = 0; i < 100; i++) fan.tick(0, 0, 0, 0);
        check(step(fan) == 0, "Fans settle to a complete stop");
        fan.tick(0, .15, 0, 0);
        check(step(fan) > 0, "Vertical movement also spins fans");
        var turning = new SubmarineFanAnimation();
        turning.tick(-.35, 0, 0, 90);
        check(step(turning) > 0, "Forward direction follows vehicle yaw");
        var teleport = new SubmarineFanAnimation();
        teleport.tick(100, 0, 0, 0);
        check(step(teleport) == 0, "Teleport does not spike rotor speed");

        int[] members = new int[3];
        check(DeepseekerGeometry.ROTORS.length == DeepseekerGeometry.CUBES.length,
                "Every cube has rotor membership metadata");
        for (int c = 0; c < DeepseekerGeometry.ROTORS.length; c++) {
            int rotor = DeepseekerGeometry.ROTORS[c];
            if (rotor < 0) continue;
            members[rotor]++;
            check(!DeepseekerGeometry.GLASS[c], "Rotors render in the opaque pass");
            float[] pivot = DeepseekerGeometry.ROTOR_ORIGINS[rotor];
            float[] cube = DeepseekerGeometry.CUBES[c];
            for (int i = 0; i < cube.length; i += 8) {
                check(Math.hypot(cube[i] - pivot[0], cube[i + 1] - pivot[1]) < 1,
                        "Rotating vertices stay near their own hub");
            }
        }
        for (int count : members) check(count == 5, "Each fan contains only its hub and four blades");
        System.out.println("Submarine fan animation and geometry checks passed");
    }
}
