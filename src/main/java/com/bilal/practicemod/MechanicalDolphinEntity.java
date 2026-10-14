package com.bilal.practicemod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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

/** Rider intent is validated here; the server alone moves the dolphin. */
public class MechanicalDolphinEntity extends Entity {
    private final MechanicalDolphinDynamics dynamics = new MechanicalDolphinDynamics();
    private final MechanicalDolphinAnimation animation = new MechanicalDolphinAnimation();
    private float throttle, lookYaw, lookPitch;
    private boolean rise;
    private long lastInputTick = Long.MIN_VALUE;
    private int lerpSteps;
    private Vec3 target = Vec3.ZERO;
    private float targetYaw, targetPitch;

    public MechanicalDolphinEntity(EntityType<? extends MechanicalDolphinEntity> type, Level level) {
        super(type, level);
        blocksBuilding = true;
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean dismountsUnderwater() { return false; }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean canBeCollidedWith() { return isAlive(); }
    @Override public boolean canCollideWith(Entity other) {
        return !isPassengerOfSameVehicle(other) && other.canBeCollidedWith();
    }
    @Override protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Player && getPassengers().isEmpty();
    }

    // Keep Entity's null controlling passenger so vanilla vehicle-move packets cannot teleport us.
    public void acceptInput(Player sender, float throttle, float yaw, float pitch, boolean rise) {
        if (level().isClientSide || getFirstPassenger() != sender || !Float.isFinite(throttle)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) return;
        this.throttle = Mth.clamp(throttle, -1, 1);
        this.lookYaw = Mth.wrapDegrees(yaw);
        this.lookPitch = Mth.clamp(pitch, -80, 80);
        this.rise = rise;
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

    public static Vec3 pilotEyeOffset(float yaw, float pitch) {
        return new Vec3(0, 1.05, .90).xRot(-pitch * Mth.DEG_TO_RAD).yRot(-yaw * Mth.DEG_TO_RAD);
    }

    @Override protected void positionRider(Entity passenger, MoveFunction move) {
        Vec3 eye = pilotEyeOffset(getYRot(), getXRot());
        move.accept(passenger, getX()+eye.x, getY()+eye.y-passenger.getEyeHeight(), getZ()+eye.z);
        if (passenger instanceof LivingEntity living) living.yBodyRot = getYRot();
        passenger.fallDistance = 0;
    }

    @Override protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        throttle = 0; rise = false; lastInputTick = Long.MIN_VALUE;
    }

    boolean propulsionInWater() {
        BlockPos pos = BlockPos.containing(getX(), getY() + .30, getZ());
        if (!level().hasChunkAt(pos)) return false;
        var fluid = level().getFluidState(pos);
        return fluid.is(FluidTags.WATER) && getY() + .30 < pos.getY() + fluid.getHeight(level(), pos);
    }

    double nearbySurface() {
        BlockPos start = BlockPos.containing(getX(), getY() + .30, getZ());
        for (int i = 0; i <= 5; i++) {
            BlockPos pos = start.above(i);
            if (!level().hasChunkAt(pos) || level().isOutsideBuildHeight(pos)) break;
            var fluid = level().getFluidState(pos);
            if (fluid.is(FluidTags.WATER) && !level().getFluidState(pos.above()).is(FluidTags.WATER))
                return pos.getY() + fluid.getHeight(level(), pos);
        }
        return Double.NEGATIVE_INFINITY;
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            float previousYaw = getYRot(), previousPitch = getXRot();
            if (lerpSteps > 0) {
                setPos(position().add(target.subtract(position()).scale(1.0 / lerpSteps)));
                setYRot(getYRot() + Mth.wrapDegrees(targetYaw - getYRot()) / lerpSteps);
                setXRot(getXRot() + (targetPitch - getXRot()) / lerpSteps);
                lerpSteps--;
            }
            animation.tick(getDeltaMovement().length(), propulsionInWater(),
                    Mth.wrapDegrees(getYRot() - previousYaw), getXRot() - previousPitch);
            return;
        }
        boolean fresh = isVehicle() && lastInputTick != Long.MIN_VALUE && level().getGameTime() - lastInputTick <= 10;
        boolean wet = propulsionInWater();
        Vec3 velocity = getDeltaMovement();
        dynamics.vx = velocity.x; dynamics.vy = velocity.y; dynamics.vz = velocity.z;
        dynamics.yaw = getYRot(); dynamics.pitch = getXRot();
        dynamics.step(getY(), nearbySurface(), wet, onGround(), fresh,
                fresh ? throttle : 0, lookYaw, lookPitch, fresh && rise);
        setYRot((float) dynamics.yaw); setXRot((float) dynamics.pitch);
        setDeltaMovement(dynamics.vx, dynamics.vy, dynamics.vz);
        Vec3 before = position();
        move(MoverType.SELF, getDeltaMovement());
        Vec3 actual = position().subtract(before);
        setDeltaMovement(Math.abs(actual.x - dynamics.vx) > .001 ? 0 : dynamics.vx,
                verticalCollision ? 0 : dynamics.vy,
                Math.abs(actual.z - dynamics.vz) > .001 ? 0 : dynamics.vz);
        fallDistance = 0;
        if (getFirstPassenger() instanceof LivingEntity rider) {
            rider.setAirSupply(rider.getMaxAirSupply());
            rider.fallDistance = 0;
        }
        var server = (ServerLevel) level();
        if (dynamics.launched || dynamics.landed) {
            server.sendParticles(ParticleTypes.SPLASH, getX(), getY() + .3, getZ(), 28, .55, .12, .55, .15);
            level().playSound(null, blockPosition(), SoundEvents.PLAYER_SPLASH, SoundSource.NEUTRAL,
                    .8F, dynamics.launched ? 1.3F : .8F);
        } else if (wet && actual.lengthSqr() > .02 && tickCount % 3 == 0) {
            Vec3 tail = new Vec3(0, .53125, -2.7).xRot(-getXRot() * Mth.DEG_TO_RAD)
                    .yRot(-getYRot() * Mth.DEG_TO_RAD).add(position());
            server.sendParticles(ParticleTypes.BUBBLE, tail.x, tail.y, tail.z, 3, .12, .08, .12, .01);
        }
    }

    MechanicalDolphinAnimation.Pose animationPose(float partial) { return animation.sample(partial); }

    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        target = new Vec3(x, y, z); targetYaw = yaw; targetPitch = pitch;
        lerpSteps = Mth.clamp(steps, 1, 2);
    }
    @Override public ItemStack getPickResult() { return new ItemStack(PracticeMod.MECHANICAL_DOLPHIN.get()); }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || isRemoved()) return false;
        if (!level().isClientSide && !isVehicle() && source.getEntity() instanceof Player player) {
            if (!player.getAbilities().instabuild && level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS))
                spawnAtLocation(PracticeMod.MECHANICAL_DOLPHIN.get());
            discard();
        }
        return true;
    }
}
