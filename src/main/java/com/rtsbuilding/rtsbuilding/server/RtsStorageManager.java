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
        if (worldTime % 100 == 0) {
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
                    RtsbuildingMod.LOGGER
                        .debug("RtsStorageManager: AE2 link invalidated for {}", player.getDisplayName());
                    continue;
                }

                // 定期刷新 AE2 数据
                tryPopulateFromAe2(player, session);
            }
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

            // 扫描漏斗目标位置附近的掉落物（默认 5x5x5，可由客户端滑条调整到 1-16）
            double range = Math.max(1, Math.min(16, session.getFunnelRangeSize())) / 2.0D;
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

                int before = stack.stackSize;
                net.minecraft.item.ItemStack remaining = insertIntoLinkedThenPlayer(player, session, stack.copy());
                int after = remaining == null ? 0 : remaining.stackSize;
                int inserted = Math.max(0, before - after);
                if (inserted > 0) {
                    session.addItem(itemId, stack.getItemDamage(), inserted);
                    collected++;
                    if (after <= 0) {
                        entityItem.setDead();
                    } else {
                        entityItem.getEntityItem().stackSize = after;
                    }
                }
            }
        }
    }

    private static net.minecraft.item.ItemStack insertIntoLinkedThenPlayer(EntityPlayerMP player,
        RtsStorageSession session, net.minecraft.item.ItemStack stack) {
        if (player == null || session == null || stack == null || stack.stackSize <= 0) return stack;
        World world = player.worldObj;
        net.minecraft.item.ItemStack remaining = stack.copy();

        if (session.isAe2Linked() && world != null && remaining.stackSize > 0) {
            TileEntity te = world.getTileEntity(session.getLinkedX(), session.getLinkedY(), session.getLinkedZ());
            if (te != null) {
                long inserted = RtsAe2Compat.insertItem(te, ForgeDirection.UNKNOWN, remaining.copy());
                remaining.stackSize -= (int) Math.min(inserted, remaining.stackSize);
                if (remaining.stackSize <= 0) return null;
            }
        }

        if (session.isContainerLinked() && world != null && remaining.stackSize > 0) {
            for (com.rtsbuilding.rtsbuilding.server.storage.LinkedStorageRef ref : session.getLinkedStorages()) {
                if (session.getLinkedMode(ref)
                    == com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStorageMessage.MODE_EXTRACT_ONLY) continue;
                remaining = insertIntoIInventoryAt(world, ref.x, ref.y, ref.z, remaining);
                if (remaining == null || remaining.stackSize <= 0) return null;
            }
        }

        if (remaining != null && remaining.stackSize > 0) {
            remaining = insertIntoPlayerInventory(player, remaining);
        }
        return remaining;
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

            // 检查容器绑定（遍历所有已链接容器）
            com.rtsbuilding.rtsbuilding.server.storage.LinkedStorageRef refToRemove = null;
            for (com.rtsbuilding.rtsbuilding.server.storage.LinkedStorageRef ref : session.getLinkedStorages()) {
                if (ref.dimension == dimId && ref.x == blockX && ref.y == blockY && ref.z == blockZ) {
                    refToRemove = ref;
                    break;
                }
            }
            if (refToRemove != null) {
                session.removeLinkedStorage(refToRemove);
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
                        sendStoragePage(player, 0, 0);
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
            RtsbuildingMod.LOGGER.debug(
                "RtsStorageManager: AE2 candidate TE={} at ({}, {}, {})",
                te.getClass()
                    .getSimpleName(),
                x,
                y,
                z);
            // 切换行为：再次点击同一 AE2 目标 → 解绑
            if (session.isAe2Linked() && session.getLinkedX() == x
                && session.getLinkedY() == y
                && session.getLinkedZ() == z) {
                session.clearAe2Link();
                session.clear();
                saveSessionNBT(player);
                RtsbuildingMod.LOGGER.info(
                    "RtsStorageManager: player {} toggled off AE2 link at ({}, {}, {})",
                    player.getDisplayName(),
                    x,
                    y,
                    z);
                return true;
            }
            if (RtsAe2Compat.tryConnectAe2(te, ForgeDirection.UNKNOWN)) {
                session.clearAe2Link();
                session.clearContainerLink();
                session.setAe2Link(x, y, z, world.provider.dimensionId);
                session.clear();
                saveSessionNBT(player);
                RtsbuildingMod.LOGGER
                    .info("RtsStorageManager: player {} linked AE2 at ({}, {}, {})", player.getDisplayName(), x, y, z);
                return true;
            }
            RtsbuildingMod.LOGGER.debug(
                "RtsStorageManager: AE2 connect failed for TE={} at ({}, {}, {})",
                te.getClass()
                    .getSimpleName(),
                x,
                y,
                z);
        }

        // 阶段6：普通容器链接（箱子、漏斗、储罐等实现 IInventory 的 TileEntity）
        if (te instanceof net.minecraft.inventory.IInventory) {
            com.rtsbuilding.rtsbuilding.server.storage.LinkedStorageRef ref = new com.rtsbuilding.rtsbuilding.server.storage.LinkedStorageRef(
                world.provider.dimensionId,
                x,
                y,
                z);
            // 切换行为：再次点击同一容器 → 解绑
            if (session.hasLinkedStorage(ref)) {
                session.removeLinkedStorage(ref);
                session.clear();
                saveSessionNBT(player);
                RtsbuildingMod.LOGGER.info(
                    "RtsStorageManager: player {} toggled off container link at ({}, {}, {})",
                    player.getDisplayName(),
                    x,
                    y,
                    z);
                return true;
            }
            session.clearAe2Link();
            session.addLinkedStorage(ref, linkMode, 0);
            session.clear();
            session.scanContainerInventory((net.minecraft.inventory.IInventory) te);
            saveSessionNBT(player);
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
        session.clearContainerLink();
        session.clear();
        saveSessionNBT(player);
        RtsbuildingMod.LOGGER.info("RtsStorageManager: player {} unlinked all storage", player.getDisplayName());
    }

    /**
     * 解除玩家指定坐标的链接（支持容器和 AE2）。
     * 支持多容器：只移除指定坐标的链接，不影响其他链接。
     *
     * @return true 如果成功移除
     */
    public static boolean unlinkStorageAt(EntityPlayerMP player, int x, int y, int z) {
        if (player == null) return false;
        RtsStorageSession session = getSession(player);
        int dimId = player.worldObj.provider.dimensionId;

        // 检查 AE2 链接
        if (session.isAe2Linked() && session.getLinkedX() == x
            && session.getLinkedY() == y
            && session.getLinkedZ() == z) {
            session.clearAe2Link();
            session.clear();
            saveSessionNBT(player);
            RtsbuildingMod.LOGGER
                .info("RtsStorageManager: player {} unlinked AE2 at ({}, {}, {})", player.getDisplayName(), x, y, z);
            return true;
        }

        // 检查容器链接
        com.rtsbuilding.rtsbuilding.server.storage.LinkedStorageRef ref = new com.rtsbuilding.rtsbuilding.server.storage.LinkedStorageRef(
            dimId,
            x,
            y,
            z);
        if (session.removeLinkedStorage(ref)) {
            session.clear();
            saveSessionNBT(player);
            RtsbuildingMod.LOGGER.info(
                "RtsStorageManager: player {} unlinked container at ({}, {}, {})",
                player.getDisplayName(),
                x,
                y,
                z);
            return true;
        }
        return false;
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

    // ======== 旋转方块 ========

    /**
     * 旋转指定位置的方块（每次 90°）。
     * 对齐原版 RtsStorageManager.rotateBlock()。
     */
    public static void rotateBlock(EntityPlayerMP player, int x, int y, int z) {
        if (player == null || player.worldObj == null) return;
        if (!player.worldObj.blockExists(x, y, z)) return;

        net.minecraft.block.Block block = player.worldObj.getBlock(x, y, z);
        if (block == null) return;

        int meta = player.worldObj.getBlockMetadata(x, y, z);

        // 尝试 Forge 旋转钩子
        if (block.rotateBlock(player.worldObj, x, y, z, net.minecraftforge.common.util.ForgeDirection.UP)) {
            RtsbuildingMod.LOGGER.debug(
                "RtsStorageManager: rotated block {} at ({}, {}, {}) via Forge rotateBlock",
                block.getLocalizedName(),
                x,
                y,
                z);
            return;
        }

        // 回退：简单 metadata 旋转
        net.minecraft.world.World world = player.worldObj;
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(x, y, z);

        try {
            // 对常见可旋转方块做 metadata 递增
            if (block instanceof net.minecraft.block.BlockDirectional
                || block instanceof net.minecraft.block.BlockStairs
                || block instanceof net.minecraft.block.BlockPistonBase
                || block instanceof net.minecraft.block.BlockHopper) {
                int newMeta = (meta + 1) % 4;
                world.setBlockMetadataWithNotify(x, y, z, newMeta, 3);
                if (te != null) {
                    te.updateContainingBlockInfo();
                }
                world.markBlockForUpdate(x, y, z);
                RtsbuildingMod.LOGGER.debug(
                    "RtsStorageManager: rotated directional block {} at ({}, {}, {}) meta {} → {}",
                    block.getLocalizedName(),
                    x,
                    y,
                    z,
                    meta,
                    newMeta);
                return;
            }

            if (block instanceof net.minecraft.block.BlockLog) {
                int newMeta = (meta & 3) == 3 ? meta & ~3 : meta + 4;
                world.setBlockMetadataWithNotify(x, y, z, newMeta, 3);
                if (te != null) {
                    te.updateContainingBlockInfo();
                }
                world.markBlockForUpdate(x, y, z);
                RtsbuildingMod.LOGGER.debug(
                    "RtsStorageManager: rotated log block {} at ({}, {}, {}) meta {} → {}",
                    block.getLocalizedName(),
                    x,
                    y,
                    z,
                    meta,
                    newMeta);
                return;
            }
        } catch (Exception e) {
            RtsbuildingMod.LOGGER
                .warn("RtsStorageManager: rotateBlock failed at ({},{},{}): {}", x, y, z, e.getMessage());
        }
    }

    // ======== GUI 绑定 ========

    public static void setGuiBinding(EntityPlayerMP player, byte slotId, boolean clear, int x, int y, int z, byte face,
        String itemIdHint) {
        if (player == null) return;
        RtsStorageSession session = getSession(player);

        com.rtsbuilding.rtsbuilding.server.storage.GuiBinding[] guiBindings = session.getGuiBindings();
        if (slotId < 0 || slotId >= guiBindings.length) return;

        if (clear) {
            guiBindings[slotId] = null;
            saveSessionNBT(player);
            sendStoragePage(player, 0, 0);
            return;
        }

        if (!player.worldObj.blockExists(x, y, z)) return;
        net.minecraft.tileentity.TileEntity te = player.worldObj.getTileEntity(x, y, z);
        if (te == null) return;

        com.rtsbuilding.rtsbuilding.server.storage.GuiBinding binding = new com.rtsbuilding.rtsbuilding.server.storage.GuiBinding(
            x,
            y,
            z,
            player.worldObj.provider.dimensionId,
            face);
        guiBindings[slotId] = binding;
        saveSessionNBT(player);
        sendStoragePage(player, 0, 0);
        RtsbuildingMod.LOGGER.info(
            "RtsStorageManager: player {} bound GUI slot {} to ({}, {}, {})",
            player.getDisplayName(),
            slotId,
            x,
            y,
            z);
    }

    public static void openGuiBinding(EntityPlayerMP player, byte slotId) {
        if (player == null) return;
        RtsStorageSession session = getSession(player);

        com.rtsbuilding.rtsbuilding.server.storage.GuiBinding[] guiBindings = session.getGuiBindings();
        if (slotId < 0 || slotId >= guiBindings.length) return;

        com.rtsbuilding.rtsbuilding.server.storage.GuiBinding binding = guiBindings[slotId];
        if (binding == null) return;

        net.minecraft.world.World world = player.worldObj;
        if (world == null || world.provider.dimensionId != binding.dimensionId) return;
        if (!world.blockExists(binding.x, binding.y, binding.z)) return;

        net.minecraft.tileentity.TileEntity te = world.getTileEntity(binding.x, binding.y, binding.z);
        if (te == null) return;

        if (te instanceof net.minecraft.inventory.IInventory) {
            player.displayGUIChest((net.minecraft.inventory.IInventory) te);
        } else {
            net.minecraft.block.Block block = world.getBlock(binding.x, binding.y, binding.z);
            if (block != null) {
                block.onBlockActivated(world, binding.x, binding.y, binding.z, player, binding.face, 0.5f, 0.5f, 0.5f);
            }
        }
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
            // 容器物理提取：遍历所有已链接容器，尝试从中移除对应物品
            World world = player.worldObj;
            if (world != null) {
                boolean extracted = false;
                for (com.rtsbuilding.rtsbuilding.server.storage.LinkedStorageRef ref : session.getLinkedStorages()) {
                    TileEntity te = world.getTileEntity(ref.x, ref.y, ref.z);
                    if (te instanceof net.minecraft.inventory.IInventory) {
                        if (extractFromIInventory(
                            (net.minecraft.inventory.IInventory) te,
                            itemId,
                            meta,
                            (int) amount)) {
                            extracted = true;
                            break;
                        }
                    }
                }
                if (!extracted) {
                    session.addItem(itemId, meta, amount);
                    RtsbuildingMod.LOGGER.debug(
                        "RtsStorageManager: container extraction failed for {} x{}, rolled back for {}",
                        itemId,
                        amount,
                        player.getDisplayName());
                    return false;
                }
            }
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

            // 2. 尝试存入容器链接（遍历所有已链接容器）
            if (session.isContainerLinked() && remaining.stackSize > 0) {
                for (com.rtsbuilding.rtsbuilding.server.storage.LinkedStorageRef ref : session.getLinkedStorages()) {
                    if (session.getLinkedMode(ref)
                        == com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStorageMessage.MODE_EXTRACT_ONLY)
                        continue;
                    remaining = insertIntoIInventoryAt(world, ref.x, ref.y, ref.z, remaining);
                    if (remaining == null || remaining.stackSize <= 0) break;
                }
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
     * 将物品堆插入指定坐标 IInventory 容器（支持同类堆叠），返回剩余物品堆（null 表示全部插入）。
     */
    private static net.minecraft.item.ItemStack insertIntoIInventoryAt(World world, int cx, int cy, int cz,
        net.minecraft.item.ItemStack stack) {
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

    /**
     * 从 IInventory 容器中提取指定物品。
     * 遍历容器槽位，找到匹配的物品后扣除数量。
     *
     * @param inv    容器
     * @param itemId 物品ID（如 "minecraft:stone"）
     * @param meta   物品元数据
     * @param amount 需要提取的数量
     * @return true 如果成功提取了足够数量
     */
    private static boolean extractFromIInventory(net.minecraft.inventory.IInventory inv, String itemId, int meta,
        int amount) {
        if (inv == null || itemId == null || amount <= 0) return false;

        int remaining = amount;

        // 解析目标 Item
        String lookupId = itemId;
        if (lookupId.startsWith("minecraft:")) {
            lookupId = lookupId.substring("minecraft:".length());
        }
        net.minecraft.item.Item targetItem = (net.minecraft.item.Item) net.minecraft.item.Item.itemRegistry
            .getObject(lookupId);
        if (targetItem == null) {
            targetItem = (net.minecraft.item.Item) net.minecraft.item.Item.itemRegistry.getObject(itemId);
        }
        if (targetItem == null) return false;

        // 遍历容器槽位提取物品
        for (int slot = 0; slot < inv.getSizeInventory() && slot < 256; slot++) {
            if (remaining <= 0) break;
            net.minecraft.item.ItemStack stackInSlot = inv.getStackInSlot(slot);
            if (stackInSlot == null || stackInSlot.getItem() == null) continue;
            if (stackInSlot.getItem() != targetItem || stackInSlot.getItemDamage() != meta) continue;

            int toExtract = Math.min(remaining, stackInSlot.stackSize);
            stackInSlot.stackSize -= toExtract;
            remaining -= toExtract;

            if (stackInSlot.stackSize <= 0) {
                inv.setInventorySlotContents(slot, null);
            }
            inv.markDirty();
        }

        return remaining <= 0;
    }

    // ======== 存储页面构建与发送 ========

    /**
     * 构建存储页面数据并发送给客户端。
     * 供 C2SRtsRequestStoragePageMessage 和 C2SRtsLinkStorageMessage 共用。
     *
     * @param player   目标玩家
     * @param page     页码 (0-based)
     * @param windowId 窗口 ID
     */
    public static void sendStoragePage(EntityPlayerMP player, int page, int windowId) {
        if (player == null) return;

        RtsStorageSession session = getSession(player);
        session.clear();

        boolean hasAe2 = tryPopulateFromAe2(player, session);
        if (!hasAe2) {
            session.scanLinkedContainers(player.worldObj);
        }
        session.scanPlayerInventory(player);

        RtsStorageSession.PageResult result = session.queryPage("name_asc", page, 88);
        java.util.List<net.minecraft.item.ItemStack> stacks = session.toItemStacks(result.items);

        // 构建已链接存储并行数组
        java.util.List<com.rtsbuilding.rtsbuilding.server.storage.LinkedStorageRef> refs = session.getLinkedStorages();
        int linkedCount = refs.size();
        int[] linkedDimIds = new int[linkedCount];
        int[] linkedX = new int[linkedCount];
        int[] linkedY = new int[linkedCount];
        int[] linkedZ = new int[linkedCount];
        byte[] linkedModes = new byte[linkedCount];
        int[] linkedPriorities = new int[linkedCount];
        for (int i = 0; i < linkedCount; i++) {
            com.rtsbuilding.rtsbuilding.server.storage.LinkedStorageRef ref = refs.get(i);
            linkedDimIds[i] = ref.dimension;
            linkedX[i] = ref.x;
            linkedY[i] = ref.y;
            linkedZ[i] = ref.z;
            linkedModes[i] = session.getLinkedMode(ref);
            linkedPriorities[i] = session.getLinkedPriority(ref);
        }

        // 追加 AE2 链接条目（如果已链接，使客户端也能高亮 AE2 目标）
        if (session.isAe2Linked()) {
            linkedDimIds = java.util.Arrays.copyOf(linkedDimIds, linkedCount + 1);
            linkedX = java.util.Arrays.copyOf(linkedX, linkedCount + 1);
            linkedY = java.util.Arrays.copyOf(linkedY, linkedCount + 1);
            linkedZ = java.util.Arrays.copyOf(linkedZ, linkedCount + 1);
            linkedModes = java.util.Arrays.copyOf(linkedModes, linkedCount + 1);
            linkedPriorities = java.util.Arrays.copyOf(linkedPriorities, linkedCount + 1);
            linkedDimIds[linkedCount] = session.getLinkedDimId();
            linkedX[linkedCount] = session.getLinkedX();
            linkedY[linkedCount] = session.getLinkedY();
            linkedZ[linkedCount] = session.getLinkedZ();
            linkedModes[linkedCount] = 0;
            linkedPriorities[linkedCount] = 0;
            linkedCount++;
        }

        RtsbuildingMod.LOGGER.debug(
            "sendStoragePage: player={}, hasAe2={}, sessionEmpty={}, items={}, stacks={}, linkedEntries={}, page={}, totalPages={}",
            player.getDisplayName(),
            hasAe2,
            session.isEmpty(),
            result.items.size(),
            stacks.size(),
            linkedCount,
            result.page,
            result.totalPages);

        com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePageMessage response = new com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePageMessage(
            result.page,
            result.totalPages,
            windowId,
            stacks,
            linkedDimIds,
            linkedX,
            linkedY,
            linkedZ,
            linkedModes,
            linkedPriorities);
        com.rtsbuilding.rtsbuilding.network.RtsNetworkManager.NETWORK.sendTo(response, player);
    }
}
