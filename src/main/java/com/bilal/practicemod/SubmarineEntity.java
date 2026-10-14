package com.bilal.practicemod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Boat steering and networking with neutral buoyancy and rider-controlled depth. */
public class SubmarineEntity extends Boat {
    // Entity-owned state is discarded on despawn/dimension transfer; never shared between vehicles.
    public final SubmarineHistory instrumentHistory = new SubmarineHistory();
    final SubmarineFanAnimation fanAnimation = new SubmarineFanAnimation();
    final SubmarineJoystickAnimation joystickAnimation = new SubmarineJoystickAnimation();
    private net.minecraft.resources.ResourceKey<Level> instrumentDimension;
    private float instrumentDepth = Float.NaN;
    public float instrumentDepth() { return instrumentDepth; }

    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> ENTRY_OFFSET =
            net.minecraft.network.syncher.SynchedEntityData.defineId(SubmarineEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Long> ENTRY_START =
            net.minecraft.network.syncher.SynchedEntityData.defineId(SubmarineEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.LONG);
    private static final net.minecraft.network.syncher.EntityDataAccessor<org.joml.Vector3f> ENTRY_ORIGIN =
            net.minecraft.network.syncher.SynchedEntityData.defineId(SubmarineEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.VECTOR3);

    public float entryTime(float partial) {
        long start = entityData.get(ENTRY_START);
        return start < 0 ? SubmarineEntryAnimation.DURATION : net.minecraft.util.Mth.clamp(
                (float) (level().getGameTime() - start) + entityData.get(ENTRY_OFFSET) + partial, 0, SubmarineEntryAnimation.DURATION);
    }

    public boolean isBoarding() { return entryTime(0) < SubmarineEntryAnimation.DURATION; }
    public boolean insideCabin() { return !isBoarding() || entryTime(0) >= SubmarineEntryAnimation.CABIN_TICK; }
    public Vec3 entryOrigin() { return new Vec3(entityData.get(ENTRY_ORIGIN)); }
    public boolean isReturningToChair() { return entityData.get(ENTRY_OFFSET) == 70; }
    public Vec3 entryEye(float partial) {
        Vec3 eye = SubmarineEntryAnimation.eye(entryOrigin(), entryTime(partial));
        if (isReturningToChair() && entryTime(partial) >= 70)
            return entryOrigin().lerp(eye, SubmarineEntryAnimation.ease(entryTime(partial), 70, 80));
        return eye;
    }

    public Vec3 modelToWorld(Vec3 model, float partial) {
        return model.subtract(SubmarineEntryAnimation.PILOT)
                .xRot(net.minecraft.util.Mth.lerp(partial, xRotO, getXRot()) * net.minecraft.util.Mth.DEG_TO_RAD)
                .yRot((180 - net.minecraft.util.Mth.rotLerp(partial, yRotO, getYRot())) * net.minecraft.util.Mth.DEG_TO_RAD)
                .add(getPosition(partial)).add(0, pilotEyeOffset(), 0);
    }

    @Override
    public net.minecraft.world.InteractionResult interact(Player player, net.minecraft.world.InteractionHand hand) {
        if (player.isSecondaryUseActive() || player.isPassenger() || !canAddPassenger(player))
            return net.minecraft.world.InteractionResult.PASS;
        if (!level().isClientSide) {
            entityData.set(PARKED, false);
            parkPosition = null;
            Vec3 origin = SubmarineEntryAnimation.LADDER_START;
            entityData.set(ENTRY_ORIGIN, origin.toVector3f());
            entityData.set(ENTRY_OFFSET, 0);
            entityData.set(ENTRY_START, level().getGameTime());
            if (!player.startRiding(this)) {
                entityData.set(ENTRY_START, -1L);
                return net.minecraft.world.InteractionResult.PASS;
            }
            positionRider(player, Entity::setPos);
            verticalInput = 0;
            setDeltaMovement(Vec3.ZERO);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (isParked() && hasPassenger(passenger)) {
            Vec3 eye = isCabinTransition() ? SubmarineEntryAnimation.eye(entryOrigin(), cabinAnimationTime(0))
                    : passenger instanceof Player p && p.isSleeping() ? new Vec3(1.3, 2.35, -2.7)
                    : cabinFeet().add(0, passenger.getEyeHeight(), 0);
            Vec3 world = modelToWorld(eye, 1);
            move.accept(passenger, world.x, world.y - passenger.getEyeHeight(), world.z);
            if (isCabinTransition()) {
                float yaw = getYRot() - SubmarineEntryAnimation.heading(entryOrigin(), cabinAnimationTime(0));
                passenger.setYRot(yaw); passenger.setXRot(0);
                if (passenger instanceof net.minecraft.world.entity.LivingEntity living) {
                    living.setYHeadRot(yaw); living.setYBodyRot(yaw);
                }
            }
        } else if (isBoarding() && hasPassenger(passenger)) {
            Vec3 eye = modelToWorld(entryEye(0), 1);
            move.accept(passenger, eye.x, eye.y - passenger.getEyeHeight(), eye.z);
            float heading = getYRot() - SubmarineEntryAnimation.heading(entryOrigin(), entryTime(0));
            passenger.setYRot(heading);
            passenger.setXRot(0);
            if (passenger instanceof net.minecraft.world.entity.LivingEntity living) {
                living.setYHeadRot(heading);
                living.setYBodyRot(heading);
            }
        } else super.positionRider(passenger, move);
    }

    @Override
    protected void addPassenger(Entity passenger) {
        float yaw = passenger.getYRot(), pitch = passenger.getXRot();
        super.addPassenger(passenger);
        // Boat.addPassenger assigns its own yaw. Never let that transient reset reach the camera.
        passenger.setYRot(yaw);
        passenger.setXRot(pitch);
        if (passenger instanceof net.minecraft.world.entity.LivingEntity living) {
            living.setYHeadRot(yaw);
            living.setYBodyRot(yaw);
        }
    }

    /** Raise the mounted player with the authored seat; vanilla mouse look already allows +/-105 degrees. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, net.minecraft.world.entity.EntityDimensions dimensions, float partialTick) {
        return super.getPassengerAttachmentPoint(passenger, dimensions, partialTick).add(0, DeepseekerPilot.SEAT_RISE, 0);
    }

    /** Raised boat seat plus the actual passenger eye height. */
    public double pilotEyeOffset() {
        return getBbHeight() / 3.0 + DeepseekerPilot.SEAT_RISE + Player.DEFAULT_EYE_HEIGHT - Player.DEFAULT_VEHICLE_ATTACHMENT.y;
    }

    private void updateInstruments() {
        if (instrumentDimension != level().dimension()) {
            clearInstruments();
            instrumentDimension = level().dimension();
        }
        // Client-local loaded-world data: no packets, chunk loads, or queries during rendering.
        if (tickCount % 4 == 0) {
            int x = net.minecraft.util.Mth.floor(getX()), z = net.minecraft.util.Mth.floor(getZ());
            instrumentDepth = SubmarineDepth.measure(getY() + pilotEyeOffset(),
                    new SubmarineWaterColumn(level(), x, z));
        }
        instrumentHistory.sample(level().getGameTime(), instrumentDepth);
    }

    public void clearInstruments() { instrumentDepth = Float.NaN; instrumentHistory.clear(); }

    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> HEADLIGHTS =
            net.minecraft.network.syncher.SynchedEntityData.defineId(SubmarineEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PARKED, false);
        builder.define(CABIN_TRANSITION, -100L);
        builder.define(CABIN_FEET, new org.joml.Vector3f(0, DeepseekerPilot.FLOOR_Y, -2.26F));
        builder.define(HEADLIGHTS, false);
        builder.define(ENTRY_OFFSET, 0);
        builder.define(ENTRY_START, -1L);
        builder.define(ENTRY_ORIGIN, new org.joml.Vector3f());
    }

    public boolean headlightsOn() { return entityData.get(HEADLIGHTS); }

    public void toggleHeadlights(Player sender) {
        if (level().isClientSide || getControllingPassenger() != sender) return;
        entityData.set(HEADLIGHTS, !headlightsOn());
        sender.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                headlightsOn() ? "message.practicemod.headlights_on" : "message.practicemod.headlights_off"), true);
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Headlights", headlightsOn());
        var items = new net.minecraft.nbt.CompoundTag();
        net.minecraft.world.ContainerHelper.saveAllItems(items, storage.getItems(), level().registryAccess());
        tag.put("SubStorage", items);
        tag.putBoolean("Parked", isParked());
        tag.putFloat("CabinX", (float) cabinFeet().x);
        tag.putFloat("CabinZ", (float) cabinFeet().z);
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(HEADLIGHTS, tag.getBoolean("Headlights"));
        storage.clearContent();
        net.minecraft.world.ContainerHelper.loadAllItems(tag.getCompound("SubStorage"), storage.getItems(), level().registryAccess());
        entityData.set(PARKED, tag.getBoolean("Parked"));
        if (tag.contains("CabinX") && Float.isFinite(tag.getFloat("CabinX")) && Float.isFinite(tag.getFloat("CabinZ")))
            entityData.set(CABIN_FEET, new org.joml.Vector3f(
                    net.minecraft.util.Mth.clamp(tag.getFloat("CabinX"), -.53F, .53F), DeepseekerPilot.FLOOR_Y,
                    net.minecraft.util.Mth.clamp(tag.getFloat("CabinZ"), -3.45F, .04F)));
        if (isParked()) { parkPosition = position(); parkYaw = getYRot(); parkPitch = getXRot(); }
    }

    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> PARKED =
            net.minecraft.network.syncher.SynchedEntityData.defineId(SubmarineEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Long> CABIN_TRANSITION =
            net.minecraft.network.syncher.SynchedEntityData.defineId(SubmarineEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.LONG);
    private static final net.minecraft.network.syncher.EntityDataAccessor<org.joml.Vector3f> CABIN_FEET =
            net.minecraft.network.syncher.SynchedEntityData.defineId(SubmarineEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.VECTOR3);
    public final net.minecraft.world.SimpleContainer storage = new net.minecraft.world.SimpleContainer(27);
    private Vec3 parkPosition;
    private float parkYaw, parkPitch;
    private float cabinForward, cabinStrafe;
    private Vec3 previousCabinFeet;
    float cabinWalkPhase, cabinWalkSpeed;
    private long cabinInputTick = Long.MIN_VALUE;

    public boolean isParked() { return entityData.get(PARKED); }
    public boolean isCabinTransition() { return isParked() && cabinTransitionTime(0) < 30; }
    public float cabinTransitionTime(float partial) {
        return net.minecraft.util.Mth.clamp(level().getGameTime() - entityData.get(CABIN_TRANSITION) + partial, 0, 30);
    }
    public float cabinAnimationTime(float partial) { return 100 - cabinTransitionTime(partial); }
    public Vec3 cabinFeet() { return new Vec3(entityData.get(CABIN_FEET)); }
    public float chairAngle(float partial) {
        return SubmarineEntryAnimation.chair(isParked() ? cabinAnimationTime(partial) : entryTime(partial));
    }
    public void toggleCabin(Player player) {
        if (level().isClientSide || getControllingPassenger() != player || isBoarding() || isCabinTransition()) return;
        if (player.isSleeping()) { player.stopSleepInBed(true, true); return; }
        if (isParked()) return;
        {
            parkPosition = position(); parkYaw = getYRot(); parkPitch = getXRot();
            entityData.set(PARKED, true);
            entityData.set(CABIN_TRANSITION, level().getGameTime());
            entityData.set(CABIN_FEET, new org.joml.Vector3f(0, DeepseekerPilot.FLOOR_Y, -2.26F));
        }
        cabinForward = cabinStrafe = 0;
        verticalInput = 0; pitchRate = 0;
        setDeltaMovement(Vec3.ZERO);
        player.closeContainer();
    }
    public void sitInChair(Player player) {
        if (level().isClientSide || getControllingPassenger() != player || !isParked()
                || isCabinTransition() || player.isSleeping()) return;
        entityData.set(ENTRY_ORIGIN, cabinFeet().add(0, player.getEyeHeight(), 0).toVector3f());
        entityData.set(PARKED, false);
        entityData.set(ENTRY_OFFSET, 70);
        entityData.set(ENTRY_START, level().getGameTime());
        parkPosition = null;
        cabinForward = cabinStrafe = 0;
        verticalInput = 0; pitchRate = 0;
        setDeltaMovement(Vec3.ZERO);
    }
    public void acceptCabinInput(Player player, float forward, float strafe) {
        if (getControllingPassenger() != player || !isParked() || !Float.isFinite(forward) || !Float.isFinite(strafe)) return;
        cabinForward = net.minecraft.util.Mth.clamp(forward, -1, 1);
        cabinStrafe = net.minecraft.util.Mth.clamp(strafe, -1, 1);
        cabinInputTick = level().getGameTime();
    }
    @Override public boolean isControlledByLocalInstance() {
        return !isParked() && super.isControlledByLocalInstance();
    }
    @Override public boolean shouldRiderSit() { return !isParked() || isCabinTransition(); }
    @Override public void onPassengerTurned(Entity passenger) {
        if (!isParked()) super.onPassengerTurned(passenger);
    }
    @Override public void move(net.minecraft.world.entity.MoverType type, Vec3 movement) {
        if (!isParked()) super.move(type, movement);
    }
    @Override public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy()) {
            net.minecraft.world.Containers.dropContents(level(), this, storage);
            storage.clearContent();
        }
        super.remove(reason);
    }
    private void tickCabin() {
        if (!isParked()) return;
        setDeltaMovement(Vec3.ZERO);
        if (!level().isClientSide && parkPosition != null) { setPos(parkPosition); setYRot(parkYaw); setXRot(parkPitch); }
        {
            if (getControllingPassenger() instanceof Player player && (!level().isClientSide || player.isLocalPlayer()) && !isCabinTransition() && !player.isSleeping()
                    && player.containerMenu == player.inventoryMenu && level().getGameTime() - cabinInputTick <= 5) {
                Vec3 input = new Vec3(cabinStrafe, 0, cabinForward);
                if (input.lengthSqr() > 1) input = input.normalize();
                Vec3 world = input.yRot(-player.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD).scale(.1);
                Vec3 local = world.yRot((getYRot() - 180) * net.minecraft.util.Mth.DEG_TO_RAD);
                Vec3 feet = cabinFeet();
                entityData.set(CABIN_FEET, new org.joml.Vector3f(
                        (float) net.minecraft.util.Mth.clamp(feet.x + local.x, -.53, .53),
                        DeepseekerPilot.FLOOR_Y,
                        (float) net.minecraft.util.Mth.clamp(feet.z + local.z, -3.45, .04)));
            }
        }
    }

    private int verticalInput;
    private float pitchRate;
    private long lastInputTick = Long.MIN_VALUE;

    public void acceptInput(Player sender, boolean rise, boolean descend, boolean exit) {
        if (getControllingPassenger() != sender) return;
        if (exit && !level().isClientSide) {
            sender.stopRiding();
            return;
        }
        if (isBoarding() || isParked()) return;
        verticalInput = (rise ? 1 : 0) - (descend ? 1 : 0);
        lastInputTick = level().getGameTime();
    }

    /** Called at the boat's buoyancy step, before normal steering and collision-aware movement. */
    public void applyDepthControl() {
        if (isBoarding() || isParked()) { setDeltaMovement(Vec3.ZERO); return; }
        boolean fresh = isVehicle() && lastInputTick != Long.MIN_VALUE
                && level().getGameTime() - lastInputTick <= 10;
        Vec3 velocity = getDeltaMovement();
        double yaw = Math.toRadians(getYRot());
        double forwardSpeed = -Math.sin(yaw) * velocity.x + Math.cos(yaw) * velocity.z;
        boolean wet = isInWater();
        var motion = SubmarineDynamics.step(getXRot(), pitchRate, velocity.y,
                wet ? forwardSpeed : 0, wet && fresh ? verticalInput : 0);
        pitchRate = motion.pitchRate();
        setXRot(motion.pitch());
        double y = wet ? motion.verticalSpeed() : Math.max(-1.5, velocity.y - getGravity());
        setDeltaMovement(velocity.x * 0.9, y, velocity.z * 0.9);
    }

    @Override
    public void tick() {
        double previousX = getX(), previousY = getY(), previousZ = getZ();
        float previousYaw = getYRot();
        super.tick();
        tickCabin();
        if (level().isClientSide) {
            Vec3 feet = cabinFeet();
            float speed = isParked() && !isCabinTransition() && previousCabinFeet != null
                    ? (float) Math.min(1, feet.distanceTo(previousCabinFeet) * 8) : 0;
            cabinWalkSpeed += (speed - cabinWalkSpeed) * .4F;
            cabinWalkPhase += cabinWalkSpeed;
            previousCabinFeet = feet;
            updateInstruments();
            // Position deltas include vanilla interpolation for remote passengers too.
            fanAnimation.tick(getX() - previousX, getY() - previousY, getZ() - previousZ, getYRot());
            joystickAnimation.tick(getX() - previousX, getY() - previousY, getZ() - previousZ,
                    getYRot(), getYRot() - previousYaw);
        }
        if (!level().isClientSide && insideCabin() && getControllingPassenger() instanceof Player player) {
            player.setAirSupply(player.getMaxAirSupply());
        }
    }

    @Override
    public boolean dismountsUnderwater() { return false; }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Player && getPassengers().isEmpty();
    }

    @Override
    protected void removePassenger(Entity passenger) {
        if (passenger instanceof Player player && player.isSleeping()) player.stopSleepInBed(true, true);
        super.removePassenger(passenger);
        entityData.set(ENTRY_START, -1L);
        verticalInput = 0;
        lastInputTick = Long.MIN_VALUE;
    }

    public SubmarineEntity(EntityType<? extends SubmarineEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public Item getDropItem() {
        return PracticeMod.SUBMARINE.get();
    }

    @Override
    protected int getMaxPassengers() {
        return 1;
    }

    @Override
    protected float getSinglePassengerXOffset() {
        return 0.0F;
    }

    // The metal placeholder hull must never turn into vanilla oak planks and sticks on landing.
    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        resetFallDistance();
    }
}
