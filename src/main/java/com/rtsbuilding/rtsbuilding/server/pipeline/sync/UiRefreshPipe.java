package com.rtsbuilding.rtsbuilding.server.pipeline.sync;

import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;

public final class UiRefreshPipe implements PipelinePipe<PipelineContext> {

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        RtsStorageManager.sendStoragePage(ctx.player(), 0, 0);
        return PipelineResult.success();
    }
}
