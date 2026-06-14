package com.rtsbuilding.rtsbuilding.server.feedback;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.network.feedback.S2CRtsDamageFeedbackMessage;

public final class RtsDamageFeedbackManager {

    private RtsDamageFeedbackManager() {}

    public static void sendDamageFeedback(EntityPlayerMP player, float amount, boolean lowHealth) {
        RtsNetworkManager.NETWORK.sendTo(new S2CRtsDamageFeedbackMessage(amount, lowHealth), player);
    }
}
