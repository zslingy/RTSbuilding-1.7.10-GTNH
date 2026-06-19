package com.rtsbuilding.rtsbuilding.server.loadout;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;

public final class RtsMiningRules {

    private RtsMiningRules() {}

    public static List<MiningLoadoutState> scanLoadout(EntityPlayer player) {
        List<MiningLoadoutState> results = new ArrayList<>();
        if (player == null) return results;
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack == null) continue;
            MiningLoadoutRole role = classifyTool(stack);
            if (role == MiningLoadoutRole.NONE) continue;
            results.add(new MiningLoadoutState(i, stack.copy(), role));
        }
        return results;
    }

    public static MiningLoadoutRole classifyTool(ItemStack stack) {
        if (stack == null) return MiningLoadoutRole.NONE;
        Item item = stack.getItem();
        if (item instanceof ItemPickaxe) return MiningLoadoutRole.PICKAXE;
        if (item instanceof ItemAxe) return MiningLoadoutRole.AXE;
        if (item instanceof ItemSpade) return MiningLoadoutRole.SHOVEL;
        return MiningLoadoutRole.NONE;
    }
}
