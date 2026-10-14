package com.bilal.practicemod;

/** Standalone regression coverage for the defining swim -> breach -> dive loop. */
public final class ValidateMechanicalDolphinDynamics {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static final class Ride {
        final MechanicalDolphinDynamics dolphin = new MechanicalDolphinDynamics();
        double y = -3, maxY = -3;
        int launches, landings;
        void tick(float throttle, float yaw, float pitch, boolean rise) {
            dolphin.step(y, 0, y + .3 < 0, false, true, throttle, yaw, pitch, rise);
            y += dolphin.vy;
            maxY = Math.max(maxY, y);
            if (dolphin.launched) launches++;
            if (dolphin.landed) landings++;
            check(Double.isFinite(y + dolphin.vx + dolphin.vz + dolphin.pitch), "finite state");
        }
    }
    public static void main(String[] args) {
        var ride = new Ride();
        for (int i=0;i<40;i++) ride.tick(1,0,0,false);
        check(ride.dolphin.vz > .8 && Math.abs(ride.y+3)<.001, "level swim builds speed without sinking");
        for (int i=0;i<80;i++) ride.tick(1,0,0,true);
        check(ride.launches==1, "holding Jump gives only one powered breach");
        check(ride.maxY>3 && ride.maxY<8, "breach clears water by several blocks");
        check(ride.landings>=1, "ballistic arc returns to water");
        // Dive to rebuild submerged contact, then release/repress for a second real stroke.
        for (int i=0;i<55;i++) ride.tick(1,0,60,false);
        check(ride.y < -3, "look down dives below the surface");
        for (int i=0;i<110;i++) ride.tick(1,0,0,true);
        check(ride.launches==2, "submerge and release Jump rearms the next breach");

        var natural = new Ride();
        for (int i=0;i<80;i++) natural.tick(1,0,-55,false);
        check(natural.maxY>1 && natural.landings>0 && natural.launches==0,
                "ordinary upward swimming also carries momentum out of the water");
        var airA = new MechanicalDolphinDynamics(); var airB = new MechanicalDolphinDynamics();
        airA.vz=airB.vz=.6; airA.vy=airB.vy=.9;
        for(int i=0;i<25;i++) {
            airA.step(10,0,false,false,true,1,90,-80,true);
            airB.step(10,0,false,false,true,0,0,0,false);
            check(airA.vy==airB.vy && airA.vz==airB.vz && airA.vx==0, "no midair propulsion or steering");
        }
        check(airA.pitch>30, "nose follows falling trajectory for re-entry");
        var land = new MechanicalDolphinDynamics();
        for(int i=0;i<80;i++) land.step(0,Double.NEGATIVE_INFINITY,false,true,true,1,90,-70,true);
        check(land.vx==0 && land.vy==0 && land.vz==0 && !land.launched, "no dry-land launch");
        var turn = new Ride();
        for(int i=0;i<40;i++) turn.tick(1,90,0,false);
        check(turn.dolphin.vx<-.75 && Math.abs(turn.dolphin.vz)<.1, "mouse heading steers underwater");
        for(int i=0;i<60;i++) turn.tick(-1,90,0,false);
        check(turn.dolphin.vx>.3, "S brakes and gives slow reverse");
        System.out.printf("Mechanical dolphin passed: cruise %.1f blocks/s, breach apex %.2f blocks; natural breaches, re-entry, rearm, air and land gating verified.%n", .82*20, ride.maxY);
    }
}
