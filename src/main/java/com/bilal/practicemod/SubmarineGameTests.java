package com.bilal.practicemod;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PracticeMod.MODID)
@PrefixGameTestTemplate(false)
public class SubmarineGameTests {
    @GameTest(template = "empty", timeoutTicks = 360)
    public void boardingCompletesAndReleasesControls(GameTestHelper helper) {
        var sub = new SubmarineEntity(PracticeMod.SUBMARINE_ENTITY.get(), helper.getLevel());
        sub.setPos(helper.absoluteVec(new Vec3(1.5, 3, 1.5)));
        helper.getLevel().addFreshEntity(sub);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(sub.position().add(3, 1, 0));
        player.setYRot(137);
        sub.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(sub.isBoarding(), "interaction must start the boarding timeline");
        helper.assertTrue(sub.entryEye(0).distanceTo(SubmarineEntryAnimation.LADDER_START) < .001,
                "every boarding begins on the rear starting tread");
        helper.assertFalse(helper.makeMockPlayer(GameType.SURVIVAL).startRiding(sub), "boarding reserves the seat");
        sub.acceptInput(player, true, false, false);
        sub.setDeltaMovement(1, 1, 1);
        sub.applyDepthControl();
        helper.assertTrue(sub.getDeltaMovement().lengthSqr() == 0, "boarding locks propulsion");
        helper.runAfterDelay(SubmarineEntryAnimation.DURATION + 1, () -> {
            helper.assertFalse(sub.isBoarding(), "timeline finishes without extra input");
            helper.assertTrue(player.getVehicle() == sub, "finished player stays mounted");
            helper.assertTrue(sub.entryEye(0).distanceTo(SubmarineEntryAnimation.PILOT) < .001,
                    "timeline ends at the existing pilot reference");
            helper.assertTrue(SubmarineEntryAnimation.hatch(sub.entryTime(0)) == 0
                    && SubmarineEntryAnimation.door(sub.entryTime(0)) == 0, "both doors close");
            sub.acceptInput(player, false, false, true);
            helper.assertFalse(player.isPassenger(), "exit works after boarding");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public void boardingCancellationAndChoreography(GameTestHelper helper) {
        var sub = new SubmarineEntity(PracticeMod.SUBMARINE_ENTITY.get(), helper.getLevel());
        sub.setPos(helper.absoluteVec(new Vec3(1, 3, 1)));
        helper.getLevel().addFreshEntity(sub);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(sub.position().add(-3, 0, 0));
        sub.interact(player, InteractionHand.MAIN_HAND);
        sub.acceptInput(player, false, false, true);
        helper.assertFalse(sub.isBoarding() || player.isPassenger(), "exit clears boarding");
        for (double side : new double[]{-3, 3}) {
            Vec3 origin = new Vec3(side, 3, -2);
            Vec3 previous = SubmarineEntryAnimation.LADDER_START;
            Vec3 previousCamera = SubmarineEntryAnimation.cameraEye(0);
            for (int tick = 0; tick <= SubmarineEntryAnimation.DURATION; tick++) {
                Vec3 eye = SubmarineEntryAnimation.eye(origin, tick);
                helper.assertTrue(eye.distanceTo(previous) < .4, "path remains continuous");
                if (tick <= 3) helper.assertTrue(eye.distanceTo(SubmarineEntryAnimation.LADDER_START)<.01,
                        "boarding starts crouched beside the hatch without a run-up");
                if (tick >= 16 && tick <= 44) helper.assertTrue(SubmarineEntryAnimation.hatch(tick)<-89,
                        "hatch stays open through transfer and ladder descent");
                if (tick >= 38 && tick <= 50) {
                    helper.assertTrue(eye.x>1.0 && eye.x<1.2 && Math.abs(eye.z+.23)<.02,
                            "descent stays aligned with the rear-corner ladder");
                    helper.assertTrue(Math.abs(SubmarineEntryAnimation.heading(origin,tick)+90)<.01,
                            "player faces the side ladder");
                }
                if (tick >= 84) helper.assertTrue(Math.abs(SubmarineEntryAnimation.heading(origin,tick)
                        -SubmarineEntryAnimation.chair(tick))<.01,"rider follows swivel chair");
                helper.assertTrue(Double.isFinite(eye.lengthSqr()),"track positions stay finite");
                Vec3 camera = SubmarineEntryAnimation.cameraEye(tick);
                helper.assertTrue(camera.distanceTo(previousCamera)<.4, "first-person eye track has no cuts");
                helper.assertTrue(Float.isFinite(SubmarineEntryAnimation.cameraHeading(tick))
                        && Float.isFinite(SubmarineEntryAnimation.cameraPitch(tick)), "first-person orientation stays finite");
                helper.assertTrue(Math.abs(SubmarineEntryAnimation.cameraPitch(tick))<=60, "first-person gaze avoids a tumbling horizon");
                previousCamera = camera;
                previous = eye;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void depthControlAndUnderwaterExit(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                for (int y = 1; y < 6; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                }
            }
        }
        var submarine = new SubmarineEntity(PracticeMod.SUBMARINE_ENTITY.get(), helper.getLevel());
        submarine.setPos(helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
        helper.getLevel().addFreshEntity(submarine);
        var rider = helper.makeMockPlayer(GameType.SURVIVAL);
        submarine.tick();
        helper.assertTrue(rider.startRiding(submarine), "must be able to board underwater");
        double start = submarine.getY();
        submarine.acceptInput(rider, true, false, false);
        submarine.tick();
        helper.assertTrue(submarine.getY() > start && submarine.getY() < start + .01,
                "ballast must begin rising gradually, not jump to full speed");
        helper.assertTrue(submarine.getXRot() < 0, "ascent must begin trimming nose-up");
        double risingSpeed = submarine.getDeltaMovement().y;
        submarine.acceptInput(rider, false, true, false);
        submarine.tick();
        helper.assertTrue(Math.abs(submarine.getDeltaMovement().y - risingSpeed) <= .0041,
                "reversing depth input must respect vertical inertia");
        submarine.acceptInput(rider, true, true, false);
        submarine.tick();
        submarine.acceptInput(rider, false, false, false);
        for (int tick = 0; tick < 160; tick++) {
            rider.setAirSupply(1);
            submarine.tick();
        }
        helper.assertTrue(rider.getVehicle() == submarine, "submersion must never force a dismount");
        helper.assertTrue(rider.getAirSupply() == rider.getMaxAirSupply(), "the cabin must supply air");
        helper.assertTrue(Math.abs(submarine.getDeltaMovement().y) < .0001,
                "neutral controls must settle to depth hold");
        helper.assertTrue(Math.abs(submarine.getXRot()) < .02, "neutral trim must settle level");
        start = submarine.getY();
        var outsider = helper.makeMockPlayer(GameType.SURVIVAL);
        submarine.acceptInput(outsider, true, false, true);
        helper.assertTrue(rider.getVehicle() == submarine, "another player cannot eject the pilot");
        submarine.acceptInput(rider, false, false, true);
        helper.assertFalse(rider.isPassenger(), "the pilot's exit input must dismount");
        submarine.tick();
        helper.assertTrue(Math.abs(submarine.getY() - start) < 0.001, "an empty submarine must hold depth");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void movingAscentTiltsAndTravelsDiagonally(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
            for (int y = 1; y < 6; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
        var submarine = new SubmarineEntity(PracticeMod.SUBMARINE_ENTITY.get(), helper.getLevel());
        submarine.setPos(helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
        helper.getLevel().addFreshEntity(submarine);
        submarine.tick();
        var pilot = helper.makeMockPlayer(GameType.SURVIVAL);
        pilot.startRiding(submarine);
        submarine.setYRot(90);
        submarine.setDeltaMovement(-.2, 0, 0);
        submarine.acceptInput(pilot, true, false, false);
        Vec3 start = submarine.position();
        submarine.tick();
        helper.assertTrue(submarine.getX() < start.x && submarine.getY() > start.y,
                "moving ascent must travel forward and upward in world coordinates");
        helper.assertTrue(submarine.getXRot() < 0, "vehicle pitch must tilt nose-up, not just animate vertical travel");
        helper.assertTrue(Math.abs(submarine.getZ() - start.z) < .001, "depth control must follow vehicle heading");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void pressureCabinProtectsPilotAndRestoresWaterOnExit(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
            for (int y = 1; y < 6; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
        player.baseTick();
        helper.assertTrue(player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER), "unprotected eyes must detect water");
        var sub = new SubmarineEntity(PracticeMod.SUBMARINE_ENTITY.get(), helper.getLevel());
        sub.setPos(player.position());
        helper.getLevel().addFreshEntity(sub);
        helper.assertTrue(player.startRiding(sub), "pilot can enter the cabin underwater");
        helper.assertTrue(player.getEyeInFluidType().isAir(), "occupied pressure cabin must expose air at the pilot's eyes");
        helper.assertFalse(player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER), "pilot must not have a submerged-eye HUD");
        helper.assertFalse(player.isUnderWater(), "pilot must not use underwater camera/audio state");
        helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(1, 3, 1)))
                .is(net.minecraft.tags.FluidTags.WATER), "enclosure must not remove ocean water blocks");
        player.stopRiding();
        helper.assertTrue(player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER), "exit must restore water detection immediately");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void instrumentWaterSurfaceSemantics(GameTestHelper helper) {
        for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(1, y, 1), Blocks.WATER);
        helper.setBlock(new BlockPos(1, 5, 1), Blocks.AIR);
        BlockPos eye = helper.absolutePos(new BlockPos(1, 2, 1));
        var column = new SubmarineWaterColumn(helper.getLevel(), eye.getX(), eye.getZ());
        float depth = SubmarineDepth.measure(eye.getY() + .25, column);
        helper.assertTrue(Math.abs(depth - (2 + 8F / 9 - .25)) < .001, "fluid surface height must be included");
        helper.setBlock(new BlockPos(1, 5, 1), Blocks.STONE);
        helper.assertTrue(Float.isNaN(SubmarineDepth.measure(eye.getY(), column)), "roof must give NO DATA");
        helper.setBlock(new BlockPos(1, 5, 1), Blocks.AIR);
        helper.setBlock(new BlockPos(1, 3, 1), Blocks.OAK_SLAB.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true));
        helper.assertTrue(Float.isNaN(SubmarineDepth.measure(eye.getY(), column)), "obstructed waterlogged column must give NO DATA");
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.LAVA);
        helper.assertTrue(SubmarineDepth.measure(eye.getY(), column) == 0, "lava must not count as water");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void headlightsIlluminateWaterButStopAtWalls(GameTestHelper helper) {
        for (int z = 0; z <= 4; z++) helper.setBlock(new BlockPos(1, 2, z), Blocks.WATER);
        var submarine = new SubmarineEntity(PracticeMod.SUBMARINE_ENTITY.get(), helper.getLevel());
        Vec3 origin = helper.absoluteVec(new Vec3(1.5, 2.5, .5));
        submarine.setPos(origin);
        var forward = new Vec3(0, 0, 1);
        double open = SubmarineLightField.distance(helper.getLevel(), submarine, origin, forward);
        helper.assertTrue(open > 4, "shadow rays must pass through water");
        helper.setBlock(new BlockPos(1, 2, 2), Blocks.STONE);
        double blocked = SubmarineLightField.distance(helper.getLevel(), submarine, origin, forward);
        helper.assertTrue(Math.abs(blocked - 1.5) < .001, "wall must stop the ray at its exact surface");
        // Fractional movement must change the shadow continuously, not snap to a block center.
        double moved = SubmarineLightField.distance(helper.getLevel(), submarine, origin.add(0, 0, .125), forward);
        helper.assertTrue(Math.abs(moved - 1.375) < .001, "sub-block movement must retain full precision");
        helper.setBlock(new BlockPos(1, 2, 2), Blocks.WATER);
        double restored = SubmarineLightField.distance(helper.getLevel(), submarine, origin, forward);
        helper.assertTrue(Math.abs(restored - open) < .001, "removed obstruction must restore the ray");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void headlightsRequirePilotAndSurviveReload(GameTestHelper helper) {
        var submarine = new SubmarineEntity(PracticeMod.SUBMARINE_ENTITY.get(), helper.getLevel());
        submarine.setPos(helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
        helper.getLevel().addFreshEntity(submarine);
        var pilot = helper.makeMockPlayer(GameType.SURVIVAL);
        var outsider = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertFalse(submarine.headlightsOn(), "new submarines start with lights off");
        submarine.toggleHeadlights(outsider);
        helper.assertFalse(submarine.headlightsOn(), "an unoccupied sub cannot be toggled remotely");
        helper.assertTrue(pilot.startRiding(submarine), "pilot must mount");
        submarine.toggleHeadlights(outsider);
        helper.assertFalse(submarine.headlightsOn(), "another player cannot toggle the pilot's lights");
        submarine.toggleHeadlights(pilot);
        helper.assertTrue(submarine.headlightsOn(), "pilot can switch on the headlights");
        pilot.stopRiding();
        CompoundTag saved = new CompoundTag();
        submarine.save(saved);
        var restored = (SubmarineEntity) EntityType.loadEntityRecursive(saved, helper.getLevel(), entity -> entity);
        helper.assertTrue(restored != null && restored.headlightsOn(), "headlights must survive save/reload");
        helper.assertTrue(pilot.startRiding(submarine), "pilot can reboard");
        submarine.toggleHeadlights(pilot);
        helper.assertFalse(submarine.headlightsOn(), "second press switches lights off");
        // Old saves have no field and must safely default to off.
        saved.remove("Headlights");
        restored = (SubmarineEntity) EntityType.loadEntityRecursive(saved, helper.getLevel(), entity -> entity);
        helper.assertTrue(restored != null && !restored.headlightsOn(), "old saves default to lights off");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void canopySeatAndSideLook(GameTestHelper helper) {
        var submarine = PracticeMod.SUBMARINE_ENTITY.get().create(helper.getLevel());
        submarine.setPos(helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
        submarine.setYRot(37);
        helper.getLevel().addFreshEntity(submarine);
        var pilot = helper.makeMockPlayer(GameType.SURVIVAL);
        double emptyEye = submarine.pilotEyeOffset();
        helper.assertTrue(pilot.startRiding(submarine), "pilot must mount the raised seat");
        submarine.positionRider(pilot);
        double attachmentHeight = submarine.getPassengerRidingPosition(pilot).y - submarine.getY();
        helper.assertTrue(Math.abs(attachmentHeight - submarine.getBbHeight() / 3.0 - DeepseekerPilot.SEAT_RISE) < 0.0001,
                "seat attachment must follow the enlarged authored cockpit");
        helper.assertTrue(Math.abs(pilot.getEyeY() - submarine.getY() - submarine.pilotEyeOffset()) < 0.0001,
                "renderer reference must match the actual mounted eye");
        helper.assertTrue(Math.abs(emptyEye - submarine.pilotEyeOffset()) < 0.0001,
                "mounting must not shift the hull vertically");
        double floorY = submarine.getY() + submarine.pilotEyeOffset() - DeepseekerPilot.Y + DeepseekerPilot.FLOOR_Y;
        helper.assertTrue(pilot.getY() - floorY > 0.10,
                "even the unposed player feet must clear the cabin deck by ten centimeters");
        helper.assertTrue(pilot.getEyeY() - floorY >= 1.7,
                "the enlarged cockpit must have at least 1.7 blocks of eye-to-deck space");
        for (float yaw : new float[]{-90, 90}) {
            pilot.setYRot(submarine.getYRot() + yaw);
            submarine.onPassengerTurned(pilot);
            helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(pilot.getYRot() - submarine.getYRot()) - yaw) < 0.0001,
                    "pilot must be able to look fully sideways at any vehicle heading");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void cabinRejectsSwimmingAndCrouchingPose(GameTestHelper helper) {
        var sub = PracticeMod.SUBMARINE_ENTITY.get().create(helper.getLevel());
        sub.setPos(helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
        helper.getLevel().addFreshEntity(sub);
        var pilot = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(pilot.startRiding(sub), "pilot must mount");
        pilot.setSwimming(true);
        pilot.setPose(net.minecraft.world.entity.Pose.SWIMMING);
        pilot.setShiftKeyDown(true);
        pilot.tick();
        helper.assertTrue(pilot.getPose() == net.minecraft.world.entity.Pose.STANDING && !pilot.isSwimming(),
                "mounted pilot must retain standing dimensions for the seated render pose");
        helper.assertTrue(Math.abs(pilot.getEyeHeight() - 1.62) < .001,
                "swim/crouch input must not lower the mounted eye and shift the hull");
        pilot.setShiftKeyDown(false);
        pilot.stopRiding();
        pilot.setPose(net.minecraft.world.entity.Pose.SWIMMING);
        helper.assertTrue(pilot.getPose() == net.minecraft.world.entity.Pose.SWIMMING,
                "ordinary pose changes resume after dismount");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void chaseCameraTerrainAndWater(GameTestHelper helper) {
        for (int x=0;x<3;x++) for (int y=1;y<4;y++) for (int z=0;z<3;z++)
            helper.setBlock(new BlockPos(x,y,z), Blocks.AIR);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var anchor = helper.absoluteVec(new Vec3(.85, 2.5, .5));
        var back = new Vec3(0,0,1);
        helper.setBlock(new BlockPos(1,2,1), Blocks.WATER);
        double free = SubmarineCameraCollision.clearBoom(helper.getLevel(),player,anchor,back,1.5);
        helper.assertTrue(Math.abs(free-1.5)<.0001,"camera must travel through water without retracting");
        helper.setBlock(new BlockPos(1,2,1), Blocks.STONE);
        double corner = SubmarineCameraCollision.clearBoom(helper.getLevel(),player,anchor,back,1.5);
        helper.assertTrue(corner<.31,"near-plane corner probes must catch a wall missed by the center ray");
        helper.setBlock(new BlockPos(1,2,1), Blocks.AIR);
        helper.assertTrue(SubmarineCameraCollision.clearBoom(helper.getLevel(),player,anchor,back,1.5)>1.49,
                "removing the wall must allow the camera to extend again");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void chaseCameraSurfaceClearance(GameTestHelper helper) {
        for (int y=1;y<=5;y++) helper.setBlock(new BlockPos(1,y,1),Blocks.AIR);
        helper.setBlock(new BlockPos(1,2,1),Blocks.WATER);
        var submerged=helper.absoluteVec(new Vec3(1.5,2.3,1.5));
        double expected=helper.absolutePos(new BlockPos(1,2,1)).getY()+8.0/9-.75;
        helper.assertTrue(Math.abs(SubmarineCameraCollision.waterCeiling(helper.getLevel(),submerged)-expected)<.001,
                "camera near plane needs three quarters of a block below actual water height");
        helper.assertTrue(Math.abs(SubmarineCameraCollision.waterCeiling(helper.getLevel(),submerged.add(0,1,0))-expected)<.001,
                "a camera crossing just above the water must still find the surface");
        helper.setBlock(new BlockPos(1,3,1),Blocks.WATER);
        helper.assertTrue(Math.abs(SubmarineCameraCollision.waterCeiling(helper.getLevel(),submerged)-expected-1)<.001,
                "stacked water must use the exposed top, not an internal fluid boundary");
        helper.setBlock(new BlockPos(1,4,1),Blocks.STONE);
        helper.assertTrue(Double.isInfinite(SubmarineCameraCollision.waterCeiling(helper.getLevel(),submerged)),
                "solid ceilings belong to terrain collision, not surface avoidance");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void boardingUsesVisibleHull(GameTestHelper helper) {
        var sub = new SubmarineEntity(PracticeMod.SUBMARINE_ENTITY.get(), helper.getLevel());
        sub.setPos(helper.absoluteVec(new Vec3(1.5, 20, 1.5)));
        helper.getLevel().addFreshEntity(sub);
        // Rays from all sides must follow yaw/trim and include the canopy and aft hull.
        for (float yaw : new float[]{0, 90, 173}) for (float pitch : new float[]{-24, 0, 24}) {
            sub.setYRot(yaw);
            sub.setXRot(pitch);
            Vec3 center = boardingWorld(sub, new Vec3(0, 3, 0));
            for (Vec3 outside : new Vec3[]{new Vec3(20,3,0), new Vec3(-20,3,0),
                    new Vec3(0,3,-20), new Vec3(0,3,20), new Vec3(0,20,0), new Vec3(0,-20,0)}) {
                double hit = SubmarineBoarding.hitFraction(sub, boardingWorld(sub, outside), center);
                helper.assertTrue(hit > 0 && hit < 1, "boarding ray must hit rotated hull from every side");
            }
        }
        sub.setYRot(0);
        sub.setXRot(0);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 outside = boardingWorld(sub, new Vec3(20,3,0));
        Vec3 inside = boardingWorld(sub, new Vec3(0,3,0));
        Vec3 direction = inside.subtract(outside).normalize();
        double fraction = SubmarineBoarding.hitFraction(sub, outside, inside);
        Vec3 surface = outside.lerp(inside, fraction), eye = surface.subtract(direction.scale(2));
        aimForBoarding(player, eye, direction);
        helper.assertTrue(player.distanceTo(sub) > player.entityInteractionRange(),
                "regression must target hull outside the original boat interaction range");
        helper.assertTrue(SubmarineBoarding.pick(player) == sub, "visible aft side must be clickable");
        SubmarineBoarding.board(player, sub.getId()+10000);
        helper.assertFalse(player.isPassenger(), "an unrelated entity id cannot grant boarding");
        var obstruction = BlockPos.containing(eye.add(direction.scale(.8)));
        helper.getLevel().setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
        SubmarineBoarding.board(player, sub.getId());
        helper.assertFalse(player.isPassenger(), "boarding cannot pass through solid blocks");
        helper.getLevel().setBlockAndUpdate(obstruction, Blocks.AIR.defaultBlockState());
        aimForBoarding(player, surface.subtract(direction.scale(6)), direction);
        SubmarineBoarding.board(player, sub.getId());
        helper.assertFalse(player.isPassenger(), "distant hull is outside interaction reach");
        aimForBoarding(player, eye, direction);
        SubmarineBoarding.board(player, sub.getId());
        helper.assertTrue(player.getVehicle() == sub, "right-click boarding ray must mount at the pilot seat");
        var other = helper.makeMockPlayer(GameType.SURVIVAL);
        aimForBoarding(other, eye, direction);
        SubmarineBoarding.board(other, sub.getId());
        helper.assertFalse(other.isPassenger(), "an occupied submarine cannot accept a second pilot");
        helper.succeed();
    }

    private static Vec3 boardingWorld(SubmarineEntity sub, Vec3 model) {
        return model.subtract(DeepseekerPilot.X, DeepseekerPilot.Y, DeepseekerPilot.Z)
                .xRot(sub.getXRot()*net.minecraft.util.Mth.DEG_TO_RAD)
                .yRot((180-sub.getYRot())*net.minecraft.util.Mth.DEG_TO_RAD)
                .add(sub.position()).add(0,sub.pilotEyeOffset(),0);
    }

    private static void aimForBoarding(net.minecraft.world.entity.player.Player player, Vec3 eye, Vec3 direction) {
        player.setPos(eye.subtract(0,player.getEyeHeight(),0));
        player.setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));
        player.setXRot((float)-Math.toDegrees(Math.asin(direction.y)));
    }

    private static void clearPlacementSpace(GameTestHelper helper) {
        // The small shared template has a barrier roof; placement rays need open air above it.
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                for (int y = 2; y < 6; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    @GameTest(template = "empty")
    public void placeRideSaveAndRecover(GameTestHelper helper) {
        clearPlacementSpace(helper);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.WATER);
        player.setPos(helper.absoluteVec(new Vec3(1.5, 3, 1.5)));
        player.setXRot(90);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PracticeMod.SUBMARINE.get()));
        var result = PracticeMod.SUBMARINE.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result.getResult().consumesAction(), "water placement must succeed");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "survival placement must consume the item");
        var submarines = helper.getLevel().getEntitiesOfClass(SubmarineEntity.class,
                player.getBoundingBox().inflate(4));
        helper.assertTrue(submarines.size() == 1, "placement must spawn exactly one submarine");
        var submarine = submarines.getFirst();
        submarine.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getVehicle() == submarine, "right click must mount the submarine");
        helper.assertTrue(submarine.getControllingPassenger() == player, "rider must control the vehicle");
        var secondPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertFalse(secondPlayer.startRiding(submarine), "the prototype has only one seat");
        player.stopRiding();
        CompoundTag saved = new CompoundTag();
        submarine.save(saved);
        var restored = EntityType.loadEntityRecursive(saved, helper.getLevel(), entity -> entity);
        helper.assertTrue(restored instanceof SubmarineEntity, "saving must retain the submarine entity type");
        helper.assertTrue(submarine.getPickResult().is(PracticeMod.SUBMARINE.get()), "pick block must return the submarine item");
        submarine.hurt(helper.getLevel().damageSources().playerAttack(player), 5.0F);
        helper.assertTrue(submarine.isRemoved(), "sufficient damage must break the vehicle");
        helper.assertItemEntityCountIs(PracticeMod.SUBMARINE.get(), new BlockPos(1, 2, 1), 3, 1);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void creativePlacementAndBlockedPlacement(GameTestHelper helper) {
        clearPlacementSpace(helper);
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        player.setPos(helper.absoluteVec(new Vec3(1.5, 3, 1.5)));
        player.setXRot(90);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PracticeMod.SUBMARINE.get()));
        player.getAbilities().instabuild = true;
        var item = PracticeMod.SUBMARINE.get();
        helper.assertTrue(item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "placement on land must succeed");
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "creative placement must keep the item");
        helper.assertFalse(item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "an existing vehicle must obstruct a second placement");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(SubmarineEntity.class,
                player.getBoundingBox().inflate(4)).size() == 1, "blocked placement must not duplicate the vehicle");
        helper.succeed();
    }
}
