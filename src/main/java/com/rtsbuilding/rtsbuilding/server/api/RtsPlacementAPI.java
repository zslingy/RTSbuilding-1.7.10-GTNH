package com.rtsbuilding.rtsbuilding.server.api;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;

public class RtsPlacementAPI {

    static final RtsPlacementAPI INSTANCE = new RtsPlacementAPI();

    public boolean placeBlock(EntityPlayerMP player, int x, int y, int z, byte face, String itemId) {
        return RtsStorageManager
            .placeBlockDirect(player, x, y, z, face, 0.5, 0.5, 0.5, (byte) 0, false, true, itemId, null, false);
    }

    public boolean placeBlockForce(EntityPlayerMP player, int x, int y, int z, byte face, String itemId) {
        return RtsStorageManager
            .placeBlockDirect(player, x, y, z, face, 0.5, 0.5, 0.5, (byte) 0, true, false, itemId, null, false);
    }

    public boolean placeBlockWithStack(EntityPlayerMP player, int x, int y, int z, byte face, ItemStack prototype) {
        String itemId = prototype != null && prototype.getItem() != null
            ? net.minecraft.item.Item.itemRegistry.getNameForObject(prototype.getItem())
            : null;
        return RtsStorageManager
            .placeBlockDirect(player, x, y, z, face, 0.5, 0.5, 0.5, (byte) 0, false, true, itemId, prototype, false);
    }
}
