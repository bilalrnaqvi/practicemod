package com.bilal.practicemod.yacht.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Conservative circular turning envelope; only already loaded terrain is surveyed. */
public final class YachtNavigation {
    public static final int RADIUS=34, RANGE=128, STEP=8;
    public static boolean column(ServerLevel level,int x,int z,int waterY) {
        var p=new BlockPos(x,waterY-1,z);
        return waterY-5>=level.getMinBuildHeight() && waterY+20<level.getMaxBuildHeight() && level.getWorldBorder().isWithinBounds(p) && level.hasChunkAt(p) && level.getFluidState(p).is(FluidTags.WATER)
                && level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)<=waterY
                && level.getHeight(Heightmap.Types.OCEAN_FLOOR,x,z)<=waterY-5;
    }
    public static boolean clear(ServerLevel level,double x,double z,int waterY) {
        int cx=(int)Math.floor(x),cz=(int)Math.floor(z);
        for(int dx=-RADIUS;dx<=RADIUS;dx++) for(int dz=-RADIUS;dz<=RADIUS;dz++)
            if(dx*dx+dz*dz<=RADIUS*RADIUS && !column(level,cx+dx,cz+dz,waterY)) return false;
        return true;
    }
    static String diagnose(ServerLevel level,double x,double z,int waterY) {
        int cx=(int)Math.floor(x),cz=(int)Math.floor(z);
        for(int dx=-RADIUS;dx<=RADIUS;dx++)for(int dz=-RADIUS;dz<=RADIUS;dz++)if(dx*dx+dz*dz<=RADIUS*RADIUS && !column(level,cx+dx,cz+dz,waterY)) {
            var p=new BlockPos(cx+dx,waterY-1,cz+dz);
            if(!level.hasChunkAt(p))return p+" unloaded";
            return p+" loaded="+level.hasChunkAt(p)+" fluid="+level.getFluidState(p)+" surface="+level.getHeight(Heightmap.Types.WORLD_SURFACE,p.getX(),p.getZ())+" floor="+level.getHeight(Heightmap.Types.OCEAN_FLOOR,p.getX(),p.getZ());
        }
        return "terrain clear";
    }
    /** Bounded incremental A*: eight expanded nodes per server tick, max 1024 nodes. */
    public static final class Search {
        private record Node(int x,int z) {}
        private record Candidate(Node node,double score) {}
        final ServerLevel level;final Vec3 origin;final int waterY;final Node goal;
        final PriorityQueue<Candidate> open=new PriorityQueue<>(Comparator.comparingDouble(Candidate::score));
        final Map<Node,Node> previous=new HashMap<>();final Map<Node,Double> costs=new HashMap<>();final Set<Node> closed=new HashSet<>();
        final Map<Long,Boolean> surveyed=new HashMap<>();
        public boolean done;public List<Vec3> result=List.of();
        public Search(ServerLevel level,Vec3 origin,int waterY,double x,double z) {
            this.level=level;this.origin=origin;this.waterY=waterY;
            goal=new Node((int)Math.round((x-origin.x)/STEP),(int)Math.round((z-origin.z)/STEP));
            var start=new Node(0,0);open.add(new Candidate(start,0));costs.put(start,0d);
        }
        private boolean surveyedClear(double x,double z) {
            int cx=(int)Math.floor(x),cz=(int)Math.floor(z);
            for(int dx=-RADIUS;dx<=RADIUS;dx++)for(int dz=-RADIUS;dz<=RADIUS;dz++) {
                if(dx*dx+dz*dz>RADIUS*RADIUS)continue;
                int wx=cx+dx,wz=cz+dz;long key=((long)wx<<32)^(wz&0xffffffffL);
                if(!surveyed.computeIfAbsent(key,k->column(level,wx,wz,waterY)))return false;
            }
            return true;
        }
        public void tick() {
            for(int budget=0;budget<8 && !done;budget++) {
                if(open.isEmpty() || closed.size()>=1024) {done=true;return;}
                var n=open.remove().node();if(!closed.add(n)) continue;
                if(n.equals(goal)) {
                    var reverse=new ArrayList<Vec3>();
                    for(var at=n;previous.containsKey(at);at=previous.get(at))reverse.add(origin.add(at.x*STEP,0,at.z*STEP));
                    Collections.reverse(reverse);result=reverse;done=true;return;
                }
                for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                    var next=new Node(n.x+d[0],n.z+d[1]);
                    if(Math.abs(next.x)>RANGE/STEP || Math.abs(next.z)>RANGE/STEP || closed.contains(next))continue;
                    double cost=costs.get(n)+STEP;if(cost>=costs.getOrDefault(next,Double.POSITIVE_INFINITY))continue;
                    // Sample the complete eight-block edge, not only its endpoint.
                    boolean safe=true;
                    for(int t=1;t<=STEP;t++) if(!surveyedClear(origin.x+n.x*STEP+d[0]*t,origin.z+n.z*STEP+d[1]*t)) {safe=false;break;}
                    if(!safe)continue;
                    previous.put(next,n);costs.put(next,cost);
                    open.add(new Candidate(next,cost+Math.hypot(next.x-goal.x,next.z-goal.z)*STEP));
                }
            }
        }
    }
}
