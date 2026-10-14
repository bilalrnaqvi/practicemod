package com.bilal.practicemod;

import static com.bilal.practicemod.SkateboardDynamics.*;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Server-simulated arcade skating. Packets carry controls, never velocity or points. */
public class SkateboardEntity extends Entity {
    public static final double NORMAL_SPEED = CRUISE_SPEED;
    public static final double FAST_SPEED = SkateboardDynamics.FAST_SPEED;
    public static final int GROUND = 0, AIR = 1, MANUAL_STATE = 2, NOSE_MANUAL = 3,
            GRIND_STATE = 4, WALLRIDE = 5, BAIL = 6;
    private static final EntityDataAccessor<Byte> PUSH_MODE = SynchedEntityData.defineId(SkateboardEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(SkateboardEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(SkateboardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> BALANCE = SynchedEntityData.defineId(SkateboardEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPIN = SynchedEntityData.defineId(SkateboardEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(SkateboardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TRICK = SynchedEntityData.defineId(SkateboardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TRICK_TICKS = SynchedEntityData.defineId(SkateboardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> GRIND_STYLE = SynchedEntityData.defineId(SkateboardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BASE = SynchedEntityData.defineId(SkateboardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MULTIPLIER = SynchedEntityData.defineId(SkateboardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SPECIAL_METER = SynchedEntityData.defineId(SkateboardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RESULT = SynchedEntityData.defineId(SkateboardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> TOTAL = SynchedEntityData.defineId(SkateboardEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> BEST = SynchedEntityData.defineId(SkateboardEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<String> LINE = SynchedEntityData.defineId(SkateboardEntity.class, EntityDataSerializers.STRING);
    private final SkateboardAnimation animation = new SkateboardAnimation();
    private final SkateboardTricks tricks = new SkateboardTricks();
    private int input, previousInput, mode, charge, bailTicks, airTicks, landingTicks, railCooldown;
    private int manualQueue, queuedManualMode, balanceTicks, revertTicks, revertQueue, rampTicks, wallTicks;
    private int lastDirection, directionTick = -100, grindStyle, transitionCooldown;
    private boolean reverted;
    private float inputYaw, steering, airSpin, specialSpin, stanceSpin, balance, balanceVelocity;
    private long lastInputTick = Long.MIN_VALUE;
    private SkateboardRails.Rail rail;
    private int lerpSteps;
    private double targetX, targetY, targetZ;
    private float targetYaw, previousVisualSpin, visualSpin;

    public SkateboardEntity(EntityType<? extends SkateboardEntity> type, Level level) { super(type, level); blocksBuilding = true; }
    public SkateboardAnimation animation() { return animation; }
    public int pushMode() { return entityData.get(PUSH_MODE); }
    public int skateMode() { return entityData.get(MODE); }
    public float speed() { return entityData.get(SPEED); }
    public float balance() { return entityData.get(BALANCE); }
    public int charge() { return entityData.get(CHARGE); }
    public int grindStyle() { return entityData.get(GRIND_STYLE); }
    public int comboBase() { return entityData.get(BASE); }
    public int multiplier() { return entityData.get(MULTIPLIER); }
    public int specialMeter() { return entityData.get(SPECIAL_METER); }
    public int lastResult() { return entityData.get(RESULT); }
    public long totalScore() { return entityData.get(TOTAL); }
    public long bestCombo() { return entityData.get(BEST); }
    public String trickLine() { return entityData.get(LINE); }
    public SkateboardTricks.Trick currentTrick() { return SkateboardTricks.Trick.fromId(entityData.get(TRICK)); }
    public float trickProgress(float partial) {
        var trick = currentTrick();
        return trick == SkateboardTricks.Trick.NONE ? 0 : Mth.clamp(1 - (entityData.get(TRICK_TICKS) - partial) / trick.duration, 0, 1);
    }
    public float visualSpin(float partial) { return Mth.lerp(partial, previousVisualSpin, visualSpin); }
    public boolean balancing() { return skateMode() == MANUAL_STATE || skateMode() == NOSE_MANUAL || skateMode() == GRIND_STATE; }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PUSH_MODE, (byte) 0); builder.define(SPEED, 0F); builder.define(MODE, GROUND);
        builder.define(BALANCE, 0F); builder.define(SPIN, 0F); builder.define(CHARGE, 0);
        builder.define(TRICK, 0); builder.define(TRICK_TICKS, 0); builder.define(GRIND_STYLE, 0); builder.define(BASE, 0);
        builder.define(MULTIPLIER, 0); builder.define(SPECIAL_METER, 0); builder.define(RESULT, 0);
        builder.define(TOTAL, 0L); builder.define(BEST, 0L); builder.define(LINE, "");
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        tricks.restore(tag.getLong("SkateTotal"), tag.getLong("SkateBest")); publish(false);
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("SkateTotal", tricks.total()); tag.putLong("SkateBest", tricks.best());
    }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean canBeCollidedWith() { return isAlive(); }
    @Override public boolean canCollideWith(Entity other) { return !isPassengerOfSameVehicle(other) && other.canBeCollidedWith(); }
    @Override public float maxUpStep() { return mode == GRIND_STATE ? 0 : 0.6F; }
    @Override public boolean shouldRiderSit() { return false; }
    @Override protected boolean canAddPassenger(Entity passenger) { return passenger instanceof Player && getPassengers().isEmpty(); }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }

    public void acceptInput(Player sender, int flags, float steering, float yaw) {
        if (level().isClientSide || getFirstPassenger() != sender || !Float.isFinite(yaw) || !Float.isFinite(steering)) return;
        input = flags & INPUT_MASK;
        this.steering = Mth.clamp(steering, -1, 1);
        inputYaw = Mth.wrapDegrees(yaw);
        lastInputTick = level().getGameTime();
    }
    @Override public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive() || isVehicle()) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        inputYaw = getYRot();
        return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }
    @Override protected void positionRider(Entity passenger, MoveFunction move) {
        move.accept(passenger, getX(), getY() + 0.25, getZ()); passenger.resetFallDistance();
    }
    @Override protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        input = previousInput = charge = manualQueue = revertQueue = 0;
        steering = 0; lastInputTick = Long.MIN_VALUE; rail = null; mode = onGround() ? GROUND : AIR;
        if (!level().isClientSide) {
            if (tricks.active() || tricks.trick() != SkateboardTricks.Trick.NONE) tricks.bail();
            publish(false);
        }
    }
    private boolean held(int key) { return (input & key) != 0; }
    private boolean pressed(int key) { return held(key) && (previousInput & key) == 0; }
    private boolean manual() { return mode == MANUAL_STATE || mode == NOSE_MANUAL; }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            animation.tick(isVehicle(), pushMode(), speed() / (float) FAST_SPEED);
            int state = skateMode();
            float crouch = state == BAIL ? 6 : state == AIR ? (currentTrick().grab() ? 4 : 2.5F) : charge() * 3F / MAX_CHARGE;
            if (balancing()) crouch = Math.max(crouch, 1.8F);
            float pitch = state == MANUAL_STATE ? 14 : state == NOSE_MANUAL ? -14 : state == AIR ? 5 : 0;
            if (state == GRIND_STATE) pitch = grindStyle() == 2 ? -14 : grindStyle() == 3 ? 14 : 0;
            animation.tickAction(crouch, pitch, state == GRIND_STATE ? balance() * 12 : state == WALLRIDE ? 35 : 0);
            previousVisualSpin = visualSpin;
            float next = entityData.get(SPIN);
            if (skateMode() != AIR && skateMode() != WALLRIDE)
                previousVisualSpin = next - Mth.wrapDegrees(next - visualSpin);
            visualSpin = next;
            if (lerpSteps > 0) {
                setPos(getX() + (targetX - getX()) / lerpSteps, getY() + (targetY - getY()) / lerpSteps,
                        getZ() + (targetZ - getZ()) / lerpSteps);
                setYRot(getYRot() + Mth.wrapDegrees(targetYaw - getYRot()) / lerpSteps); lerpSteps--;
            }
            return;
        }
        boolean live = held(ACTIVE) && getFirstPassenger() instanceof Player && lastInputTick != Long.MIN_VALUE
                && level().getGameTime() - lastInputTick <= INPUT_TIMEOUT;
        if (!live) {
            input = previousInput = charge = manualQueue = revertQueue = 0; steering = 0;
            if (tricks.active() || tricks.trick() != SkateboardTricks.Trick.NONE) bail();
        }
        boolean completingSpecial = tricks.trick() == SkateboardTricks.Trick.SPECIAL_900 && tricks.trickTicks() == 1;
        tricks.tick(held(GRAB));
        if (completingSpecial) specialSpin += 900;
        if (railCooldown > 0) railCooldown--;
        if (transitionCooldown > 0) transitionCooldown--;
        if (manualQueue > 0) manualQueue--;
        if (rampTicks > 0) rampTicks--;
        if (landingTicks > 0) landingTicks--;
        if (revertTicks > 0 && --revertTicks == 0) stanceSpin = Mth.wrapDegrees(stanceSpin + 180);
        if (revertQueue > 0) revertQueue--;
        if (pressed(REVERT)) revertQueue = 6;
        if (bailTicks > 0) {
            bailTicks--;
            double y = Math.max(-1.5, getDeltaMovement().y - GRAVITY);
            move(MoverType.SELF, new Vec3(0, y, 0)); setDeltaMovement(0, onGround() ? 0 : y, 0);
            if (bailTicks == 0) mode = onGround() ? GROUND : AIR;
            previousInput = input; publish(false); return;
        }
        boolean wasGrounded = onGround() || rail != null;
        readManualGesture();
        if (held(OLLIE) && wasGrounded) charge = Math.min(MAX_CHARGE, charge + 1);
        boolean jump = !held(OLLIE) && (previousInput & OLLIE) != 0 && charge > 0 && wasGrounded;
        if (jump) {
            setDeltaMovement(getDeltaMovement().x, ollie(charge, tricks.specialReady()) + (rampTicks > 0 ? 0.12 : 0), getDeltaMovement().z);
            charge = 0; rail = null; railCooldown = 5; airSpin = specialSpin = 0; airTicks = 0; wallTicks = 0;
            mode = AIR; setOnGround(false); playSound(SoundEvents.WOOD_STEP, 0.6F, 1.5F);
        }
        double speed = getDeltaMovement().horizontalDistance();
        if (rail != null && !held(GRIND)) { rail = null; mode = AIR; railCooldown = 5; landingTicks = 6; }
        if (manual() && (!onGround() || speed < 0.07)) mode = onGround() ? GROUND : AIR;
        if (rail != null && speed < 0.07) { rail = null; mode = GROUND; }
        if (onGround() && rail == null && tryRevert()) speed *= 0.85;
        if (onGround() && !jump && manualQueue > 0 && speed > 0.07 && rail == null) enterManual();
        if (onGround() && rail == null && !manual()) {
            speed = SkateboardDynamics.speed(speed, live && (held(PUSH) || held(OLLIE)), held(BRAKE) || !live,
                    held(SPRINT), tricks.specialReady());
            if (live) setYRot(steer(getYRot(), inputYaw, speed));
        } else if (manual()) {
            speed *= 0.995;
            if (live) setYRot(steer(getYRot(), inputYaw, speed));
        } else if (rail != null) speed = Math.max(0, speed * 0.997);
        else speed *= 0.999;
        if (!isVehicle()) speed *= 0.7;
        if (manual() || rail != null) {
            float correction = rail != null ? steering : (held(BRAKE) ? 1 : 0) - (held(PUSH) ? 1 : 0);
            balanceVelocity = balanceVelocity(balanceVelocity, balance, correction, ++balanceTicks);
            balance += balanceVelocity;
            if (Math.abs(balance) >= 1) { bail(); publish(false); previousInput = input; return; }
            tricks.sustain(rail != null ? 8 : 4);
            if (rail != null && transitionCooldown == 0 && (pressed(FLIP) || pressed(GRAB))) {
                grindStyle = (grindStyle + 1) % 4; addGrind();
                transitionCooldown = 10;
            }
        }
        if (!onGround() && rail == null) {
            charge = 0;
            mode = AIR; airTicks++; airSpin += steering * 15; startAirTrick();
        }
        double radians = getYRot() * Mth.DEG_TO_RAD;
        double vertical = rail != null ? -0.08 : Math.max(-1.5, getDeltaMovement().y - GRAVITY);
        Vec3 movement = new Vec3(-Math.sin(radians) * speed, vertical, Math.cos(radians) * speed);
        if (rail != null) {
            var next = SkateboardRails.find(level(), position().add(movement.x, 0, movement.z), getYRot(), 0.18);
            if (next == null) { rail = null; mode = AIR; railCooldown = 5; airSpin = specialSpin = 0; airTicks = 0; landingTicks = 6; }
            else {
                rail = next;
                Vec3 centered = rail.center(position());
                movement = movement.add(Mth.clamp(centered.x - getX(), -0.18, 0.18), 0,
                        Mth.clamp(centered.z - getZ(), -0.18, 0.18));
            }
        }
        Vec3 before = position();
        setDeltaMovement(movement); move(MoverType.SELF, movement);
        double traveled = position().subtract(before).horizontalDistance();
        if (held(GRIND) && rail == null && railCooldown == 0 && !jump && vertical <= 0 && speed > 0.12) {
            var candidate = SkateboardRails.find(level(), position(), getYRot(), 0.32);
            if (candidate != null && before.y >= candidate.top() - 0.05
                    && level().noCollision(this, getBoundingBox().move(0, candidate.top() - getY() + 0.001, 0))) {
                if (!tricks.land(airSpin)) { bail(); publish(false); previousInput = input; return; }
                rail = candidate; mode = GRIND_STATE; setYRot(rail.yaw(getYRot()));
                setPos(getX(), rail.top(), getZ()); setOnGround(true);
                finishAirSpin(); airTicks = 0; resetBalance();
                grindStyle = held(PUSH) ? 2 : held(BRAKE) ? 3 : Math.abs(steering) > 0.1 ? 1 : 0;
                addGrind(); transitionCooldown = 10;
                playSound(SoundEvents.CHAIN_STEP, 0.6F, 1.4F);
            }
        }
        if (horizontalCollision && speed > 0.14 && !onGround() && rail == null && held(GRIND) && wallTicks < 18) {
            mode = WALLRIDE;
            if (wallTicks++ == 0) tricks.add("Wallride", 300);
            boolean hitX = Math.abs(getX() - before.x - movement.x) > 0.01;
            boolean hitZ = Math.abs(getZ() - before.z - movement.z) > 0.01;
            setDeltaMovement(hitX ? 0 : Math.copySign(speed, movement.x), Math.max(-0.06, movement.y),
                    hitZ ? 0 : Math.copySign(speed, movement.z));
            tricks.sustain(6);
            if (pressed(OLLIE)) {
                Vec3 kick = new Vec3(hitX ? -movement.x : movement.x, 0.58, hitZ ? -movement.z : movement.z);
                setDeltaMovement(kick); setYRot((float) Math.toDegrees(Math.atan2(-kick.x, kick.z)));
                tricks.add("Wallplant", 500); railCooldown = 5; wallTicks = 18;
            }
        } else if (horizontalCollision && traveled < speed * 0.45 && speed > 0.3 && isVehicle()) bail();
        if (bailTicks == 0 && onGround() && rail == null) {
            if (airTicks > 0) {
                if (!tricks.land(airSpin)) bail();
                else {
                    landingTicks = 6; reverted = false; tryRevert(); playSound(SoundEvents.WOOD_FALL, 0.65F, 1.15F);
                    if (manualQueue > 0 && speed > 0.07) enterManual();
                }
                airTicks = 0;
                if (bailTicks == 0) finishAirSpin();
            }
            if (bailTicks == 0) {
                if (!manual()) mode = GROUND;
                if (!manual() && landingTicks == 0) tricks.bank();
                setDeltaMovement(getDeltaMovement().x, 0, getDeltaMovement().z);
            }
        }
        if (onGround() && getY() - before.y > 0.2 && level().getBlockState(blockPosition().below()).is(BlockTags.STAIRS)) rampTicks = 5;
        if (wasGrounded && !onGround() && !jump && rampTicks > 0 && speed > 0.3) {
            setDeltaMovement(getDeltaMovement().x, 0.42 + speed * 0.2, getDeltaMovement().z); rampTicks = 0;
        }
        if (isInWater() || isInLava()) {
            if (tricks.active()) bail();
            setDeltaMovement(getDeltaMovement().multiply(0.6, 0.7, 0.6));
        }
        if (rail != null && tickCount % 3 == 0 && level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.08, getZ(), 2, 0.1, 0.04, 0.1, 0.03);
            if (tickCount % 12 == 0) playSound(SoundEvents.CHAIN_STEP, 0.35F, 1.6F);
        }
        previousInput = input;
        publish(live && onGround() && mode == GROUND && charge == 0 && held(PUSH) && traveled > 0.01);
    }
    private void readManualGesture() {
        int direction = pressed(PUSH) ? 1 : pressed(BRAKE) ? -1 : 0;
        boolean gesture = direction != 0 && direction == -lastDirection && tickCount - directionTick <= 6;
        if (direction != 0) { lastDirection = direction; directionTick = tickCount; }
        if (pressed(MANUAL) || (gesture && !manual())) {
            if (manual()) { mode = GROUND; manualQueue = 0; }
            else { manualQueue = 10; queuedManualMode = gesture && direction == 1 ? NOSE_MANUAL : MANUAL_STATE; }
        }
    }
    private void enterManual() {
        mode = queuedManualMode; manualQueue = 0; resetBalance();
        tricks.add(mode == NOSE_MANUAL ? "Nose Manual" : "Manual", 100);
    }
    private boolean tryRevert() {
        if (revertQueue > 0 && landingTicks > 0 && !reverted && tricks.active()) {
            tricks.add("Revert", 180); reverted = true; revertTicks = 8; landingTicks = 10; revertQueue = 0;
            setDeltaMovement(getDeltaMovement().multiply(0.85, 1, 0.85));
            return true;
        }
        return false;
    }
    private void addGrind() {
        String[] names = {"50-50 Grind", "Boardslide", "Nosegrind", "5-0 Grind"};
        tricks.add(names[grindStyle], grindStyle == 0 ? 200 : 300);
    }
    private void resetBalance() { balance = 0; balanceVelocity = 0; balanceTicks = 0; }
    private void finishAirSpin() {
        // A landed 180/900 changes stance instead of snapping the rider back to regular.
        stanceSpin = Mth.wrapDegrees(stanceSpin + Math.round((airSpin + specialSpin) / 180) * 180);
        airSpin = specialSpin = 0;
    }
    private void startAirTrick() {
        if (pressed(SPECIAL)) tricks.start(SkateboardTricks.Trick.SPECIAL_900);
        else if (pressed(FLIP)) tricks.start(held(PUSH) ? SkateboardTricks.Trick.TRE_FLIP
                : held(BRAKE) ? SkateboardTricks.Trick.SHUVIT
                : steering > 0 ? SkateboardTricks.Trick.HEELFLIP : SkateboardTricks.Trick.KICKFLIP);
        else if (pressed(GRAB)) tricks.start(held(PUSH) ? SkateboardTricks.Trick.NOSEGRAB
                : held(BRAKE) ? SkateboardTricks.Trick.TAILGRAB
                : steering > 0 ? SkateboardTricks.Trick.MELON : SkateboardTricks.Trick.INDY);
    }
    private void bail() {
        tricks.bail(); mode = BAIL; bailTicks = 22; rail = null;
        charge = manualQueue = airTicks = revertTicks = revertQueue = 0; airSpin = specialSpin = 0; resetBalance();
        setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0); playSound(SoundEvents.PLAYER_SMALL_FALL, 0.8F, 0.85F);
    }
    private void publish(boolean pushing) {
        entityData.set(PUSH_MODE, (byte) (pushing ? (held(SPRINT) ? 2 : 1) : 0));
        entityData.set(SPEED, (float) getDeltaMovement().horizontalDistance());
        entityData.set(MODE, mode); entityData.set(BALANCE, Mth.clamp(balance, -1, 1));
        float spin = stanceSpin + airSpin + specialSpin;
        if (tricks.trick() == SkateboardTricks.Trick.SPECIAL_900) spin += 900 * (1 - tricks.trickTicks() / 24F);
        if (revertTicks > 0) spin += 180 * (1 - revertTicks / 8F);
        entityData.set(SPIN, spin); entityData.set(CHARGE, charge);
        entityData.set(TRICK, tricks.trick().ordinal()); entityData.set(TRICK_TICKS, tricks.trickTicks());
        entityData.set(GRIND_STYLE, grindStyle);
        entityData.set(BASE, tricks.base()); entityData.set(MULTIPLIER, tricks.multiplier());
        entityData.set(SPECIAL_METER, tricks.special()); entityData.set(RESULT, tricks.lastResult());
        entityData.set(TOTAL, tricks.total()); entityData.set(BEST, tricks.best()); entityData.set(LINE, tricks.line());
    }
    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        targetX = x; targetY = y; targetZ = z; targetYaw = yaw; lerpSteps = Math.max(1, Math.min(3, steps));
    }
    @Override public ItemStack getPickResult() { return new ItemStack(PracticeMod.SKATEBOARD.get()); }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || isRemoved()) return false;
        if (!level().isClientSide && !isVehicle() && source.getEntity() instanceof Player player) {
            if (!player.getAbilities().instabuild && level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) spawnAtLocation(PracticeMod.SKATEBOARD.get());
            discard();
        }
        return true;
    }
}
