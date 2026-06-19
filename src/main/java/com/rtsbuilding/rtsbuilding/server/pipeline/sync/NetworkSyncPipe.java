package com.rtsbuilding.rtsbuilding.server.pipeline.sync;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowSyncService;

public final class NetworkSyncPipe implements PipelinePipe<PipelineContext> {

    private final RtsWorkflowSyncService syncService = new RtsWorkflowSyncService();

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        syncService.notifyPlayer(
            ctx.player(),
            RtsWorkflowEngine.getInstance()
                .getSlotManager(ctx.player()));
        return PipelineResult.success();
    }
}
