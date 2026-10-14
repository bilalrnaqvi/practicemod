package com.bilal.practicemod;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gameevent.GameEvent;

public class AtvItem extends Item {
    public AtvItem(Properties properties) { super(properties); }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() != Direction.UP) return InteractionResult.PASS;
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() || !level.getFluidState(pos).isEmpty())
            return InteractionResult.FAIL;
        var atv = new AtvEntity(PracticeMod.ATV_ENTITY.get(), level);
        var location = context.getClickLocation();
        atv.setPos(location.x, location.y + .001, location.z);
        atv.setYRot(context.getRotation());
        if (!level.noCollision(atv, atv.getBoundingBox()) || !level.getEntities(atv, atv.getBoundingBox(),
                e -> e.isPickable() && !e.isSpectator()).isEmpty()) return InteractionResult.FAIL;
        if (!level.isClientSide) {
            if (!level.addFreshEntity(atv)) return InteractionResult.FAIL;
            context.getItemInHand().consume(1, context.getPlayer());
            level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, location);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.practicemod.atv.place").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.practicemod.atv.drive").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.practicemod.atv.pickup").withStyle(ChatFormatting.GRAY));
    }
}
