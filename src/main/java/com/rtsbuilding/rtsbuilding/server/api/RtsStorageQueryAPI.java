package com.rtsbuilding.rtsbuilding.server.api;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.service.RtsSessionService;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;

public class RtsStorageQueryAPI {

    static final RtsStorageQueryAPI INSTANCE = new RtsStorageQueryAPI();

    public long getCount(EntityPlayerMP player, String itemId) {
        RtsStorageSession session = RtsSessionService.getSession(player);
        return session != null ? session.getCount(itemId) : 0;
    }

    public long getCount(EntityPlayerMP player, String itemId, int meta) {
        RtsStorageSession session = RtsSessionService.getSession(player);
        if (session == null) return 0;
        return session.getCount(itemId);
    }

    public boolean hasItems(EntityPlayerMP player, String itemId, long amount) {
        return getCount(player, itemId) >= amount;
    }
}
