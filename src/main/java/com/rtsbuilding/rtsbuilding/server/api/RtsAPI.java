package com.rtsbuilding.rtsbuilding.server.api;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.service.RtsSessionService;

public class RtsAPI {

    private RtsAPI() {}

    public static RtsStorageQueryAPI storage() {
        return RtsStorageQueryAPI.INSTANCE;
    }

    public static RtsPlacementAPI placement() {
        return RtsPlacementAPI.INSTANCE;
    }

    public static RtsMiningAPI mining() {
        return RtsMiningAPI.INSTANCE;
    }

    public static RtsTransferAPI transfer() {
        return RtsTransferAPI.INSTANCE;
    }

    public static RtsCraftingAPI crafting() {
        return RtsCraftingAPI.INSTANCE;
    }

    public static RtsInteractionAPI interaction() {
        return RtsInteractionAPI.INSTANCE;
    }

    public static RtsBlueprintAPI blueprint() {
        return RtsBlueprintAPI.INSTANCE;
    }

    public static RtsFluidAPI fluid() {
        return RtsFluidAPI.INSTANCE;
    }

    public static boolean isRtsActive(EntityPlayerMP player) {
        return com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager.isActive(player);
    }

    public static RtsSessionService session() {
        return null;
    }
}
