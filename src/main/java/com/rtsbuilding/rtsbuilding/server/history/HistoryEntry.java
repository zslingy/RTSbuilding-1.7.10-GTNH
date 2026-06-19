package com.rtsbuilding.rtsbuilding.server.history;

import java.util.ArrayList;
import java.util.List;

import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;

public final class HistoryEntry {

    public final long timestamp;
    public final RtsWorkflowType type;
    public final List<HistoryBlockRecord> records;

    public HistoryEntry(long timestamp, RtsWorkflowType type) {
        this.timestamp = timestamp;
        this.type = type;
        this.records = new ArrayList<HistoryBlockRecord>();
    }

    public HistoryEntry(long timestamp, RtsWorkflowType type, List<HistoryBlockRecord> records) {
        this.timestamp = timestamp;
        this.type = type;
        this.records = records != null ? records : new ArrayList<HistoryBlockRecord>();
    }
}
