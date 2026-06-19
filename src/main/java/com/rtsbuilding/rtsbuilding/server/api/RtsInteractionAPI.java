package com.rtsbuilding.rtsbuilding.server.api;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;

public class RtsInteractionAPI {

    static final RtsInteractionAPI INSTANCE = new RtsInteractionAPI();

    public boolean enableCamera(EntityPlayerMP player) {
        try {
            RtsCameraManager.enableCamera(player, false);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean disableCamera(EntityPlayerMP player) {
        try {
            RtsCameraManager.disableCamera(player);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isCameraActive(EntityPlayerMP player) {
        return RtsCameraManager.isActive(player);
    }
}
