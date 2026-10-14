package com.bilal.practicemod;

/** Run with java -cp build/classes/java/main tools/ValidateSkateboardGameplay.java. */
public final class ValidateSkateboardGameplay {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        double speed = 0;
        for (int i = 0; i < 120; i++) {
            speed = SkateboardDynamics.speed(speed, true, false, true, false);
            check(speed <= SkateboardDynamics.FAST_SPEED, "input cannot exceed speed cap");
        }
        check(speed > 0.70, "arcade speed reaches more than fourteen blocks per second");
        double coast = SkateboardDynamics.speed(speed, false, false, false, false);
        check(coast > speed * 0.98, "coasting preserves momentum");
        for (int i = 0; i < 15; i++) speed = SkateboardDynamics.speed(speed, false, true, false, false);
        check(speed == 0, "braking reaches a complete stop");
        check(SkateboardDynamics.ollie(16, false) > SkateboardDynamics.ollie(1, false), "charging increases jump height");
        check(SkateboardDynamics.cleanLanding(180) && SkateboardDynamics.cleanLanding(360), "180 and 360 land aligned");
        check(!SkateboardDynamics.cleanLanding(90), "sideways landing bails");
        float balance = 0, velocity = 0;
        for (int i = 0; i < 300 && Math.abs(balance) < 1; i++) {
            velocity = SkateboardDynamics.balanceVelocity(velocity, balance, 0, i); balance += velocity;
        }
        check(Math.abs(balance) >= 1, "unattended balance eventually fails");
        var combo = new SkateboardTricks();
        check(!combo.start(SkateboardTricks.Trick.SPECIAL_900), "special needs a filled meter");
        check(combo.start(SkateboardTricks.Trick.KICKFLIP), "flip starts");
        check(!combo.start(SkateboardTricks.Trick.INDY), "tricks cannot overlap");
        for (int i = 0; i < 10; i++) combo.tick(false);
        check(combo.base() == 100 && combo.multiplier() == 1, "points require trick completion");
        combo.add("Manual", 100); combo.sustain(40);
        check(combo.land(180), "completed trick can land");
        check(combo.multiplier() == 3 && combo.base() == 420, "manual and spin extend multiplier");
        combo.bank();
        check(combo.total() == 1260 && combo.best() == 1260, "bank uses base times multiplier");
        check(combo.multiplier() == 0, "bank closes combo");
        combo.add("Kickflip", 100); combo.add("Kickflip", 100); combo.add("Kickflip", 100);
        check(combo.base() == 175, "repeated tricks lose value");
        combo.bail(); check(combo.total() == 1260 && combo.multiplier() == 0 && combo.special() == 0, "bails lose only unbanked points and special");
        combo.start(SkateboardTricks.Trick.KICKFLIP);
        check(!combo.land(0), "unfinished flip bails on landing");
        combo.start(SkateboardTricks.Trick.INDY);
        for (int i = 0; i < 12; i++) combo.tick(true);
        check(combo.base() == 0 && combo.trick() == SkateboardTricks.Trick.INDY, "held grab must be released");
        combo.tick(false); check(combo.base() > 200, "holding grab awards duration points after release");
        combo.bail();
        for (int i = 0; i < 5; i++) combo.add("trick" + i, 500);
        check(combo.specialReady() && combo.start(SkateboardTricks.Trick.SPECIAL_900), "completed combo builds special");
        for (int i = 0; i < 24; i++) combo.tick(false);
        check(combo.line().contains("The 900"), "900 finishes after its full animation");
        for (int i = 0; i < 5000; i++) combo.add("repeated", 1000000);
        combo.bank(); check(combo.lastResult() > 0 && combo.lastResult() <= 64000000, "long combos cannot overflow");
        var restore = new SkateboardTricks(); restore.restore(-1, -1);
        check(restore.total() == 0 && restore.best() == 0, "invalid saved scores cannot become negative");
        System.out.println("PASS: acceleration, braking, coasting, charged ollies, landing alignment, balance, trick timing, combos, repetition, grabs, specials, bails, and score bounds");
    }
}
