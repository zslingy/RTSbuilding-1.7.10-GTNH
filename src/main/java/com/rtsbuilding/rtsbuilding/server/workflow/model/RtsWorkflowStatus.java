package com.rtsbuilding.rtsbuilding.server.workflow.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 工作流进度不可变快照，用于服务端查询和后续网络同步。
 */
public final class RtsWorkflowStatus {

    private final RtsWorkflowType type;
    private final RtsWorkflowPriority priority;
    private final int totalBlocks;
    private final int completedBlocks;
    private final int failedBlocks;
    private final int remainingBlocks;
    private final float progress;
    private final boolean suspended;
    private final boolean paused;
    private final boolean complete;
    private final List<String> missingItems;
    private final String detailMessage;
    private final int entryId;

    private RtsWorkflowStatus(RtsWorkflowType type, RtsWorkflowPriority priority, int totalBlocks, int completedBlocks,
        int failedBlocks, int remainingBlocks, float progress, boolean suspended, boolean paused, boolean complete,
        List<String> missingItems, String detailMessage, int entryId) {
        this.type = type;
        this.priority = priority == null ? RtsWorkflowPriority.NORMAL : priority;
        this.totalBlocks = totalBlocks;
        this.completedBlocks = completedBlocks;
        this.failedBlocks = failedBlocks;
        this.remainingBlocks = remainingBlocks;
        this.progress = progress;
        this.suspended = suspended;
        this.paused = paused;
        this.complete = complete;
        this.missingItems = Collections.unmodifiableList(new ArrayList<String>(missingItems));
        this.detailMessage = detailMessage == null ? "" : detailMessage;
        this.entryId = entryId;
    }

    public static RtsWorkflowStatus fromRaw(RtsWorkflowType type, RtsWorkflowPriority priority, int totalBlocks,
        int completedBlocks, int failedBlocks, List<String> missingItems, String detailMessage, boolean suspended,
        boolean paused, int entryId) {
        int safeTotal = Math.max(0, totalBlocks);
        int safeDone = Math.max(0, completedBlocks);
        int safeFailed = Math.max(0, failedBlocks);
        int remaining = safeTotal > 0 ? Math.max(0, safeTotal - (safeDone + safeFailed)) : 0;
        float progress = safeTotal > 0 ? Math.min(1.0F, (float) (safeDone + safeFailed) / (float) safeTotal) : 0.0F;
        boolean complete = safeTotal > 0 && safeDone + safeFailed >= safeTotal;
        return new RtsWorkflowStatus(
            type,
            priority,
            safeTotal,
            safeDone,
            safeFailed,
            remaining,
            progress,
            suspended,
            paused,
            complete,
            missingItems == null ? Collections.<String>emptyList() : missingItems,
            detailMessage,
            entryId);
    }

    public static RtsWorkflowStatus idle() {
        return new RtsWorkflowStatus(
            null,
            RtsWorkflowPriority.NORMAL,
            0,
            0,
            0,
            0,
            0.0F,
            false,
            false,
            false,
            Collections.<String>emptyList(),
            "",
            -1);
    }

    public RtsWorkflowType type() {
        return type;
    }

    public RtsWorkflowPriority priority() {
        return priority;
    }

    public int totalBlocks() {
        return totalBlocks;
    }

    public int completedBlocks() {
        return completedBlocks;
    }

    public int failedBlocks() {
        return failedBlocks;
    }

    public int remainingBlocks() {
        return remainingBlocks;
    }

    public float progress() {
        return progress;
    }

    public boolean suspended() {
        return suspended;
    }

    public boolean paused() {
        return paused;
    }

    public boolean isComplete() {
        return complete;
    }

    public List<String> missingItems() {
        return missingItems;
    }

    public String detailMessage() {
        return detailMessage;
    }

    public int entryId() {
        return entryId;
    }

    public boolean isActive() {
        return type != null;
    }

    public boolean hasMissingItems() {
        return !missingItems.isEmpty();
    }

    public boolean hasFailures() {
        return failedBlocks > 0;
    }

    public String progressText() {
        return completedBlocks + "/" + totalBlocks;
    }

    public String typeLabel() {
        if (type == null) return "Idle";
        switch (type) {
            case MINE_SINGLE:
                return "Mine";
            case ULTIMINE:
                return "Ultimine";
            case AREA_MINE:
                return "Area Mine";
            case AREA_DESTROY:
                return "Destroy";
            case PLACE_SINGLE:
                return "Place";
            case PLACE_BATCH:
                return "Place Batch";
            case QUICK_BUILD:
                return "Quick Build";
            case STOP_MINING:
                return "Stop Mining";
            default:
                return type.name();
        }
    }
}
