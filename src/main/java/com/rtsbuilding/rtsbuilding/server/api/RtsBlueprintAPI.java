package com.rtsbuilding.rtsbuilding.server.api;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.blueprint.server.BlueprintPlacementService;

public class RtsBlueprintAPI {

    static final RtsBlueprintAPI INSTANCE = new RtsBlueprintAPI();

    public void queuePlacement(EntityPlayerMP player, RtsBlueprint blueprint, int anchorX, int anchorY, int anchorZ) {
        BlueprintPlacementService.queuePlacement(player, blueprint.getName(), anchorX, anchorY, anchorZ, (byte) 0);
    }
}
