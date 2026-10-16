package com.bilal.practicemod.yacht.runtime;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.LightTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Matrix4f;
import java.nio.file.*;

/** Opt-in baked block-model review. This is not an in-world movement test. */
@EventBusSubscriber(modid="practicemod",value=Dist.CLIENT)
public final class YachtModelReview {
    static boolean finished;
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        var output=System.getProperty("practicemod.yachtModelReview");var mc=Minecraft.getInstance();
        if(output==null||finished||!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return;
        finished=true;var directory=Path.of(output);
        try {
            Files.createDirectories(directory);
            draw(mc,directory.resolve("exterior.png"),42,27,-50,0,0,0,20,false);
            draw(mc,directory.resolve("bow.png"),38,24,50,0,0,0,20,false);
            draw(mc,directory.resolve("lower-cutaway.png"),22,26,-28,0,-4,0,-1,false);
            draw(mc,directory.resolve("main-cutaway.png"),28,31,-38,0,0,0,3,false);
            draw(mc,directory.resolve("salon.png"),0,1.62f,-3,0,1.62f,10,20,true);
            draw(mc,directory.resolve("lower-corridor.png"),0,-2.38f,-16,0,-2.38f,13,20,true);
            draw(mc,directory.resolve("bridge.png"),0,5.62f,14,0,5.62f,23,20,true);
            Files.writeString(directory.resolve("result.txt"),"Rendered canonical native block models with Minecraft's baked-model renderer. Full-bright studio images; not world lighting or multiplayer validation.\n");
        } catch(Exception failure) {org.slf4j.LoggerFactory.getLogger(YachtModelReview.class).error("Yacht model review failed",failure);}
        finally {mc.stop();}
    }
    static void draw(Minecraft mc,Path output,float x,float y,float z,float tx,float ty,float tz,int ceiling,boolean interior)throws Exception {
        var target=new TextureTarget(1400,1000,true,Minecraft.ON_OSX);var projection=new Matrix4f(RenderSystem.getProjectionMatrix());var sorting=RenderSystem.getVertexSorting();var view=RenderSystem.getModelViewStack();view.pushMatrix();
        try {
            target.setClearColor(.09f,.16f,.22f,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            RenderSystem.setProjectionMatrix(interior?new Matrix4f().setPerspective((float)Math.toRadians(80),1.4f,.05f,150):new Matrix4f().setOrtho(-41,41,-29.3f,29.3f,.1f,200),interior?VertexSorting.DISTANCE_TO_ORIGIN:VertexSorting.ORTHOGRAPHIC_Z);
            view.identity().lookAt(x,y,z,tx,ty,tz,0,1,0);RenderSystem.applyModelViewMatrix();RenderSystem.enableDepthTest();RenderSystem.setShaderColor(1,1,1,1);
            com.mojang.blaze3d.platform.Lighting.setupFor3DItems();mc.gameRenderer.lightTexture().turnOnLightLayer();
            var buffers=mc.renderBuffers().bufferSource();
            for(var raw:YachtAssets.DATA.getAsJsonArray("cells")) {
                var a=raw.getAsJsonArray();if(a.get(1).getAsInt()>=ceiling)continue;
                var pose=new PoseStack();pose.translate(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());
                mc.getBlockRenderer().renderSingleBlock(YachtAssets.HULL.defaultBlockState().setValue(YachtAssets.CELL,a.get(3).getAsInt()),pose,buffers,LightTexture.FULL_BRIGHT,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            }
            buffers.endBatch();try(var pixels=Screenshot.takeScreenshot(target)){pixels.writeToFile(output);}
        } finally {view.popMatrix();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,sorting);target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);}
    }
}
