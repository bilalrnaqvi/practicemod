package com.bilal.practicemod.yacht.runtime;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid="practicemod")
public final class YachtInteractions {
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickItem event) {
        if(event.getItemStack().is(YachtAssets.DEED) && event.getEntity() instanceof ServerPlayer player) {
            try {YachtRuntime.launch(player);if(!player.getAbilities().instabuild)event.getItemStack().shrink(1);player.displayClientMessage(Component.literal("White Horizon launched 42 blocks ahead. Board via the aft ladders; the captain is in the upper bridge."),false);}
            catch(IllegalArgumentException e){player.displayClientMessage(Component.literal(e.getMessage()),false);}
            event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
    @SubscribeEvent public static void captain(PlayerInteractEvent.EntityInteract event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        for(var c:YachtRuntime.ACTIVE.values())if(c.data.hasUUID("captain")&&c.data.getUUID("captain").equals(event.getTarget().getUUID())) {
            if(c.ship.getLevel()==player.level() && player.distanceToSqr(event.getTarget())<64)YachtNetwork.send(c,player,true,true);
            event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);return;
        }
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("yacht")
            .then(Commands.literal("deed").requires(s->s.hasPermission(2)).executes(c->{c.getSource().getPlayerOrException().getInventory().add(new net.minecraft.world.item.ItemStack(YachtAssets.DEED));return 1;}))
            .then(Commands.literal("status").executes(c->{var yacht=YachtRuntime.nearest(c.getSource().getPlayerOrException());c.getSource().sendSuccess(()->Component.literal(yacht==null?"No yacht nearby":yacht.status),false);return 1;}))
            .then(Commands.literal("stop").executes(c->{var p=c.getSource().getPlayerOrException();var yacht=YachtRuntime.nearest(p);if(yacht!=null&&(YachtRuntime.aboard(yacht,p)||yacht.owns(p)))yacht.stop("STOPPING — emergency command");return 1;}))
            .then(Commands.literal("navigator").then(Commands.argument("player",EntityArgument.player()).executes(c->{var p=c.getSource().getPlayerOrException();var yacht=YachtRuntime.nearest(p);if(yacht==null||!yacht.data.getUUID("owner").equals(p.getUUID()))return 0;yacht.data.putString("navigator",EntityArgument.getPlayer(c,"player").getUUID().toString());c.getSource().sendSuccess(()->Component.literal("Navigator permission granted."),false);return 1;})))
            .then(Commands.literal("steward").then(Commands.argument("player",EntityArgument.player()).executes(c->{var p=c.getSource().getPlayerOrException();var yacht=YachtRuntime.nearest(p);if(yacht==null||!yacht.data.getUUID("owner").equals(p.getUUID()))return 0;yacht.data.putString("steward",EntityArgument.getPlayer(c,"player").getUUID().toString());return 1;})))
            .then(Commands.literal("revokeSteward").executes(c->{var p=c.getSource().getPlayerOrException();var yacht=YachtRuntime.nearest(p);if(yacht==null||!yacht.data.getUUID("owner").equals(p.getUUID()))return 0;yacht.data.remove("steward");return 1;}))
            .then(Commands.literal("revokeNavigator").executes(c->{var p=c.getSource().getPlayerOrException();var yacht=YachtRuntime.nearest(p);if(yacht==null||!yacht.data.getUUID("owner").equals(p.getUUID()))return 0;yacht.data.remove("navigator");return 1;})));
    }
}
