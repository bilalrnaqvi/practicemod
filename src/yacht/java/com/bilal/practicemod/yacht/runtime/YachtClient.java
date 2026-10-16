package com.bilal.practicemod.yacht.runtime;

import com.google.gson.*;
import dev.ryanhcode.sable.sublevel.water_occlusion.*;
import dev.ryanhcode.sable.util.BoundedBitVolume3i;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

@net.neoforged.fml.common.EventBusSubscriber(modid="practicemod",value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class YachtClient {
    static int silence;
    @net.neoforged.bus.api.SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var level=Minecraft.getInstance().level;
        if(level!=lastLevel || ++silence>60) {
            if(lastLevel instanceof net.minecraft.client.multiplayer.ClientLevel old){var container=WaterOcclusionContainer.getContainer(old);if(container!=null)masks.forEach(container::removeRegion);}
            masks.clear();maskKey="";lastLevel=level;
        }
    }
    static String dimension="",maskKey="";static Object lastLevel;
    static final List<WaterOcclusionRegion> masks=new ArrayList<>();
    public static void receive(String raw) {
        silence=0;var mc=Minecraft.getInstance();if(mc.level==null)return;var data=JsonParser.parseString(raw).getAsJsonObject();
        if(!mc.level.dimension().location().toString().equals(data.get("dimension").getAsString()))return;
        var container=WaterOcclusionContainer.getContainer(mc.level);String key=data.getAsJsonArray("masks").toString();
        if(lastLevel!=mc.level){masks.clear();maskKey="";lastLevel=mc.level;}
        if(container!=null && !key.equals(maskKey)) {
            masks.forEach(container::removeRegion);masks.clear();maskKey=key;
            for(var element:data.getAsJsonArray("masks")) {
                var a=element.getAsJsonArray();int x=a.get(0).getAsInt(),y=a.get(1).getAsInt(),z=a.get(2).getAsInt(),X=a.get(3).getAsInt(),Y=a.get(4).getAsInt(),Z=a.get(5).getAsInt();
                var volume=new BoundedBitVolume3i(x,y,z,X,Y,Z);
                for(var p:net.minecraft.core.BlockPos.betweenClosed(x,y,z,X,Y,Z))volume.setOccupied(p.getX(),p.getY(),p.getZ(),true);
                masks.add(container.addRegion(volume));
            }
        }
        if(data.get("open").getAsBoolean())mc.setScreen(new Chart(data));
        else if(mc.screen instanceof Chart chart && chart.id.equals(UUID.fromString(data.get("ship").getAsString())))chart.update(data);
    }
    public static final class Chart extends Screen {
        final UUID id;JsonObject state;JsonArray tiles;int mapX,mapZ,left,top,size;String feedback="Click blue water to request a destination";
        Chart(JsonObject data){super(Component.literal("White Horizon · Captain's chart"));id=UUID.fromString(data.get("ship").getAsString());update(data);}
        void update(JsonObject data){state=data;if(data.has("tiles")){tiles=data.getAsJsonArray("tiles");mapX=data.get("mapX").getAsInt();mapZ=data.get("mapZ").getAsInt();}}
        @Override protected void init(){size=Math.min(width-50,height-110);left=(width-size)/2;top=40;
            addRenderableWidget(Button.builder(Component.literal("Stop voyage"),b->send(2,0,0)).bounds(width/2-105,height-28,100,20).build());
            addRenderableWidget(Button.builder(Component.literal("Close"),b->onClose()).bounds(width/2+5,height-28,100,20).build());}
        void send(int action,int x,int z){PacketDistributor.sendToServer(new YachtNetwork.Request(id,action,x,z));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g,mx,my,partial);g.fill(0,0,width,height,0xe018202a);g.drawCenteredString(font,title,width/2,12,0xffffff);
            if(tiles!=null)for(int z=0;z<33;z++)for(int x=0;x<33;x++) {
                int t=tiles.get(z*33+x).getAsInt();g.fill(left+x*size/33,top+z*size/33,left+(x+1)*size/33+1,top+(z+1)*size/33+1,t==0?0xff48515c:t==1?0xff287fa4:0xff657d4c);
            }
            var route=state.getAsJsonArray("route");for(var element:route){var p=element.getAsJsonArray();mark(g,p.get(0).getAsDouble(),p.get(1).getAsDouble(),0xffffd66b,2);}
            if(state.has("destination")){var d=state.getAsJsonArray("destination");mark(g,d.get(0).getAsDouble(),d.get(1).getAsDouble(),0xffff8855,4);}
            mark(g,state.get("x").getAsDouble(),state.get("z").getAsDouble(),0xffffffff,4);
            g.drawCenteredString(font,state.get("status").getAsString(),width/2,top+size+7,0xffffff);
            g.drawCenteredString(font,state.get("control").getAsBoolean()?feedback:"Guest chart · emergency stop available",width/2,top+size+20,0xffd6c89c);
            for(var widget:renderables)widget.render(g,mx,my,partial);
        }
        void mark(GuiGraphics g,double x,double z,int color,int radius){int px=left+(int)((x-mapX+132)/264*size),pz=top+(int)((z-mapZ+132)/264*size);if(px>=left&&px<=left+size&&pz>=top&&pz<=top+size)g.fill(px-radius,pz-radius,px+radius,pz+radius,color);}
        @Override public boolean mouseClicked(double x,double y,int button){if(button==0&&x>=left&&x<left+size&&y>=top&&y<top+size&&state.get("control").getAsBoolean()){
            int wx=mapX+(int)((x-left)/size*264)-132,wz=mapZ+(int)((y-top)/size*264)-132;send(1,wx,wz);feedback="Requested "+wx+", "+wz+" · server validates the entire route";return true;}return super.mouseClicked(x,y,button);}
    }
}
