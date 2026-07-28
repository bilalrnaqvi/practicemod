package com.bilal.practicemod;

import net.minecraft.util.Mth;

// Per-player, client-only animation state for the worn jetpack. This is a pure READOUT of the
// flight simulation: it is fed the sim's outputs once per tick (tick(), from
// JetpackAnimationController) and sampled per frame (the float getters, from
// JetpackModel.applyAnimation). Nothing here ever writes back to input, physics, fuel, or heat.
//
// Motion is built from three primitives, chosen so every reachable state pair transitions smoothly:
//  - Channels: values that ease toward a target with cubic slow-in/slow-out. Retargeting always
//    starts from the current value, so interrupting a transition mid-flight (sputter -> overheat,
//    stall -> refuel -> ignition, ...) is itself a smooth transition - geometry can never teleport.
//  - Impulses: decaying kicks (ignition spike, shutdown shudder, sputter cough) that make a state
//    change visible on the very next rendered frame, while the channel underneath blooms on its
//    slower eased curve.
//  - Phases: free-running oscillators (idle breathing, sputter strobe, vent heat-shimmer).
//
// Ticks store prev+cur so frame sampling is a cheap lerp by partialTick; sampling does a handful of
// float ops and allocates nothing.
public final class JetpackAnimationState {

    // Transition durations, in ticks (20/s). Ignition blooms fast, release follows through slower,
    // a fuel stall cuts almost instantly (deliberately harsh), vents are ponderous.
    private static final float IGNITION_BLOOM_TICKS = 6.0F;
    private static final float RELEASE_TICKS = 8.0F;
    private static final float STALL_CUT_TICKS = 3.0F;
    private static final float SAG_DROP_TICKS = 4.0F;
    private static final float SAG_RELAX_TICKS = 16.0F;
    private static final float VENT_OPEN_TICKS = 12.0F;
    private static final float VENT_CLOSE_TICKS = 15.0F;
    private static final float GLIDE_ENGAGE_TICKS = 8.0F;
    private static final float GLIDE_RELEASE_TICKS = 6.0F;
    private static final float VIBRATION_EASE_TICKS = 8.0F;
    private static final float SPUTTER_BLEND_TICKS = 4.0F;
    private static final float UNEQUIP_SETTLE_TICKS = 12.0F;

    // Impulse shapes. The ignition kick adds ~0.35 flare on the first frame after the edge and dies
    // within ~3 ticks; the shudder is a decaying sine lasting ~10 ticks (double amplitude on stall).
    private static final float KICK_AMPLITUDE = 0.35F;
    private static final float KICK_DECAY = 1.2F;
    private static final float SHUDDER_DECAY = 0.35F;
    private static final float SHUDDER_FREQUENCY = 2.4F;
    private static final float COUGH_AMPLITUDE = 0.12F;
    private static final float COUGH_DECAY = 0.9F;
    private static final float IMPULSE_LIFETIME_TICKS = 30.0F;

    // Oscillator rates, in cycles per tick.
    private static final float BREATHE_RATE = 1.0F / 80.0F; // one idle breath every 4 seconds
    private static final float SPUTTER_RATE = 0.16F;        // ~3.2 Hz engine cough
    private static final float SHIMMER_RATE = 0.05F;        // ~1 Hz heat pulse on open vents

    // How deep the sputter strobe cuts into the flare (0.45 = down to 55% at the bottom of a cough).
    private static final float SPUTTER_DEPTH = 0.45F;

    private static final float TWO_PI = (float) (Math.PI * 2.0);

    // A value easing toward a target on a cubic slow-in/slow-out curve, ticked at game rate and
    // sampled per frame by lerping prev -> cur with the partial tick.
    private static final class Channel {
        float prev;
        float cur;
        float start;
        float target;
        float elapsed = 1.0F;
        float duration = 1.0F;

        // Starts a new tween unless one toward this target is already set (so per-tick retargeting
        // doesn't restart the ease every tick).
        void easeTo(float newTarget, float durationTicks) {
            if (this.target != newTarget) {
                forceEaseTo(newTarget, durationTicks);
            }
        }

        // Always restarts, for edges whose duration must win over an in-flight tween toward the
        // same target (a stall must cut in 3 ticks even if a release toward 0 is already running).
        void forceEaseTo(float newTarget, float durationTicks) {
            this.start = this.cur;
            this.target = newTarget;
            this.duration = Math.max(1.0F, durationTicks);
            this.elapsed = 0.0F;
        }

        void tick() {
            this.prev = this.cur;
            if (this.elapsed < this.duration) {
                this.elapsed += 1.0F;
                this.cur = this.start + (this.target - this.start) * easeInOutCubic(Math.min(1.0F, this.elapsed / this.duration));
            } else {
                this.cur = this.target;
            }
        }

        float value(float partialTick) {
            return Mth.lerp(partialTick, this.prev, this.cur);
        }
    }

    private final Channel flare = new Channel();       // 0..1 nozzle bloom; tracks effective thrust
    private final Channel vibration = new Channel();   // 0..1 tank shake amplitude; tracks heat
    private final Channel ventOpen = new Channel();    // 0..1 overheat flap swing
    private final Channel sag = new Channel();         // 0..1 out-of-fuel nozzle droop
    private final Channel sputterBlend = new Channel();// 0..1 how much the sputter strobe applies
    private final Channel poseWeight = new Channel();  // 0..1 body flight-pose engagement
    private final Channel glide = new Channel();       // 0..1 airbrake pose while gliding

    // Speed-driven hover<->cruise factor. Deliberately NOT a Channel: a Channel restarts its tween
    // whenever the target changes, which stutters against a target that moves every tick; a
    // first-order exponential smoother tracks continuous speed cleanly. prev+cur for frame lerp.
    private float cruisePrev;
    private float cruiseCur;

    // Render-pass scratch: written once per frame by the setupRotations hook (which has the
    // partial tick and evaluates all pose guards), read by the setupAnim hook later in the same
    // render() call. Not part of the ticked simulation state.
    private float renderHoverWeight;
    private float renderCruiseWeight;
    private float renderBodyPitchDeg;

    // Impulse clocks: ticks since fired, or -1 when inactive. Aged at the START of tick() so a
    // freshly fired impulse samples at age ~0 for the whole following frame interval.
    private float kickAge = -1.0F;
    private float shudderAge = -1.0F;
    private float shudderAmplitude = 1.0F;
    private float coughAge = -1.0F;
    private int coughSide = 1; // +1 = left nozzle, -1 = right nozzle; alternates per cough

    private float breathePhase;
    private float sputterPhase;
    private float shimmerPhase;

    private int tickCounter;
    private boolean prevThrusting;
    private boolean prevOverheated;

    // One-tick event flags, valid between a tick() call and the next; the controller reads them
    // right after ticking to fire the matching particles and sounds off the same edge.
    private boolean firedIgnition;
    private boolean firedStall;
    private boolean firedVentOpen;
    private boolean firedPop;

    // Advances one game tick. `thrusting` is the EFFECTIVE thrust (wants && fuel && !overheated),
    // `gliding` the near-ground hover-boot cushion, `surging` the look-directed Superman mode,
    // `coasting` the unpowered momentum glide after a thrust cut, `lowFuel` means the tank is in its
    // last ~10%, `fuelEmpty` means nothing left to burn at all. `horizontalSpeed` is in blocks/tick,
    // derived from position deltas so it is equally valid for remote players.
    public void tick(boolean wearing, boolean thrusting, boolean gliding, boolean surging, boolean coasting,
            float heat, boolean overheated, boolean lowFuel, boolean fuelEmpty, float horizontalSpeed) {
        this.firedIgnition = false;
        this.firedStall = false;
        this.firedVentOpen = false;
        this.firedPop = false;
        this.tickCounter++;

        if (this.kickAge >= 0.0F) {
            this.kickAge = this.kickAge >= IMPULSE_LIFETIME_TICKS ? -1.0F : this.kickAge + 1.0F;
        }
        if (this.shudderAge >= 0.0F) {
            this.shudderAge = this.shudderAge >= IMPULSE_LIFETIME_TICKS ? -1.0F : this.shudderAge + 1.0F;
        }
        if (this.coughAge >= 0.0F) {
            this.coughAge = this.coughAge >= IMPULSE_LIFETIME_TICKS ? -1.0F : this.coughAge + 1.0F;
        }

        if (!wearing) {
            this.flare.easeTo(0.0F, UNEQUIP_SETTLE_TICKS);
            this.vibration.easeTo(0.0F, UNEQUIP_SETTLE_TICKS);
            this.ventOpen.easeTo(0.0F, VENT_CLOSE_TICKS);
            this.sag.easeTo(0.0F, SAG_RELAX_TICKS);
            this.sputterBlend.easeTo(0.0F, SPUTTER_BLEND_TICKS);
            this.glide.easeTo(0.0F, GLIDE_RELEASE_TICKS);
            this.prevThrusting = false;
            this.prevOverheated = false;
        } else {
            boolean ignition = thrusting && !this.prevThrusting;
            boolean cut = !thrusting && this.prevThrusting;

            if (ignition) {
                // Instant kick (impulse, visible next frame), then the channel blooms underneath.
                this.kickAge = 0.0F;
                this.firedIgnition = true;
                this.flare.forceEaseTo(1.0F, IGNITION_BLOOM_TICKS);
            } else if (cut) {
                if (fuelEmpty) {
                    // Stall: the one transition that SHOULD feel harsh - near-instant cut,
                    // double-strength shudder, nozzles drop into the sag pose.
                    this.firedStall = true;
                    this.flare.forceEaseTo(0.0F, STALL_CUT_TICKS);
                    this.sag.forceEaseTo(1.0F, SAG_DROP_TICKS);
                    this.shudderAge = 0.0F;
                    this.shudderAmplitude = 2.0F;
                } else {
                    // Release follow-through: ease shut with a light residual shudder.
                    this.flare.forceEaseTo(0.0F, RELEASE_TICKS);
                    this.shudderAge = 0.0F;
                    this.shudderAmplitude = 1.0F;
                }
            } else {
                this.flare.easeTo(thrusting ? 1.0F : 0.0F, thrusting ? IGNITION_BLOOM_TICKS : RELEASE_TICKS);
            }

            // The sag pose holds while there is nothing to burn and relaxes once fuel returns.
            if (!fuelEmpty) {
                this.sag.easeTo(0.0F, SAG_RELAX_TICKS);
            }

            // Vibration is driven by continuous values, not the binary flag: heat sets the floor,
            // active thrust raises it.
            this.vibration.easeTo(thrusting ? Math.max(heat, 0.25F) : heat * 0.3F, VIBRATION_EASE_TICKS);

            if (overheated && !this.prevOverheated) {
                this.firedVentOpen = true;
            }
            this.ventOpen.easeTo(overheated ? 1.0F : 0.0F, overheated ? VENT_OPEN_TICKS : VENT_CLOSE_TICKS);

            // Airbrake pose: engage slightly slower than it releases, so entering a glide reads as
            // a deliberate deployment while resuming thrust snaps the brakes shut. If fuel dies
            // mid-glide, sag eases in (4t) while this eases out (6t) - both are additive on the
            // nozzles, so the handoff blends instead of popping.
            this.glide.easeTo(gliding ? 1.0F : 0.0F, gliding ? GLIDE_ENGAGE_TICKS : GLIDE_RELEASE_TICKS);

            boolean sputtering = thrusting && lowFuel;
            this.sputterBlend.easeTo(sputtering ? 1.0F : 0.0F, SPUTTER_BLEND_TICKS);
            if (sputtering) {
                this.sputterPhase += SPUTTER_RATE;
                if (this.sputterPhase >= 1.0F) {
                    // One cough = one event: this flag drives the nozzle twitch (via coughAge),
                    // the smoke pop, and the hiss, all on the same tick and the same side.
                    this.sputterPhase -= 1.0F;
                    this.firedPop = true;
                    this.coughSide = -this.coughSide;
                    this.coughAge = 0.0F;
                }
            }

            this.prevThrusting = thrusting;
            this.prevOverheated = overheated;
        }

        // Body flight pose. Under power the attitude is MODE-driven, not speed-driven: the surge is
        // the flat Superman pose and the glide is deliberately upright, so the two modes stay
        // visually distinct at any speed. Unpowered (coasting, or following through after a cut) it
        // falls back to tracking actual speed, which is what makes a coast read right - you follow
        // through flat out of a surge and rise toward upright as the momentum bleeds off.
        float rawCruise;
        if (surging) {
            rawCruise = 1.0F;
        } else if (thrusting) {
            rawCruise = 0.0F;
        } else {
            // Both thresholds are FRACTIONS of the server's horizontal cap, so normalise the measured
            // speed against it before the blend - an absolute threshold above the cap is unreachable
            // and would pin the pose upright. The cap lives in the server config, which is only loaded
            // while in a world (same guard as JetpackFuelHandler.getFuelCapacity); with nothing to
            // normalise against, hold the cruise factor where it is.
            float cap = JetpackServerConfig.SPEC.isLoaded()
                    ? (float) (double) JetpackServerConfig.GLIDE_HORIZONTAL_CAP.get()
                    : 0.0F;
            if (cap <= 0.0F) {
                rawCruise = this.cruiseCur;
            } else {
                float cruiseStart = (float) (double) JetpackClientConfig.CRUISE_SPEED_THRESHOLD.get();
                float cruiseFull = Math.max(cruiseStart + 0.01F, (float) (double) JetpackClientConfig.CRUISE_FULL_SPEED.get());
                // Clamped to 1: the afterburner multiplies the cap, so an afterburning run can exceed
                // it - that should pin the pose at full cruise, not overshoot past it.
                float speedFraction = Math.min(1.0F, horizontalSpeed / cap);
                float speedT = Mth.clamp((speedFraction - cruiseStart) / (cruiseFull - cruiseStart), 0.0F, 1.0F);
                rawCruise = speedT * speedT * (3.0F - 2.0F * speedT); // smoothstep
            }
        }
        float smoothingTicks = Math.max(1.0F, (float) (double) JetpackClientConfig.SPEED_SMOOTHING_TICKS.get());
        this.cruisePrev = this.cruiseCur;
        this.cruiseCur += (rawCruise - this.cruiseCur) * (1.0F - (float) Math.exp(-1.0F / smoothingTicks));

        // A coast is still flight, so the pose stays fully engaged through it - cutting the throttle
        // at speed must never snap the body back to the vanilla falling pose. The blend-out only
        // starts once the coast itself has decayed, and even a fuel stall leaves the body flying if
        // there's momentum left to fly on (the harsh cut still hits the nozzles).
        boolean flying = thrusting || coasting;
        if (this.firedStall && !coasting) {
            // Fuel died mid-thrust with nothing left to carry: drop the body on the same harsh cut
            // as the nozzles.
            this.poseWeight.forceEaseTo(0.0F, STALL_CUT_TICKS);
        } else {
            // Ease in fast on thrust, out slower (follow-through) when the flight ends.
            this.poseWeight.easeTo(flying ? 1.0F : 0.0F,
                    flying ? (float) (double) JetpackClientConfig.BLEND_IN_TICKS.get()
                            : (float) (double) JetpackClientConfig.BLEND_OUT_TICKS.get());
        }

        this.breathePhase = wrap(this.breathePhase + BREATHE_RATE);
        this.shimmerPhase = wrap(this.shimmerPhase + SHIMMER_RATE);

        this.flare.tick();
        this.vibration.tick();
        this.ventOpen.tick();
        this.sag.tick();
        this.sputterBlend.tick();
        this.poseWeight.tick();
        this.glide.tick();
    }

    // --- per-frame sampling (pure math, no allocation) ---

    // Nozzle bloom including the ignition kick spike and the sputter strobe. Can briefly exceed 1
    // during the kick; clamped so scale math stays sane.
    public float flare(float partialTick) {
        float value = this.flare.value(partialTick);
        if (this.kickAge >= 0.0F) {
            value += KICK_AMPLITUDE * (float) Math.exp(-(this.kickAge + partialTick) * KICK_DECAY);
        }
        float blend = this.sputterBlend.value(partialTick);
        if (blend > 0.001F) {
            float wave = 0.5F + 0.5F * Mth.sin(TWO_PI * (this.sputterPhase + partialTick * SPUTTER_RATE));
            value *= 1.0F - blend * SPUTTER_DEPTH * (1.0F - wave);
        }
        return Mth.clamp(value, 0.0F, 1.2F);
    }

    public float vibration(float partialTick) {
        return this.vibration.value(partialTick);
    }

    // 0..1 engagement of the body flight pose (0 = vanilla pose, 1 = fully in hover/cruise).
    public float poseWeight(float partialTick) {
        return this.poseWeight.value(partialTick);
    }

    // 0..1 hover-to-cruise blend, driven by smoothed horizontal speed.
    public float cruiseFactor(float partialTick) {
        return Mth.lerp(partialTick, this.cruisePrev, this.cruiseCur);
    }

    // --- render-pass scratch (see field comment) ---

    public void setRenderPose(float hoverWeight, float cruiseWeight, float bodyPitchDeg) {
        this.renderHoverWeight = hoverWeight;
        this.renderCruiseWeight = cruiseWeight;
        this.renderBodyPitchDeg = bodyPitchDeg;
    }

    public void clearRenderPose() {
        setRenderPose(0.0F, 0.0F, 0.0F);
    }

    public float renderHoverWeight() {
        return this.renderHoverWeight;
    }

    public float renderCruiseWeight() {
        return this.renderCruiseWeight;
    }

    public float renderBodyPitchDeg() {
        return this.renderBodyPitchDeg;
    }

    public float ventOpen(float partialTick) {
        return this.ventOpen.value(partialTick);
    }

    public float sag(float partialTick) {
        return this.sag.value(partialTick);
    }

    // 0..1 airbrake deployment while gliding.
    public float glide(float partialTick) {
        return this.glide.value(partialTick);
    }

    // Decaying-sine shutdown shudder, signed (it oscillates around zero).
    public float shudder(float partialTick) {
        if (this.shudderAge < 0.0F) {
            return 0.0F;
        }
        float age = this.shudderAge + partialTick;
        return this.shudderAmplitude * (float) Math.exp(-age * SHUDDER_DECAY) * Mth.sin(age * SHUDDER_FREQUENCY);
    }

    // One-sided nozzle dip for the current sputter cough.
    public float coughTwitch(float partialTick) {
        if (this.coughAge < 0.0F) {
            return 0.0F;
        }
        return COUGH_AMPLITUDE * (float) Math.exp(-(this.coughAge + partialTick) * COUGH_DECAY);
    }

    public float breathe(float partialTick) {
        return Mth.sin(TWO_PI * (this.breathePhase + partialTick * BREATHE_RATE));
    }

    public float shimmer(float partialTick) {
        return Mth.sin(TWO_PI * (this.shimmerPhase + partialTick * SHIMMER_RATE));
    }

    // Continuous per-player time, for the jitter noise.
    public float time(float partialTick) {
        return this.tickCounter + partialTick;
    }

    public int coughSide() {
        return this.coughSide;
    }

    public boolean ignitionFired() {
        return this.firedIgnition;
    }

    public boolean stallFired() {
        return this.firedStall;
    }

    public boolean ventOpenFired() {
        return this.firedVentOpen;
    }

    public boolean popFired() {
        return this.firedPop;
    }

    // Deterministic two-sine pseudo-noise in roughly [-1.5, 1.5]: frame-stable (a pure function of
    // time, so no per-frame randomness flicker) and allocation-free, unlike RandomSource.
    public static float noise(float time) {
        return Mth.sin(time * 0.9F) + 0.5F * Mth.sin(time * 2.3F);
    }

    private static float easeInOutCubic(float t) {
        if (t < 0.5F) {
            return 4.0F * t * t * t;
        }
        float inv = -2.0F * t + 2.0F;
        return 1.0F - inv * inv * inv * 0.5F;
    }

    private static float wrap(float phase) {
        return phase >= 1.0F ? phase - 1.0F : phase;
    }
}
