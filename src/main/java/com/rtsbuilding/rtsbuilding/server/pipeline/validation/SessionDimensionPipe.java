package com.rtsbuilding.rtsbuilding.server.pipeline.validation;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;

public final class SessionDimensionPipe implements PipelinePipe<PipelineContext> {

    public static final TypedKey<Integer> KEY_DIMENSION_ID = new TypedKey<Integer>("dimensionId", Integer.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        Integer expected = ctx.getArg(KEY_DIMENSION_ID);
        if (expected != null && ctx.player().worldObj.provider.dimensionId != expected.intValue())
            return PipelineResult.failure("dimension mismatch");
        return PipelineResult.success();
    }
}
