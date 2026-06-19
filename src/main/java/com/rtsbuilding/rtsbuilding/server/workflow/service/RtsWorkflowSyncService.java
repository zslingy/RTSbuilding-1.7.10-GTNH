package com.rtsbuilding.rtsbuilding.server.workflow.service;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsWorkflowProgressBatchMessage;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsWorkflowProgressMessage;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEntry;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowSlotManager;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;

public final class RtsWorkflowSyncService {

    public void notifyPlayer(EntityPlayerMP player, RtsWorkflowSlotManager slots) {
        if (player == null || slots == null) return;
        List<RtsWorkflowStatus> statuses = new ArrayList<RtsWorkflowStatus>();
        for (RtsWorkflowEntry entry : slots.occupiedEntries()) statuses.add(entry.snapshot());
        RtsNetworkManager.NETWORK.sendTo(new S2CRtsWorkflowProgressBatchMessage(statuses), player);
    }

    public void sendIdle(EntityPlayerMP player) {
        if (player != null)
            RtsNetworkManager.NETWORK.sendTo(new S2CRtsWorkflowProgressMessage(RtsWorkflowStatus.idle()), player);
    }
}
