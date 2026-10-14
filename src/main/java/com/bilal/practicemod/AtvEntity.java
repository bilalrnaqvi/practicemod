package com.bilal.practicemod;

import static com.bilal.practicemod.AtvGeometry.WHEEL_X;
import static com.bilal.practicemod.AtvGeometry.WHEEL_Z;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server-owned quad: actual obstacle hops plus four sampled wheels and a sprung rider/chassis. */
public class AtvEntity extends Entity {
    // Hip pivot sits above the cushion so the posed thighs rest on its surface.
    static final double SEAT_HEIGHT = .99, SEAT_Z = -.40, SEAT_X = .56, HIP_OFFSET = .7040625;
    private final java.util.Set<Integer> turnedPassengers = new java.util.HashSet<>();
    // Heave, roll, steering, four local wheel travels, pitch: one synchronized suspension pose.
    private static final List<EntityDataAccessor<Float>> VISUALS = java.util.stream.IntStream.range(0, 8)
            .mapToObj(i -> SynchedEntityData.defineId(AtvEntity.class, EntityDataSerializers.FLOAT)).toList();
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(AtvEntity.class, EntityDataSerializers.FLOAT);
    private final float[] previous = new float[8], displayed = new float[8];
    private final AtvDynamics dynamics = new AtvDynamics();
    private float throttle, steering, pendingRiderTurn, wheelAngle, oldWheelAngle;
    private long lastInputTick = Long.MIN_VALUE;
    private int lerpSteps;
    private Vec3 target = Vec3.ZERO;
    private float targetYaw;

    public AtvEntity(EntityType<? extends AtvEntity> type, Level level) { super(type, level); blocksBuilding = true; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        for (var key : VISUALS) builder.define(key, 0F);
        builder.define(SPEED, 0F);
    }
    // Entity persists position, rotation, motion and passengers. Control and springs reset on load.
    @Override protected void readAdditionalSaveData(CompoundTag tag) { }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean canBeCollidedWith() { return isAlive(); }
    @Override public boolean canCollideWith(Entity other) { return !isPassengerOfSameVehicle(other) && other.canBeCollidedWith(); }
    @Override public float maxUpStep() { return 1.05F; }
    @Override protected boolean canAddPassenger(Entity passenger) { return passenger instanceof Player && getPassengers().size() < 2; }
    public boolean isDriver(Entity passenger) { return getFirstPassenger() == passenger; }
    public double seatX(Entity passenger) { return isDriver(passenger) ? -SEAT_X : SEAT_X; }
    public float speed() { return entityData.get(SPEED); }
    public float visual(int index, float partial) {
        return Mth.lerp(partial, previous[index], level().isClientSide ? displayed[index] : entityData.get(VISUALS.get(index)));
    }
    public float wheelAngle(float partial) { return Mth.lerp(partial, oldWheelAngle, wheelAngle); }

    // Keep Entity's null controlling passenger: clients may send intent, never authoritative positions.
    public void acceptInput(Player sender, float throttle, float steering) {
        if (level().isClientSide || getFirstPassenger() != sender || !Float.isFinite(throttle) || !Float.isFinite(steering)) return;
        this.throttle = Mth.clamp(throttle, -1, 1);
        this.steering = Mth.clamp(steering, -1, 1);
        lastInputTick = level().getGameTime();
    }
    @Override public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive() || getPassengers().size() >= 2) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!player.startRiding(this)) return InteractionResult.PASS;
        player.setYRot(getYRot()); player.setYHeadRot(getYRot());
        return InteractionResult.CONSUME;
    }
    @Override protected void positionRider(Entity passenger, MoveFunction move) {
        Vec3 seat = new Vec3(seatX(passenger), SEAT_HEIGHT, SEAT_Z).zRot(visual(1, 1) * Mth.DEG_TO_RAD)
                .xRot(-getXRot() * Mth.DEG_TO_RAD).yRot(-getYRot() * Mth.DEG_TO_RAD);
        move.accept(passenger, getX() + seat.x, getY() + seat.y + visual(0, 1) - HIP_OFFSET, getZ() + seat.z);
        // Apply after the passenger history snapshot, once, so steering preserves camera interpolation.
        float turn = turnedPassengers.add(passenger.getId()) ? pendingRiderTurn : 0;
        passenger.setYRot(passenger.getYRot() + turn);
        if (passenger instanceof LivingEntity living) {
            living.setYHeadRot(living.getYHeadRot() + turn);
            living.yBodyRot = getYRot();
        }
        passenger.fallDistance = 0;
    }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity rider) {
        for (int side : new int[]{1, -1}) {
            Vec3 offset = new Vec3(side * (getBbWidth() * .5 + rider.getBbWidth() * .5 + .2), 0, 0)
                    .yRot(-getYRot() * Mth.DEG_TO_RAD);
            BlockPos top = BlockPos.containing(getX() + offset.x, getY() + .8, getZ() + offset.z);
            for (int down = 0; down <= 1; down++) {
                BlockPos pos = top.below(down);
                if (!level().hasChunkAt(pos)) continue;
                double floor = level().getBlockFloorHeight(pos);
                if (!DismountHelper.isBlockFloorValid(floor)) continue;
                Vec3 spot = new Vec3(getX() + offset.x, pos.getY() + floor, getZ() + offset.z);
                for (var pose : rider.getDismountPoses()) if (DismountHelper.canDismountTo(level(), spot, rider, pose)) {
                    rider.setPose(pose); return spot;
                }
            }
        }
        return super.getDismountLocationForPassenger(rider);
    }
    @Override protected void removePassenger(Entity passenger) {
        boolean driver = isDriver(passenger);
        super.removePassenger(passenger);
        turnedPassengers.remove(passenger.getId());
        if (driver) {
            // The remaining occupant moves into the driver's seat, but must send fresh input.
            throttle = steering = 0;
            lastInputTick = Long.MIN_VALUE;
        }
    }

    /** Collision-shape probes handle slabs/stairs; unloaded chunks are never queried. */
    private double wheelGround(int index) {
        // Sample the tire's circular footprint. A corner meets the leading edge before
        // crossing its hub, and support rolls off the trailing edge instead of popping.
        double support = Double.NaN;
        double radius = .441;
        for (int sample = -2; sample <= 2; sample++) {
            double along = sample * radius * .45;
            Vec3 offset = new Vec3(WHEEL_X[index], 0, WHEEL_Z[index] + along)
                    .yRot(-getYRot() * Mth.DEG_TO_RAD);
            Vec3 start = position().add(offset).add(0, 1.15, 0), end = start.add(0, -2.7, 0);
            if (!level().hasChunkAt(BlockPos.containing(start))) continue;
            var hit = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.BLOCK) continue;
            double height = hit.getLocation().y - getY() - (radius - Math.sqrt(radius * radius - along * along));
            support = Double.isFinite(support) ? Math.max(support, height) : height;
        }
        return support;
    }

    /** Missing contacts exert no leveling torque when an axle leaves a ledge. */
    private static double terrainAngle(double[] heights, int a, int b, int c, int d, double baseline, double fallback) {
        double difference = 0;
        int pairs = 0;
        if (Double.isFinite(heights[a]) && Double.isFinite(heights[b])) { difference += heights[a] - heights[b]; pairs++; }
        if (Double.isFinite(heights[c]) && Double.isFinite(heights[d])) { difference += heights[c] - heights[d]; pairs++; }
        return pairs == 0 ? fallback : Math.toDegrees(Math.atan2(difference / pairs, baseline));
    }

    @Override public void tick() {
        super.tick();
        for (int i = 0; i < 8; i++) previous[i] = level().isClientSide ? displayed[i] : entityData.get(VISUALS.get(i));
        pendingRiderTurn = 0;
        turnedPassengers.clear();
        oldWheelAngle = wheelAngle;
        wheelAngle += speed() / .441F * Mth.RAD_TO_DEG;
        if (Math.abs(wheelAngle) > 3600) { float wrap = Math.copySign(3600, wheelAngle); wheelAngle -= wrap; oldWheelAngle -= wrap; }
        if (level().isClientSide) {
            for (int i = 0; i < 8; i++) displayed[i] = Mth.lerp(.5F, displayed[i], entityData.get(VISUALS.get(i)));
            setXRot(displayed[7]);
            if (lerpSteps > 0) {
                float yaw = getYRot();
                setPos(position().add(target.subtract(position()).scale(1.0 / lerpSteps)));
                setYRot(getYRot() + Mth.wrapDegrees(targetYaw - getYRot()) / lerpSteps);
                lerpSteps--;
                pendingRiderTurn = Mth.wrapDegrees(getYRot() - yaw);
            }
            return;
        }
        boolean fresh = isVehicle() && lastInputTick != Long.MIN_VALUE && level().getGameTime() - lastInputTick <= 10;
        boolean grounded = onGround(), wet = isInWater() || isInLava();
        Vec3 velocity = getDeltaMovement();
        dynamics.vx = velocity.x; dynamics.vz = velocity.z; dynamics.yaw = getYRot();
        dynamics.drive(grounded, wet, fresh ? throttle : 0, fresh ? steering : 0);
        float yaw = getYRot();
        setYRot((float) dynamics.yaw);
        pendingRiderTurn = Mth.wrapDegrees(getYRot() - yaw);
        double vy = Math.max(-1.4, wet ? velocity.y * .8 - .025 : velocity.y - .08);
        Vec3 intended = new Vec3(dynamics.vx, vy, dynamics.vz), before = position();
        setDeltaMovement(intended);
        move(MoverType.SELF, intended);
        Vec3 actual = position().subtract(before);
        double rise = Math.max(0, actual.y - Math.max(0, intended.y));
        double impact = onGround() && !grounded ? Math.max(0, -vy) : 0;
        double launch = wet ? 0 : AtvDynamics.bumpLaunch(rise, actual.horizontalDistance());
        setDeltaMovement(Math.abs(actual.x - intended.x) > .001 ? 0 : intended.x,
                launch > 0 ? launch : verticalCollision ? 0 : vy,
                Math.abs(actual.z - intended.z) > .001 ? 0 : intended.z);
        if (launch > 0) setOnGround(false);
        double[] wheels = new double[4];
        for (int i = 0; i < 4; i++) {
            wheels[i] = wheelGround(i);
        }
        double terrainPitch = -terrainAngle(wheels, 0, 2, 1, 3, WHEEL_Z[0] - WHEEL_Z[2], -dynamics.pitch);
        double terrainRoll = terrainAngle(wheels, 0, 1, 2, 3, WHEEL_X[1] - WHEEL_X[0], dynamics.roll);
        double radians = Math.toRadians(getYRot());
        double travelForward = -Math.sin(radians) * actual.x + Math.cos(radians) * actual.z;
        dynamics.suspension(rise, impact, terrainPitch, terrainRoll, onGround(), travelForward, actual.y);
        dynamics.wheels(wheels, WHEEL_X, WHEEL_Z, AtvGeometry.WHEEL_Y, onGround());
        for (int i = 0; i < 4; i++) entityData.set(VISUALS.get(i + 3), (float) dynamics.wheelTravel[i]);
        setXRot((float) dynamics.pitch);
        entityData.set(VISUALS.get(7), (float) dynamics.pitch);
        entityData.set(VISUALS.get(0), (float) dynamics.heave);
        entityData.set(VISUALS.get(1), (float) dynamics.roll);
        entityData.set(VISUALS.get(2), (float) dynamics.steer);
        entityData.set(SPEED, (float) travelForward);
        fallDistance = 0;
        if (impact > .24) level().playSound(null, blockPosition(), SoundEvents.PLAYER_SMALL_FALL, SoundSource.NEUTRAL,
                (float) Math.min(.7, impact), .75F);
        if (!wet && onGround() && actual.horizontalDistance() > .12 && tickCount % 4 == 0) {
            Vec3 rear = new Vec3(0, 0, WHEEL_Z[2]).yRot(-getYRot() * Mth.DEG_TO_RAD).add(position());
            var state = level().getBlockState(BlockPos.containing(rear.add(0, -.1, 0)));
            if (!state.isAir()) ((ServerLevel) level()).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    rear.x, rear.y + .12, rear.z, 3, .45, .05, .15, .035);
        }
    }
    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        target = new Vec3(x, y, z); targetYaw = yaw; lerpSteps = Mth.clamp(steps, 1, 2);
    }
    @Override public ItemStack getPickResult() { return new ItemStack(PracticeMod.ATV.get()); }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || isRemoved()) return false;
        if (!level().isClientSide && !isVehicle() && source.getEntity() instanceof Player player) {
            if (!player.getAbilities().instabuild && level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) spawnAtLocation(PracticeMod.ATV.get());
            discard();
        }
        return true;
    }
}
