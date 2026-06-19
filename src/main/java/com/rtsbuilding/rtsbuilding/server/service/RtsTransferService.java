package com.rtsbuilding.rtsbuilding.server.service;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;

public class RtsTransferService {

    public static boolean tryConsumeBlock(EntityPlayerMP player, String itemId, int meta, long amount) {
        if (player == null || itemId == null || amount <= 0) return false;

        RtsStorageSession session = RtsSessionService.getSession(player);
        return session.tryConsume(itemId, meta, amount);
    }

    public static long getAvailableAmount(EntityPlayerMP player, String itemId, int meta) {
        if (player == null || itemId == null) return 0;
        RtsStorageSession session = RtsSessionService.getSession(player);
        // getCount only takes itemId (no meta), returns total across all metas
        return session.getCount(itemId);
    }

    public static void storeItemStack(EntityPlayerMP player, ItemStack stack) {
        if (player == null || stack == null || stack.getItem() == null) return;
        String itemId = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
        if (itemId == null) return;
        RtsStorageSession session = RtsSessionService.getSession(player);
        session.addItem(itemId, stack.getItemDamage(), stack.stackSize);
    }
}
