package com.rtsbuilding.rtsbuilding.client;

import java.util.ArrayList;
import java.util.List;

/**
 * 合成 ViewModel — 管理客户端合成视图状态。
 * 
 * 从原 ClientRtsController 中拆出，负责：
 * - 可合成物品列表
 * - 合成反馈（成功/失败消息）
 * - 当前选中的合成请求
 */
public class CraftViewModel {

    // ---- 可合成列表 ----
    public final List<CraftableEntry> craftableEntries = new ArrayList<>();

    // ---- 合成搜索 ----
    public String craftSearchQuery = "";
    public boolean craftShowAll = false;
    public int craftScroll = 0;

    // ---- 合成反馈 ----
    public String feedbackMessage = "";
    public long feedbackTimestamp = 0;
    public boolean feedbackSuccess = true;
    public static final long FEEDBACK_DISPLAY_MS = 3000;

    // ---- 数量对话框 ----
    public boolean quantityDialogOpen = false;
    public String quantityDialogItemId = "";
    public int quantityDialogCount = 1;
    public int quantityDialogMax = 64;

    // ---- 同步 ----
    public boolean recipesDirty = true;

    /** 可合成条目 */
    public static class CraftableEntry {

        public String itemId;
        public int meta;
        public String displayName;
        public int craftableCount;
        public List<IngredientSlot> ingredients = new ArrayList<>();

        public CraftableEntry(String itemId, int meta, String displayName, int craftableCount) {
            this.itemId = itemId;
            this.meta = meta;
            this.displayName = displayName;
            this.craftableCount = craftableCount;
        }
    }

    /** 合成材料槽位 */
    public static class IngredientSlot {

        public String itemId;
        public int meta;
        public int required;
        public int available;

        public IngredientSlot(String itemId, int meta, int required, int available) {
            this.itemId = itemId;
            this.meta = meta;
            this.required = required;
            this.available = available;
        }

        public boolean isSatisfied() {
            return available >= required;
        }
    }

    public boolean isFeedbackActive() {
        if (feedbackMessage.isEmpty()) return false;
        return System.currentTimeMillis() - feedbackTimestamp < FEEDBACK_DISPLAY_MS;
    }

    public void showFeedback(String message, boolean success) {
        this.feedbackMessage = message;
        this.feedbackSuccess = success;
        this.feedbackTimestamp = System.currentTimeMillis();
    }

    public void resetFrameState() {
        // 无每帧状态需重置
    }

    public void resetForNewSession() {
        craftableEntries.clear();
        feedbackMessage = "";
        feedbackTimestamp = 0;
        feedbackSuccess = true;
        quantityDialogOpen = false;
        recipesDirty = true;
    }
}
