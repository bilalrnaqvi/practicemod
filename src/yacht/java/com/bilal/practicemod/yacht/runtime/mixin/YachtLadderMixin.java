package com.bilal.practicemod.yacht.runtime.mixin;

import com.bilal.practicemod.yacht.runtime.YachtAssets;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Authored aft ladders sit on a cell boundary. Allow contact with their actual
 * rungs/rails, including reaching the first rung from water, instead of requiring
 * the player's feet to enter the solid rung's block. Vanilla climbing does motion. */
@Mixin(LivingEntity.class)
public abstract class YachtLadderMixin {
    @Inject(method="onClimbable",at=@At("RETURN"),cancellable=true)
    private void whiteHorizonLadderContact(CallbackInfoReturnable<Boolean> result) {
        var self=(LivingEntity)(Object)this;
        if(result.getReturnValue() || self.isSpectator() || YachtAssets.HULL==null)return;
        for(var ship:Sable.HELPER.getAllIntersecting(self.level(),new BoundingBox3d(self.getBoundingBox().inflate(.1)))) {
            var feet=ship.logicalPose().transformPositionInverse(self.position());
            double r=self.getBbWidth()*.71+.06;
            var contact=new AABB(feet.x-r,feet.y,feet.z-r,feet.x+r,feet.y+Math.min(1.2,self.getBbHeight()),feet.z+r);
            for(var pos:BlockPos.betweenClosed(BlockPos.containing(contact.minX,contact.minY,contact.minZ),BlockPos.containing(contact.maxX,contact.maxY,contact.maxZ))) {
                var state=self.level().getBlockState(pos);if(!state.is(YachtAssets.HULL))continue;
                var ladder=YachtAssets.DATA.getAsJsonArray("palette").get(state.getValue(YachtAssets.CELL)).getAsJsonObject().getAsJsonArray("ladders");
                for(var raw:ladder){var a=raw.getAsJsonArray();var box=new AABB(a.get(0).getAsDouble()/16,a.get(1).getAsDouble()/16,a.get(2).getAsDouble()/16,a.get(3).getAsDouble()/16,a.get(4).getAsDouble()/16,a.get(5).getAsDouble()/16).move(pos);
                    if(box.intersects(contact)){result.setReturnValue(true);return;}
                }
            }
        }
    }
}
