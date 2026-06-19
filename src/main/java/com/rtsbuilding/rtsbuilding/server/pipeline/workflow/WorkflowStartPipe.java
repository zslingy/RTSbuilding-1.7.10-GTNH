package com.rtsbuilding.rtsbuilding.server.pipeline.workflow;

import java.util.Optional;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowToken;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;

public final class WorkflowStartPipe implements PipelinePipe<PipelineContext> {

    public static final TypedKey<RtsWorkflowType> KEY_WORKFLOW_TYPE = new TypedKey<RtsWorkflowType>(
        "workflowType",
        RtsWorkflowType.class);
    public static final TypedKey<Integer> KEY_TOTAL_BLOCKS = new TypedKey<Integer>("totalBlocks", Integer.class);

    private final RtsWorkflowType defaultType;

    public WorkflowStartPipe() {
        this(null);
    }

    public WorkflowStartPipe(RtsWorkflowType defaultType) {
        this.defaultType = defaultType;
    }

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        RtsWorkflowType type = ctx.getArg(KEY_WORKFLOW_TYPE);
        if (type == null) type = defaultType;
        if (type == null) return PipelineResult.failure("missing workflow type");
        Integer total = ctx.getArg(KEY_TOTAL_BLOCKS);
        Optional<RtsWorkflowToken> token = RtsWorkflowEngine.getInstance()
            .start(ctx.player(), type, RtsWorkflowPriority.NORMAL, total == null ? 0 : total.intValue());
        if (!token.isPresent()) return PipelineResult.failure("workflow slots full");
        ctx.setData(
            PipelineContext.KEY_WORKFLOW_ENTRY_ID,
            token.get()
                .getEntryId());
        ctx.setData(KEY_WORKFLOW_TYPE, type);
        return PipelineResult.success();
    }
}
