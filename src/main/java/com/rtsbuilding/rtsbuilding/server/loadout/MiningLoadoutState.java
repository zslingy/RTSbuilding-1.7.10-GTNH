package com.rtsbuilding.rtsbuilding.server.loadout;

import net.minecraft.item.ItemStack;

public class MiningLoadoutState {

    private int slotIndex;
    private ItemStack stack;
    private MiningLoadoutRole role;

    public MiningLoadoutState() {}

    public MiningLoadoutState(int slotIndex, ItemStack stack, MiningLoadoutRole role) {
        this.slotIndex = slotIndex;
        this.stack = stack;
        this.role = role;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public ItemStack getStack() {
        return stack;
    }

    public MiningLoadoutRole getRole() {
        return role;
    }
}
