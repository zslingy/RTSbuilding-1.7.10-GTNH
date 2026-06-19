package com.rtsbuilding.rtsbuilding.server.workflow.core;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.workflow.event.RtsWorkflowEventListener;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;

public interface IWorkflowEngine {

    Optional<RtsWorkflowToken> start(EntityPlayerMP player, RtsWorkflowType type, RtsWorkflowPriority priority,
        int totalBlocks);

    Optional<RtsWorkflowToken> from(EntityPlayerMP player, int entryId);

    Optional<RtsWorkflowToken> lastActive(EntityPlayerMP player);

    void addListener(RtsWorkflowEventListener listener);

    void removeListener(RtsWorkflowEventListener listener);

    RtsWorkflowStatus getProgress(RtsWorkflowToken token);

    RtsWorkflowStatus getProgress(EntityPlayerMP player, int entryId);

    List<RtsWorkflowStatus> getAllProgress(EntityPlayerMP player);

    boolean hasActiveWorkflow(EntityPlayerMP player);

    int activeWorkflowCount(EntityPlayerMP player);

    int occupiedSlotCount(EntityPlayerMP player);

    boolean isFull(EntityPlayerMP player);

    void deleteWorkflow(EntityPlayerMP player, int entryId);

    void cancelAll(EntityPlayerMP player);

    void clearPlayerData(UUID playerId);

    void clearAllData();

    boolean isEntryPaused(UUID playerId, int dimensionId, int entryId);

    int cleanupStaleWorkflows(Duration maxIdleTime);
}
