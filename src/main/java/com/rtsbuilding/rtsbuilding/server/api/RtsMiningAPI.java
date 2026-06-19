package com.rtsbuilding.rtsbuilding.server.api;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.RtsMineManager;

public class RtsMiningAPI {

    static final RtsMiningAPI INSTANCE = new RtsMiningAPI();

    public boolean breakBlock(EntityPlayerMP player, int x, int y, int z) {
        try {
            RtsMineManager.breakBlockDirect(player, x, y, z, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isMiningActive(EntityPlayerMP player) {
        return com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager.isActive(player);
    }
}
