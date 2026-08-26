package com.bilal.practicemod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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

// The jetpack fueling menu: one persistent Blaze Powder storage slot plus the standard player
// inventory, operating on the jetpack in the player's CHEST slot. The slot accepts a normal stack
// and writes its count onto the jetpack ItemStack, so its contents survive closing the menu,
// unequipping the pack, dropping it, and storing it in a chest. Flight consumes the stored powder
// only when the hidden current fuel reserve needs another charge.
public class JetpackMenu extends AbstractContainerMenu {

    public static final int POWDER_SLOT = 0;
    private static final int INV_START = 1;
    private static final int HOTBAR_START = 28;
    private static final int SLOT_END = 37;

    private final SimpleContainer fuelInput;
    private final Inventory playerInventory;
    private boolean syncingFuelInput;

    // Client-side mirror of the tank fraction, in permille. The chest armor slot is not a slot of
    // this menu, so its data component does not sync through the container; a DataSlot carries the
    // display value instead (permille also stays comfortably inside the 16-bit container-data
    // sync limit regardless of the configured capacity).
    private int fuelPermilleDisplay;

    public JetpackMenu(int containerId, Inventory playerInventory) {
        super(PracticeMod.JETPACK_MENU.get(), containerId);
        this.playerInventory = playerInventory;
        this.fuelInput = new SimpleContainer(1) {
            @Override
            public void setChanged() {
                super.setChanged();
                ItemStack jetpack = JetpackMenu.this.getJetpack();
                if (!JetpackMenu.this.syncingFuelInput && jetpack.getItem() instanceof JetpackItem) {
                    JetpackFuelHandler.setStoredBlazePowder(jetpack, this.getItem(0).getCount());
                }
            }
        };

        syncFuelInputFromJetpack();

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

    // Hover can keep burning fuel while this screen is open on a multiplayer server. If that burn
    // consumes a stored powder, reflect the component's new count back into the visible slot before
    // normal menu broadcasting. The guard prevents that mirror write from being mistaken for a
    // player insertion and restoring the old count.
    @Override
    public void broadcastChanges() {
        syncFuelInputFromJetpack();
        super.broadcastChanges();
    }

    private void syncFuelInputFromJetpack() {
        int storedPowder = JetpackFuelHandler.getStoredBlazePowder(getJetpack());
        if (this.fuelInput.getItem(0).getCount() == storedPowder) {
            return;
        }

        this.syncingFuelInput = true;
        try {
            this.fuelInput.setItem(0, storedPowder > 0
                    ? new ItemStack(Items.BLAZE_POWDER, storedPowder)
                    : ItemStack.EMPTY);
        } finally {
            this.syncingFuelInput = false;
        }
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

}
