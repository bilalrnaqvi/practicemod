package com.bilal.practicemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
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

    // Adds the jetpack heat/fuel HUD bars above the vanilla status rows. Registered above AIR_LEVEL
    // (the last status row) so the vanilla Gui height trackers are final when the overlay renders.
    @SubscribeEvent
    static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.AIR_LEVEL, JetpackHudOverlay.ID, new JetpackHudOverlay());
    }

    // Tells the client which Screen renders the jetpack fueling menu.
    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(PracticeMod.JETPACK_MENU.get(), JetpackScreen::new);
    }

    // Makes the open-fueling-menu key rebindable in the vanilla Controls screen.
    @SubscribeEvent
    static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(JetpackClientHandler.OPEN_MENU_KEY);
    }

    // Registers the jetpack's worn-model geometry (tanks + nozzles) under its layer location.
    @SubscribeEvent
    static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(JetpackModel.LAYER, JetpackModel::createLayer);
    }

    // Swaps the vanilla chestplate model for the custom jetpack model when the armor layer renders
    // the worn jetpack. NeoForge copies pose and part visibility from the vanilla model onto the
    // replacement (root parts only), so the tanks follow torso animation without any setup here;
    // setupModelAnimations then runs after that copy, right before rendering, and poses the
    // animated child parts (nozzles, tanks, vents) from the wearer's animation state.
    @SubscribeEvent
    static void registerClientExtensions(RegisterClientExtensionsEvent event) {
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
    }
}
