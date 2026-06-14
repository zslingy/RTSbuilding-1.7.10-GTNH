package com.rtsbuilding.rtsbuilding.server.menu;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.world.World;

/**
 * RTS Craft Terminal Container — 3x3 crafting grid + result slot + player inventory.
 * Remote access: canInteractWith() always returns true.
 * Based on vanilla ContainerWorkbench but without physical block requirement.
 */
public class RtsCraftTerminalMenu extends Container {

    private final EntityPlayer player;
    private final World world;
    private final InventoryCrafting craftMatrix;
    private final InventoryCraftResult craftResult;

    public RtsCraftTerminalMenu(InventoryPlayer playerInventory) {
        this.player = playerInventory.player;
        this.world = player.worldObj;
        this.craftMatrix = new InventoryCrafting(this, 3, 3);
        this.craftResult = new InventoryCraftResult();

        // Result slot (slot 0) — SlotCrafting handles crafting logic
        this.addSlotToContainer(new SlotCrafting(player, craftMatrix, craftResult, 0, 124, 35));

        // Crafting grid (slots 1-9) — 3x3 grid
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlotToContainer(new Slot(craftMatrix, col + row * 3, 30 + col * 18, 17 + row * 18));
            }
        }

        // Player inventory (slots 10-36)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlotToContainer(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Hotbar (slots 37-45)
        for (int col = 0; col < 9; col++) {
            this.addSlotToContainer(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public void onCraftMatrixChanged(IInventory inventory) {
        craftResult.setInventorySlotContents(
            0,
            CraftingManager.getInstance()
                .findMatchingRecipe(craftMatrix, world));
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true; // Remote access — no physical block required
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        ItemStack result = null;
        Slot slot = (Slot) inventorySlots.get(index);

        if (slot != null && slot.getHasStack()) {
            ItemStack stackInSlot = slot.getStack();
            result = stackInSlot.copy();

            // Result slot (0) → Player inventory (10-45)
            if (index == 0) {
                if (!mergeItemStack(stackInSlot, 10, 46, true)) {
                    return null;
                }
                slot.onSlotChange(stackInSlot, result);
            }
            // Crafting grid (1-9) → Player inventory (10-45)
            else if (index >= 1 && index <= 9) {
                if (!mergeItemStack(stackInSlot, 10, 46, false)) {
                    return null;
                }
            }
            // Player inventory (10-45) → Crafting grid (1-9)
            else if (index >= 10 && index <= 45) {
                if (!mergeItemStack(stackInSlot, 1, 10, false)) {
                    return null;
                }
            }

            if (stackInSlot.stackSize == 0) {
                slot.putStack(null);
            } else {
                slot.onSlotChanged();
            }

            if (stackInSlot.stackSize == result.stackSize) {
                return null;
            }

            slot.onPickupFromSlot(player, stackInSlot);
        }

        return result;
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        // Drop crafting grid contents back to player
        for (int i = 0; i < 9; i++) {
            ItemStack stack = craftMatrix.getStackInSlotOnClosing(i);
            if (stack != null) {
                player.dropPlayerItemWithRandomChoice(stack, false);
            }
        }
        // Drop result slot contents
        ItemStack resultStack = craftResult.getStackInSlotOnClosing(0);
        if (resultStack != null) {
            player.dropPlayerItemWithRandomChoice(resultStack, false);
        }
    }

    public InventoryCrafting getCraftMatrix() {
        return craftMatrix;
    }

    public InventoryCraftResult getCraftResult() {
        return craftResult;
    }

    public EntityPlayer getPlayer() {
        return player;
    }

    public World getWorld() {
        return world;
    }
}
