package com.rtsbuilding.rtsbuilding.server.pipeline.sync;

import java.util.List;

import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.history.HistoryEntry;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;

public final class HistoryRecordPipe implements PipelinePipe<PipelineContext> {

    public static final TypedKey<List<HistoryBlockRecord>> KEY_BLOCK_RECORDS = new TypedKey<List<HistoryBlockRecord>>(
        "blockRecords",
        (Class<List<HistoryBlockRecord>>) (Class<?>) List.class);

    public static final TypedKey<RtsWorkflowType> KEY_WORKFLOW_TYPE = new TypedKey<RtsWorkflowType>(
        "workflowType",
        RtsWorkflowType.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        RtsWorkflowType type = ctx.getData(KEY_WORKFLOW_TYPE);
        if (type == null) return PipelineResult.success();

        List<HistoryBlockRecord> records = ctx.getData(KEY_BLOCK_RECORDS);
        HistoryEntry entry = new HistoryEntry(System.currentTimeMillis(), type, records);
        ServerHistoryManager.getInstance()
            .record(ctx.player(), entry);
        return PipelineResult.success();
    }
}
