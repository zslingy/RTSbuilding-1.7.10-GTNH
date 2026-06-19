package com.rtsbuilding.rtsbuilding.server.pipeline.workflow;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;

public final class WorkflowProgressPipe implements PipelinePipe<PipelineContext> {

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        if (ctx.hasData(PipelineContext.KEY_WORKFLOW_ENTRY_ID)) {
            RtsWorkflowEngine.getInstance()
                .from(ctx.player(), ctx.getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID))
                .ifPresent(token -> token.markProgress());
        }
        return PipelineResult.success();
    }
}
