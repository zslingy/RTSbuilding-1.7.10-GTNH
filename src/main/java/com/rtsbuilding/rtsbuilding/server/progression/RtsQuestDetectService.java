package com.rtsbuilding.rtsbuilding.server.progression;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsQuestDetectStatusMessage;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;

public final class RtsQuestDetectService {

    private RtsQuestDetectService() {}

    public static void runDetection(EntityPlayerMP player) {
        if (player == null) return;
        if (!RtsCameraManager.isActive(player)) {
            RtsNetworkManager.NETWORK.sendTo(
                new S2CRtsQuestDetectStatusMessage(S2CRtsQuestDetectStatusMessage.PHASE_UNAVAILABLE, 0, 0, 0),
                player);
            return;
        }
        if (!RtsProgressionManager.canUse(player, RtsFeature.CAMERA)) {
            RtsNetworkManager.NETWORK.sendTo(
                new S2CRtsQuestDetectStatusMessage(S2CRtsQuestDetectStatusMessage.PHASE_UNAVAILABLE, 0, 0, 0),
                player);
            return;
        }
        RtsNetworkManager.NETWORK
            .sendTo(new S2CRtsQuestDetectStatusMessage(S2CRtsQuestDetectStatusMessage.PHASE_STARTED, 0, 0, 0), player);
        int scanned = 0;
        int completed = 0;
        int total = player.worldObj != null ? player.worldObj.playerEntities.size() : 0;
        for (Object obj : player.worldObj.playerEntities) {
            if (obj instanceof EntityPlayerMP) {
                scanned++;
                if (RtsProgressionManager.canUse((EntityPlayerMP) obj, RtsFeature.CAMERA)) completed++;
            }
        }
        RtsNetworkManager.NETWORK.sendTo(
            new S2CRtsQuestDetectStatusMessage(
                S2CRtsQuestDetectStatusMessage.PHASE_COMPLETE,
                scanned,
                total,
                completed),
            player);
    }
}
