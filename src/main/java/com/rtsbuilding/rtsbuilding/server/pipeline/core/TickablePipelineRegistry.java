package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventType;

public final class TickablePipelineRegistry {

    private static final Map<UUID, ActivePipeline> ACTIVE = new ConcurrentHashMap<UUID, ActivePipeline>();

    private TickablePipelineRegistry() {}

    public static void register(EntityPlayerMP player, PipelineContext ctx, TickablePipe pipe, List<?> postPipes) {
        if (player != null) ACTIVE.put(player.getUniqueID(), new ActivePipeline(ctx, pipe, postPipes));
    }

    public static void cancel(EntityPlayerMP player) {
        if (player != null) ACTIVE.remove(player.getUniqueID());
    }

    public static int size() {
        return ACTIVE.size();
    }

    public static void tickAll() {
        Iterator<Map.Entry<UUID, ActivePipeline>> it = ACTIVE.entrySet()
            .iterator();
        while (it.hasNext()) {
            ActivePipeline active = it.next()
                .getValue();
            TickResult result = active.pipe()
                .tick(active.context());
            if (result == TickResult.COMPLETE || result == TickResult.FAILED) {
                it.remove();
                List<?> postPipes = active.postPipes();
                if (postPipes != null) {
                    PipelineContext ctx = active.context();
                    for (Object pp : postPipes) {
                        @SuppressWarnings("unchecked")
                        PipelinePipe<PipelineContext> p = (PipelinePipe<PipelineContext>) pp;
                        p.execute(ctx);
                    }
                }
                if (active.context()
                    .hasData(PipelineContext.KEY_WORKFLOW_ENTRY_ID)) {
                    RtsWorkflowEngine.getInstance()
                        .firePipelineEvent(
                            active.context()
                                .player(),
                            active.context()
                                .getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID),
                            WorkflowEventType.SYNC_PHASE_COMPLETED);
                }
            }
        }
    }
}
