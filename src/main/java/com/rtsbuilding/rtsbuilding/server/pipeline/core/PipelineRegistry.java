package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;

public final class PipelineRegistry {

    private static final Map<RtsWorkflowType, WorkflowPipeline<? extends PipelineContext>> PIPELINES = new ConcurrentHashMap<RtsWorkflowType, WorkflowPipeline<? extends PipelineContext>>();

    private PipelineRegistry() {}

    public static <C extends PipelineContext> WorkflowPipeline<C> register(RtsWorkflowType type) {
        return new WorkflowPipeline<C>(type);
    }

    public static void register(WorkflowPipeline<? extends PipelineContext> pipeline) {
        PIPELINES.put(pipeline.type(), pipeline);
    }

    public static WorkflowPipeline<? extends PipelineContext> get(RtsWorkflowType type) {
        return PIPELINES.get(type);
    }

    public static boolean has(RtsWorkflowType type) {
        return PIPELINES.containsKey(type);
    }

    public static void clear() {
        PIPELINES.clear();
    }
}
