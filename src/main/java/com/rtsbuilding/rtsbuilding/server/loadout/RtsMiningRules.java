package com.rtsbuilding.rtsbuilding.server.loadout;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

public final class RtsMiningRules {

    private RtsMiningRules() {}

    public static List<MiningLoadoutState> scanLoadout(EntityPlayer player) {
        // stub — stage 3
        return new ArrayList<>();
    }

    public static MiningLoadoutRole classifyTool(ItemStack stack) {
        // stub — stage 3
        return MiningLoadoutRole.NONE;
    }
}
