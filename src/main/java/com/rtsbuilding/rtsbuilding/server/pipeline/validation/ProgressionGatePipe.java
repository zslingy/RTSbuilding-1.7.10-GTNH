package com.rtsbuilding.rtsbuilding.server.pipeline.validation;

import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;

public final class ProgressionGatePipe implements PipelinePipe<PipelineContext> {

    private final RtsFeature feature;

    public ProgressionGatePipe() {
        this(null);
    }

    public ProgressionGatePipe(RtsFeature feature) {
        this.feature = feature;
    }

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        if (feature != null && !RtsProgressionManager.canUse(ctx.player(), feature))
            return PipelineResult.failure("feature locked: " + feature);
        return PipelineResult.success();
    }
}
