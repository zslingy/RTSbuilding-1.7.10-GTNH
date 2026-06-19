package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import java.util.List;

public final class ActivePipeline {

    private final PipelineContext context;
    private final TickablePipe pipe;
    private final List<?> postPipes;

    public ActivePipeline(PipelineContext context, TickablePipe pipe, List<?> postPipes) {
        this.context = context;
        this.pipe = pipe;
        this.postPipes = postPipes;
    }

    public PipelineContext context() {
        return context;
    }

    public TickablePipe pipe() {
        return pipe;
    }

    public List<?> postPipes() {
        return postPipes;
    }
}
