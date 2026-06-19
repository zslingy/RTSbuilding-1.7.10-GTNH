package com.rtsbuilding.rtsbuilding.server.api;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.service.RtsContainerService;

public class RtsCraftingAPI {

    static final RtsCraftingAPI INSTANCE = new RtsCraftingAPI();

    public boolean isLinkedToStorage(EntityPlayerMP player) {
        return RtsContainerService.isAe2Linked(player) || RtsContainerService.isContainerLinked(player);
    }

    public void refreshAe2Data(EntityPlayerMP player) {
        RtsContainerService.refreshAe2Data(player);
    }
}
