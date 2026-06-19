package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.AreaDestroyComputePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.AreaMineComputePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.MiningExecutePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.StopMiningPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.StopPreviousPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.UltimineComputePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.UltimineTickPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.placement.PendingPlacementPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.placement.PlacementExecutePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.sync.HistoryRecordPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.sync.NetworkSyncPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.sync.UiRefreshPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolBorrowPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolReturnPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.ProgressionGatePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionDimensionPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowCompletePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowProgressPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowStartPipe;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class RtsPipelineRegistration {

    private static boolean registered;
    private static boolean tickRegistered;

    private RtsPipelineRegistration() {}

    public static void registerAll() {
        if (registered) return;
        registered = true;

        PipelineRegistry.<PipelineContext>register(RtsWorkflowType.MINE_SINGLE)
            .pipe(new ProgressionGatePipe(RtsFeature.REMOTE_BREAK))
            .pipe(new SessionValidatePipe())
            .pipe(new SessionDimensionPipe())
            .pipe(new StopPreviousPipe())
            .pipe(new WorkflowStartPipe(RtsWorkflowType.MINE_SINGLE))
            .pipe(new ToolBorrowPipe())
            .pipe(new MiningExecutePipe())
            .pipe(new ToolReturnPipe())
            .pipe(new WorkflowProgressPipe())
            .pipe(new NetworkSyncPipe())
            .pipe(new UiRefreshPipe())
            .pipe(new WorkflowCompletePipe())
            .pipe(new HistoryRecordPipe())
            .asyncCompletion()
            .register();

        PipelineRegistry.<PipelineContext>register(RtsWorkflowType.ULTIMINE)
            .pipe(new ProgressionGatePipe(RtsFeature.ULTIMINE))
            .pipe(new SessionValidatePipe())
            .pipe(new SessionDimensionPipe())
            .pipe(new StopPreviousPipe())
            .pipe(new WorkflowStartPipe(RtsWorkflowType.ULTIMINE))
            .pipe(new ToolBorrowPipe())
            .pipe(new UltimineComputePipe())
            .pipe(new ToolReturnPipe())
            .tickable(new UltimineTickPipe())
            .pipe(new WorkflowProgressPipe())
            .pipe(new NetworkSyncPipe())
            .pipe(new UiRefreshPipe())
            .pipe(new WorkflowCompletePipe())
            .pipe(new HistoryRecordPipe())
            .register();

        PipelineRegistry.<PipelineContext>register(RtsWorkflowType.AREA_MINE)
            .pipe(new ProgressionGatePipe(RtsFeature.ULTIMINE))
            .pipe(new SessionValidatePipe())
            .pipe(new SessionDimensionPipe())
            .pipe(new StopPreviousPipe())
            .pipe(new WorkflowStartPipe(RtsWorkflowType.AREA_MINE))
            .pipe(new ToolBorrowPipe())
            .pipe(new AreaMineComputePipe())
            .pipe(new ToolReturnPipe())
            .tickable(new UltimineTickPipe())
            .pipe(new WorkflowProgressPipe())
            .pipe(new NetworkSyncPipe())
            .pipe(new UiRefreshPipe())
            .pipe(new WorkflowCompletePipe())
            .pipe(new HistoryRecordPipe())
            .register();

        PipelineRegistry.<PipelineContext>register(RtsWorkflowType.AREA_DESTROY)
            .pipe(new ProgressionGatePipe(RtsFeature.AREA_DESTROY))
            .pipe(new SessionValidatePipe())
            .pipe(new SessionDimensionPipe())
            .pipe(new StopPreviousPipe())
            .pipe(new WorkflowStartPipe(RtsWorkflowType.AREA_DESTROY))
            .pipe(new ToolBorrowPipe())
            .pipe(new AreaDestroyComputePipe())
            .pipe(new ToolReturnPipe())
            .tickable(new UltimineTickPipe())
            .pipe(new WorkflowProgressPipe())
            .pipe(new NetworkSyncPipe())
            .pipe(new UiRefreshPipe())
            .pipe(new WorkflowCompletePipe())
            .pipe(new HistoryRecordPipe())
            .register();

        PipelineRegistry.<PipelineContext>register(RtsWorkflowType.PLACE_SINGLE)
            .pipe(new SessionValidatePipe())
            .pipe(new WorkflowStartPipe(RtsWorkflowType.PLACE_SINGLE))
            .pipe(new PlacementExecutePipe())
            .pipe(new UiRefreshPipe())
            .pipe(new WorkflowCompletePipe())
            .pipe(new HistoryRecordPipe())
            .asyncCompletion()
            .register();

        PipelineRegistry.<PipelineContext>register(RtsWorkflowType.PLACE_BATCH)
            .pipe(new SessionValidatePipe())
            .pipe(new WorkflowStartPipe(RtsWorkflowType.PLACE_BATCH))
            .pipe(new PlacementExecutePipe())
            .pipe(new PendingPlacementPipe())
            .pipe(new UiRefreshPipe())
            .pipe(new WorkflowCompletePipe())
            .pipe(new HistoryRecordPipe())
            .asyncCompletion()
            .register();

        PipelineRegistry.<PipelineContext>register(RtsWorkflowType.QUICK_BUILD)
            .pipe(new SessionValidatePipe())
            .pipe(new WorkflowStartPipe(RtsWorkflowType.QUICK_BUILD))
            .pipe(new PlacementExecutePipe())
            .pipe(new UiRefreshPipe())
            .pipe(new WorkflowCompletePipe())
            .pipe(new HistoryRecordPipe())
            .asyncCompletion()
            .register();

        PipelineRegistry.<PipelineContext>register(RtsWorkflowType.STOP_MINING)
            .pipe(new SessionValidatePipe())
            .pipe(new StopMiningPipe())
            .register();

        if (!tickRegistered) {
            tickRegistered = true;
            FMLCommonHandler.instance()
                .bus()
                .register(new TickHandler());
        }
    }

    public static final class TickHandler {

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END) TickablePipelineRegistry.tickAll();
        }
    }
}
