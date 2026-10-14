package com.bilal.practicemod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Clients send rider intent only. Hull position, skips, charge and impacts are server-owned. */
public class JetskiEntity extends Entity {
    public static final float HULL_SCALE = 1.425F; // 95% of the previous 1.5x world size.
    static final double SEAT_HEIGHT = 0.66 * HULL_SCALE;
    static final double SEAT_Z = -0.20 * HULL_SCALE;
    static final double RIDER_HIP_OFFSET = 0.65;
    private static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(JetskiEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> CHARGE = SynchedEntityData.defineId(JetskiEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(JetskiEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> BOOST = SynchedEntityData.defineId(JetskiEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> JET_POWER = SynchedEntityData.defineId(JetskiEntity.class, EntityDataSerializers.BYTE);
    private final JetskiDynamics dynamics = new JetskiDynamics();
    private float throttle, steering, oldRoll, clientRoll, pendingRiderTurn;
    private boolean boost;
    private long lastInputTick = Long.MIN_VALUE;
    private int lerpSteps;
    private Vec3 target = Vec3.ZERO;
    private float targetYaw, targetPitch;

    public JetskiEntity(EntityType<? extends JetskiEntity> type, Level level) {
        super(type, level);
        blocksBuilding = true;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ROLL, 0F);
        builder.define(CHARGE, 1F);
        builder.define(SPEED, 0F);
        builder.define(BOOST, (byte) 0);
        builder.define(JET_POWER, (byte) 0);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        double charge = tag.contains("BoostCharge") ? tag.getDouble("BoostCharge") : 1;
        dynamics.charge = Double.isFinite(charge) ? Mth.clamp(charge, 0, 1) : 1;
        dynamics.exhausted = tag.getBoolean("BoostExhausted");
        entityData.set(CHARGE, (float) dynamics.charge);
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("BoostCharge", dynamics.charge);
        tag.putBoolean("BoostExhausted", dynamics.exhausted);
    }
    public float boostCharge() { return entityData.get(CHARGE); }
    public boolean isBoosting() { return entityData.get(BOOST) == 1; }
    public boolean isBoostExhausted() { return entityData.get(BOOST) == 2; }
    public float speed() { return entityData.get(SPEED); }
    public float jetStrength() { return entityData.get(JET_POWER) / 100F; }
    public float roll(float partialTick) {
        return Mth.lerp(partialTick, oldRoll, level().isClientSide ? clientRoll : entityData.get(ROLL));
    }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean canBeCollidedWith() { return isAlive(); }
    @Override public boolean canCollideWith(Entity other) {
        return !isPassengerOfSameVehicle(other) && other.canBeCollidedWith();
    }
    @Override protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Player && getPassengers().isEmpty();
    }

    // Deliberately keep Entity's null controlling passenger to reject vanilla vehicle-move packets.
    public void acceptInput(Player sender, float throttle, float steering, boolean boost) {
        if (level().isClientSide || getFirstPassenger() != sender
                || !Float.isFinite(throttle) || !Float.isFinite(steering)) return;
        this.throttle = Mth.clamp(throttle, -1, 1);
        this.steering = Mth.clamp(steering, -1, 1);
        this.boost = boost;
        lastInputTick = level().getGameTime();
    }

    @Override public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive() || isVehicle()) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!player.startRiding(this)) return InteractionResult.PASS;
        player.setYRot(getYRot());
        player.setYHeadRot(getYRot());
        return InteractionResult.CONSUME;
    }

    @Override protected void positionRider(Entity passenger, MoveFunction move) {
        // Scale the saddle with the hull, not the player's own seated hip offset.
        Vec3 seat = new Vec3(0, SEAT_HEIGHT, SEAT_Z)
                .zRot(roll(1) * Mth.DEG_TO_RAD)
                .xRot(-getXRot() * Mth.DEG_TO_RAD).yRot(-getYRot() * Mth.DEG_TO_RAD);
        move.accept(passenger, getX() + seat.x, getY() + seat.y - RIDER_HIP_OFFSET, getZ() + seat.z);
        // ClientLevel snapshots the passenger's previous rotation AFTER ticking the hull.
        // Apply its turn here, after rideTick, or that snapshot erases camera interpolation.
        // Consume once: extra positioning calls must not rotate the rider a second time.
        turnRider(pendingRiderTurn);
        pendingRiderTurn = 0;
        if (passenger instanceof LivingEntity living) living.yBodyRot = getYRot();
        passenger.fallDistance = 0;
    }

    @Override protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        throttle = steering = 0;
        pendingRiderTurn = 0;
        boost = false;
        lastInputTick = Long.MIN_VALUE;
    }

    /** Local bounded samples avoid loading chunks or mistaking submerged water for a free surface. */
    double waterSurface() {
        double highest = Double.NEGATIVE_INFINITY;
        for (double dx : new double[]{-0.38 * HULL_SCALE, 0.38 * HULL_SCALE})
                for (double dz : new double[]{-0.38 * HULL_SCALE, 0.38 * HULL_SCALE}) {
            int x = Mth.floor(getX() + dx), z = Mth.floor(getZ() + dz);
            for (int y = Mth.floor(getY()) + 2; y >= Mth.floor(getY()) - 2; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!level().hasChunkAt(pos) || level().isOutsideBuildHeight(pos)) continue;
                var fluid = level().getFluidState(pos);
                if (fluid.is(FluidTags.WATER) && !level().getFluidState(pos.above()).is(FluidTags.WATER)
                        && level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()) {
                    highest = Math.max(highest, y + fluid.getHeight(level(), pos));
                    break;
                }
            }
        }
        // Recover a submerged hull without searching an unbounded water column.
        if (!Double.isFinite(highest) && isInWater()) return getY() + 0.7;
        return highest;
    }

    @Override public void tick() {
        super.tick();
        // Network metadata may already contain the next bank angle. Preserve the last
        // displayed angle instead, and smooth bank on the same two-tick horizon as yaw.
        oldRoll = level().isClientSide ? clientRoll : entityData.get(ROLL);
        pendingRiderTurn = 0;
        if (level().isClientSide) {
            clientRoll = Mth.lerp(0.5F, clientRoll, entityData.get(ROLL));
            if (lerpSteps > 0) {
                float previousYaw = getYRot();
                setPos(position().add(target.subtract(position()).scale(1.0 / lerpSteps)));
                setYRot(getYRot() + Mth.wrapDegrees(targetYaw - getYRot()) / lerpSteps);
                setXRot(getXRot() + (targetPitch - getXRot()) / lerpSteps);
                lerpSteps--;
                pendingRiderTurn = Mth.wrapDegrees(getYRot() - previousYaw);
            }
            JetskiWaterEffects.tick(this);
            return;
        }
        boolean fresh = isVehicle() && lastInputTick != Long.MIN_VALUE && level().getGameTime() - lastInputTick <= 10;
        Vec3 velocity = getDeltaMovement();
        dynamics.vx = velocity.x; dynamics.vy = velocity.y; dynamics.vz = velocity.z;
        dynamics.yaw = getYRot();
        double surface = waterSurface();
        boolean previouslyBoosting = dynamics.boosting;
        dynamics.step(getY(), surface, onGround(), fresh ? throttle : 0, fresh ? steering : 0, fresh && boost);
        float previousYaw = getYRot();
        setYRot((float) dynamics.yaw);
        pendingRiderTurn = Mth.wrapDegrees(getYRot() - previousYaw);
        setXRot((float) dynamics.pitch);
        setDeltaMovement(dynamics.vx, dynamics.vy, dynamics.vz);
        Vec3 before = position();
        move(MoverType.SELF, getDeltaMovement());
        Vec3 actual = position().subtract(before);
        // Clear blocked components, preventing stored velocity from launching the hull off a wall.
        setDeltaMovement(Math.abs(actual.x - dynamics.vx) > 0.001 ? 0 : dynamics.vx,
                verticalCollision ? 0 : dynamics.vy,
                Math.abs(actual.z - dynamics.vz) > 0.001 ? 0 : dynamics.vz);
        fallDistance = 0;
        entityData.set(ROLL, (float) dynamics.roll);
        entityData.set(CHARGE, (float) dynamics.charge);
        entityData.set(SPEED, (float) actual.horizontalDistance());
        entityData.set(BOOST, (byte) (dynamics.exhausted ? 2 : dynamics.boosting ? 1 : 0));
        // The server gates the pump to water contact/short skips. Coasting leaves a weaker wake;
        // idle, beached hulls and long airborne gaps do not spray. Quantize to limit metadata churn.
        float jet = 0;
        if (Double.isFinite(surface) && !onGround() && getY() < surface + 0.45) {
            float wake = speed() > .08F ? Math.min(.25F, speed() * .3F) : 0;
            float power = fresh ? Math.max(0, throttle) * (dynamics.boosting ? 1F : .6F) : 0;
            jet = Math.max(wake, power);
        }
        entityData.set(JET_POWER, (byte) Math.round(jet * 100));
        if (dynamics.boosting && !previouslyBoosting) {
            level().playSound(null, blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 0.7F, 0.65F);
        }
        if (Double.isFinite(surface) && getY() < surface + 0.45 && speed() > 0.12) {
            double yaw = getYRot() * Mth.DEG_TO_RAD;
            double sternX = getX() + Math.sin(yaw) * 0.95 * HULL_SCALE;
            double sternZ = getZ() - Math.cos(yaw) * 0.95 * HULL_SCALE;
            ((ServerLevel) level()).sendParticles(ParticleTypes.SPLASH, sternX, surface + 0.06, sternZ,
                    dynamics.landed ? 20 : isBoosting() ? 9 : 4,
                    0.36 * HULL_SCALE, 0.06, 0.36 * HULL_SCALE, 0.08);
            if (dynamics.landed) level().playSound(null, blockPosition(), SoundEvents.PLAYER_SPLASH,
                    SoundSource.NEUTRAL, 0.4F + speed() * 0.3F, 1.15F);
        }
    }

    private void turnRider(float change) {
        if (getFirstPassenger() instanceof LivingEntity rider) {
            // Carry the camera through a carve while preserving the rider's free-look offset.
            rider.setYRot(rider.getYRot() + change);
            rider.setYHeadRot(rider.getYHeadRot() + change);
            rider.yBodyRot = getYRot();
        }
    }

    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        target = new Vec3(x, y, z);
        targetYaw = yaw; targetPitch = pitch;
        lerpSteps = Mth.clamp(steps, 1, 2);
    }
    @Override public ItemStack getPickResult() { return new ItemStack(PracticeMod.JETSKI.get()); }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || isRemoved()) return false;
        if (!level().isClientSide && !isVehicle() && source.getEntity() instanceof Player player) {
            if (!player.getAbilities().instabuild && level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS))
                spawnAtLocation(PracticeMod.JETSKI.get());
            discard();
        }
        return true;
    }
}
