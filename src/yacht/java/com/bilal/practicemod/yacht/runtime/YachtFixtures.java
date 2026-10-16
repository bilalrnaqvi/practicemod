package com.bilal.practicemod.yacht.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import java.util.*;

/** Interactions retain the canonical furniture; inventories and door blocks live with the hull. */
@EventBusSubscriber(modid="practicemod")
public final class YachtFixtures {
    static final Map<UUID,Map<String,SimpleContainer>> STORAGE=new HashMap<>();
    @SubscribeEvent public static void click(PlayerInteractEvent.RightClickBlock event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        var c=YachtRuntime.nearest(player);if(c==null)return;
        var center=c.ship.getPlot().getCenterBlock();var pos=event.getPos().subtract(center);
        // Sable's block hit is in plot coordinates. Never trust client reach alone.
        if(Math.abs(pos.getX())>10 || Math.abs(pos.getZ())>32)return;
        if(YachtRuntime.point(c.ship,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5).distanceToSqr(player.position())>36)return;
        for(var raw:YachtAssets.DATA.getAsJsonArray("doors")) {
            var door=raw.getAsJsonObject();boolean hit=false;
            for(var patch:door.getAsJsonArray("patches")){var a=patch.getAsJsonArray();if(pos.equals(new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt())))hit=true;}
            if(!hit)continue;
            String key="door_"+door.get("name").getAsString();boolean closed=!c.data.getBoolean(key);
            if(door.get("name").getAsString().contains("service") && !steward(c,player))return;
            if(closed && obstructed(c,door)) {player.displayClientMessage(Component.literal("Doorway occupied."),true);consume(event);return;}
            for(var patch:door.getAsJsonArray("patches")) {
                var a=patch.getAsJsonArray();int id=a.get(closed?4:3).getAsInt();
                c.ship.getPlot().getEmbeddedLevelAccessor().setBlock(new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()),id<0?Blocks.AIR.defaultBlockState():YachtAssets.HULL.defaultBlockState().setValue(YachtAssets.CELL,id),3);
            }
            c.data.putBoolean(key,closed);consume(event);return;
        }
        for(var raw:YachtAssets.DATA.getAsJsonObject("layout").getAsJsonArray("anchors")) {
            var anchor=raw.getAsJsonObject();String name=anchor.get("name").getAsString();var p=anchor.getAsJsonArray("position");
            var at=new Vec3(p.get(0).getAsDouble(),p.get(1).getAsDouble(),p.get(2).getAsDouble());
            if(Vec3.atCenterOf(pos).distanceToSqr(at)>3)continue;
            if(name.startsWith("storage_")) {
                if(steward(c,player))openStorage(c,player,name,at);else player.displayClientMessage(Component.literal("Storage is reserved for the owner and steward."),true);
                consume(event);return;
            }
            if(anchor.get("kind").getAsString().equals("bed")) {
                // Rest without assigning an invalid plot-coordinate respawn point.
                player.resetStat(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.TIME_SINCE_REST));
                player.displayClientMessage(Component.literal("Rested. Your land respawn point is unchanged."),true);consume(event);return;
            }
        }
    }
    @SubscribeEvent public static void protectBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if(event.getState().is(YachtAssets.HULL))event.setCanceled(true);
    }
    @SubscribeEvent public static void protectPlacement(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
        for(var c:YachtRuntime.ACTIVE.values()) {
            if(event.getLevel()!=c.ship.getLevel())continue;
            var p=event.getPos().subtract(c.ship.getPlot().getCenterBlock());
            if(Math.abs(p.getX())<40 && Math.abs(p.getZ())<40 && p.getY()>-16 && p.getY()<24){event.setCanceled(true);return;}
        }
    }
    static boolean steward(YachtRuntime.Controller c,ServerPlayer p){return p.hasPermissions(2)||c.data.getUUID("owner").equals(p.getUUID())||c.data.getString("steward").equals(p.getUUID().toString());}
    static void consume(PlayerInteractEvent.RightClickBlock event){event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);}
    static boolean obstructed(YachtRuntime.Controller c,com.google.gson.JsonObject door) {
        var origin=YachtRuntime.point(c.ship,0,0,0);
        for(var e:c.ship.getLevel().getEntities((net.minecraft.world.entity.Entity)null,new AABB(origin.subtract(40,12,40),origin.add(40,18,40)),e->e instanceof LivingEntity)) {
            var p=YachtRuntime.local(c.ship,e.position());double r=e.getBbWidth()*.71;
            var entity=new AABB(p.x-r,p.y,p.z-r,p.x+r,p.y+e.getBbHeight(),p.z+r);
            for(var patch:door.getAsJsonArray("patches")) {
                var a=patch.getAsJsonArray();int id=a.get(4).getAsInt();if(id<0)continue;
                for(var box:YachtAssets.SHAPES[id].toAabbs())if(entity.intersects(box.move(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble())))return true;
            }
        }
        return false;
    }
    static void openStorage(YachtRuntime.Controller c,ServerPlayer player,String name,Vec3 anchor) {
        var inventory=STORAGE.computeIfAbsent(c.ship.getUniqueId(),id->new HashMap<>()).computeIfAbsent(name,key->{
            var box=new SimpleContainer(27){@Override public void setChanged(){super.setChanged();var items=new ListTag();
                for(int slot=0;slot<getContainerSize();slot++)if(!getItem(slot).isEmpty()){var entry=new CompoundTag();entry.putInt("slot",slot);entry.put("item",getItem(slot).save(c.ship.getLevel().registryAccess()));items.add(entry);}c.data.put(key,items);
            }};
            var saved=c.data.getList(key,Tag.TAG_COMPOUND).copy();
            for(var tag:saved){var entry=(CompoundTag)tag;int slot=entry.getInt("slot");if(slot>=0&&slot<27)box.setItem(slot,ItemStack.parseOptional(c.ship.getLevel().registryAccess(),entry.getCompound("item")));}
            return box;
        });
        player.openMenu(new SimpleMenuProvider((id,inv,p)->new ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x3,id,inv,inventory,3){
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player user){return !c.ship.isRemoved()&&YachtRuntime.point(c.ship,anchor.x,anchor.y,anchor.z).distanceToSqr(user.position())<64 && user instanceof ServerPlayer server && steward(c,server);}
        },Component.literal("White Horizon · Service locker")));
    }
}
