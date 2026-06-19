package com.rtsbuilding.rtsbuilding.server.workflow.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;

public final class RtsWorkflowEntry {

    private static final String NBT_ID = "id";
    private static final String NBT_TYPE = "type";
    private static final String NBT_PRIORITY = "priority";
    private static final String NBT_TOTAL_BLOCKS = "total_blocks";
    private static final String NBT_COMPLETED_BLOCKS = "completed_blocks";
    private static final String NBT_FAILED_BLOCKS = "failed_blocks";
    private static final String NBT_MISSING_ITEMS = "missing_items";
    private static final String NBT_DETAIL = "detail";
    private static final String NBT_SUSPENDED = "suspended";
    private static final String NBT_PAUSED = "paused";
    private static final String NBT_CREATED_AT = "created_at";
    private static final String NBT_LAST_UPDATED_AT = "last_updated_at";

    private final int id;
    private long createdAt;
    private long lastUpdatedAt;
    private RtsWorkflowType type;
    private RtsWorkflowPriority priority = RtsWorkflowPriority.NORMAL;
    private int totalBlocks;
    private int completedBlocks;
    private int failedBlocks;
    private final List<String> missingItems = new ArrayList<String>();
    private String detailMessage = "";
    private boolean suspended;
    private boolean paused;

    public RtsWorkflowEntry(int id) {
        this.id = id;
        this.createdAt = System.currentTimeMillis();
        this.lastUpdatedAt = createdAt;
    }

    public int id() {
        return id;
    }

    public RtsWorkflowType type() {
        return type;
    }

    public RtsWorkflowPriority priority() {
        return priority;
    }

    public int totalBlocks() {
        return totalBlocks;
    }

    public int completedBlocks() {
        return completedBlocks;
    }

    public int failedBlocks() {
        return failedBlocks;
    }

    public List<String> missingItems() {
        return Collections.unmodifiableList(missingItems);
    }

    public String detailMessage() {
        return detailMessage;
    }

    public boolean suspended() {
        return suspended;
    }

    public boolean paused() {
        return paused;
    }

    public long createdAt() {
        return createdAt;
    }

    public long lastUpdatedAt() {
        return lastUpdatedAt;
    }

    public boolean hasActiveWorkflow() {
        return type != null && !suspended && !paused;
    }

    public boolean isOccupied() {
        return type != null;
    }

    public boolean isComplete() {
        return totalBlocks > 0 && completedBlocks + failedBlocks >= totalBlocks;
    }

    public RtsWorkflowStatus snapshot() {
        if (type == null) return RtsWorkflowStatus.idle();
        return RtsWorkflowStatus.fromRaw(
            type,
            priority,
            totalBlocks,
            completedBlocks,
            failedBlocks,
            missingItems,
            detailMessage,
            suspended,
            paused,
            id);
    }

    void setType(RtsWorkflowType type) {
        this.type = type;
        touch();
    }

    public void setPriority(RtsWorkflowPriority priority) {
        this.priority = priority == null ? RtsWorkflowPriority.NORMAL : priority;
        touch();
    }

    void setTotalBlocks(int totalBlocks) {
        this.totalBlocks = Math.max(0, totalBlocks);
        touch();
    }

    void addCompletedBlocks(int delta) {
        completedBlocks = Math.max(0, Math.min(totalBlocks, completedBlocks + Math.max(0, delta)));
        touch();
    }

    void setCompletedBlocks(int value) {
        completedBlocks = Math.max(0, Math.min(totalBlocks, value));
        touch();
    }

    void addFailedBlocks(int delta) {
        failedBlocks = Math.max(0, failedBlocks + Math.max(0, delta));
        touch();
    }

    void addMissingItems(List<String> items) {
        if (items != null) {
            for (String item : items) {
                if (item != null && !item.isEmpty() && !missingItems.contains(item)) missingItems.add(item);
            }
        }
        touch();
    }

    void setDetailMessage(String detailMessage) {
        this.detailMessage = detailMessage == null ? "" : detailMessage;
        touch();
    }

    void setSuspended(boolean suspended) {
        this.suspended = suspended;
        touch();
    }

    void setPaused(boolean paused) {
        this.paused = paused;
        touch();
    }

    void touch() {
        lastUpdatedAt = System.currentTimeMillis();
    }

    public NBTTagCompound toNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(NBT_ID, id);
        if (type != null) tag.setString(NBT_TYPE, type.name());
        tag.setInteger(NBT_PRIORITY, priority.rank());
        tag.setInteger(NBT_TOTAL_BLOCKS, totalBlocks);
        tag.setInteger(NBT_COMPLETED_BLOCKS, completedBlocks);
        tag.setInteger(NBT_FAILED_BLOCKS, failedBlocks);
        NBTTagList items = new NBTTagList();
        for (String item : missingItems) items.appendTag(new NBTTagString(item));
        tag.setTag(NBT_MISSING_ITEMS, items);
        tag.setString(NBT_DETAIL, detailMessage);
        tag.setBoolean(NBT_SUSPENDED, suspended);
        tag.setBoolean(NBT_PAUSED, paused);
        tag.setLong(NBT_CREATED_AT, createdAt);
        tag.setLong(NBT_LAST_UPDATED_AT, lastUpdatedAt);
        return tag;
    }

    public static RtsWorkflowEntry fromNbt(NBTTagCompound tag) {
        RtsWorkflowEntry entry = new RtsWorkflowEntry(tag.getInteger(NBT_ID));
        if (tag.hasKey(NBT_TYPE)) {
            try {
                entry.type = RtsWorkflowType.valueOf(tag.getString(NBT_TYPE));
            } catch (IllegalArgumentException ignored) {}
        }
        int rank = tag.getInteger(NBT_PRIORITY);
        for (RtsWorkflowPriority priority : RtsWorkflowPriority.values()) {
            if (priority.rank() == rank) entry.priority = priority;
        }
        entry.totalBlocks = Math.max(0, tag.getInteger(NBT_TOTAL_BLOCKS));
        entry.completedBlocks = Math.max(0, tag.getInteger(NBT_COMPLETED_BLOCKS));
        entry.failedBlocks = Math.max(0, tag.getInteger(NBT_FAILED_BLOCKS));
        NBTTagList items = tag.getTagList(NBT_MISSING_ITEMS, 8);
        for (int i = 0; i < items.tagCount(); i++) entry.missingItems.add(items.getStringTagAt(i));
        entry.detailMessage = tag.getString(NBT_DETAIL);
        entry.suspended = tag.getBoolean(NBT_SUSPENDED);
        entry.paused = tag.getBoolean(NBT_PAUSED);
        entry.createdAt = tag.hasKey(NBT_CREATED_AT) ? tag.getLong(NBT_CREATED_AT) : System.currentTimeMillis();
        entry.lastUpdatedAt = tag.hasKey(NBT_LAST_UPDATED_AT) ? tag.getLong(NBT_LAST_UPDATED_AT) : entry.createdAt;
        return entry;
    }
}
