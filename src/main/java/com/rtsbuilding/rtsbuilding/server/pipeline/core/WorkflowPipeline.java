package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolBorrowPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventType;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;

public final class WorkflowPipeline<C extends PipelineContext> {

    private final RtsWorkflowType type;
    private final List<PipelinePipe<? super C>> prePipes = new ArrayList<PipelinePipe<? super C>>();
    private final List<PipelinePipe<? super C>> postPipes = new ArrayList<PipelinePipe<? super C>>();
    private final List<TickablePipe> tickablePipes = new ArrayList<TickablePipe>();
    private boolean tickableMode;
    private boolean asyncCompletion;

    WorkflowPipeline(RtsWorkflowType type) {
        this.type = type;
    }

    public WorkflowPipeline<C> pipe(PipelinePipe<? super C> pipe) {
        if (tickableMode) {
            postPipes.add(pipe);
        } else {
            prePipes.add(pipe);
        }
        return this;
    }

    public WorkflowPipeline<C> tickable(TickablePipe pipe) {
        tickableMode = true;
        tickablePipes.add(pipe);
        return this;
    }

    public WorkflowPipeline<C> asyncCompletion() {
        asyncCompletion = true;
        return this;
    }

    public PipelineResult execute(C ctx) {
        for (PipelinePipe<? super C> pipe : prePipes) {
            try {
                PipelineResult result = pipe.execute(ctx);
                ctx.setResult(result);
                if (result instanceof PipelineResult.Failure || result instanceof PipelineResult.Skip) {
                    fireResultEvent(ctx, WorkflowEventType.CANCELLED);
                    return result;
                }
            } catch (Exception e) {
                RtsbuildingMod.LOGGER.error(
                    "[Pipeline] pipe {} failed",
                    pipe.getClass()
                        .getSimpleName(),
                    e);
                PipelineResult failure = PipelineResult.failure(e.getMessage(), e);
                ctx.setResult(failure);
                fireResultEvent(ctx, WorkflowEventType.CANCELLED);
                return failure;
            }
        }
        if (!tickablePipes.isEmpty()) {
            if (postPipes.isEmpty()) {
                postPipes.addAll(prePipes);
            }
            ctx.retainOnly(
                PipelineContext.KEY_WORKFLOW_ENTRY_ID,
                SessionValidatePipe.KEY_SESSION,
                ToolBorrowPipe.KEY_TOOL_LEASE);
            TickablePipelineRegistry.register(ctx.player(), ctx, tickablePipes.get(0), postPipes);
        } else if (!asyncCompletion) {
            fireResultEvent(ctx, WorkflowEventType.SYNC_PHASE_COMPLETED);
        }
        return PipelineResult.success();
    }

    public WorkflowPipeline<C> register() {
        PipelineRegistry.register(this);
        return this;
    }

    public RtsWorkflowType type() {
        return type;
    }

    public List<PipelinePipe<? super C>> pipes() {
        List<PipelinePipe<? super C>> all = new ArrayList<PipelinePipe<? super C>>(prePipes);
        all.addAll(postPipes);
        return Collections.unmodifiableList(all);
    }

    public boolean hasTickablePhase() {
        return !tickablePipes.isEmpty();
    }

    public List<TickablePipe> tickablePipes() {
        return Collections.unmodifiableList(tickablePipes);
    }

    private void fireResultEvent(PipelineContext ctx, WorkflowEventType type) {
        if (ctx.hasData(PipelineContext.KEY_WORKFLOW_ENTRY_ID)) {
            RtsWorkflowEngine.getInstance()
                .firePipelineEvent(ctx.player(), ctx.getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID), type);
        }
    }
}
