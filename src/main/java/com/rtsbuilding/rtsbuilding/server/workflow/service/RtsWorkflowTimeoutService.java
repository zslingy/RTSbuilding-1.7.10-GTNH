package com.rtsbuilding.rtsbuilding.server.workflow.service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowSlotManager;

/**
 * 后台清理长时间无更新的工作流。
 */
public final class RtsWorkflowTimeoutService {

    private final RtsWorkflowEngine engine;
    private final Map<UUID, Map<Integer, RtsWorkflowSlotManager>> playerSlots;
    private volatile boolean running;
    private Thread worker;

    public RtsWorkflowTimeoutService(RtsWorkflowEngine engine,
        Map<UUID, Map<Integer, RtsWorkflowSlotManager>> playerSlots) {
        this.engine = engine;
        this.playerSlots = playerSlots;
    }

    public void start(final Duration checkInterval, final Duration maxIdleTime) {
        if (running) return;
        running = true;
        worker = new Thread(new Runnable() {

            @Override
            public void run() {
                while (running) {
                    try {
                        Thread.sleep(Math.max(1000L, checkInterval.toMillis()));
                        engine.cleanupStaleWorkflows(maxIdleTime);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread()
                            .interrupt();
                        return;
                    } catch (Exception e) {
                        RtsbuildingMod.LOGGER.warn("[Workflow] timeout cleanup failed", e);
                    }
                }
            }
        }, "RTS Workflow Timeout");
        worker.setDaemon(true);
        worker.start();
        RtsbuildingMod.LOGGER.info("[Workflow] Timeout service started, players={}", playerSlots.size());
    }

    public void stop() {
        running = false;
        if (worker != null) worker.interrupt();
        worker = null;
    }
}
