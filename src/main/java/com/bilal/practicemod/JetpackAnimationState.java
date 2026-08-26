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
    // The hover engine hitch: a smaller, slower nozzle twitch than a fuel cough, decaying over the
    // same ~8 ticks as the physical impulse that fired it (JetpackHoverHitch) so the model settles
    // exactly as the pack does.
    private static final float HITCH_AMPLITUDE = 0.07F;
    private static final float HITCH_DECAY = 0.45F;
    private static final float IMPULSE_LIFETIME_TICKS = 30.0F;

    // Floor under the vibration channel while hovering, so the continuous hum has a visible
    // counterpart on the tanks even when heat has settled at the low hover ceiling.
    private static final float HOVER_IDLE_VIBRATION = 0.18F;

    // Oscillator rates, in cycles per tick.
    private static final float BREATHE_RATE = 1.0F / 80.0F; // one idle breath every 4 seconds
    private static final float SPUTTER_RATE = 0.16F;        // ~3.2 Hz engine cough
    private static final float SHIMMER_RATE = 0.05F;        // ~1 Hz heat pulse on open vents

    // Cooling fan angular speed in radians/tick. Heat progressively loads the fan; a latched
    // overheat adds the emergency surge used by the SCAVPACK's thermal-bloom sequence.
    private static final float FAN_IDLE_SPEED = 0.06F;
    private static final float FAN_HEAT_SPEED = 0.40F;
    private static final float FAN_EMERGENCY_SPEED = 0.90F;

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

    // Smoothed world-space horizontal velocity in blocks/tick, feeding the directional body lean.
    // The VECTOR is what gets smoothed, never a derived heading angle: an angle wraps, so smoothing
    // it would snap a full 180 degrees the instant travel reverses. Same first-order smoother and
    // the same time constant as the cruise factor above. prev+cur for frame lerp.
    private float leanVxPrev;
    private float leanVxCur;
    private float leanVzPrev;
    private float leanVzCur;

    // Hover climb/descend attitude: realized vertical speed normalised against hoverClimbRate and
    // clamped, so -1 is a full-rate descent, +1 a full-rate climb and 0 a held altitude. Same
    // first-order smoother and time constant as the two above, and a smoother rather than a Channel
    // for the same reason - the target moves every tick. prev+cur for frame lerp.
    private float verticalPrev;
    private float verticalCur;

    // Render-pass scratch: written once per frame by the setupRotations hook (which has the
    // partial tick and evaluates all pose guards), read by the setupAnim hook later in the same
    // render() call. Not part of the ticked simulation state.
    private float renderHoverWeight;
    private float renderCruiseWeight;
    private float renderBodyPitchDeg; // forward component of the lean, in degrees (Lf)
    private float renderBodyRollDeg;  // rightward component of the lean, in degrees (Lr)
    private float renderVerticalTrim; // sampled hover climb/descend factor, -1..1, for the limb pass

    // Last body-local travel direction (unit forward/right) the lean used, held while measured
    // speed is under the epsilon so a near-standstill cannot flicker the lean direction. Pure
    // forward until the wearer actually travels somewhere. Also render-pass scratch.
    private float lastLeanDirForward = 1.0F;
    private float lastLeanDirRight;

    // Impulse clocks: ticks since fired, or -1 when inactive. Aged at the START of tick() so a
    // freshly fired impulse samples at age ~0 for the whole following frame interval.
    private float kickAge = -1.0F;
    private float shudderAge = -1.0F;
    private float shudderAmplitude = 1.0F;
    private float coughAge = -1.0F;
    private int coughSide = 1; // +1 = left nozzle, -1 = right nozzle; alternates per cough
    private float hitchAge = -1.0F;
    private int hitchSide = 1; // +1 = left nozzle, -1 = right nozzle; alternates per hitch

    private float breathePhase;
    private float sputterPhase;
    private float shimmerPhase;
    private float coolingFanPrev;
    private float coolingFanCur;

    private int tickCounter;
    private boolean prevThrusting;
    private boolean prevOverheated;

    // Which powered mode the current unpowered flight is following through from. Only a surge earns
    // the flat coast attitude; a cut from ordinary glide thrust keeps the upright posture it had.
    // Latched while thrusting and cleared when flight ends entirely.
    private boolean coastFromSurge;

    // One-tick event flags, valid between a tick() call and the next; the controller reads them
    // right after ticking to fire the matching particles and sounds off the same edge.
    private boolean firedIgnition;
    private boolean firedStall;
    private boolean firedVentOpen;
    private boolean firedPop;
    private boolean firedHitch;

    // Advances one game tick. `thrusting` is the EFFECTIVE thrust (wants && fuel && !overheated),
    // `gliding` the near-ground hover-boot cushion, `surging` the look-directed Superman mode,
    // `coasting` the unpowered momentum glide after a thrust cut, `hovering` the engaged hover-mode
    // altitude hold, `lowFuel` means the tank is in its last ~10%, `fuelEmpty` means nothing left to
    // burn at all. `hitch` is a one-shot: the hover pack stumbled this tick. `horizontalSpeed` is in
    // blocks/tick, derived from position deltas so it is equally valid for remote players; `dx`/`dz`
    // are the signed world-space components of that same delta, which the directional lean needs (the
    // magnitude alone cannot tell a strafe from a forward run). `dy` is the vertical component of the
    // same delta, driving the hover climb/descend attitude.
    public void tick(boolean wearing, boolean thrusting, boolean gliding, boolean surging, boolean coasting,
            boolean hovering, boolean hitch, float heat, boolean overheated, boolean lowFuel, boolean fuelEmpty,
            float horizontalSpeed, float dx, float dz, float dy) {
        this.firedIgnition = false;
        this.firedStall = false;
        this.firedVentOpen = false;
        this.firedPop = false;
        this.firedHitch = false;
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
        if (this.hitchAge >= 0.0F) {
            this.hitchAge = this.hitchAge >= IMPULSE_LIFETIME_TICKS ? -1.0F : this.hitchAge + 1.0F;
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
                    // Release follow-through: ease shut with a light residual shudder. Toggling hover
                    // on mid-burn lands here too; hover uses cyan rings alone, so the flare closes.
                    this.flare.forceEaseTo(0.0F, RELEASE_TICKS);
                    this.shudderAge = 0.0F;
                    this.shudderAmplitude = 1.0F;
                }
            } else {
                this.flare.easeTo(thrusting ? 1.0F : 0.0F,
                        thrusting ? IGNITION_BLOOM_TICKS : RELEASE_TICKS);
            }

            // One hitch = one event, exactly like a sputter cough: this flag drives the nozzle
            // twitch (via hitchAge), the smoke puff and the sputter sound, all on the same tick and
            // the same side. The impulse itself was applied by JetpackHoverHitch on the owning
            // client; remote viewers reach here from the synced flag.
            if (hitch) {
                this.firedHitch = true;
                this.hitchSide = -this.hitchSide;
                this.hitchAge = 0.0F;
            }

            // The sag pose holds while there is nothing to burn and relaxes once fuel returns.
            if (!fuelEmpty) {
                this.sag.easeTo(0.0F, SAG_RELAX_TICKS);
            }

            // Vibration is driven by continuous values, not the binary flag: heat sets the floor,
            // active thrust raises it. Hovering gets a floor of its own - the pack is holding the
            // player up, and hover heat settles low enough that heat alone would leave it still.
            float vibrationTarget = thrusting ? Math.max(heat, 0.25F)
                    : hovering ? Math.max(heat, HOVER_IDLE_VIBRATION)
                    : heat * 0.3F;
            this.vibration.easeTo(vibrationTarget, VIBRATION_EASE_TICKS);

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

        // A coast is still flight, and so is a glide, so the pose stays fully engaged through both -
        // cutting the throttle at speed must never snap the body back to the vanilla falling pose.
        // Gliding matters most for the boost/dip rhythm of light thrust: thrust arrives as a
        // sawtooth of short pulses, and gating on `thrusting` alone would drop the pose in every gap
        // between pulses, flashing the vanilla run cycle through. Gliding already means worn,
        // airborne, fuelled and not thrusting, so it covers exactly those gaps and goes false on
        // landing, which is where the blend-out belongs. Even a fuel stall leaves the body flying if
        // there's momentum left to fly on (the harsh cut still hits the nozzles).
        boolean flying = thrusting || gliding || coasting || hovering;

        // Which mode an unpowered stretch is following through FROM. Latched under power and held
        // across the whole coast; cleared only once flight has ended, so it survives the gaps in a
        // pulsed burn instead of resetting between them.
        if (thrusting) {
            this.coastFromSurge = surging;
        } else if (!flying) {
            this.coastFromSurge = false;
        }

        // Body flight pose. Under power the attitude is MODE-driven, not speed-driven: the surge is
        // the flat Superman pose and the glide is deliberately upright, so the two modes stay
        // visually distinct at any speed. Unpowered, the mode that was just cut decides: a surge
        // follows through flat and rises toward upright as momentum bleeds off, while ordinary glide
        // thrust simply keeps standing. Speed alone must NOT decide it - holding a direction key
        // under glide thrust pins speed at the cap, which is past the full-cruise threshold, so a
        // speed-driven coast flopped the body flat the instant the throttle was released.
        float rawCruise;
        if (surging) {
            rawCruise = 1.0F;
        } else if (thrusting || hovering) {
            // Hover is upright for the same reason the glide is: it is the precise, stationary-ish
            // mode. Without this it would fall through to the speed-driven branch and blend most of
            // the way into the flat cruise pose just from crossing a room.
            rawCruise = 0.0F;
        } else if (!this.coastFromSurge) {
            // Coasting out of ordinary glide thrust - or an unpowered fall that was never under
            // power at all. Hold the upright posture the wearer had; the speed-driven blend below
            // belongs to the surge alone.
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
        float alpha = 1.0F - (float) Math.exp(-1.0F / smoothingTicks);
        this.cruisePrev = this.cruiseCur;
        this.cruiseCur += (rawCruise - this.cruiseCur) * alpha;

        // Same smoother, same time constant, applied to the velocity vector that aims the lean.
        this.leanVxPrev = this.leanVxCur;
        this.leanVzPrev = this.leanVzCur;
        this.leanVxCur += (dx - this.leanVxCur) * alpha;
        this.leanVzCur += (dz - this.leanVzCur) * alpha;

        // Hover climb/descend attitude, on the same smoother again. hoverClimbRate is the trim rate
        // the spring settles to when the key is held, so normalising against it puts a full-rate climb
        // at +1 and a full-rate descent at -1 whatever the config says. The cap only exists while a
        // world is loaded (same guard as the cruise blend); with nothing to normalise against, the
        // attitude simply stays out. Gated on `hovering` rather than left free-running so it eases
        // back to neutral when hover ends - a fall is not a hover descent, and must not pose like one.
        float climbRef = JetpackServerConfig.SPEC.isLoaded()
                ? (float) (double) JetpackServerConfig.HOVER_CLIMB_RATE.get()
                : 0.0F;
        float rawVertical = hovering && climbRef > 0.0F ? Mth.clamp(dy / climbRef, -1.0F, 1.0F) : 0.0F;
        this.verticalPrev = this.verticalCur;
        this.verticalCur += (rawVertical - this.verticalCur) * alpha;

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

        // Integrate speed into a phase instead of multiplying global time by the current speed.
        // That lets the fan accelerate and decelerate without teleporting to a different angle.
        this.coolingFanPrev = this.coolingFanCur;
        float fanSpeed = wearing
                ? FAN_IDLE_SPEED + FAN_HEAT_SPEED * this.vibration.cur
                        + FAN_EMERGENCY_SPEED * this.ventOpen.cur
                : 0.0F;
        this.coolingFanCur += fanSpeed;
        if (this.coolingFanCur > TWO_PI) {
            this.coolingFanCur -= TWO_PI;
            this.coolingFanPrev -= TWO_PI;
        }
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

    // Smoothed world-space horizontal velocity, blocks/tick. World space, not body-local: the
    // caller resolves it against the interpolated body yaw of the frame it is drawing.
    public float leanVelocityX(float partialTick) {
        return Mth.lerp(partialTick, this.leanVxPrev, this.leanVxCur);
    }

    public float leanVelocityZ(float partialTick) {
        return Mth.lerp(partialTick, this.leanVzPrev, this.leanVzCur);
    }

    // -1..1 hover climb/descend attitude: -1 full-rate descent, +1 full-rate climb, 0 holding.
    public float verticalTrim(float partialTick) {
        return Mth.lerp(partialTick, this.verticalPrev, this.verticalCur);
    }

    // --- render-pass scratch (see field comment) ---

    // The body lean as its two degree components: forward (face-down positive) and rightward, plus
    // the vertical trim the limb pass needs (it has no partial tick of its own to sample with).
    public void setRenderPose(float hoverWeight, float cruiseWeight, float bodyPitchDeg, float bodyRollDeg,
            float verticalTrim) {
        this.renderHoverWeight = hoverWeight;
        this.renderCruiseWeight = cruiseWeight;
        this.renderBodyPitchDeg = bodyPitchDeg;
        this.renderBodyRollDeg = bodyRollDeg;
        this.renderVerticalTrim = verticalTrim;
    }

    public void clearRenderPose() {
        setRenderPose(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
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

    public float renderBodyRollDeg() {
        return this.renderBodyRollDeg;
    }

    public float renderVerticalTrim() {
        return this.renderVerticalTrim;
    }

    // Held travel direction in body-local space (unit vector: forward, right).
    public float lastLeanDirForward() {
        return this.lastLeanDirForward;
    }

    public float lastLeanDirRight() {
        return this.lastLeanDirRight;
    }

    public void setLastLeanDir(float forward, float right) {
        this.lastLeanDirForward = forward;
        this.lastLeanDirRight = right;
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

    // One-sided nozzle twitch for the current hover engine hitch. Same shape as the cough, smaller
    // and slower, so it settles in step with the physical impulse instead of ahead of it.
    public float hitchTwitch(float partialTick) {
        if (this.hitchAge < 0.0F) {
            return 0.0F;
        }
        return HITCH_AMPLITUDE * (float) Math.exp(-(this.hitchAge + partialTick) * HITCH_DECAY);
    }

    public float breathe(float partialTick) {
        return Mth.sin(TWO_PI * (this.breathePhase + partialTick * BREATHE_RATE));
    }

    public float shimmer(float partialTick) {
        return Mth.sin(TWO_PI * (this.shimmerPhase + partialTick * SHIMMER_RATE));
    }

    public float coolingFanSpin(float partialTick) {
        return Mth.lerp(partialTick, this.coolingFanPrev, this.coolingFanCur);
    }

    // Continuous per-player time, for the jitter noise.
    public float time(float partialTick) {
        return this.tickCounter + partialTick;
    }

    public int coughSide() {
        return this.coughSide;
    }

    public int hitchSide() {
        return this.hitchSide;
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

    public boolean hitchFired() {
        return this.firedHitch;
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
