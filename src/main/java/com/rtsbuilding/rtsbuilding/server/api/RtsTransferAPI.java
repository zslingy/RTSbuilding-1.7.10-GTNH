package com.rtsbuilding.rtsbuilding.server.api;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.rtsbuilding.rtsbuilding.server.service.RtsTransferService;

public class RtsTransferAPI {

    static final RtsTransferAPI INSTANCE = new RtsTransferAPI();

    public boolean consumeItems(EntityPlayerMP player, String itemId, int meta, long amount) {
        return RtsTransferService.tryConsumeBlock(player, itemId, meta, amount);
    }

    public long getAvailable(EntityPlayerMP player, String itemId, int meta) {
        return RtsTransferService.getAvailableAmount(player, itemId, meta);
    }

    public void storeItem(EntityPlayerMP player, ItemStack stack) {
        RtsTransferService.storeItemStack(player, stack);
    }
}
