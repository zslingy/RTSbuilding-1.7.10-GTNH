package com.rtsbuilding.rtsbuilding.server.workflow.model;

/**
 * 工作流优先级，rank 越高越优先。
 */
public enum RtsWorkflowPriority {

    LOW(0),
    NORMAL(1),
    HIGH(2),
    CRITICAL(3);

    private final int rank;

    RtsWorkflowPriority(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public boolean isHigherThan(RtsWorkflowPriority other) {
        return other != null && rank > other.rank;
    }
}
