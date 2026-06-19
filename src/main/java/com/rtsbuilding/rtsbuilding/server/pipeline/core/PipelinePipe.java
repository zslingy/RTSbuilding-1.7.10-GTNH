package com.rtsbuilding.rtsbuilding.server.pipeline.core;

public interface PipelinePipe<C extends PipelineContext> {

    PipelineResult execute(C ctx);
}
