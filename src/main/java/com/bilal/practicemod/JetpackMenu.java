package com.bilal.practicemod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// The jetpack fueling menu: one blaze-powder slot plus the standard player inventory, operating
// on the jetpack in the player's CHEST slot (opened with the G keybind while worn - see
// JetpackClientHandler / JetpackNetworking). While open, powder in the slot drips into the tank
// one piece at a time (broadcastChanges below), so refueling is a visible fill, and works even
// mid-hover. Closing the menu returns any unconsumed powder.
public class JetpackMenu extends AbstractContainerMenu {

    public static final int POWDER_SLOT = 0;
    private static final int INV_START = 1;
    private static final int HOTBAR_START = 28;
    private static final int SLOT_END = 37;

    // One powder consumed at most every this many ticks while the menu is open (4/s: a full
    // default tank from empty is 4 powder, ~1s - gradual enough to watch the gauge climb).
    private static final int DRIP_INTERVAL_TICKS = 5;

    private final SimpleContainer fuelInput = new SimpleContainer(1);
    private final Inventory playerInventory;
    private int dripCooldown = DRIP_INTERVAL_TICKS;

    // Client-side mirror of the tank fraction, in permille. The chest armor slot is not a slot of
    // this menu, so its data component does not sync through the container; a DataSlot carries the
    // display value instead (permille also stays comfortably inside the 16-bit container-data
    // sync limit regardless of the configured capacity).
    private int fuelPermilleDisplay;

    public JetpackMenu(int containerId, Inventory playerInventory) {
        super(PracticeMod.JETPACK_MENU.get(), containerId);
        this.playerInventory = playerInventory;

        this.addSlot(new Slot(this.fuelInput, 0, 80, 22) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.BLAZE_POWDER);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() { // polled server-side during broadcastChanges
                return Math.round(JetpackFuelHandler.getFuelFraction(getJetpack()) * 1000.0F);
            }

            @Override
            public void set(int value) { // written client-side from the sync packet
                JetpackMenu.this.fuelPermilleDisplay = value;
            }
        });
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, playerInventory, p) -> new JetpackMenu(containerId, playerInventory),
                Component.translatable("menu.practicemod.jetpack")));
    }

    private ItemStack getJetpack() {
        return this.playerInventory.player.getItemBySlot(EquipmentSlot.CHEST);
    }

    public float getFuelFractionDisplay() {
        return this.fuelPermilleDisplay / 1000.0F;
    }

    // The drip refuel. broadcastChanges runs once per tick on the SERVER for the open menu (the
    // client-side mirror never calls it), which makes it the natural home for container-tick
    // logic without any extra event subscription.
    @Override
    public void broadcastChanges() {
        if (--this.dripCooldown <= 0) {
            this.dripCooldown = DRIP_INTERVAL_TICKS;
            ItemStack jetpack = getJetpack();
            ItemStack powder = this.fuelInput.getItem(0);
            if (jetpack.getItem() instanceof JetpackItem && !powder.isEmpty()
                    && JetpackFuelHandler.getFuel(jetpack) < JetpackFuelHandler.getFuelCapacity()) {
                powder.shrink(1);
                this.fuelInput.setChanged();
                // The final powder may overfill; setFuel clamps to capacity.
                JetpackFuelHandler.setFuel(jetpack, JetpackFuelHandler.getFuel(jetpack)
                        + (float) (double) JetpackServerConfig.FUEL_PER_BLAZE_POWDER.get());
                Player player = this.playerInventory.player;
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BUCKET_FILL_LAVA, SoundSource.PLAYERS, 0.4F, 1.2F);
            }
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        // Unequipping the jetpack closes the menu (vanilla kicks the player out when this fails).
        return getJetpack().getItem() instanceof JetpackItem;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack moved = slot.getItem();
            result = moved.copy();
            if (index == POWDER_SLOT) {
                if (!this.moveItemStackTo(moved, INV_START, SLOT_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (moved.is(Items.BLAZE_POWDER)) {
                if (!this.moveItemStackTo(moved, POWDER_SLOT, POWDER_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index < HOTBAR_START) {
                if (!this.moveItemStackTo(moved, HOTBAR_START, SLOT_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(moved, INV_START, HOTBAR_START, false)) {
                return ItemStack.EMPTY;
            }
            if (moved.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // Unconsumed powder goes back to the player (or drops), the crafting-table convention.
        this.clearContainer(player, this.fuelInput);
    }
}
