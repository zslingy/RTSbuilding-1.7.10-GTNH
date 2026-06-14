package com.rtsbuilding.rtsbuilding.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.util.RtsPlayerUtil;

/**
 * RTS Storage Manager — 管理所有玩家的链接存储容器。
 * 1.7.10 版本的中央存储管理器。
 * 阶段3：基础 Session 管理。
 * 阶段6：AE2 ME 网络接入，存储绑定解析。
 *
 */
public final class RtsStorageManager {

    private static final Map<UUID, RtsStorageSession> sessions = new HashMap<>();

    private RtsStorageManager() {}

    public static RtsStorageSession getSession(EntityPlayerMP player) {
        UUID id = RtsPlayerUtil.getUUID(player);
        RtsStorageSession session = sessions.get(id);
        if (session == null) {
            session = new RtsStorageSession();
            sessions.put(id, session);
        }
        return session;
    }

    public static void removeSession(EntityPlayerMP player) {
        UUID id = RtsPlayerUtil.getUUID(player);
        sessions.remove(id);
    }

    private static final String NBT_STORAGE_ROOT = "rtsbuilding_storage";

    public static void saveSessionNBT(EntityPlayerMP player) {
        if (player == null) return;
        RtsStorageSession session = sessions.get(RtsPlayerUtil.getUUID(player));
        if (session == null) return;
        NBTTagCompound root = new NBTTagCompound();
        session.writeToNBT(root);
        player.getEntityData()
            .setTag(NBT_STORAGE_ROOT, root);
    }

    public static void loadSessionNBT(EntityPlayerMP player) {
        if (player == null) return;
        UUID id = RtsPlayerUtil.getUUID(player);
        RtsStorageSession session = sessions.get(id);
        if (session == null) return;
        NBTTagCompound persistent = player.getEntityData();
        if (persistent.hasKey(NBT_STORAGE_ROOT, 10)) {
            session.readFromNBT(persistent.getCompoundTag(NBT_STORAGE_ROOT));
        }
    }

    public static void onServerTick(WorldServer world) {
        if (world == null || world.playerEntities.isEmpty()) return;

        // 每 100 tick（5 秒）刷新 AE2 链接
        long worldTime = world.getTotalWorldTime();
        if (worldTime % 100 != 0) return;

        for (Object obj : world.playerEntities) {
            if (!(obj instanceof EntityPlayerMP)) continue;
            EntityPlayerMP player = (EntityPlayerMP) obj;
            RtsStorageSession session = sessions.get(RtsPlayerUtil.getUUID(player));
            if (session == null || !session.isAe2Linked()) continue;

            // 验证 AE2 链接目标仍然存在
            int x = session.getLinkedX();
            int y = session.getLinkedY();
            int z = session.getLinkedZ();
            if (!world.blockExists(x, y, z)) {
                session.clearAe2Link();
                RtsbuildingMod.LOGGER.debug("RtsStorageManager: AE2 link invalidated for {}", player.getDisplayName());
                continue;
            }

            // 定期刷新 AE2 数据
            tryPopulateFromAe2(player, session);
        }

        // 漏斗 tick（每 2 tick 执行一次）
        tickFunnels(world, worldTime);
    }

    private static void tickFunnels(WorldServer world, long worldTime) {
        if (worldTime % 2 != 0) return;

        for (Object obj : world.playerEntities) {
            if (!(obj instanceof EntityPlayerMP)) continue;
            EntityPlayerMP player = (EntityPlayerMP) obj;
            RtsStorageSession session = sessions.get(RtsPlayerUtil.getUUID(player));
            if (session == null || !session.isFunnelActive()) continue;

            // Bug5修复：扫描漏斗目标位置附近的掉落物（而非玩家位置）
            double range = 3.0D;
            double scanX = player.posX;
            double scanY = player.posY;
            double scanZ = player.posZ;
            if (session.hasFunnelTargetPos()) {
                scanX = session.getFunnelTargetX();
                scanY = session.getFunnelTargetY();
                scanZ = session.getFunnelTargetZ();
            }
            net.minecraft.util.AxisAlignedBB area = net.minecraft.util.AxisAlignedBB.getBoundingBox(
                scanX - range,
                scanY - range,
                scanZ - range,
                scanX + range,
                scanY + range,
                scanZ + range);
            java.util.List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class, area);

            int collected = 0;
            int maxPerTick = 16;
            for (EntityItem entityItem : items) {
                if (collected >= maxPerTick) break;
                if (entityItem == null || entityItem.isDead) continue;
                net.minecraft.item.ItemStack stack = entityItem.getEntityItem();
                if (stack == null || stack.getItem() == null) continue;

                String itemId = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
                if (itemId == null) continue;

                // 尝试将物品存入链接存储
                if (session.isAe2Linked()) {
                    long inserted = RtsAe2Compat.insertItem(
                        world.getTileEntity(session.getLinkedX(), session.getLinkedY(), session.getLinkedZ()),
                        ForgeDirection.UNKNOWN,
                        stack.copy());
                    if (inserted > 0) {
                        entityItem.setDead();
                        session.addItem(itemId, stack.getItemDamage(), inserted);
                        collected++;
                    }
                } else if (session.isContainerLinked()) {
                    TileEntity te = world
                        .getTileEntity(session.getContainerX(), session.getContainerY(), session.getContainerZ());
                    if (te instanceof net.minecraft.inventory.IInventory) {
                        net.minecraft.inventory.IInventory inv = (net.minecraft.inventory.IInventory) te;
                        // 尝试将物品插入容器
                        for (int slot = 0; slot < inv.getSizeInventory() && slot < 256; slot++) {
                            net.minecraft.item.ItemStack existing = inv.getStackInSlot(slot);
                            if (existing == null) {
                                inv.setInventorySlotContents(slot, stack.copy());
                                entityItem.setDead();
                                session.addItem(itemId, stack.getItemDamage(), stack.stackSize);
                                collected++;
                                break;
                            } else if (existing.isItemEqual(stack)
                                && net.minecraft.item.ItemStack.areItemStackTagsEqual(existing, stack)) {
                                    int space = existing.getMaxStackSize() - existing.stackSize;
                                    if (space > 0) {
                                        int toAdd = Math.min(stack.stackSize, space);
                                        existing.stackSize += toAdd;
                                        stack.stackSize -= toAdd;
                                        if (stack.stackSize <= 0) {
                                            entityItem.setDead();
                                        }
                                    }
                                }
                        }
                    }
                }
            }
        }
    }

    public static void clearAll() {
        sessions.clear();
    }

    // ======== Bug8修复: 方块破坏时清理存储绑定 ========

    /**
     * 方块被破坏时调用：遍历所有玩家 session，如果绑定的存储容器坐标与破坏位置匹配，
     * 则自动解绑并向客户端发送同步消息。
     * 对齐原版 RtsStorageManager.onLinkedStorageBlockBroken()。
     *
     * @param world  方块所在世界
     * @param blockX 破坏位置 X
     * @param blockY 破坏位置 Y
     * @param blockZ 破坏位置 Z
     */
    public static void onLinkedStorageBlockBroken(net.minecraft.world.World world, int blockX, int blockY, int blockZ) {
        if (world == null) return;
        int dimId = world.provider.dimensionId;
        net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
        if (server == null) return;

        for (java.util.Map.Entry<UUID, RtsStorageSession> entry : new java.util.ArrayList<>(sessions.entrySet())) {
            UUID uuid = entry.getKey();
            RtsStorageSession session = entry.getValue();

            boolean unlinked = false;

            // 检查 AE2 绑定
            if (session.isAe2Linked() && session.getLinkedDimId() == dimId
                && session.getLinkedX() == blockX
                && session.getLinkedY() == blockY
                && session.getLinkedZ() == blockZ) {
                session.clearAe2Link();
                unlinked = true;
                RtsbuildingMod.LOGGER.debug(
                    "RtsStorageManager: AE2 link auto-cleared for session {} at ({}, {}, {})",
                    uuid,
                    blockX,
                    blockY,
                    blockZ);
            }

            // 检查容器绑定
            if (session.isContainerLinked() && session.getContainerDimId() == dimId
                && session.getContainerX() == blockX
                && session.getContainerY() == blockY
                && session.getContainerZ() == blockZ) {
                session.clearContainerLink();
                unlinked = true;
                RtsbuildingMod.LOGGER.debug(
                    "RtsStorageManager: container link auto-cleared for session {} at ({}, {}, {})",
                    uuid,
                    blockX,
                    blockY,
                    blockZ);
            }

            // 向对应玩家发送同步消息（清除客户端高亮）
            if (unlinked && server.getConfigurationManager() != null) {
                for (Object obj : server.getConfigurationManager().playerEntityList) {
                    if (obj instanceof EntityPlayerMP && ((EntityPlayerMP) obj).getUniqueID()
                        .equals(uuid)) {
                        EntityPlayerMP player = (EntityPlayerMP) obj;
                        com.rtsbuilding.rtsbuilding.network.RtsNetworkManager.NETWORK.sendTo(
                            new com.rtsbuilding.rtsbuilding.network.storage.S2CRtsLinkStorageStatusMessage(
                                blockX,
                                blockY,
                                blockZ,
                                false),
                            player);
                        break;
                    }
                }
            }
        }
    }

    // ======== 阶段6: AE2 存储绑定 ========

    /**
     * 将玩家链接到指定坐标的容器/AE2网格方块作为存储源。
     * 客户端发送 C2SRtsLinkStorageMessage，服务端调用此方法绑定。
     * Bug3修复：添加 linkMode 参数，0=NORMAL(可存可取)，1=EXTRACT_ONLY(仅提取)。
     *
     * @param linkMode 绑定模式
     * @return true 如果成功绑定（坐标有效且为 AE2 或可链接容器）
     */
    public static boolean linkStorage(EntityPlayerMP player, int x, int y, int z, byte linkMode) {
        if (player == null) return false;
        RtsStorageSession session = getSession(player);

        World world = player.worldObj;
        if (world == null || !world.blockExists(x, y, z)) return false;

        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null) return false;

        // AE2 网络优先
        if (RtsAe2Compat.isAvailable() && RtsAe2Compat.isAe2GridHost(te)) {
            if (RtsAe2Compat.tryConnectAe2(te, ForgeDirection.UNKNOWN)) {
                session.setAe2Link(x, y, z, world.provider.dimensionId);
                session.setLinkMode(linkMode);
                session.clear();
                RtsbuildingMod.LOGGER
                    .info("RtsStorageManager: player {} linked AE2 at ({}, {}, {})", player.getDisplayName(), x, y, z);
                return true;
            }
        }

        // 阶段6：普通容器链接（箱子、漏斗、储罐等实现 IInventory 的 TileEntity）
        if (te instanceof net.minecraft.inventory.IInventory) {
            session.setContainerLink(x, y, z, world.provider.dimensionId);
            session.setLinkMode(linkMode);
            session.clear();
            session.scanContainerInventory((net.minecraft.inventory.IInventory) te);
            RtsbuildingMod.LOGGER.info(
                "RtsStorageManager: player {} linked container at ({}, {}, {})",
                player.getDisplayName(),
                x,
                y,
                z);
            return true;
        }

        RtsbuildingMod.LOGGER
            .debug("RtsStorageManager: no linkable storage at ({}, {}, {}) for {}", x, y, z, player.getDisplayName());
        return false;
    }

    /**
     * 解除玩家的存储链接。
     */
    public static void unlinkStorage(EntityPlayerMP player) {
        if (player == null) return;
        RtsStorageSession session = getSession(player);
        session.clearAe2Link();
        session.clear();
        RtsbuildingMod.LOGGER.info("RtsStorageManager: player {} unlinked storage", player.getDisplayName());
    }

    /**
     * 尝试从 AE2 ME 网络填充存储会话数据。
     * 优先级：AE2 绑定 → 降级模拟数据。
     *
     * @return true 表示成功获取到真实存储数据
     */
    public static boolean tryPopulateFromAe2(EntityPlayerMP player, RtsStorageSession session) {
        if (player == null || session == null) return false;
        if (!session.isAe2Linked()) return false;

        World world = player.worldObj;
        if (world == null) return false;

        int x = session.getLinkedX();
        int y = session.getLinkedY();
        int z = session.getLinkedZ();

        if (!world.blockExists(x, y, z)) {
            RtsbuildingMod.LOGGER.debug("RtsStorageManager: AE2 link target missing at ({}, {}, {})", x, y, z);
            session.clearAe2Link();
            return false;
        }

        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null) {
            session.clearAe2Link();
            return false;
        }

        return session.populateFromAe2Cached(te, ForgeDirection.UNKNOWN);
    }

    // ======== 存储消耗逻辑 ========

    /**
     * 尝试从玩家链接的存储中消耗指定物品（数量）。
     * 执行顺序：1) 会话缓存消耗 2) AE2/容器物理提取。
     * 如果物理提取失败，回滚缓存消耗。
     *
     * @param itemId 带命名空间的物品ID（如 "minecraft:stone"）
     * @return true 如果消耗成功
     */
    public static boolean tryConsumeBlock(EntityPlayerMP player, String itemId, int meta, long amount) {
        if (player == null || itemId == null || amount <= 0) return false;

        RtsStorageSession session = getSession(player);

        if (!session.tryConsume(itemId, meta, amount)) {
            return false;
        }

        if (session.isAe2Linked()) {
            World world = player.worldObj;
            if (world != null && world.blockExists(session.getLinkedX(), session.getLinkedY(), session.getLinkedZ())) {
                TileEntity te = world.getTileEntity(session.getLinkedX(), session.getLinkedY(), session.getLinkedZ());
                if (te != null) {
                    long extracted = RtsAe2Compat.extractItem(te, ForgeDirection.UNKNOWN, itemId, meta, amount);
                    if (extracted < amount) {
                        session.addItem(itemId, meta, amount);
                        RtsbuildingMod.LOGGER.debug(
                            "RtsStorageManager: AE2 extraction failed for {} x{} (got {}), rolled back for {}",
                            itemId,
                            amount,
                            extracted,
                            player.getDisplayName());
                        return false;
                    }
                    session.invalidateAe2Cache();
                }
            }
        } else if (session.isContainerLinked()) {
            RtsbuildingMod.LOGGER.debug("RtsStorageManager: container-linked consumption for {} (cache-only)", itemId);
        }

        RtsbuildingMod.LOGGER
            .debug("RtsStorageManager: consumed {} x{} from storage for {}", itemId, amount, player.getDisplayName());

        return true;
    }

    /**
     * 吸收指定位置周围的掉落物到链接存储（对齐原版 RtsStorageMining.absorbNearbyMinedDrops）。
     * 插入顺序：AE2 → 容器链接 → 玩家背包，逐级 fallback。
     */
    public static void absorbNearbyMinedDrops(EntityPlayerMP player, int x, int y, int z, RtsStorageSession session) {
        if (player == null || session == null || player.worldObj == null) return;
        World world = player.worldObj;

        net.minecraft.util.AxisAlignedBB area = net.minecraft.util.AxisAlignedBB
            .getBoundingBox(x - 1.25D, y - 1.25D, z - 1.25D, x + 1.25D, y + 1.25D, z + 1.25D);
        java.util.List<EntityItem> drops = world.getEntitiesWithinAABB(EntityItem.class, area);

        for (EntityItem entityItem : drops) {
            if (entityItem == null || entityItem.isDead) continue;
            net.minecraft.item.ItemStack stack = entityItem.getEntityItem();
            if (stack == null || stack.getItem() == null) continue;

            net.minecraft.item.ItemStack remaining = stack.copy();

            // 1. 尝试存入 AE2
            if (session.isAe2Linked() && remaining.stackSize > 0) {
                TileEntity te = world.getTileEntity(session.getLinkedX(), session.getLinkedY(), session.getLinkedZ());
                if (te != null) {
                    long inserted = RtsAe2Compat.insertItem(te, ForgeDirection.UNKNOWN, remaining);
                    remaining.stackSize -= (int) inserted;
                    if (remaining.stackSize <= 0) {
                        entityItem.setDead();
                        continue;
                    }
                }
            }

            // 2. 尝试存入容器链接
            if (session.isContainerLinked() && remaining.stackSize > 0) {
                remaining = insertIntoIInventory(world, session, remaining);
                if (remaining == null || remaining.stackSize <= 0) {
                    entityItem.setDead();
                    continue;
                }
            }

            // 3. 尝试存入玩家背包
            if (remaining.stackSize > 0) {
                remaining = insertIntoPlayerInventory(player, remaining);
                if (remaining == null || remaining.stackSize <= 0) {
                    entityItem.setDead();
                    continue;
                }
            }

            // 仍有剩余：更新实体物品堆
            if (remaining.stackSize != stack.stackSize) {
                entityItem.getEntityItem().stackSize = remaining.stackSize;
            }
        }
    }

    /**
     * 将物品堆插入 IInventory 容器（支持同类堆叠），返回剩余物品堆（null 表示全部插入）。
     */
    private static net.minecraft.item.ItemStack insertIntoIInventory(World world, RtsStorageSession session,
        net.minecraft.item.ItemStack stack) {
        int cx = session.getContainerX();
        int cy = session.getContainerY();
        int cz = session.getContainerZ();
        if (!world.blockExists(cx, cy, cz)) return stack;
        TileEntity te = world.getTileEntity(cx, cy, cz);
        if (!(te instanceof net.minecraft.inventory.IInventory)) return stack;

        net.minecraft.inventory.IInventory inv = (net.minecraft.inventory.IInventory) te;
        for (int slot = 0; slot < inv.getSizeInventory() && slot < 256; slot++) {
            net.minecraft.item.ItemStack existing = inv.getStackInSlot(slot);
            if (existing == null) {
                inv.setInventorySlotContents(slot, stack);
                return null;
            } else if (existing.isItemEqual(stack)
                && net.minecraft.item.ItemStack.areItemStackTagsEqual(existing, stack)) {
                    int space = existing.getMaxStackSize() - existing.stackSize;
                    if (space > 0) {
                        int toAdd = Math.min(stack.stackSize, space);
                        existing.stackSize += toAdd;
                        stack.stackSize -= toAdd;
                        if (stack.stackSize <= 0) {
                            return null;
                        }
                    }
                }
        }
        return stack;
    }

    /**
     * 将物品堆插入玩家背包（支持同类堆叠），返回剩余物品堆（null 表示全部插入）。
     */
    private static net.minecraft.item.ItemStack insertIntoPlayerInventory(EntityPlayerMP player,
        net.minecraft.item.ItemStack stack) {
        net.minecraft.inventory.IInventory inv = player.inventory;
        for (int slot = 0; slot < inv.getSizeInventory(); slot++) {
            net.minecraft.item.ItemStack existing = inv.getStackInSlot(slot);
            if (existing == null) {
                inv.setInventorySlotContents(slot, stack);
                player.inventoryContainer.detectAndSendChanges();
                return null;
            } else if (existing.isItemEqual(stack)
                && net.minecraft.item.ItemStack.areItemStackTagsEqual(existing, stack)) {
                    int space = existing.getMaxStackSize() - existing.stackSize;
                    if (space > 0) {
                        int toAdd = Math.min(stack.stackSize, space);
                        existing.stackSize += toAdd;
                        stack.stackSize -= toAdd;
                        if (stack.stackSize <= 0) {
                            player.inventoryContainer.detectAndSendChanges();
                            return null;
                        }
                    }
                }
        }
        player.inventoryContainer.detectAndSendChanges();
        return stack;
    }

    public static boolean refreshAe2Data(EntityPlayerMP player, RtsStorageSession session) {
        if (player == null || session == null) return false;
        if (!session.isAe2Linked()) return false;

        World world = player.worldObj;
        if (world == null) return false;

        int x = session.getLinkedX();
        int y = session.getLinkedY();
        int z = session.getLinkedZ();

        if (!world.blockExists(x, y, z)) return false;

        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null) return false;

        return session.refreshFromAe2(te, ForgeDirection.UNKNOWN);
    }
}
