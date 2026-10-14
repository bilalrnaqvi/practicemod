package com.bilal.practicemod;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;

public class MechanicalDolphinItem extends Item {
    public MechanicalDolphinItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        var hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResultHolder.pass(stack);
        if (!level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER))
            return InteractionResultHolder.fail(stack);
        var ski = new MechanicalDolphinEntity(PracticeMod.MECHANICAL_DOLPHIN_ENTITY.get(), level);
        ski.setPos(hit.getLocation().x, hit.getLocation().y + 0.04, hit.getLocation().z);
        ski.setYRot(player.getYRot());
        if (!level.noCollision(ski, ski.getBoundingBox()) || !level.getEntities(ski,
                ski.getBoundingBox(), entity -> entity.isPickable() && !entity.isSpectator()).isEmpty())
            return InteractionResultHolder.fail(stack);
        if (!level.isClientSide) {
            level.addFreshEntity(ski);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, hit.getLocation());
            stack.consume(1, player);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.practicemod.mechanical_dolphin.place").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.practicemod.mechanical_dolphin.drive").withStyle(ChatFormatting.AQUA));
    }
}
