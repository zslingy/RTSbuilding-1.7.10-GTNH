package com.rtsbuilding.rtsbuilding.client;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 进度 ViewModel — 管理客户端进度/技能树状态。
 * 
 * 从原 ClientRtsController 中拆出，负责：
 * - 已解锁节点映射
 * - 当前选择的节点
 * - 家园锚点位置
 */
public class ProgressionViewModel {

    // ---- 解锁状态 ----
    /** 节点路径 → 是否已解锁 */
    public final Map<String, Boolean> unlockedNodes = new LinkedHashMap<>();

    /** 当前选中的节点（用于技能树界面） */
    public String selectedNodePath = "";

    // ---- 家园锚点 ----
    public int homeX, homeY, homeZ;
    public boolean homeSet = false;

    // ---- 搜索 ----
    public String searchFilter = "";

    // ---- 同步 ----
    public boolean stateDirty = true;

    public boolean isNodeUnlocked(String nodePath) {
        Boolean unlocked = unlockedNodes.get(nodePath);
        return unlocked != null && unlocked;
    }

    public int getUnlockedCount() {
        int count = 0;
        for (Boolean v : unlockedNodes.values()) {
            if (v) count++;
        }
        return count;
    }

    public void resetFrameState() {
        // 无每帧状态需重置
    }

    public void resetForNewSession() {
        unlockedNodes.clear();
        selectedNodePath = "";
        homeSet = false;
        searchFilter = "";
        stateDirty = true;
    }
}
