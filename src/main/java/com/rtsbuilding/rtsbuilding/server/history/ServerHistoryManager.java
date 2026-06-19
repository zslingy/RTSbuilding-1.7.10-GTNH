package com.rtsbuilding.rtsbuilding.server.history;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

public final class ServerHistoryManager {

    private static final ServerHistoryManager INSTANCE = new ServerHistoryManager();
    private static final int MAX_HISTORY = 100;

    private final Map<UUID, Deque<HistoryEntry>> historyMap = new HashMap<UUID, Deque<HistoryEntry>>();
    private final Map<UUID, Deque<HistoryEntry>> redoMap = new HashMap<UUID, Deque<HistoryEntry>>();
    private final HistoryExecutor executor = new HistoryExecutor(this);

    private ServerHistoryManager() {}

    public static ServerHistoryManager getInstance() {
        return INSTANCE;
    }

    public HistoryExecutor executor() {
        return executor;
    }

    public void record(EntityPlayerMP player, HistoryEntry entry) {
        if (player == null || entry == null) return;
        UUID id = player.getUniqueID();
        Deque<HistoryEntry> deque = historyMap.get(id);
        if (deque == null) {
            deque = new ArrayDeque<HistoryEntry>();
            historyMap.put(id, deque);
        }
        if (deque.size() >= MAX_HISTORY) {
            deque.pollFirst();
        }
        deque.addLast(entry);
        redoMap.remove(id);
    }

    HistoryEntry popHistory(EntityPlayerMP player) {
        if (player == null) return null;
        Deque<HistoryEntry> deque = historyMap.get(player.getUniqueID());
        return (deque != null && !deque.isEmpty()) ? deque.pollLast() : null;
    }

    void pushHistory(EntityPlayerMP player, HistoryEntry entry) {
        if (player == null || entry == null) return;
        Deque<HistoryEntry> deque = historyMap.get(player.getUniqueID());
        if (deque == null) {
            deque = new ArrayDeque<HistoryEntry>();
            historyMap.put(player.getUniqueID(), deque);
        }
        deque.addLast(entry);
    }

    HistoryEntry popRedo(EntityPlayerMP player) {
        if (player == null) return null;
        Deque<HistoryEntry> deque = redoMap.get(player.getUniqueID());
        return (deque != null && !deque.isEmpty()) ? deque.pollLast() : null;
    }

    void pushRedo(EntityPlayerMP player, HistoryEntry entry) {
        if (player == null || entry == null) return;
        Deque<HistoryEntry> deque = redoMap.get(player.getUniqueID());
        if (deque == null) {
            deque = new ArrayDeque<HistoryEntry>();
            redoMap.put(player.getUniqueID(), deque);
        }
        deque.addLast(entry);
    }

    public int historyCount(EntityPlayerMP player) {
        if (player == null) return 0;
        Deque<HistoryEntry> d = historyMap.get(player.getUniqueID());
        return d != null ? d.size() : 0;
    }

    public int redoCount(EntityPlayerMP player) {
        if (player == null) return 0;
        Deque<HistoryEntry> d = redoMap.get(player.getUniqueID());
        return d != null ? d.size() : 0;
    }

    public void clear(EntityPlayerMP player) {
        if (player == null) return;
        historyMap.remove(player.getUniqueID());
        redoMap.remove(player.getUniqueID());
    }
}
