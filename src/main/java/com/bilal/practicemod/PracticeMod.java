package com.bilal.practicemod;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;

import com.bilal.practicemod.datagen.ModItemModelProvider;
import com.bilal.practicemod.network.JetpackNetworking;
import com.mojang.logging.LogUtils;

import com.mojang.serialization.Codec;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(PracticeMod.MODID)
public class PracticeMod {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "practicemod";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "practicemod" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "practicemod" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "practicemod" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    // Create a Deferred Register to hold ArmorMaterials which will all be registered under the "practicemod" namespace
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, MODID);
    // Create a Deferred Register to hold AttachmentTypes which will all be registered under the "practicemod" namespace
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MODID);
    // Create a Deferred Register to hold DataComponentTypes which will all be registered under the "practicemod" namespace
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);
    // Create a Deferred Register to hold MenuTypes which will all be registered under the "practicemod" namespace
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);

    // Tracks whether a player is currently holding jump while wearing the jetpack. Set directly from
    // local input on the client, and from the synced JetpackInputPayload on the server - see
    // JetpackClientHandler and JetpackNetworking. Not persisted; it's pure per-tick input state.
    public static final Supplier<AttachmentType<Boolean>> JETPACK_THRUSTING = ATTACHMENT_TYPES.register("jetpack_thrusting",
            () -> AttachmentType.builder(() -> Boolean.FALSE).build());

    // The player's WASD movement impulses this tick, in [-1, 1] (forward = W positive, strafe = A
    // positive, matching vanilla's leftImpulse). Set from local input on the client and from the
    // synced JetpackInputPayload on the server, same dual-sided pattern as JETPACK_THRUSTING. The
    // flight sim rotates these by the player's yaw so thrust steers relative to facing. Not persisted.
    public static final Supplier<AttachmentType<Float>> JETPACK_FORWARD = ATTACHMENT_TYPES.register("jetpack_forward",
            () -> AttachmentType.builder(() -> 0.0F).build());

    public static final Supplier<AttachmentType<Float>> JETPACK_STRAFE = ATTACHMENT_TYPES.register("jetpack_strafe",
            () -> AttachmentType.builder(() -> 0.0F).build());

    // Whether the player is holding the boost (sprint) key. Only meaningful under the OVERDRIVE boost
    // profile, where holding it while thrusting engages the surge. Set from local input on the client
    // and from the synced JetpackInputPayload on the server, same dual-sided pattern as
    // JETPACK_THRUSTING. Not persisted; pure per-tick input state.
    public static final Supplier<AttachmentType<Boolean>> JETPACK_OVERDRIVE = ATTACHMENT_TYPES.register("jetpack_overdrive",
            () -> AttachmentType.builder(() -> Boolean.FALSE).build());

    // Whether the surge is actually engaged this tick: OVERDRIVE profile, thrusting, boost key held.
    // Unlike JETPACK_OVERDRIVE (raw key state) this is the gated result, so it already accounts for
    // fuel, overheat and the boost profile. Computed identically on both sides by JetpackFlightHandler
    // (client predicts the local player); remote viewers get it via JetpackAnimSyncPayload. Drives the
    // flat Superman body pose and the HUD's surge color.
    public static final Supplier<AttachmentType<Boolean>> JETPACK_SURGING = ATTACHMENT_TYPES.register("jetpack_surging",
            () -> AttachmentType.builder(() -> Boolean.FALSE).build());

    // Whether the player is coasting: airborne with the throttle released but still carrying real
    // horizontal speed, so the jetpack keeps them flying (gentle momentum bleed, capped sink) instead
    // of dropping them. Same dual-sided computation as JETPACK_SURGING; keeps the flight pose engaged
    // so a thrust cut never snaps the body back to the vanilla falling pose.
    public static final Supplier<AttachmentType<Boolean>> JETPACK_COASTING = ATTACHMENT_TYPES.register("jetpack_coasting",
            () -> AttachmentType.builder(() -> Boolean.FALSE).build());

    // Whether the player is in the near-ground hover-boot cushion (worn, airborne, descending within
    // hoverBootRange, not thrusting). Computed identically on both sides by JetpackFlightHandler (client
    // predicts the local player); remote viewers get it via JetpackAnimSyncPayload. Drives the airbrake
    // pose and its exhaust. (Named "gliding" for historical reasons - the old glide feature it replaced.)
    public static final Supplier<AttachmentType<Boolean>> JETPACK_GLIDING = ATTACHMENT_TYPES.register("jetpack_gliding",
            () -> AttachmentType.builder(() -> Boolean.FALSE).build());

    // Jetpack heat, 0 = cold up to heatMax (default 100) = overheat stall. Simulated identically on
    // client and server each tick from the shared thrusting state (same pattern as JETPACK_THRUSTING),
    // so the local player needs no syncing; remote viewers get it via JetpackAnimSyncPayload.
    // Not persisted: heat is transient and resets across relogs, which is fine for a lockout timer.
    public static final Supplier<AttachmentType<Float>> JETPACK_HEAT = ATTACHMENT_TYPES.register("jetpack_heat",
            () -> AttachmentType.builder(() -> 0.0F).build());

    // Latched when heat reaches heatMax; cleared once heat falls back below the configured recovery
    // threshold. While latched the jetpack refuses to thrust (a lockout, not damage).
    public static final Supplier<AttachmentType<Boolean>> JETPACK_OVERHEATED = ATTACHMENT_TYPES.register("jetpack_overheated",
            () -> AttachmentType.builder(() -> Boolean.FALSE).build());

    // Fuel in the jetpack's tank, stored on the ItemStack itself so fuel travels with the item
    // (dropped, chested, etc.). A float because burn and regen are fractional per tick. Written by
    // the server; the client reads the copy that vanilla inventory syncing delivers. See
    // JetpackFuelHandler for refueling and JetpackFlightHandler for burn/regen.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> JETPACK_FUEL = DATA_COMPONENTS.registerComponentType("fuel",
            builder -> builder.persistent(Codec.floatRange(0.0F, Float.MAX_VALUE)).networkSynchronized(ByteBufCodecs.FLOAT));

    // The fueling menu opened with the G keybind while the jetpack is worn (JetpackClientHandler
    // sends OpenJetpackMenuPayload; the server validates and opens). Operates on the chest slot,
    // so the client factory needs no extra data.
    public static final DeferredHolder<MenuType<?>, MenuType<JetpackMenu>> JETPACK_MENU = MENUS.register("jetpack_menu",
            () -> IMenuTypeExtension.create((containerId, playerInventory, buf) -> new JetpackMenu(containerId, playerInventory)));

    // Creates a new Block with the id "practicemod:example_block", combining the namespace and path
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
    // Creates a new BlockItem with the id "practicemod:example_block", combining the namespace and path
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);

    // Creates a new food item with the id "practicemod:example_id", nutrition 1 and saturation 2
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder()
            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));

    // Creates a placeholder armor material for the jetpack. It has no defense/toughness of its own since it's a
    // utility item rather than protective armor, but it still needs a material to be equippable as a chestplate.
    public static final Holder<ArmorMaterial> JETPACK_ARMOR_MATERIAL = ARMOR_MATERIALS.register("jetpack",
            () -> new ArmorMaterial(
                    Map.of(ArmorItem.Type.CHESTPLATE, 0),
                    9,
                    SoundEvents.ARMOR_EQUIP_GENERIC,
                    () -> Ingredient.of(Items.IRON_INGOT),
                    List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(MODID, "jetpack"))),
                    0.0F,
                    0.0F));

    // Creates a new jetpack chestplate item with the id "practicemod:jetpack", stack size 1 (implied by durability)
    public static final DeferredItem<JetpackItem> JETPACK = ITEMS.register("jetpack",
            () -> new JetpackItem(JETPACK_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(15))));

    // Creates a creative tab with the id "practicemod:example_tab" for the example item, that is placed after the combat tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.practicemod")) //The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> EXAMPLE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(EXAMPLE_ITEM.get()); // Add the example item to the tab. For your own tabs, this method is preferred over the event
            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public PracticeMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so armor materials get registered
        ARMOR_MATERIALS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so attachment types get registered
        ATTACHMENT_TYPES.register(modEventBus);
        // Register the Deferred Register to the mod event bus so data components get registered
        DATA_COMPONENTS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so menu types get registered
        MENUS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (PracticeMod) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our datagen providers
        modEventBus.addListener(this::gatherData);

        // Register the jetpack's client -> server input packet
        modEventBus.addListener(JetpackNetworking::register);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        // Jetpack tuning is server-authoritative and synced to clients on join
        modContainer.registerConfig(ModConfig.Type.SERVER, JetpackServerConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(EXAMPLE_BLOCK_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(JETPACK);
        }
    }

    // Register datagen providers, e.g. the item model for the jetpack
    private void gatherData(GatherDataEvent event) {
        event.getGenerator().addProvider(event.includeClient(),
                new ModItemModelProvider(event.getGenerator().getPackOutput(), event.getExistingFileHelper()));
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}
