package com.rtsbuilding.rtsbuilding.compat.ae2;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;

/**
 * AE2兼容层 — RTSbuilding与Applied Energistics 2 ME网络存储的桥接。
 *
 * 1.7.10 GTNH AE2 Unofficial rv3 API (appeng.api)。
 * 采用直接API调用的编译时软依赖（compileOnly），AE2未安装时所有方法安全降级。
 *
 * API调用链：
 * {@code IGridHost -> IGridNode -> IGrid -> IStorageGrid -> IMEMonitor<IAEItemStack>}
 *
 * <p>
 * 读取操作（getStorageList）纯查询，不修改ME网络。
 * 写入/提取操作需要 Actionable + IActionSource，需谨慎使用。
 */
public final class RtsAe2Compat {

    private RtsAe2Compat() {}

    // ========================================================================
    // 可用性检测
    // ========================================================================

    /** AE2模组是否已加载 */
    public static boolean isAvailable() {
        return cpw.mods.fml.common.Loader.isModLoaded("appliedenergistics2");
    }

    /** 给定的TileEntity是否是AE2网格主机（实现了IGridHost接口） */
    public static boolean isAe2GridHost(TileEntity te) {
        return te instanceof IGridHost;
    }

    // ========================================================================
    // 网络连接检测
    // ========================================================================

    /**
     * 检测能否从指定的AE2 TileEntity访问ME网络存储。
     *
     * @param te   AE2 TileEntity（如ME Interface、ME Drive、ME Chest）
     * @param side 访问方向
     * @return 是否能访问ME网络物品存储
     */
    public static boolean tryConnectAe2(TileEntity te, ForgeDirection side) {
        if (!isAvailable() || te == null) return false;
        if (!(te instanceof IGridHost)) return false;

        try {
            IGridNode node = getGridNode((IGridHost) te, side);
            if (node == null) return false;
            IGrid grid = node.getGrid();
            if (grid == null) return false;
            IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
            return storageGrid != null && storageGrid.getItemInventory() != null;
        } catch (Exception e) {
            RtsbuildingMod.LOGGER.debug("RtsAe2Compat: connect failed at {} side {}: {}", te, side, e.toString());
            return false;
        }
    }

    // ========================================================================
    // 物品查询
    // ========================================================================

    /**
     * 从AE2 ME网络中查询所有存储物品。
     *
     * @param te   AE2 TileEntity
     * @param side 访问方向
     * @return 存储条目列表（可能为空，但不会为null）
     */
    public static List<Ae2StorageEntry> queryAllItems(TileEntity te, ForgeDirection side) {
        List<Ae2StorageEntry> result = new ArrayList<>();
        if (!isAvailable() || te == null) return result;

        IMEMonitor<IAEItemStack> monitor = getItemMonitor(te, side);
        if (monitor == null) return result;

        try {
            IItemList<IAEItemStack> list = monitor.getStorageList();
            if (list == null) return result;

            for (IAEItemStack aeStack : list) {
                if (aeStack == null) continue;
                long count = aeStack.getStackSize();
                if (count <= 0) continue;

                ItemStack mcStack = aeStack.getItemStack();
                if (mcStack == null) continue;

                String itemId = net.minecraft.item.Item.itemRegistry.getNameForObject(mcStack.getItem());
                if (itemId == null || itemId.isEmpty()) continue;
                int meta = mcStack.getItemDamage();

                // display copy (size=1) 用于客户端渲染
                ItemStack display = mcStack.copy();
                display.stackSize = 1;

                result.add(new Ae2StorageEntry(itemId, meta, count, display));
            }
        } catch (Exception e) {
            RtsbuildingMod.LOGGER.debug("RtsAe2Compat: queryAllItems failed: {}", e.toString());
        }
        return result;
    }

    /**
     * 查询ME网络中指定物品的数量。
     *
     * @return 物品数量，-1表示不可用或未找到
     */
    public static long queryItemCount(TileEntity te, ForgeDirection side, String itemId, int meta) {
        if (!isAvailable() || te == null || itemId == null) return -1L;

        IMEMonitor<IAEItemStack> monitor = getItemMonitor(te, side);
        if (monitor == null) return -1L;

        try {
            IItemList<IAEItemStack> list = monitor.getStorageList();
            if (list == null) return -1L;

            for (IAEItemStack aeStack : list) {
                if (aeStack == null) continue;
                ItemStack mcStack = aeStack.getItemStack();
                if (mcStack == null) continue;

                String id = net.minecraft.item.Item.itemRegistry.getNameForObject(mcStack.getItem());
                if (itemId.equals(id) && mcStack.getItemDamage() == meta) {
                    return aeStack.getStackSize();
                }
            }
        } catch (Exception e) {
            RtsbuildingMod.LOGGER.debug("RtsAe2Compat: queryItemCount failed: {}", e.toString());
        }
        return 0L;
    }

    // ========================================================================
    // 物品提取
    // ========================================================================

    /**
     * 从 AE2 ME 网络中物理提取指定物品。
     *
     * @return 实际提取的数量，0 表示失败或不足
     */
    public static long extractItem(TileEntity te, ForgeDirection side, String itemId, int meta, long amount) {
        if (!isAvailable() || te == null || itemId == null || amount <= 0) return 0L;

        IMEMonitor<IAEItemStack> monitor = getItemMonitor(te, side);
        if (monitor == null) return 0L;

        try {
            Item item = (Item) net.minecraft.item.Item.itemRegistry.getObject(itemId);
            if (item == null) return 0L;
            ItemStack mcStack = new ItemStack(item, 1, meta);

            IAEItemStack request = appeng.api.AEApi.instance()
                .storage()
                .createItemStack(mcStack);
            request.setStackSize(amount);

            IAEItemStack extracted = monitor.extractItems(request, Actionable.MODULATE, new BaseActionSource());
            return extracted != null ? extracted.getStackSize() : 0L;
        } catch (Exception e) {
            RtsbuildingMod.LOGGER.debug("RtsAe2Compat: extractItem failed for {}: {}", itemId, e.toString());
            return 0L;
        }
    }

    /**
     * 向 AE2 ME 网络中插入物品。
     *
     * @param te    AE2 TileEntity
     * @param side  访问方向
     * @param stack 要插入的物品堆
     * @return 实际插入的数量，0 表示失败或网络已满
     */
    public static long insertItem(TileEntity te, ForgeDirection side, ItemStack stack) {
        if (!isAvailable() || te == null || stack == null) return 0L;

        IMEMonitor<IAEItemStack> monitor = getItemMonitor(te, side);
        if (monitor == null) return 0L;

        try {
            IAEItemStack request = appeng.api.AEApi.instance()
                .storage()
                .createItemStack(stack);
            IAEItemStack remaining = monitor.injectItems(request, Actionable.MODULATE, new BaseActionSource());
            if (remaining == null) {
                return stack.stackSize;
            }
            return Math.max(0L, stack.stackSize - remaining.getStackSize());
        } catch (Exception e) {
            RtsbuildingMod.LOGGER.debug("RtsAe2Compat: insertItem failed: {}", e.toString());
            return 0L;
        }
    }

    /**
     * 从 AE2 ME 网络中查询指定物品数量。
     * 使用 IMEInventory 快速查询，比遍历 getStorageList 更高效。
     */
    public static long queryAvailable(TileEntity te, ForgeDirection side, String itemId, int meta) {
        if (!isAvailable() || te == null || itemId == null) return 0L;

        IMEMonitor<IAEItemStack> monitor = getItemMonitor(te, side);
        if (monitor == null) return 0L;

        try {
            Item item = (Item) net.minecraft.item.Item.itemRegistry.getObject(itemId);
            if (item == null) return 0L;
            ItemStack mcStack = new ItemStack(item, 1, meta);
            IAEItemStack request = appeng.api.AEApi.instance()
                .storage()
                .createItemStack(mcStack);
            IAEItemStack result = monitor.extractItems(request, Actionable.SIMULATE, new BaseActionSource());
            return result != null ? result.getStackSize() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    // ========================================================================
    // 数据类
    // ========================================================================

    /** AE2存储条目，供RtsStorageSession消费 */
    public static final class Ae2StorageEntry {

        public final String itemId;
        public final int meta;
        public final long count;
        public final ItemStack displayStack;

        Ae2StorageEntry(String itemId, int meta, long count, ItemStack displayStack) {
            this.itemId = itemId;
            this.meta = meta;
            this.count = count;
            this.displayStack = displayStack;
        }
    }

    // ========================================================================
    // 内部辅助方法
    // ========================================================================

    /**
     * 从TileEntity获取IMEMonitor&lt;IAEItemStack&gt;。
     * 调用链：IGridHost → IGridNode → IGrid → IStorageGrid → getItemInventory()
     */
    private static IMEMonitor<IAEItemStack> getItemMonitor(TileEntity te, ForgeDirection side) {
        if (!(te instanceof IGridHost)) return null;
        try {
            IGridNode node = getGridNode((IGridHost) te, side);
            if (node == null) return null;

            IGrid grid = node.getGrid();
            if (grid == null) return null;

            IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
            if (storageGrid == null) return null;

            return storageGrid.getItemInventory();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 安全获取IGridNode，失败时尝试所有方向。
     */
    private static IGridNode getGridNode(IGridHost host, ForgeDirection preferred) {
        try {
            IGridNode node = host.getGridNode(preferred);
            if (node != null) return node;

            for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                node = host.getGridNode(dir);
                if (node != null) return node;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
