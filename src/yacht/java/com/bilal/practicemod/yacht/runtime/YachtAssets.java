package com.bilal.practicemod.yacht.runtime;

import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;

@EventBusSubscriber(modid="practicemod",bus=EventBusSubscriber.Bus.MOD)
public final class YachtAssets {
    public static final JsonObject DATA=load();
    public static final IntegerProperty CELL=IntegerProperty.create("cell",0,DATA.getAsJsonArray("palette").size()-1);
    public static final VoxelShape[] SHAPES=shapes();
    public static HullBlock HULL;
    public static net.minecraft.world.item.Item DEED;
    private static JsonObject load() {
        try(var in=YachtAssets.class.getResourceAsStream("/white_horizon_runtime.json")) {
            if(in==null) throw new IllegalStateException("Missing canonical yacht export");
            return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        } catch(IOException e) { throw new ExceptionInInitializerError(e); }
    }
    private static VoxelShape[] shapes() {
        var palette=DATA.getAsJsonArray("palette");var result=new VoxelShape[palette.size()];
        for(int i=0;i<result.length;i++) {
            var shape=Shapes.empty();
            for(var element:palette.get(i).getAsJsonObject().getAsJsonArray("boxes")) {
                var b=element.getAsJsonArray();shape=Shapes.or(shape,Block.box(b.get(0).getAsDouble(),b.get(1).getAsDouble(),b.get(2).getAsDouble(),b.get(3).getAsDouble(),b.get(4).getAsDouble(),b.get(5).getAsDouble()));
            }
            result[i]=shape.optimize();
        }
        return result;
    }
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK,h->{
            HULL=new HullBlock(BlockBehaviour.Properties.of().strength(-1,3600000).noOcclusion()
                    .lightLevel(s->DATA.getAsJsonArray("palette").get(s.getValue(CELL)).getAsJsonObject().get("light").getAsInt()));
            h.register(ResourceLocation.fromNamespaceAndPath("practicemod","white_horizon_cell"),HULL);
        });
        event.register(Registries.ITEM,h->{
            DEED=new net.minecraft.world.item.Item(new net.minecraft.world.item.Item.Properties().stacksTo(1));
            h.register(ResourceLocation.fromNamespaceAndPath("practicemod","white_horizon_deed"),DEED);
        });
    }
    @SubscribeEvent public static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
        if(event.getTabKey()==net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(DEED);
    }
    public static final class HullBlock extends Block {
        public HullBlock(Properties properties) { super(properties);registerDefaultState(stateDefinition.any().setValue(CELL,0)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { b.add(CELL); }
        @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c) { return SHAPES[s.getValue(CELL)]; }
        @Override protected VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c) { return SHAPES[s.getValue(CELL)]; }
        @Override public boolean isLadder(BlockState state,LevelReader level,BlockPos pos,net.minecraft.world.entity.LivingEntity entity) {
            return DATA.getAsJsonArray("palette").get(state.getValue(CELL)).getAsJsonObject().get("ladder").getAsBoolean();
        }
        @Override protected boolean skipRendering(BlockState state,BlockState adjacent,Direction side) {
            if(adjacent.is(this) && DATA.getAsJsonArray("palette").get(state.getValue(CELL)).getAsJsonObject().get("water").getAsBoolean()
                    && DATA.getAsJsonArray("palette").get(adjacent.getValue(CELL)).getAsJsonObject().get("water").getAsBoolean())return true;
            return super.skipRendering(state,adjacent,side);
        }
        @Override protected int getLightBlock(BlockState state,BlockGetter level,BlockPos pos) {
            return Block.isShapeFullBlock(SHAPES[state.getValue(CELL)])?15:0;
        }
        @Override protected FluidState getFluidState(BlockState state) {
            return DATA.getAsJsonArray("palette").get(state.getValue(CELL)).getAsJsonObject().get("water").getAsBoolean()?Fluids.WATER.getSource(false):Fluids.EMPTY.defaultFluidState();
        }
    }
}
