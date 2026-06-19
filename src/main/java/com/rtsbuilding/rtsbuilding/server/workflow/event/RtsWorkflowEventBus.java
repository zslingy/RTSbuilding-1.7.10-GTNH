package com.rtsbuilding.rtsbuilding.server.workflow.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

/**
 * 轻量同步事件总线，替代 1.21 NeoForge EventBus 用法。
 */
public final class RtsWorkflowEventBus {

    private final List<RtsWorkflowEventListener> listeners = new CopyOnWriteArrayList<RtsWorkflowEventListener>();

    public void addListener(RtsWorkflowEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(RtsWorkflowEventListener listener) {
        listeners.remove(listener);
    }

    public void fire(WorkflowEvent event) {
        if (event == null) return;
        for (RtsWorkflowEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                RtsbuildingMod.LOGGER.error(
                    "[WorkflowEventBus] listener {} failed on {}",
                    listener.getClass()
                        .getSimpleName(),
                    event.type(),
                    e);
            }
        }
    }

    public int listenerCount() {
        return listeners.size();
    }
}
