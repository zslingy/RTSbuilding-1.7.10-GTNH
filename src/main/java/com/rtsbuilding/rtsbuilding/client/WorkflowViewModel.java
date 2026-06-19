package com.rtsbuilding.rtsbuilding.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsWorkflowProgressBatchMessage;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsWorkflowProgressMessage;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;

public final class WorkflowViewModel {

    private static final List<RtsWorkflowStatus> statuses = new ArrayList<RtsWorkflowStatus>();

    private WorkflowViewModel() {}

    public static synchronized void updateFromPacket(S2CRtsWorkflowProgressMessage msg) {
        if (msg == null) return;
        updateStatus(msg.toStatus());
    }

    public static synchronized void updateFromBatch(S2CRtsWorkflowProgressBatchMessage msg) {
        statuses.clear();
        if (msg == null) return;
        for (RtsWorkflowStatus status : msg.toStatuses()) if (status != null && status.isActive()) statuses.add(status);
    }

    public static synchronized List<RtsWorkflowStatus> getAllProgress() {
        return Collections.unmodifiableList(new ArrayList<RtsWorkflowStatus>(statuses));
    }

    public static synchronized RtsWorkflowStatus getProgress(int entryId) {
        for (RtsWorkflowStatus status : statuses) if (status.entryId() == entryId) return status;
        return RtsWorkflowStatus.idle();
    }

    public static synchronized void clear() {
        statuses.clear();
    }

    private static void updateStatus(RtsWorkflowStatus status) {
        if (status == null || !status.isActive()) {
            clear();
            return;
        }
        for (Iterator<RtsWorkflowStatus> it = statuses.iterator(); it.hasNext();) {
            if (it.next()
                .entryId() == status.entryId()) {
                it.remove();
                break;
            }
        }
        statuses.add(status);
    }
}
