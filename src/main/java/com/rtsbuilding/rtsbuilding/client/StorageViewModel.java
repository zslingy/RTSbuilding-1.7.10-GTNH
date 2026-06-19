package com.rtsbuilding.rtsbuilding.client;

import java.util.ArrayList;
import java.util.List;

import com.rtsbuilding.rtsbuilding.util.BlockPos;

/**
 * 存储 ViewModel — 管理客户端存储页面状态。
 * 
 * 从原 ClientRtsController 中拆出，负责：
 * - 存储物品条目列表
 * - 本地拼音搜索（客户端 PinIn 反射 + 降级子串）
 * - 排序/分类/翻页
 * - 当前 hover 的条目
 */
public class StorageViewModel {

    // ---- 存储条目 ----
    /** 当前存储页的所有物品条目（精简视图） */
    public final List<StorageEntry> entries = new ArrayList<>();

    /** 搜索过滤后的条目（searchActive=true 时有效，客户端本地过滤） */
    public final List<StorageEntry> filteredEntries = new ArrayList<>();

    /** 当前页码（0-based） */
    public int currentPage = 0;

    /** 总页数 */
    public int totalPages = 1;

    /** 每页条目数 */
    public int entriesPerPage = 40; // 20×2 网格

    // ---- 搜索 ----
    public String searchQuery = "";
    public boolean searchActive = false;

    // ---- 排序 ----
    public String sortMode = "name_asc"; // name_asc, name_desc, count_asc, count_desc

    // ---- 面板高度（可调） ----
    public static final int MIN_PANEL_H = 36;
    public static final int DEFAULT_PANEL_H = 55;
    public static final int MAX_PANEL_H = 160;
    public int panelHeight = DEFAULT_PANEL_H;

    // ---- 分类 ----
    public String activeCategory = "all";

    // ---- Hover ----
    public int hoveredIndex = -1; // -1 = 无 hover

    // ---- 连接状态 ----
    public int linkedStorageCount = 0;

    /** 已链接存储条目列表（对齐原版 LinkedStorageEntry，支持多容器） */
    public final List<LinkedStorageEntry> linkedEntries = new ArrayList<>();

    /** 已链接存储方块的世界坐标列表（用于高亮渲染，从 linkedEntries 派生） */
    public final List<BlockPos> linkedStoragePositions = new ArrayList<>();

    // ---- 阶段7: ItemStack resolve 缓存 ----
    private final java.util.Map<String, net.minecraft.item.ItemStack> resolveCache = new java.util.HashMap<>();

    // ---- 同步标记 ----
    public boolean dirty = true; // true = 需要从服务端请求新数据

    /** 精简存储条目（仅包含客户端渲染所需信息） */
    public static class StorageEntry {

        public String itemId; // "minecraft:stone"
        public int meta;
        public long count;
        public String displayName;
        public boolean craftable;
        /** 直接存储 ItemStack，避免 itemId → ItemStack 往返转换失败 */
        public net.minecraft.item.ItemStack stack;

        public StorageEntry(String itemId, int meta, long count, String displayName, boolean craftable) {
            this.itemId = itemId;
            this.meta = meta;
            this.count = count;
            this.displayName = displayName;
            this.craftable = craftable;
            this.stack = null;
        }

        public StorageEntry(net.minecraft.item.ItemStack stack, long count, String displayName, boolean craftable) {
            this.stack = stack != null ? stack.copy() : null;
            this.itemId = stack != null ? (String) cpw.mods.fml.common.registry.GameData.getItemRegistry()
                .getNameForObject(stack.getItem()) : "unknown";
            this.meta = stack != null ? stack.getItemDamage() : 0;
            this.count = count;
            this.displayName = displayName;
            this.craftable = craftable;
        }
    }

    /** 已链接存储条目（对齐原版 LinkedStorageEntry） */
    public static class LinkedStorageEntry {

        public final int dimension;
        public final int x;
        public final int y;
        public final int z;
        public final byte mode; // 0=NORMAL, 1=EXTRACT_ONLY
        public final int priority;

        public LinkedStorageEntry(int dimension, int x, int y, int z, byte mode, int priority) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.mode = mode;
            this.priority = priority;
        }
    }

    /**
     * 返回当前应显示的条目列表。
     * 搜索激活时返回本地过滤结果，否则返回全部条目。
     */
    public List<StorageEntry> getDisplayEntries() {
        List<StorageEntry> source;
        if (searchActive && !searchQuery.isEmpty()) {
            source = filteredEntries;
        } else {
            source = entries;
        }
        if (activeCategory == null || "all".equals(activeCategory)) {
            return source;
        }
        List<StorageEntry> result = new ArrayList<>();
        for (StorageEntry e : source) {
            if (matchesCategory(e, activeCategory)) {
                result.add(e);
            }
        }
        return result;
    }

    public int getEntriesOnCurrentPage() {
        int start = currentPage * entriesPerPage;
        int remaining = entries.size() - start;
        return Math.min(remaining, entriesPerPage);
    }

    public StorageEntry getEntry(int index) {
        if (index < 0 || index >= entries.size()) return null;
        return entries.get(index);
    }

    /**
     * 客户端本地拼音搜索。
     * 
     * 使用 RtsPinyinSearch（PinIn 反射兼容 NEC，降级为子串匹配）。
     * 服务端不做拼音搜索——客户端持有全部条目后本地过滤。
     * 这意味着拼音搜索能力完全取决于客户端是否安装了 NEC。
     */
    public void applySearch(String query) {
        this.searchQuery = query;
        if (query == null || query.trim()
            .isEmpty()) {
            clearSearch();
            return;
        }
        this.searchActive = true;
        filteredEntries.clear();
        String lower = query.toLowerCase()
            .trim();
        for (StorageEntry e : entries) {
            if (com.rtsbuilding.rtsbuilding.util.RtsPinyinSearch.matches(e.itemId, lower)) {
                filteredEntries.add(e);
            }
        }
    }

    public void clearSearch() {
        searchQuery = "";
        searchActive = false;
        filteredEntries.clear();
    }

    public void resetFrameState() {
        hoveredIndex = -1;
    }

    public int lastDirtyStorageId;
    public byte lastDirtyFlags;

    public void markDirty(int storageId, byte flags) {
        lastDirtyStorageId = storageId;
        lastDirtyFlags = flags;
    }

    public void resetForNewSession() {
        entries.clear();
        filteredEntries.clear();
        resolveCache.clear();
        linkedEntries.clear();
        linkedStoragePositions.clear();
        currentPage = 0;
        totalPages = 1;
        searchQuery = "";
        searchActive = false;
        sortMode = "name_asc";
        panelHeight = DEFAULT_PANEL_H;
        activeCategory = "all";
        hoveredIndex = -1;
        linkedStorageCount = 0;
        dirty = true;
    }

    // ---- 阶段7: ItemStack resolve 缓存 ----

    /**
     * 解析 itemId → ItemStack，带缓存。
     * 每帧每个条目的 GameData 查找被替换为 O(1) HashMap 命中。
     */
    public net.minecraft.item.ItemStack resolveStack(String itemId, int meta) {
        String key = itemId + "@" + meta;
        net.minecraft.item.ItemStack cached = resolveCache.get(key);
        if (cached != null) return cached.copy();
        net.minecraft.item.Item item = (net.minecraft.item.Item) cpw.mods.fml.common.registry.GameData.getItemRegistry()
            .getObject(new net.minecraft.util.ResourceLocation(itemId));
        if (item == null) {
            resolveCache.put(key, null);
            return null;
        }
        net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(item, 1, meta);
        resolveCache.put(key, stack);
        return stack.copy();
    }

    public void clearResolveCache() {
        resolveCache.clear();
    }

    /**
     * 根据 itemId 判断物品是否属于指定分类。
     * 优先使用 entry.stack 直接获取 Item，避免注册表查找失败。
     */
    private static boolean matchesCategory(StorageEntry entry, String category) {
        if (entry == null) return false;
        try {
            net.minecraft.item.Item item = null;
            if (entry.stack != null && entry.stack.getItem() != null) {
                item = entry.stack.getItem();
            } else if (entry.itemId != null) {
                item = (net.minecraft.item.Item) cpw.mods.fml.common.registry.GameData.getItemRegistry()
                    .getObject(new net.minecraft.util.ResourceLocation(entry.itemId));
            }
            if (item == null) return false;
            switch (category) {
                case "blocks":
                    return item instanceof net.minecraft.item.ItemBlock;
                case "tools":
                    return item instanceof net.minecraft.item.ItemTool || item instanceof net.minecraft.item.ItemHoe;
                case "weapons":
                    return item instanceof net.minecraft.item.ItemSword || item instanceof net.minecraft.item.ItemBow;
                case "armor":
                    return item instanceof net.minecraft.item.ItemArmor;
                case "food":
                    return item instanceof net.minecraft.item.ItemFood;
                default:
                    return true;
            }
        } catch (Exception ignored) {
            return category.equals("all");
        }
    }
}
