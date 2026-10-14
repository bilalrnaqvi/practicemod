package com.bilal.practicemod;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Advances each visible player's deployment clip and procedural suspension rig once per tick. */
@EventBusSubscriber(modid = PracticeMod.MODID, value = Dist.CLIENT)
public final class UmbrellaGliderAnimationController {
    private static final Int2ObjectOpenHashMap<UmbrellaGliderVisualState> STATES = new Int2ObjectOpenHashMap<>();

    private UmbrellaGliderAnimationController() {
    }

    static UmbrellaGliderVisualState get(int entityId) {
        return STATES.get(entityId);
    }

    /** Used by third-person held-item rendering so the item stays hidden through the closing clip. */
    public static boolean isAnimating(Player player) {
        UmbrellaGliderVisualState state = STATES.get(player.getId());
        return state != null && state.shouldRender();
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide()) {
            return;
        }

        boolean deployed = UmbrellaGliderHandler.isDeployed(player);
        UmbrellaGliderVisualState state = STATES.get(player.getId());
        if (state == null) {
            if (!deployed) {
                return;
            }
            state = new UmbrellaGliderVisualState();
            STATES.put(player.getId(), state);
        }

        state.tick(player, deployed);
        if (!deployed && !state.shouldRender()) {
            STATES.remove(player.getId());
        }
    }

    @SubscribeEvent
    static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof Player player) {
            STATES.remove(player.getId());
        }
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        STATES.clear();
    }
}
