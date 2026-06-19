package com.rtsbuilding.rtsbuilding.server.api;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.service.RtsContainerService;

public class RtsFluidAPI {

    static final RtsFluidAPI INSTANCE = new RtsFluidAPI();

    public boolean isLinked(EntityPlayerMP player) {
        return RtsContainerService.isAe2Linked(player) || RtsContainerService.isContainerLinked(player);
    }
}
