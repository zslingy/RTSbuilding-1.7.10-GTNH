package com.rtsbuilding.rtsbuilding.client.pathfinding;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.network.pathfinding.C2SRtsPathfindingMessage;

public class RtsClientPathfinding {

    public static void requestMove(double targetX, double targetY, double targetZ, String mode) {
        RtsNetworkManager.NETWORK.sendToServer(new C2SRtsPathfindingMessage(targetX, targetY, targetZ, mode));
    }

    public static void onPathfindingUpdate(double x, double y, double z, boolean arrived) {
        if (arrived) {
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            if (player != null) {
                player.motionX = 0;
                player.motionY = 0;
                player.motionZ = 0;
                player.velocityChanged = true;
            }
        }
    }
}
