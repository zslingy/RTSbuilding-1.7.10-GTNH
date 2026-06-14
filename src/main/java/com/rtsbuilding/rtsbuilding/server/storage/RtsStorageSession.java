package com.rtsbuilding.rtsbuilding.server.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.ForgeDirection;

import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat.Ae2StorageEntry;

import cpw.mods.fml.common.registry.GameData;

/**
 * RTS 存储会话 — 管理单个玩家的链接存储容器数据。
 * 阶段5扩展：支持分页查询、搜索、排序、分类过滤。
 * 阶段6扩展：支持 AE2 ME 网络存储接入。
 */
public class RtsStorageSession {

    private final Map<String, Long> itemCounts = new HashMap<>();

    /** 存储条目的完整列表 (itemId@meta → count) */
    private final Map<String, StorageEntry> entries = new HashMap<>();

    // === 阶段6: AE2 绑定坐标（-1 表示未绑定） ===
    private int linkedX = -1;
    private int linkedY = -1;
    private int linkedZ = -1;
    private int linkedDimId = 0;

    // Bug3修复：存储链接模式（0=NORMAL/可存可取, 1=EXTRACT_ONLY/仅提取）
    private byte linkMode = 0;

    // Bug1.2修复：自动存入 mined drops
    private static final String NBT_AUTO_STORE = "autoStoreMinedDrops";
    public boolean autoStoreMinedDrops = true;

    public boolean isAutoStoreMinedDrops() {
        return autoStoreMinedDrops;
    }

    public void setAutoStoreMinedDrops(boolean value) {
        this.autoStoreMinedDrops = value;
    }

    public void writeToNBT(NBTTagCompound root) {
        root.setBoolean(NBT_AUTO_STORE, autoStoreMinedDrops);
    }

    public void readFromNBT(NBTTagCompound root) {
        autoStoreMinedDrops = root.getBoolean(NBT_AUTO_STORE);
    }

    // === 阶段6后续: 普通容器绑定坐标（-1 表示未绑定） ===
    private int containerX = -1;
    private int containerY = -1;
    private int containerZ = -1;
    private int containerDimId = 0;
    private boolean containerLinked = false;

    private boolean funnelActive = false;
    private int funnelTargetSlot = -1;
    /** Bug5修复：漏斗目标世界坐标（鼠标指向位置） */
    private double funnelTargetX, funnelTargetY, funnelTargetZ;
    private boolean funnelHasPosition = false;

    private final java.util.Map<Integer, net.minecraft.item.ItemStack> quickSlots = new java.util.HashMap<>();

    public RtsStorageSession() {}

    // ======== 基本操作 ========

    public long getCount(String itemId) {
        Long c = itemCounts.get(itemId);
        return c == null ? 0L : c;
    }

    public void addItem(String itemId, int meta, long amount) {
        String key = itemId + "@" + meta;
        long current = getCount(itemId);
        itemCounts.put(itemId, current + amount);
        StorageEntry entry = entries.get(key);
        if (entry == null) {
            entry = new StorageEntry(itemId, meta, amount);
            entries.put(key, entry);
        } else {
            entry.count += amount;
        }
    }

    /**
     * 尝试从存储会话缓存中消耗指定物品。
     * 仅更新内存缓存，不执行 AE2/容器物理提取（物理提取在 RtsStorageManager.tryConsumeBlock 中处理）。
     *
     * @param itemId 带命名空间前缀的物品 ID（如 "minecraft:stone"）
     * @return true 如果物品存在且数量足够
     */
    public boolean tryConsume(String itemId, int meta, long amount) {
        if (itemId == null || amount <= 0) return false;

        String key = itemId + "@" + meta;
        StorageEntry entry = entries.get(key);
        if (entry == null || entry.count < amount) return false;

        entry.count -= amount;
        if (entry.count <= 0) {
            entries.remove(key);
        }

        // 同步 itemCounts（无 meta 汇总）
        Long current = itemCounts.get(itemId);
        if (current != null) {
            long newCount = current - amount;
            if (newCount <= 0) {
                itemCounts.remove(itemId);
            } else {
                itemCounts.put(itemId, newCount);
            }
        }
        return true;
    }

    public void clear() {
        itemCounts.clear();
        entries.clear();
    }

    public Map<String, Long> getItemCounts() {
        return itemCounts;
    }

    // ======== 阶段6: AE2 ME 网络存储接入 ========

    /** 阶段7: AE2 查询 TTL 缓存（2 秒） */
    private long lastAe2QueryTime = 0;
    private static final long AE2_QUERY_TTL_MS = 2000;

    /** AE2 是否已绑定到玩家的某个 ME 网络方块 */
    public boolean isAe2Linked() {
        return linkedX >= 0 && linkedY >= 0 && linkedZ >= 0;
    }

    /** 设置 AE2 绑定坐标 */
    public void setAe2Link(int x, int y, int z, int dimId) {
        this.linkedX = x;
        this.linkedY = y;
        this.linkedZ = z;
        this.linkedDimId = dimId;
    }

    /** 清除 AE2 绑定 */
    public void clearAe2Link() {
        this.linkedX = -1;
        this.linkedY = -1;
        this.linkedZ = -1;
        this.linkedDimId = 0;
        this.linkMode = 0;
    }

    public int getLinkedX() {
        return linkedX;
    }

    public int getLinkedY() {
        return linkedY;
    }

    public int getLinkedZ() {
        return linkedZ;
    }

    public int getLinkedDimId() {
        return linkedDimId;
    }

    // Bug3修复：链接模式 getter/setter
    public byte getLinkMode() {
        return linkMode;
    }

    public void setLinkMode(byte mode) {
        this.linkMode = mode;
    }

    /**
     * 阶段7: 带 TTL 缓存的 AE2 填充。
     * 2 秒内重复请求直接返回缓存结果，避免反复遍历 ME 网络。
     */
    public boolean populateFromAe2Cached(TileEntity te, ForgeDirection side) {
        long now = System.currentTimeMillis();
        if (now - lastAe2QueryTime < AE2_QUERY_TTL_MS && !entries.isEmpty()) {
            return true;
        }
        boolean ok = populateFromAe2(te, side);
        if (ok) {
            lastAe2QueryTime = now;
        }
        return ok;
    }

    /** 强制清除 TTL 缓存（合成/存储变更时调用） */
    public void invalidateAe2Cache() {
        lastAe2QueryTime = 0;
    }

    /**
     * 从 AE2 ME 网络填充存储条目（替换现有数据）。
     *
     * @param te   AE2 TileEntity（ME Interface / ME Drive / ME Chest）
     * @param side 访问方向
     * @return 是否成功获取到 AE2 数据（false 表示 AE2 不可用或无连接）
     */
    public boolean populateFromAe2(TileEntity te, ForgeDirection side) {
        if (!RtsAe2Compat.isAvailable() || te == null) return false;
        if (!RtsAe2Compat.tryConnectAe2(te, side)) return false;

        clear();

        List<Ae2StorageEntry> ae2Items = RtsAe2Compat.queryAllItems(te, side);
        for (Ae2StorageEntry ae : ae2Items) {
            addItem(ae.itemId, ae.meta, ae.count);
        }

        return !ae2Items.isEmpty();
    }

    // ======== 阶段6后续: 普通容器链接 ========

    /** 普通容器是否已绑定 */
    public boolean isContainerLinked() {
        return containerLinked && containerX >= 0;
    }

    /** 是否链接了任何存储（AE2 或容器） */
    public boolean isAnyLinked() {
        return isAe2Linked() || isContainerLinked();
    }

    /** 设置容器绑定坐标 */
    public void setContainerLink(int x, int y, int z, int dimId) {
        this.containerX = x;
        this.containerY = y;
        this.containerZ = z;
        this.containerDimId = dimId;
        this.containerLinked = true;
    }

    /** 清除容器绑定 */
    public void clearContainerLink() {
        this.containerX = -1;
        this.containerY = -1;
        this.containerZ = -1;
        this.containerDimId = 0;
        this.containerLinked = false;
        this.linkMode = 0;
    }

    public int getContainerX() {
        return containerX;
    }

    public int getContainerY() {
        return containerY;
    }

    public int getContainerZ() {
        return containerZ;
    }

    public int getContainerDimId() {
        return containerDimId;
    }

    // ======== Bug7修复: 漏斗模式 ========

    public boolean isFunnelActive() {
        return funnelActive;
    }

    public void setFunnelActive(boolean active) {
        this.funnelActive = active;
    }

    public int getFunnelTargetSlot() {
        return funnelTargetSlot;
    }

    public void setFunnelTargetSlot(int slot) {
        this.funnelTargetSlot = slot;
    }

    public void setFunnelTargetPos(double x, double y, double z) {
        this.funnelTargetX = x;
        this.funnelTargetY = y;
        this.funnelTargetZ = z;
        this.funnelHasPosition = true;
    }

    public boolean hasFunnelTargetPos() {
        return funnelHasPosition;
    }

    public double getFunnelTargetX() {
        return funnelTargetX;
    }

    public double getFunnelTargetY() {
        return funnelTargetY;
    }

    public double getFunnelTargetZ() {
        return funnelTargetZ;
    }

    // ======== Bug9修复: 快捷存储槽（Pin 槽） ========

    public static final int QUICK_SLOT_COUNT = 9;

    public net.minecraft.item.ItemStack getQuickSlot(int index) {
        return quickSlots.get(index);
    }

    public void setQuickSlot(int index, net.minecraft.item.ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            quickSlots.remove(index);
        } else {
            quickSlots.put(index, stack.copy());
        }
    }

    public java.util.Map<Integer, net.minecraft.item.ItemStack> getQuickSlots() {
        return quickSlots;
    }

    public void clearQuickSlots() {
        quickSlots.clear();
    }

    /** 扫描 IInventory 容器并将内容添加到存储会话 */
    public void scanContainerInventory(IInventory inv) {
        if (inv == null) return;
        int size = inv.getSizeInventory();
        for (int i = 0; i < size && i < 256; i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack == null || stack.getItem() == null) continue;
            String itemId = (String) GameData.getItemRegistry()
                .getNameForObject(stack.getItem());
            if (itemId == null) continue;
            addItem(itemId, stack.getItemDamage(), stack.stackSize);
        }
    }

    /** 刷新容器数据：清除旧条目后重新扫描 */
    public boolean refreshFromContainer(IInventory inv) {
        if (inv == null) return false;
        clear();
        scanContainerInventory(inv);
        return true;
    }

    /**
     * 刷新 AE2 数据：强制清除旧数据后重新查询。
     * 与 populateFromAe2 不同，此方法始终清除旧条目。
     */
    public boolean refreshFromAe2(TileEntity te, ForgeDirection side) {
        clear();
        return populateFromAe2(te, side);
    }

    // ======== 模拟数据（阶段5联调用） ========

    /**
     * 为开发联调填充模拟存储数据。
     * 阶段6：仅在没有 AE2 绑定且尚未填充数据时使用。
     */
    public void populateDebugData() {
        if (!entries.isEmpty()) return; // 已有数据时不覆盖

        addItem("minecraft:stone", 0, 1024);
        addItem("minecraft:dirt", 0, 512);
        addItem("minecraft:cobblestone", 0, 800);
        addItem("minecraft:oak_log", 0, 256);
        addItem("minecraft:oak_planks", 0, 640);
        addItem("minecraft:glass", 0, 128);
        addItem("minecraft:sand", 0, 384);
        addItem("minecraft:gravel", 0, 200);
        addItem("minecraft:iron_ingot", 0, 64);
        addItem("minecraft:gold_ingot", 0, 32);
        addItem("minecraft:diamond", 0, 16);
        addItem("minecraft:coal", 0, 256);
        addItem("minecraft:redstone", 0, 128);
        addItem("minecraft:stick", 0, 512);
        addItem("minecraft:string", 0, 96);
        addItem("minecraft:arrow", 0, 128);
        addItem("minecraft:bow", 0, 4);
        addItem("minecraft:iron_sword", 0, 2);
        addItem("minecraft:iron_pickaxe", 0, 3);
        addItem("minecraft:iron_axe", 0, 2);
        addItem("minecraft:iron_shovel", 0, 2);
        addItem("minecraft:iron_helmet", 0, 1);
        addItem("minecraft:iron_chestplate", 0, 1);
        addItem("minecraft:iron_leggings", 0, 1);
        addItem("minecraft:iron_boots", 0, 1);
        addItem("minecraft:bread", 0, 64);
        addItem("minecraft:cooked_beef", 0, 32);
        addItem("minecraft:apple", 0, 48);
        addItem("minecraft:torch", 0, 256);
        addItem("minecraft:crafting_table", 0, 8);
        addItem("minecraft:furnace", 0, 4);
        addItem("minecraft:chest", 0, 12);
        addItem("minecraft:ender_pearl", 0, 16);
        addItem("minecraft:obsidian", 0, 64);
        addItem("minecraft:lapis_lazuli", 0, 128);
        addItem("minecraft:dye", 4, 128);
        addItem("minecraft:bucket", 0, 8);
        addItem("minecraft:water_bucket", 0, 4);
        addItem("minecraft:lava_bucket", 0, 2);
    }

    // ======== 分页查询 ========

    /**
     * 获取排序、过滤、分页后的存储条目列表。
     *
     * @param sortMode    "name_asc" | "name_desc" | "count_asc" | "count_desc"
     * @param category    "all" | "blocks" | "items" | "tools" | "weapons" | "armor" | "food" | "redstone" | "fluids"
     * @param searchQuery 搜索文本（空=全部），支持拼音
     * @param page        页码 (0-based)
     * @param pageSize    每页条目数
     * @return 分页结果
     */
    public PageResult queryPage(String sortMode, String category, String searchQuery, int page, int pageSize) {
        // 1. 收集所有条目
        List<StorageEntry> all = new ArrayList<>(entries.values());

        // 2. 分类过滤
        if (category != null && !category.isEmpty() && !"all".equals(category)) {
            all = filterByCategory(all, category);
        }

        // 3. 搜索过滤
        if (searchQuery != null && !searchQuery.isEmpty()) {
            all = filterBySearch(all, searchQuery);
        }

        // 4. 排序
        sortEntries(all, sortMode);

        // 5. 分页
        int totalPages = Math.max(1, (int) Math.ceil((double) all.size() / pageSize));
        int clampedPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = clampedPage * pageSize;
        int end = Math.min(start + pageSize, all.size());

        List<StorageEntry> pageItems = new ArrayList<>();
        for (int i = start; i < end; i++) {
            pageItems.add(all.get(i));
        }

        return new PageResult(pageItems, clampedPage, totalPages, all.size());
    }

    public PageResult queryPage(String sortMode, int page, int pageSize) {
        return queryPage(sortMode, "all", "", page, pageSize);
    }

    // ======== 转换为 ItemStack 列表（用于网络传输） ========

    public List<ItemStack> toItemStacks(List<StorageEntry> entryList) {
        List<ItemStack> stacks = new ArrayList<>();
        for (StorageEntry e : entryList) {
            ItemStack stack = resolveStack(e.itemId, e.meta);
            if (stack != null) {
                stack.stackSize = (int) Math.min(e.count, Integer.MAX_VALUE);
                stacks.add(stack);
            }
        }
        return stacks;
    }

    private ItemStack resolveStack(String itemId, int meta) {
        try {
            Item item = (Item) GameData.getItemRegistry()
                .getObject(new ResourceLocation(itemId));
            if (item != null) return new ItemStack(item, 1, meta);
        } catch (Exception ignored) {}
        return null;
    }

    // ======== 分类过滤 ========

    private List<StorageEntry> filterByCategory(List<StorageEntry> entries, String category) {
        List<StorageEntry> result = new ArrayList<>();
        for (StorageEntry e : entries) {
            if (matchesCategory(e.itemId, category)) {
                result.add(e);
            }
        }
        return result;
    }

    private boolean matchesCategory(String itemId, String category) {
        switch (category) {
            case "blocks":
                return isBlock(itemId);
            case "items":
                return isGeneralItem(itemId);
            case "tools":
                return isTool(itemId);
            case "weapons":
                return isWeapon(itemId);
            case "armor":
                return isArmor(itemId);
            case "food":
                return isFood(itemId);
            case "redstone":
                return isRedstone(itemId);
            case "fluids":
                return isFluid(itemId);
            default:
                return true;
        }
    }

    private boolean isBlock(String id) {
        return id.contains("_log") || id.contains("_planks")
            || id.contains("stone")
            || id.contains("dirt")
            || id.contains("sand")
            || id.contains("gravel")
            || id.contains("glass")
            || id.contains("obsidian")
            || id.contains("cobblestone")
            || id.contains("crafting_table")
            || id.contains("furnace")
            || id.contains("chest")
            || id.contains("torch");
    }

    private boolean isTool(String id) {
        return id.contains("pickaxe") || id.contains("axe")
            || id.contains("shovel")
            || id.contains("hoe")
            || id.contains("bucket");
    }

    private boolean isWeapon(String id) {
        return id.contains("sword") || id.contains("bow") || id.contains("arrow");
    }

    private boolean isArmor(String id) {
        return id.contains("helmet") || id.contains("chestplate") || id.contains("leggings") || id.contains("boots");
    }

    private boolean isFood(String id) {
        return id.contains("bread") || id.contains(
            "beef") || id.contains("apple") || id.contains("pork") || id.contains("chicken") || id.contains("fish");
    }

    private boolean isRedstone(String id) {
        return id.contains("redstone") || id.contains("repeater")
            || id.contains("comparator")
            || id.contains("piston")
            || id.contains("lever")
            || id.contains("button");
    }

    private boolean isFluid(String id) {
        return id.contains("water_bucket") || id.contains("lava_bucket") || id.contains("bucket");
    }

    private boolean isGeneralItem(String id) {
        return !isBlock(id) && !isTool(id)
            && !isWeapon(id)
            && !isArmor(id)
            && !isFood(id)
            && !isRedstone(id)
            && !isFluid(id);
    }

    // ======== 搜索过滤 ========

    private List<StorageEntry> filterBySearch(List<StorageEntry> entries, String query) {
        String lower = query.toLowerCase()
            .trim();
        List<StorageEntry> result = new ArrayList<>();
        for (StorageEntry e : entries) {
            if (matchesSearch(e.itemId, lower)) {
                result.add(e);
            }
        }
        return result;
    }

    private boolean matchesSearch(String itemId, String lowerQuery) {
        // 阶段5：服务端仅做子串匹配。拼音搜索由客户端 RtsPinyinSearch 在本地完成。
        // 客户端安装 NEC（PinIn）→ 拼音可用；客户端未安装 → 降级为子串匹配。
        // 服务端始终无需 PinIn 依赖。
        if (itemId == null || lowerQuery == null) return false;
        return itemId.toLowerCase(java.util.Locale.ROOT)
            .contains(lowerQuery);
    }

    // ======== 排序 ========

    private void sortEntries(List<StorageEntry> entries, String sortMode) {
        Comparator<StorageEntry> cmp;
        switch (sortMode != null ? sortMode : "name_asc") {
            case "name_desc":
                cmp = Comparator.comparing((StorageEntry e) -> e.itemId)
                    .reversed();
                break;
            case "count_asc":
                cmp = Comparator.comparingLong((StorageEntry e) -> e.count);
                break;
            case "count_desc":
                cmp = Comparator.comparingLong((StorageEntry e) -> e.count)
                    .reversed();
                break;
            case "name_asc":
            default:
                cmp = Comparator.comparing(e -> e.itemId);
                break;
        }
        entries.sort(cmp);
    }

    // ======== 数据类 ========

    /** 存储条目 */
    public static class StorageEntry {

        public final String itemId;
        public final int meta;
        public long count;

        public StorageEntry(String itemId, int meta, long count) {
            this.itemId = itemId;
            this.meta = meta;
            this.count = count;
        }
    }

    /** 分页结果 */
    public static class PageResult {

        public final List<StorageEntry> items;
        public final int page;
        public final int totalPages;
        public final int totalItems;

        public PageResult(List<StorageEntry> items, int page, int totalPages, int totalItems) {
            this.items = items;
            this.page = page;
            this.totalPages = totalPages;
            this.totalItems = totalItems;
        }
    }
}
