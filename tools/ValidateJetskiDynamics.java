package com.bilal.practicemod;

/** Run with javac/java; no client or server is required for the fixed-step feel checks. */
public final class ValidateJetskiDynamics {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static final class Ride {
        final JetskiDynamics hull = new JetskiDynamics();
        double y = -JetskiDynamics.DRAFT;
        double maxHeight = y;
        int landings;
        void tick(float throttle, float steer, boolean boost) {
            hull.step(y, 0, false, throttle, steer, boost);
            y += hull.vy;
            maxHeight = Math.max(maxHeight, y);
            if (hull.landed) landings++;
            check(Double.isFinite(y + hull.vx + hull.vz + hull.pitch + hull.roll), "finite state");
            check(y > -1 && y < 1.7, "water contact keeps bounce bounded");
        }
        double speed() { return Math.hypot(hull.vx, hull.vz); }
    }
    public static void main(String[] args) {
        Ride cruise = new Ride(), boost = new Ride();
        for (int i=0; i<80; i++) { cruise.tick(1,0,false); boost.tick(1,0,false); }
        cruise.maxHeight = boost.maxHeight = -1;
        for (int i=0; i<50; i++) { cruise.tick(1,0,false); boost.tick(1,0,true); }
        check(boost.speed() > cruise.speed()*1.7, "boost gives a substantial speed surge");
        check(boost.maxHeight > cruise.maxHeight + .35, "boost creates stronger physical hops");
        check(boost.landings >= 3 && cruise.landings >= 3, "repeated skips reconnect with water");
        double before = boost.speed();
        boost.tick(0,0,false);
        check(boost.speed() > before*.97, "releasing boost retains momentum");
        for (int i=0;i<20;i++) boost.tick(0,0,false);
        check(boost.speed() > before*.65, "throttle release coasts instead of stopping");

        Ride turn = new Ride();
        for(int i=0;i<80;i++) turn.tick(1,0,false);
        double oldZ=turn.hull.vz;
        turn.tick(1,1,false);
        check(turn.hull.yaw < 0 && turn.hull.vz > oldZ*.9, "left steering turns hull while retaining travel momentum");
        for(int i=0;i<15;i++) turn.tick(1,1,false);
        double velocityYaw=Math.toDegrees(Math.atan2(-turn.hull.vx,turn.hull.vz));
        check(Math.abs(velocityYaw-turn.hull.yaw)>3, "velocity trails heading during a carve");
        check(Math.abs(turn.hull.roll)>3 && Math.abs(turn.hull.roll)<=20, "bank into turns with a bounded lean");

        Ride brake = new Ride();
        for(int i=0;i<80;i++) brake.tick(1,0,false);
        brake.tick(-1,0,false);
        check(brake.hull.vz>0, "braking cannot instantly reverse velocity");
        for(int i=0;i<100;i++) brake.tick(-1,0,false);
        check(brake.hull.vz<-.1 && brake.hull.vz>-.21, "S transitions into controlled reverse");

        Ride reservoir = new Ride();
        for(int i=0;i<100;i++) reservoir.tick(1,0,true);
        check(reservoir.hull.exhausted && !reservoir.hull.boosting, "held boost locks after depletion");
        for(int i=0;i<130;i++) reservoir.tick(0,0,false);
        check(reservoir.hull.charge==1 && !reservoir.hull.exhausted, "release recharges and rearms");
        reservoir.tick(1,0,true);
        check(reservoir.hull.boosting, "boost can be used again");

        Ride idle = new Ride(); idle.y=.4;
        for(int i=0;i<200;i++) idle.tick(0,0,false);
        check(Math.abs(idle.y+JetskiDynamics.DRAFT)<.005 && Math.abs(idle.hull.vy)<.001, "idle settles on the waterline");
        var land = new JetskiDynamics();
        for(int i=0;i<60;i++) land.step(0,Double.NEGATIVE_INFINITY,true,1,1,true);
        check(land.vx==0 && land.vz==0 && !land.boosting && land.charge==1, "no propulsion or boost drain on land");
        var air = new JetskiDynamics(); air.vz=.8;
        air.step(10,Double.NEGATIVE_INFINITY,false,1,1,true);
        check(air.vy<0 && air.vz<=.8 && !air.boosting, "leaving water is ballistic");
        System.out.printf("Jetski dynamics passed: cruise %.1f blocks/s, boost %.1f blocks/s; hop crests %.2f / %.2f blocks.%n",
                cruise.speed()*20, before*20, cruise.maxHeight, boost.maxHeight);
    }
}
