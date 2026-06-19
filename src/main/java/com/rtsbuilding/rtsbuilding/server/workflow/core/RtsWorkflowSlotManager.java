package com.rtsbuilding.rtsbuilding.server.workflow.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;

public class RtsWorkflowSlotManager {

    public static final int MAX_SLOTS = 8;
    private static final String NBT_NEXT_ID = "next_id";
    private static final String NBT_ENTRIES = "entries";

    private final Object lock = new Object();
    private final List<RtsWorkflowEntry> entries = new ArrayList<RtsWorkflowEntry>(MAX_SLOTS);
    private final Map<Integer, RtsWorkflowEntry> entryIndex = new HashMap<Integer, RtsWorkflowEntry>();
    private int nextId;

    public boolean isFull() {
        synchronized (lock) {
            return entries.size() >= MAX_SLOTS;
        }
    }

    public int occupiedCount() {
        synchronized (lock) {
            return entries.size();
        }
    }

    public int activeCount() {
        synchronized (lock) {
            int count = 0;
            for (RtsWorkflowEntry entry : entries) if (entry.hasActiveWorkflow()) count++;
            return count;
        }
    }

    public int size() {
        synchronized (lock) {
            return entries.size();
        }
    }

    public RtsWorkflowEntry addEntry(RtsWorkflowPriority priority) {
        synchronized (lock) {
            if (isFull()) return null;
            RtsWorkflowEntry entry = new RtsWorkflowEntry(nextId++);
            entry.setPriority(priority);
            int insertIndex = entries.size();
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i)
                    .priority()
                    .rank()
                    < entry.priority()
                        .rank()) {
                    insertIndex = i;
                    break;
                }
            }
            entries.add(insertIndex, entry);
            entryIndex.put(entry.id(), entry);
            return entry;
        }
    }

    public boolean removeEntryById(int entryId) {
        synchronized (lock) {
            RtsWorkflowEntry entry = entryIndex.remove(entryId);
            if (entry == null) return false;
            entries.remove(entry);
            return true;
        }
    }

    public RtsWorkflowEntry findEntryById(int entryId) {
        synchronized (lock) {
            return entryIndex.get(entryId);
        }
    }

    public RtsWorkflowEntry lastActive() {
        synchronized (lock) {
            for (int i = entries.size() - 1; i >= 0; i--) if (entries.get(i)
                .hasActiveWorkflow()) return entries.get(i);
            return null;
        }
    }

    public boolean hasActiveWorkflow() {
        return activeCount() > 0;
    }

    public List<RtsWorkflowEntry> occupiedEntries() {
        synchronized (lock) {
            return new ArrayList<RtsWorkflowEntry>(entries);
        }
    }

    public void clear() {
        synchronized (lock) {
            entries.clear();
            entryIndex.clear();
        }
    }

    public List<Integer> removeStaleEntries(long maxIdleMillis) {
        synchronized (lock) {
            List<Integer> removed = new ArrayList<Integer>();
            long now = System.currentTimeMillis();
            Iterator<RtsWorkflowEntry> it = entries.iterator();
            while (it.hasNext()) {
                RtsWorkflowEntry entry = it.next();
                if (now - entry.lastUpdatedAt() > maxIdleMillis) {
                    removed.add(entry.id());
                    entryIndex.remove(entry.id());
                    it.remove();
                }
            }
            return removed;
        }
    }

    public NBTTagCompound saveToNbt() {
        synchronized (lock) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger(NBT_NEXT_ID, nextId);
            NBTTagList list = new NBTTagList();
            for (RtsWorkflowEntry entry : entries) list.appendTag(entry.toNbt());
            tag.setTag(NBT_ENTRIES, list);
            return tag;
        }
    }

    public static RtsWorkflowSlotManager loadFromNbt(NBTTagCompound tag) {
        RtsWorkflowSlotManager manager = new RtsWorkflowSlotManager();
        manager.nextId = tag.getInteger(NBT_NEXT_ID);
        NBTTagList list = tag.getTagList(NBT_ENTRIES, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            RtsWorkflowEntry entry = RtsWorkflowEntry.fromNbt(list.getCompoundTagAt(i));
            if (entry.isOccupied()) {
                manager.entries.add(entry);
                manager.entryIndex.put(entry.id(), entry);
            }
        }
        return manager;
    }
}
