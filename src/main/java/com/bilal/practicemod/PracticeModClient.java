package com.bilal.practicemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.fml.config.ModConfig;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = PracticeMod.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = PracticeMod.MODID, value = Dist.CLIENT)
public class PracticeModClient {
    public PracticeModClient(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, JetpackClientConfig.SPEC);

        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        PracticeMod.LOGGER.info("HELLO FROM CLIENT SETUP");
        PracticeMod.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent
    static void registerShaders(net.neoforged.neoforge.client.event.RegisterShadersEvent event) throws java.io.IOException {
        SubmarineHeadlight.register(event);
    }

    // Adds the jetpack boost-heat HUD bar above the vanilla status rows. Registered above AIR_LEVEL
    // (the last status row) so the vanilla Gui height trackers are final when the overlay renders.
    @SubscribeEvent
    static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.AIR_LEVEL, JetpackHudOverlay.ID, new JetpackHudOverlay());
        event.registerAbove(VanillaGuiLayers.AIR_LEVEL, SkateboardHudOverlay.ID, new SkateboardHudOverlay());
    }

    // Tells the client which Screen renders the jetpack fueling menu.
    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(PracticeMod.JETPACK_MENU.get(), JetpackScreen::new);
    }

    // Makes the jetpack keys (open fueling menu, toggle hover mode) rebindable in the vanilla
    // Controls screen.
    @SubscribeEvent
    static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SubmarineClientHandler.CABIN_KEY);
        event.register(SubmarineClientHandler.EXIT_KEY);
        event.register(SubmarineClientHandler.LIGHT_KEY);
        event.register(JetpackClientHandler.OPEN_MENU_KEY);
        event.register(JetpackClientHandler.HOVER_TOGGLE_KEY);
        for (var key : SkateboardClientHandler.TRICK_KEYS) event.register(key);
    }

    // Registers the imported SCAVPACK's worn-model geometry under its layer location.
    @SubscribeEvent
    static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(JetpackModel.LAYER, JetpackModel::createLayer);
        event.registerLayerDefinition(RocketBootsModel.LAYER, RocketBootsModel::createLayer);
    }

    @SubscribeEvent
    static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PracticeMod.SUBMARINE_ENTITY.get(), SubmarineRenderer::new);
        event.registerEntityRenderer(PracticeMod.SKATEBOARD_ENTITY.get(), SkateboardRenderer::new);
        event.registerEntityRenderer(PracticeMod.JETSKI_ENTITY.get(), JetskiRenderer::new);
        event.registerEntityRenderer(PracticeMod.ATV_ENTITY.get(), AtvRenderer::new);
        event.registerEntityRenderer(PracticeMod.MECHANICAL_DOLPHIN_ENTITY.get(), MechanicalDolphinRenderer::new);
    }

    // The main flight visuals are a player render layer rather than world particles. That gives the
    // layer the already-pitched player PoseStack, and its private JetpackModel copy supplies the
    // nested animated tank/thruster/emitter transforms needed to keep every plume on its nozzle.
    @SubscribeEvent
    static void addPlayerEffectLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new JetpackEffectLayer(renderer, event.getEntityModels()));
                renderer.addLayer(new RocketBootsEffectLayer(renderer));
                renderer.addLayer(new SkateboardLegLayer(renderer));
                renderer.addLayer(new UmbrellaGliderLayer(renderer, event.getEntityModels()));
            }
        }
    }

    // Swaps the vanilla chestplate model for the custom jetpack model when the armor layer renders
    // the worn jetpack. NeoForge copies pose and part visibility from the vanilla model onto the
    // replacement (root parts only), so the tanks follow torso animation without any setup here;
    // setupModelAnimations then runs after that copy, right before rendering, and poses the
    // animated child parts (tank pods, thrusters, fan, core, and latches) from the wearer's state.
    @SubscribeEvent
    static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private MechanicalDolphinItemRenderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    Minecraft minecraft = Minecraft.getInstance();
                    renderer = new MechanicalDolphinItemRenderer(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
                }
                return renderer;
            }
        }, PracticeMod.MECHANICAL_DOLPHIN.get());
        event.registerItem(new IClientItemExtensions() {
            private JetskiItemRenderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    Minecraft minecraft = Minecraft.getInstance();
                    renderer = new JetskiItemRenderer(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
                }
                return renderer;
            }
        }, PracticeMod.JETSKI.get());
        event.registerItem(new IClientItemExtensions() {
            private AtvItemRenderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    Minecraft minecraft = Minecraft.getInstance();
                    renderer = new AtvItemRenderer(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
                }
                return renderer;
            }
        }, PracticeMod.ATV.get());
        event.registerItem(new IClientItemExtensions() {
            private SubmarineItemRenderer renderer;
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    Minecraft minecraft = Minecraft.getInstance();
                    renderer = new SubmarineItemRenderer(
                            minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
                }
                return renderer;
            }
        }, PracticeMod.SUBMARINE.get());
        event.registerItem(new IClientItemExtensions() {
            private SkateboardItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    Minecraft minecraft = Minecraft.getInstance();
                    renderer = new SkateboardItemRenderer(
                            minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
                }
                return renderer;
            }
        }, PracticeMod.SKATEBOARD.get());

        event.registerItem(new IClientItemExtensions() {
            // Baked once on first use; the geometry is code-defined, so resource reloads can't
            // change it and the cached copy stays valid for the lifetime of the client.
            private JetpackModel model;

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity livingEntity, ItemStack itemStack,
                    EquipmentSlot equipmentSlot, HumanoidModel<?> original) {
                if (model == null) {
                    model = new JetpackModel(Minecraft.getInstance().getEntityModels().bakeLayer(JetpackModel.LAYER));
                }
                return model;
            }

            // The single model instance is shared by every wearer on screen, so it must be posed
            // fresh for each: from that entity's animation state if one exists, otherwise back to
            // the rest pose (e.g. armor stands, which never get a state).
            @Override
            public void setupModelAnimations(LivingEntity livingEntity, ItemStack itemStack,
                    EquipmentSlot equipmentSlot, Model renderedModel, float limbSwing, float limbSwingAmount,
                    float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                if (renderedModel instanceof JetpackModel jetpackModel) {
                    JetpackAnimationState state = JetpackAnimationController.get(livingEntity.getId());
                    if (state != null) {
                        jetpackModel.applyAnimation(state, partialTick);
                    } else {
                        jetpackModel.resetAnimationPose();
                    }
                }
            }
        }, PracticeMod.JETPACK.get());

        event.registerItem(new IClientItemExtensions() {
            private RocketBootsModel model;
            private RocketBootsItemRenderer renderer;

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack,
                    EquipmentSlot slot, HumanoidModel<?> original) {
                if (this.model == null) {
                    this.model = new RocketBootsModel(Minecraft.getInstance().getEntityModels()
                            .bakeLayer(RocketBootsModel.LAYER));
                }
                return this.model;
            }

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    Minecraft minecraft = Minecraft.getInstance();
                    this.renderer = new RocketBootsItemRenderer(
                            minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
                }
                return this.renderer;
            }
        }, PracticeMod.ROCKET_BOOTS.get());

        event.registerItem(new IClientItemExtensions() {
            private UmbrellaGliderItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    Minecraft minecraft = Minecraft.getInstance();
                    this.renderer = new UmbrellaGliderItemRenderer(
                            minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
                }
                return this.renderer;
            }
        }, PracticeMod.UMBRELLA_GLIDER.get());
    }
}
