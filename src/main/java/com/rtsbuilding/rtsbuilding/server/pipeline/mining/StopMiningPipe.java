package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import com.rtsbuilding.rtsbuilding.server.RtsMineManager;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;

public final class StopMiningPipe implements PipelinePipe<PipelineContext> {

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        RtsMineManager.abortMining(ctx.player());
        RtsWorkflowEngine.getInstance()
            .cancelAll(ctx.player());
        return PipelineResult.success();
    }
}
