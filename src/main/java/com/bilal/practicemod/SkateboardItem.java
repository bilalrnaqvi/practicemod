package com.bilal.practicemod;

import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gameevent.GameEvent;

public class SkateboardItem extends Item {
    public SkateboardItem(Properties properties) { super(properties); }

    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() != Direction.UP) return InteractionResult.PASS;
        var level = context.getLevel();
        var player = context.getPlayer();
        var location = context.getClickLocation();
        var board = new SkateboardEntity(PracticeMod.SKATEBOARD_ENTITY.get(), level);
        board.setPos(location.x, location.y + 0.001, location.z);
        board.setYRot(context.getRotation());
        if (!level.noCollision(board, board.getBoundingBox())) return InteractionResult.FAIL;
        if (!level.isClientSide) {
            if (!level.addFreshEntity(board)) return InteractionResult.FAIL;
            context.getItemInHand().consume(1, player);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, location);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
