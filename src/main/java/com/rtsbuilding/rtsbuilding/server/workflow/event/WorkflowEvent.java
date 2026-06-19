package com.rtsbuilding.rtsbuilding.server.workflow.event;

import java.util.UUID;

import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;

/**
 * 工作流事件不可变载荷。
 */
public final class WorkflowEvent {

    private final WorkflowEventType type;
    private final UUID playerId;
    private final int entryId;
    private final RtsWorkflowStatus status;

    public WorkflowEvent(WorkflowEventType type, UUID playerId, int entryId, RtsWorkflowStatus status) {
        this.type = type;
        this.playerId = playerId;
        this.entryId = entryId;
        this.status = status == null ? RtsWorkflowStatus.idle() : status;
    }

    public WorkflowEventType type() {
        return type;
    }

    public UUID playerId() {
        return playerId;
    }

    public int entryId() {
        return entryId;
    }

    public RtsWorkflowStatus status() {
        return status;
    }
}
