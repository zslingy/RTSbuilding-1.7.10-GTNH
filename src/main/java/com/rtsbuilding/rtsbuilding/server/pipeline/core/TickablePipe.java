package com.rtsbuilding.rtsbuilding.server.pipeline.core;

public interface TickablePipe {

    TickResult tick(PipelineContext ctx);
}
