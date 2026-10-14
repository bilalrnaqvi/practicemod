package com.bilal.practicemod;

/** Deterministic handling checks, run without Minecraft. */
public final class ValidateAtvDynamics {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static AtvDynamics cruise() {
        var atv = new AtvDynamics();
        for (int i=0; i<160; i++) atv.drive(true, false, 1, 0);
        return atv;
    }
    public static void main(String[] args) {
        var atv = cruise();
        double cruise = atv.vz;
        check(cruise > .48 && cruise < AtvDynamics.TOP_SPEED, "useful bounded cruise speed");
        atv.drive(true, false, 0, 0);
        check(atv.vz > cruise * .97, "release retains momentum");
        for (int i=0; i<180; i++) atv.drive(true, false, 0, 0);
        check(Math.abs(atv.vz) < .001, "unattended quad eventually stops");
        atv = cruise(); atv.drive(true, false, -1, 0);
        check(atv.vz > 0 && atv.vz < cruise - .03, "brake before reversing");
        for (int i=0; i<100; i++) atv.drive(true, false, -1, 0);
        check(atv.vz < -.15 && atv.vz > -AtvDynamics.REVERSE_SPEED, "slow controlled reverse");
        atv.drive(true, false, -1, 1);
        check(atv.yaw > 0, "reverse steering follows tire direction");
        atv = cruise();
        for (int i=0; i<12; i++) { atv.drive(true, false, 1, 1); atv.suspension(0,0,0,0,true,0,0); }
        double travelYaw = Math.toDegrees(Math.atan2(-atv.vx,atv.vz));
        check(atv.yaw < -20 && Math.abs(travelYaw-atv.yaw)>3, "carves with lateral tire slip");
        check(atv.roll < -2 && atv.roll > -18, "body rolls under cornering load");
        double yaw=atv.yaw, speed=Math.hypot(atv.vx,atv.vz);
        for (int i=0; i<20; i++) atv.drive(false, false, 1, -1);
        check(atv.yaw==yaw && Math.hypot(atv.vx,atv.vz)<speed, "no air steering or thrust");
        var wet = new AtvDynamics();
        for (int i=0; i<40; i++) wet.drive(true,true,1,1);
        check(wet.vx==0 && wet.vz==0, "no underwater propulsion");
        var spring = new AtvDynamics();
        for (int i=0; i<100; i++) spring.suspension(0,0,0,0,true,0,0);
        check(spring.heave==0, "no fake idle bobbing");
        spring.suspension(0,.8,0,0,true,0,0);
        check(spring.heave<-.08, "landing visibly compresses chassis");
        double crest=0;
        for (int i=0; i<80; i++) { spring.suspension(0,0,0,0,true,0,0); crest=Math.max(crest,spring.heave); }
        check(crest>.01 && Math.abs(spring.heave)<.001, "spring rebounds then settles");
        check(AtvDynamics.bumpLaunch(1,cruise)>.25 && AtvDynamics.bumpLaunch(1,.1)==0,
                "fast bumps launch; slow crawling stays planted");
        var climb = new AtvDynamics();
        climb.suspension(1,0,0,0,true,.1,1);
        check(climb.pitch < -.5 && climb.pitch >= -4, "slow climb starts nose lift without a pose snap");
        for (int i=0; i<45; i++) climb.suspension(0,0,0,0,false,.4,.2);
        check(Math.abs(climb.pitch + Math.toDegrees(Math.atan2(.2,.4))) < 1,
                "airborne pitch follows ascending trajectory");
        for (int i=0; i<45; i++) climb.suspension(0,0,0,0,false,.4,-.2);
        check(climb.pitch > 25, "descending trajectory lowers nose");
        for (int i=0; i<80; i++) climb.suspension(0,0,0,0,true,0,0);
        check(Math.abs(climb.pitch) < .001, "landing settles back to level");
        climb.suspension(1,0,0,0,true,-.1,1);
        check(climb.pitch > .5 && climb.pitch <= 4, "reverse climb starts rear lift without a pose snap");
        for (int i=0; i<120; i++) climb.suspension(0,0,0,0,false,0,-1);
        check(Math.abs(climb.pitch) < .001, "stationary fall does not tip vertically");
        // A step's impulse must carry through the next tick, rather than switching to
        // a flat pose as soon as the collision box has completed its instant step-up.
        var momentum = new AtvDynamics();
        momentum.suspension(1,0,0,0,true,.1,1);
        double firstPitch = momentum.pitch;
        momentum.suspension(0,0,0,0,true,.1,0);
        check(momentum.pitch < firstPitch, "climb angular momentum survives the step tick");
        double[] x = {-.8925,.8925,-.8925,.8925}, z = {1.06875,1.06875,-1.06875,-1.06875};
        double[] contacts = {1,1,0,0};
        var articulated = new AtvDynamics();
        for (int i=0; i<300; i++) {
            double oldPitch = articulated.pitch, oldRoll = articulated.roll;
            boolean ground = i % 60 < 35;
            articulated.suspension(i%60==0 ? 1 : 0,i%60==20 ? .8 : 0,
                    i%60<15 ? -25 : 0, i%60<15 ? 18 : 0,ground,.4,i%60<15 ? .2 : -.2);
            articulated.wheels(contacts,x,z,.4473,ground);
            check(Math.abs(articulated.pitch-oldPitch)<=4.00001, "continuous bounded pitch through contact transitions");
            check(Math.abs(articulated.roll-oldRoll)<=3.00001, "continuous bounded roll through contact transitions");
            for (double travel : articulated.wheelTravel) check(travel>=-AtvDynamics.WHEEL_DROOP
                    && travel<=AtvDynamics.WHEEL_COMPRESSION, "wheel stays within chassis-local mechanical stops");
        }
        for (int i=0; i<160; i++) {
            articulated.suspension(0,0,0,0,true,0,0);
            articulated.wheels(new double[4],x,z,.4473,true);
        }
        for (double travel : articulated.wheelTravel) check(Math.abs(travel)<.0001, "wheel travel settles on flat ground");
        for (int i=0; i<60; i++) articulated.wheels(new double[]{Double.NaN,Double.NaN,Double.NaN,Double.NaN},x,z,.4473,false);
        for (double travel : articulated.wheelTravel) check(Math.abs(travel+AtvDynamics.WHEEL_DROOP)<.0001,
                "airborne wheels settle at finite droop without detaching");
        for (int i=0; i<10000; i++) {
            spring.drive(i%31<23, false, Math.sin(i*.13), Math.cos(i*.07));
            spring.suspension(i%7==0 ? 1 : 0, i%11==0 ? 1.4 : 0, Math.sin(i)*90, Math.cos(i)*90, true,0,0);
            check(Double.isFinite(spring.heave+spring.pitch+spring.roll+spring.vx+spring.vz), "finite under repeated impacts");
            check(Math.abs(spring.heave)<=.18 && Math.abs(spring.pitch)<=55 && Math.abs(spring.roll)<=21, "bounded suspension");
        }
        System.out.printf("ATV dynamics passed: cruise %.2f blocks/s; full-block hop impulse %.3f blocks/tick.%n", cruise*20, AtvDynamics.bumpLaunch(1,cruise));
    }
}
