package com.rtsbuilding.rtsbuilding.server.workflow.core;

import java.util.List;
import java.util.UUID;

import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventType;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;

public final class RtsWorkflowToken {

    private final UUID playerId;
    private final int entryId;
    private final int dimensionId;
    private final RtsWorkflowEngine engine;

    RtsWorkflowToken(UUID playerId, int entryId, int dimensionId, RtsWorkflowEngine engine) {
        this.playerId = playerId;
        this.entryId = entryId;
        this.dimensionId = dimensionId;
        this.engine = engine;
    }

    public int getEntryId() {
        return entryId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getDimensionId() {
        return dimensionId;
    }

    public boolean isValid() {
        return resolveEntry() != null;
    }

    public void markProgress() {
        updateProgress(1, null);
    }

    public void updateProgress(int completedDelta, List<String> missingItems) {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            entry.addCompletedBlocks(completedDelta);
            entry.addMissingItems(missingItems);
            engine.fireEvent(WorkflowEventType.PROGRESS, playerId, entryId, entry);
            engine.notifyPlayer(playerId, dimensionId);
        }
    }

    public void setCompletedBlocks(int absoluteValue) {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            entry.setCompletedBlocks(absoluteValue);
            engine.fireEvent(WorkflowEventType.PROGRESS, playerId, entryId, entry);
            engine.notifyPlayer(playerId, dimensionId);
        }
    }

    public void setTotalBlocks(int totalBlocks) {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            entry.setTotalBlocks(totalBlocks);
            engine.notifyPlayer(playerId, dimensionId);
        }
    }

    public void recordFailure() {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            entry.addFailedBlocks(1);
            engine.fireEvent(WorkflowEventType.PROGRESS, playerId, entryId, entry);
            engine.notifyPlayer(playerId, dimensionId);
        }
    }

    public void setDetailMessage(String message) {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            entry.setDetailMessage(message);
            engine.notifyPlayer(playerId, dimensionId);
        }
    }

    public void suspend() {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            entry.setSuspended(true);
            entry.setDetailMessage("等待物品...");
            engine.fireEvent(WorkflowEventType.SUSPENDED, playerId, entryId, entry);
            engine.notifyPlayer(playerId, dimensionId);
        }
    }

    public boolean resume() {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null && entry.suspended()) {
            entry.setSuspended(false);
            entry.setDetailMessage("");
            engine.fireEvent(WorkflowEventType.RESUMED, playerId, entryId, entry);
            engine.notifyPlayer(playerId, dimensionId);
            return true;
        }
        return false;
    }

    public void pause() {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            entry.setPaused(true);
            engine.fireEvent(WorkflowEventType.PAUSED, playerId, entryId, entry);
            engine.notifyPlayer(playerId, dimensionId);
        }
    }

    public boolean unpause() {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null && entry.paused()) {
            entry.setPaused(false);
            engine.fireEvent(WorkflowEventType.UNPAUSED, playerId, entryId, entry);
            engine.notifyPlayer(playerId, dimensionId);
            return true;
        }
        return false;
    }

    public boolean isPaused() {
        RtsWorkflowEntry entry = resolveEntry();
        return entry != null && entry.paused();
    }

    public void complete() {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            engine.fireEvent(WorkflowEventType.COMPLETED, playerId, entryId, entry);
            engine.removeEntry(playerId, dimensionId, entryId);
        }
    }

    public void cancel() {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            engine.fireEvent(WorkflowEventType.CANCELLED, playerId, entryId, entry);
            engine.removeEntry(playerId, dimensionId, entryId);
        }
    }

    public RtsWorkflowStatus getProgress() {
        RtsWorkflowEntry entry = resolveEntry();
        return entry == null ? RtsWorkflowStatus.idle() : entry.snapshot();
    }

    private RtsWorkflowEntry resolveEntry() {
        return engine.findEntry(playerId, dimensionId, entryId);
    }
}
