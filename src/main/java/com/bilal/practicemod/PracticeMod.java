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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
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

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredHolder<EntityType<?>, EntityType<SubmarineEntity>> SUBMARINE_ENTITY =
            ENTITIES.register("submarine", () -> EntityType.Builder.<SubmarineEntity>of(SubmarineEntity::new, MobCategory.MISC)
                    .sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10)
                    .build(MODID + ":submarine"));
    public static final DeferredItem<SubmarineItem> SUBMARINE = ITEMS.register("submarine",
            () -> new SubmarineItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<EntityType<?>, EntityType<SkateboardEntity>> SKATEBOARD_ENTITY =
            ENTITIES.register("skateboard", () -> EntityType.Builder.<SkateboardEntity>of(SkateboardEntity::new, MobCategory.MISC)
                    .sized(0.8F, 0.25F).eyeHeight(0.25F).clientTrackingRange(10).updateInterval(1)
                    .build(MODID + ":skateboard"));
    public static final DeferredItem<SkateboardItem> SKATEBOARD = ITEMS.register("skateboard",
            () -> new SkateboardItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<EntityType<?>, EntityType<JetskiEntity>> JETSKI_ENTITY =
            ENTITIES.register("jetski", () -> EntityType.Builder.<JetskiEntity>of(JetskiEntity::new, MobCategory.MISC)
                    .sized(1.15F * JetskiEntity.HULL_SCALE, 0.65F * JetskiEntity.HULL_SCALE)
                    .eyeHeight(0.65F * JetskiEntity.HULL_SCALE).clientTrackingRange(12).updateInterval(1)
                    .build(MODID + ":jetski"));
    public static final DeferredItem<JetskiItem> JETSKI = ITEMS.register("jetski",
            () -> new JetskiItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<EntityType<?>, EntityType<AtvEntity>> ATV_ENTITY =
            ENTITIES.register("atv", () -> EntityType.Builder.<AtvEntity>of(AtvEntity::new, MobCategory.MISC)
                    .sized(2.2F, 2.4F).eyeHeight(1.9F).clientTrackingRange(12).updateInterval(1)
                    .build(MODID + ":atv"));
    public static final DeferredItem<AtvItem> ATV = ITEMS.register("atv",
            () -> new AtvItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<EntityType<?>, EntityType<MechanicalDolphinEntity>> MECHANICAL_DOLPHIN_ENTITY =
            ENTITIES.register("mechanical_dolphin", () -> EntityType.Builder.<MechanicalDolphinEntity>of(MechanicalDolphinEntity::new, MobCategory.MISC)
                    .sized(1.1F, .8F).eyeHeight(.7F).clientTrackingRange(12).updateInterval(1)
                    .build(MODID + ":mechanical_dolphin"));
    public static final DeferredItem<MechanicalDolphinItem> MECHANICAL_DOLPHIN = ITEMS.register("mechanical_dolphin",
            () -> new MechanicalDolphinItem(new Item.Properties().stacksTo(1)));

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

    // Whether the player is holding sneak. Vanilla does sync the entity's shift flag, but it gates it
    // (LocalPlayer.aiStep suppresses crouching while swimming, riding, or where the pose won't fit),
    // and hover uses sneak as fine altitude TRIM - it has to answer to the key itself, not to whether
    // the player is allowed to look crouched. Same reason the boost key is read raw. Set from local
    // input on the client and from the synced JetpackInputPayload on the server. Not persisted.
    public static final Supplier<AttachmentType<Boolean>> JETPACK_SNEAKING = ATTACHMENT_TYPES.register("jetpack_sneaking",
            () -> AttachmentType.builder(() -> Boolean.FALSE).build());

    // Whether HOVER MODE is armed - the H-key toggle, not whether it is currently holding altitude.
    // Armed on the ground does nothing (hover is dormant until the player is airborne); see
    // JETPACK_HOVERING for the gated result. Toggled by the client and mirrored to the server through
    // JetpackInputPayload, same dual-sided pattern as JETPACK_THRUSTING. Not persisted, and cleared
    // outright when the jetpack comes off (like heat) so a fresh pack is never secretly armed.
    public static final Supplier<AttachmentType<Boolean>> JETPACK_HOVER_MODE = ATTACHMENT_TYPES.register("jetpack_hover_mode",
            () -> AttachmentType.builder(() -> Boolean.FALSE).build());

    // Whether hover is actually engaged this tick: armed, airborne, fuelled, not stalled. The gated
    // counterpart to JETPACK_HOVER_MODE, in the same relationship as JETPACK_SURGING is to
    // JETPACK_OVERDRIVE. Computed identically on both sides by JetpackFlightHandler (client predicts
    // the local player); remote viewers get it via JetpackAnimSyncPayload. Drives the idling pack
    // animation, the HUD indicator, and the client-side hitch roll.
    public static final Supplier<AttachmentType<Boolean>> JETPACK_HOVERING = ATTACHMENT_TYPES.register("jetpack_hovering",
            () -> AttachmentType.builder(() -> Boolean.FALSE).build());

    // The altitude the hover spring is pulling toward, in world Y. Transient working state of the PD
    // controller, held per side: initialized to the player's own Y when hover first engages, trimmed
    // by jump/sneak, and re-clamped to within hoverTargetLeash of the player's ACTUAL Y every tick so
    // pinning against a ceiling can never bank up error. Not persisted (and never synced - both sides
    // derive it from the same inputs).
    public static final Supplier<AttachmentType<Double>> JETPACK_HOVER_TARGET_Y = ATTACHMENT_TYPES.register("jetpack_hover_target_y",
            () -> AttachmentType.builder(() -> 0.0).build());

    // The player's Y at the previous hover tick, so the controller can difference it for the realized
    // vertical velocity its damping term needs. Deliberately NOT Entity#yo: that is set at the top of
    // the entity tick, and a ServerPlayer never runs travel() - its position arrives from client move
    // packets, which the server processes AFTER the level ticks. So yo minus getY() is ~0 on the
    // server every tick, which would leave the server's spring undamped and quietly non-identical to
    // the client's. Differencing our own per-tick sample measures the same real movement on both
    // sides. Not persisted.
    public static final Supplier<AttachmentType<Double>> JETPACK_HOVER_PREV_Y = ATTACHMENT_TYPES.register("jetpack_hover_prev_y",
            () -> AttachmentType.builder(() -> 0.0).build());

    // One-shot "the pack hitched this tick" flag, carrying only the cosmetic event - never the
    // impulse, which is applied client-side by JetpackHoverHitch and never simulated on the server.
    // Set by the owning client, relayed through JetpackInputPayload to the server and back out via
    // JetpackAnimSyncPayload so other players see the twitch too. Read and cleared each tick by
    // JetpackAnimationController (clients) and JetpackFlightHandler (server). Not persisted.
    public static final Supplier<AttachmentType<Boolean>> JETPACK_HITCH = ATTACHMENT_TYPES.register("jetpack_hitch",
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

    // Jetpack boost heat, 0 = cold up to heatMax (default 200). Entering the final-7% red zone latches
    // the thrust lockout. Simulated identically
    // on client and server each tick from the shared surge state, so the local player needs no
    // syncing; remote viewers get it via JetpackAnimSyncPayload. Not persisted: heat is transient
    // and resets across relogs, which is fine for a brief lockout timer.
    public static final Supplier<AttachmentType<Float>> JETPACK_HEAT = ATTACHMENT_TYPES.register("jetpack_heat",
            () -> AttachmentType.builder(() -> 0.0F).build());

    // Latched when heat enters the final-7% red zone; cleared once heat falls below the recovery
    // threshold. While latched, both sprint and non-sprint thrust are disabled.
    public static final Supplier<AttachmentType<Boolean>> JETPACK_OVERHEATED = ATTACHMENT_TYPES.register("jetpack_overheated",
            () -> AttachmentType.builder(() -> Boolean.FALSE).build());

    // Effective rocket-boots thrust. The owning client predicts it and the server computes the same
    // value; attachment sync supplies the server result to remote clients for the boot-plume layer.
    public static final Supplier<AttachmentType<Boolean>> ROCKET_BOOTS_THRUSTING = ATTACHMENT_TYPES.register(
            "rocket_boots_thrusting",
            () -> AttachmentType.builder(() -> Boolean.FALSE).sync(ByteBufCodecs.BOOL).build());

    // Transient heat is mirrored by the owning client and server from the same per-tick input. It is
    // intentionally tied to wearing the boots rather than persisted on the item: unequipping fully
    // resets the short-burst device, matching the jetpack's existing transient heat behavior.
    public static final Supplier<AttachmentType<Float>> ROCKET_BOOTS_HEAT = ATTACHMENT_TYPES.register(
            "rocket_boots_heat", () -> AttachmentType.builder(() -> 0.0F).build());

    // Latches when a continuous burst consumes the entire capacity and clears only at full cooldown.
    public static final Supplier<AttachmentType<Boolean>> ROCKET_BOOTS_OVERHEATED = ATTACHMENT_TYPES.register(
            "rocket_boots_overheated", () -> AttachmentType.builder(() -> Boolean.FALSE).build());

    // Authoritative umbrella state. Unlike the jetpack's animation payload, this attachment uses
    // NeoForge's entity-attachment sync: the server sends every open/close edge to the owning
    // player and all tracking clients, and also includes the current value when a player first
    // comes into view. It is deliberately transient so reconnecting or respawning can never leave
    // an umbrella open without a fresh, valid airborne deployment.
    public static final Supplier<AttachmentType<Boolean>> UMBRELLA_GLIDER_DEPLOYED = ATTACHMENT_TYPES.register(
            "umbrella_glider_deployed",
            () -> AttachmentType.builder(() -> Boolean.FALSE).sync(ByteBufCodecs.BOOL).build());

    public static final Supplier<AttachmentType<GrappleState>> GRAPPLE_STATE = ATTACHMENT_TYPES.register(
            "grapple_state", () -> AttachmentType.builder(() -> GrappleState.IDLE).sync(GrappleState.STREAM_CODEC).build());
    public static final Supplier<AttachmentType<Integer>> GRAPPLE_TICKS = ATTACHMENT_TYPES.register(
            "grapple_ticks", () -> AttachmentType.builder(() -> 0).build());
    public static final Supplier<AttachmentType<Float>> GRAPPLE_GLIDE_CAP = ATTACHMENT_TYPES.register(
            "grapple_glide_cap", () -> AttachmentType.builder(() -> 0.0F).build());
    public static final Supplier<AttachmentType<Integer>> GRAPPLE_COAST_TICKS = ATTACHMENT_TYPES.register(
            "grapple_coast_ticks", () -> AttachmentType.builder(() -> 0).build());

    // Raw movement inputs used by the shared client prediction / server simulation. These are
    // clamped as soon as the input payload reaches the server and are not persisted or broadcast;
    // only the player who owns the movement controller needs them.
    public static final Supplier<AttachmentType<Float>> UMBRELLA_GLIDER_FORWARD = ATTACHMENT_TYPES.register(
            "umbrella_glider_forward", () -> AttachmentType.builder(() -> 0.0F).build());

    public static final Supplier<AttachmentType<Float>> UMBRELLA_GLIDER_STRAFE = ATTACHMENT_TYPES.register(
            "umbrella_glider_strafe", () -> AttachmentType.builder(() -> 0.0F).build());

    // Fuel in the jetpack's tank, stored on the ItemStack itself so fuel travels with the item
    // (dropped, chested, etc.). A float because burn and regen are fractional per tick. Written by
    // the server; the client reads the copy that vanilla inventory syncing delivers. See
    // JetpackFuelHandler for refueling and JetpackFlightHandler for burn/regen.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> JETPACK_FUEL = DATA_COMPONENTS.registerComponentType("fuel",
            builder -> builder.persistent(Codec.floatRange(0.0F, Float.MAX_VALUE)).networkSynchronized(ByteBufCodecs.FLOAT));

    // Blaze Powder stored inside the jetpack through its fueling menu. Keeping the reserve on the
    // ItemStack makes the menu slot persistent across closing, unequipping, dropping and chests.
    // The count is capped to one ordinary item stack by JetpackFuelHandler.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> JETPACK_BLAZE_POWDER =
            DATA_COMPONENTS.registerComponentType("stored_blaze_powder",
                    builder -> builder.persistent(Codec.intRange(0, 64))
                            .networkSynchronized(ByteBufCodecs.VAR_INT));

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

    // Authored rocket boots model with iron-tier protection; RocketBootsFlightHandler owns flight.
    public static final DeferredItem<RocketBootsItem> ROCKET_BOOTS = ITEMS.register("rocket_boots",
            () -> new RocketBootsItem(ArmorMaterials.IRON, ArmorItem.Type.BOOTS,
                    new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(15))));

    // A held item rather than equipment. Having it in the selected hotbar slot or the offhand is
    // the server-validated permission to deploy; it never occupies or emulates an armor/Curios-style
    // slot, and it does nothing from a stowed inventory slot.
    public static final DeferredItem<UmbrellaGliderItem> UMBRELLA_GLIDER = ITEMS.register("umbrella_glider",
            () -> new UmbrellaGliderItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<GrapplingHookItem> GRAPPLING_HOOK = ITEMS.register("grappling_hook",
            () -> new GrapplingHookItem(new Item.Properties().stacksTo(1)));

    // Include every registered item so new gadgets automatically appear in the mod's tab.
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TRAVERSAL_GADGETS_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.practicemod"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> JETPACK.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                ITEMS.getEntries().forEach(item -> output.accept(item.get()));
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
        ENTITIES.register(modEventBus);
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
            event.accept(ROCKET_BOOTS);
            event.accept(UMBRELLA_GLIDER);
            event.accept(SUBMARINE);
            event.accept(SKATEBOARD);
            event.accept(JETSKI);
            event.accept(ATV);
            event.accept(MECHANICAL_DOLPHIN);
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
