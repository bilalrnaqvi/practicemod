package com.bilal.practicemod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runtime regression coverage for the hand-slot, state, movement, and landing paths. */
@GameTestHolder(PracticeMod.MODID)
@PrefixGameTestTemplate(false)
public final class UmbrellaGliderGameTests {
    @GameTest(template = "empty")
    public void visualClipsRunOnceAndReverseCleanly(GameTestHelper helper) {
        UmbrellaGliderVisualState state = new UmbrellaGliderVisualState();
        helper.assertFalse(state.shouldRender(), "a fresh visual state must start compact and hidden");

        state.tick(true);
        helper.assertTrue(state.phase() == UmbrellaGliderVisualState.Phase.DEPLOYING,
                "the deployed edge must start the opening clip");
        for (int tick = 1; tick < (int) UmbrellaGliderVisualState.DEPLOY_TICKS; tick++) {
            state.tick(true);
        }
        helper.assertTrue(state.phase() == UmbrellaGliderVisualState.Phase.GLIDING,
                "the opening clip must settle in the fully deployed pose");
        float openRotation = state.sectorRotationDegrees(1.0F);
        state.tick(true);
        helper.assertTrue(state.phase() == UmbrellaGliderVisualState.Phase.GLIDING
                        && state.sectorRotationDegrees(1.0F) == openRotation,
                "a held deployed state must not replay the opening clip every tick");

        UmbrellaGliderVisualState interrupted = new UmbrellaGliderVisualState();
        for (int tick = 0; tick < 7; tick++) {
            interrupted.tick(true);
        }
        float rotationBeforeCancel = interrupted.sectorRotationDegrees(1.0F);
        float scaleBeforeCancel = interrupted.sectorScale(1.0F);
        interrupted.tick(false);
        helper.assertTrue(interrupted.phase() == UmbrellaGliderVisualState.Phase.CLOSING,
                "canceling an opening glide must switch to the closing clip");
        helper.assertTrue(Math.abs(interrupted.sectorRotationDegrees(0.0F) - rotationBeforeCancel) < 1.0E-5F
                        && Math.abs(interrupted.sectorScale(0.0F) - scaleBeforeCancel) < 1.0E-5F,
                "an interrupted opening must reverse from its exact current rig pose");
        for (int tick = 1; tick < (int) UmbrellaGliderVisualState.CLOSE_TICKS; tick++) {
            interrupted.tick(false);
        }
        helper.assertTrue(interrupted.phase() == UmbrellaGliderVisualState.Phase.INACTIVE,
                "the closing clip must return to the compact pose");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void umbrellaPoseKeepsItsLoadedSide(GameTestHelper helper) {
        UmbrellaGliderVisualState state = new UmbrellaGliderVisualState();
        for (int tick = 0; tick < 5; tick++) {
            state.tick(true);
        }
        state.sampleRenderPose(1.0F);
        helper.assertTrue(state.renderArmWeight() == 1.0F
                        && state.renderBodyWeight() < 0.5F,
                "the gripping arm must reach before the body catches the canopy load");
        for (int tick = 0; tick < 200; tick++) {
            state.tick(true);
            state.sampleRenderPose(0.5F);
            if (tick >= 5) {
                helper.assertTrue(state.renderBodyRoll() < -10.0F,
                        "the resting glide must keep favoring the gripping side throughout idle motion");
                helper.assertTrue(state.renderArmWeight() == 1.0F,
                        "the deployed hand must stay locked instead of repeating the reach");
            }
        }
        state.sampleRenderPose(1.0F);
        float rollBeforeClose = state.renderBodyRoll();
        state.tick(false);
        state.sampleRenderPose(0.0F);
        helper.assertTrue(Math.abs(state.renderBodyRoll() - rollBeforeClose) < 1.0E-5F,
                "closing must begin at the previous rendered posture without a snap");
        for (int tick = 0; tick <= (int) UmbrellaGliderVisualState.CLOSE_TICKS; tick++) {
            state.tick(false);
        }
        state.sampleRenderPose(1.0F);
        helper.assertTrue(state.renderArmWeight() == 0.0F && state.renderBodyRoll() == 0.0F,
                "closing must release the grip and restore the neutral body attitude");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void canopySpinStaysSmoothThroughWrapAndClosing(GameTestHelper helper) {
        UmbrellaGliderVisualState state = new UmbrellaGliderVisualState();
        state.tick(false);
        helper.assertTrue(state.canopySpinDegrees(1.0F) == 0.0F,
                "a stowed canopy must not spin");
        for (int tick = 0; tick < 1300; tick++) {
            float before = state.canopySpinDegrees(1.0F);
            state.tick(true);
            float start = state.canopySpinDegrees(0.0F);
            float end = state.canopySpinDegrees(1.0F);
            helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(start - before)) < 0.0001F,
                    "the rotation must remain continuous across ticks and full turns");
            helper.assertTrue(end >= start && end - start <= 0.601F,
                    "rotation must advance gently without reversing at the wrap boundary");
            helper.assertTrue(Math.abs(state.canopySpinDegrees(0.5F) - (start + end) * 0.5F) < 0.0001F,
                    "render frames must interpolate smoothly between spin samples");
            if (tick > 10) {
                helper.assertTrue(Math.abs(end - start - 0.6F) < 0.0001F,
                        "the open canopy must keep turning at twelve degrees per second");
            }
        }
        float previousStep = 0.601F;
        for (int tick = 0; tick < 8; tick++) {
            float before = state.canopySpinDegrees(1.0F);
            state.tick(false);
            float step = net.minecraft.util.Mth.wrapDegrees(state.canopySpinDegrees(1.0F) - before);
            helper.assertTrue(step >= 0.0F && step <= previousStep + 0.0001F,
                    "closing must slow the spin without snapping back to the starting angle");
            previousStep = step;
        }
        helper.assertTrue(previousStep == 0.0F, "the closed canopy must stop spinning");
        float stopped = state.canopySpinDegrees(1.0F);
        state.tick(true);
        helper.assertTrue(state.canopySpinDegrees(0.0F) == stopped,
                "reopening during the same animation state must preserve the rotation");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void deployGlideAndClose(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absolutePos(net.minecraft.core.BlockPos.ZERO).getCenter());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PracticeMod.UMBRELLA_GLIDER.get()));

        startFalling(player);
        helper.assertTrue(UmbrellaGliderHandler.canDeploy(player),
                "a survival player holding the canopy must be able to deploy while falling");

        UmbrellaGliderHandler.tickGlider(player);
        helper.assertTrue(UmbrellaGliderHandler.isDeployed(player),
                "a real fall with the canopy in hand must open the synchronized state on its own");
        helper.assertTrue(player.getDeltaMovement().y > -1.0 && player.getDeltaMovement().y < 0.0,
                "the first glide tick must brake the fall without creating lift");
        for (int tick = 0; tick < 20; tick++) {
            UmbrellaGliderHandler.tickGlider(player);
        }
        helper.assertTrue(Math.abs(player.getDeltaMovement().y - UmbrellaGliderPhysics.TARGET_DESCENT) < 1.0E-9,
                "a deployed umbrella must converge to predictable terminal descent");

        LivingFallEvent fallEvent = new LivingFallEvent(player, 20.0F, 1.0F);
        UmbrellaGliderHandler.onLivingFall(fallEvent);
        helper.assertTrue(fallEvent.isCanceled(), "active deployment must prevent landing fall damage");
        helper.assertFalse(UmbrellaGliderHandler.isDeployed(player),
                "landing protection must close the umbrella immediately");

        startFalling(player);
        player.setOnGround(true);
        helper.assertFalse(UmbrellaGliderHandler.canDeploy(player),
                "grounded deployment must be rejected");

        startFalling(player);
        player.fallDistance = 0.0F;
        helper.assertFalse(UmbrellaGliderHandler.canDeploy(player),
                "an ordinary jump arc must not open the canopy before any real fall distance");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(PracticeMod.UMBRELLA_GLIDER.get()));
        startFalling(player);
        helper.assertTrue(UmbrellaGliderHandler.canDeploy(player),
                "the offhand must count as holding the canopy");

        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.getInventory().setItem(9, new ItemStack(PracticeMod.UMBRELLA_GLIDER.get()));
        startFalling(player);
        helper.assertFalse(UmbrellaGliderHandler.canDeploy(player),
                "a stowed canopy in a non-selected slot must never deploy");

        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PracticeMod.UMBRELLA_GLIDER.get()));
        startFalling(player);
        UmbrellaGliderHandler.tickGlider(player);
        helper.assertTrue(UmbrellaGliderHandler.isDeployed(player),
                "the canopy must reopen once it is back in hand and falling");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        UmbrellaGliderHandler.tickGlider(player);
        helper.assertFalse(UmbrellaGliderHandler.isDeployed(player),
                "switching away from the canopy mid-glide must close it on the next tick");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PracticeMod.UMBRELLA_GLIDER.get()));
        player.setHealth(0.0F);
        player.setData(PracticeMod.UMBRELLA_GLIDER_DEPLOYED, true);
        UmbrellaGliderHandler.tickGlider(player);
        helper.assertFalse(UmbrellaGliderHandler.isDeployed(player),
                "death must clear a deployed umbrella on the next production tick");
        helper.succeed();
    }

    /** Puts the mock player into the airborne, already-descending state the deploy gate requires. */
    private static void startFalling(Player player) {
        player.setOnGround(false);
        player.setDeltaMovement(0.0, -1.0, 0.0);
        player.fallDistance = 4.0F;
    }
}
