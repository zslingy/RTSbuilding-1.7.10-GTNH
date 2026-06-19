package com.rtsbuilding.rtsbuilding.server;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsMineProgressMessage;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsUltimineProgressMessage;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.WorkflowPipeline;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.MiningExecutePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.UltimineExecutePipe;
import com.rtsbuilding.rtsbuilding.server.policy.RtsBreakPolicy;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import com.rtsbuilding.rtsbuilding.util.RtsUltimineCollector;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * RTS 渐进式挖掘管理器。
 *
 * 替代原版的即时破坏，实现渐进式挖掘：客户端按住左键 → 服务端逐 tick 累积破坏进度 → 进度满后破坏方块。
 * 对齐原版 ClientRtsController.startMining/continueMining/abortMining 流程。
 */
public final class RtsMineManager {

    /** 每 tick 破坏进度增量基准（blockHardness=1, toolSpeed=1 时约 1.5 秒 = 30 ticks） */
    private static final float BASE_PROGRESS_PER_TICK = 1.0F / 30.0F;

    /** 破坏进度阈值（1.0 = 100%） */
    private static final float BREAK_THRESHOLD = 1.0F;

    /** 活跃挖掘状态：player UUID → ActiveMine */
    private static final Map<UUID, ActiveMine> activeMines = new ConcurrentHashMap<>();

    /** 每 tick 连锁挖掘处理的方块数 */
    private static final int ULTIMINE_BLOCKS_PER_TICK = 8;

    /** 活跃连锁挖掘状态：player UUID → ActiveUltimine */
    private static final Map<UUID, ActiveUltimine> activeUltimines = new ConcurrentHashMap<>();

    private RtsMineManager() {}

    /**
     * 开始挖掘一个方块。
     *
     * @param player                   玩家
     * @param x,                       y, z 方块坐标
     * @param face                     挖掘的面
     * @param toolSlot                 快捷栏工具槽位
     * @param toolItemId               工具 itemId
     * @param toolPrototype            工具 ItemStack（可 null，null 时从toolSlot获取）
     * @param allowPlacedBlockRecovery 是否允许回收已放置方块
     */
    public static void startMining(EntityPlayerMP player, int x, int y, int z, byte face, byte toolSlot,
        String toolItemId, ItemStack toolPrototype, boolean allowPlacedBlockRecovery) {
        startMining(player, x, y, z, face, toolSlot, toolItemId, toolPrototype, allowPlacedBlockRecovery, false);
    }

    public static void startMining(EntityPlayerMP player, int x, int y, int z, byte face, byte toolSlot,
        String toolItemId, ItemStack toolPrototype, boolean allowPlacedBlockRecovery, boolean ultimine) {
        if (PipelineRegistry.has(RtsWorkflowType.MINE_SINGLE)) {
            Map<String, Object> args = new HashMap<String, Object>();
            args.put(MiningExecutePipe.KEY_X.name(), Integer.valueOf(x));
            args.put(MiningExecutePipe.KEY_Y.name(), Integer.valueOf(y));
            args.put(MiningExecutePipe.KEY_Z.name(), Integer.valueOf(z));
            args.put(MiningExecutePipe.KEY_FACE.name(), Byte.valueOf(face));
            args.put(MiningExecutePipe.KEY_TOOL_SLOT.name(), Byte.valueOf(toolSlot));
            args.put(MiningExecutePipe.KEY_TOOL_ITEM_ID.name(), toolItemId);
            args.put(MiningExecutePipe.KEY_TOOL_PROTOTYPE.name(), toolPrototype);
            args.put(MiningExecutePipe.KEY_ALLOW_PLACED_RECOVERY.name(), Boolean.valueOf(allowPlacedBlockRecovery));
            @SuppressWarnings("unchecked")
            WorkflowPipeline<PipelineContext> pipeline = (WorkflowPipeline<PipelineContext>) PipelineRegistry
                .get(RtsWorkflowType.MINE_SINGLE);
            PipelineResult result = pipeline.execute(new PipelineContext(player, args));
            if (!(result instanceof PipelineResult.Failure)) return;
            RtsbuildingMod.LOGGER.warn("RtsMineManager: pipeline startMining failed, falling back to direct execution");
        }
        startMiningDirect(
            player,
            x,
            y,
            z,
            face,
            toolSlot,
            toolItemId,
            toolPrototype,
            allowPlacedBlockRecovery,
            ultimine);
    }

    public static void startMiningDirect(EntityPlayerMP player, int x, int y, int z, byte face, byte toolSlot,
        String toolItemId, ItemStack toolPrototype, boolean allowPlacedBlockRecovery, boolean ultimine) {
        if (player == null || player.worldObj == null) return;
        UUID uuid = player.getUniqueID();

        // 如果已经在挖掘，先中止
        abortMining(player);

        World world = player.worldObj;
        Block block = world.getBlock(x, y, z);
        if (block == null || block == Blocks.air || block.isAir(world, x, y, z)) {
            return;
        }

        float hardness = block.getBlockHardness(world, x, y, z);
        if (hardness < 0) return; // 不可破坏

        // 从玩家背包获取实际工具（如果toolPrototype为null）
        ItemStack actualTool = toolPrototype;
        if (actualTool == null && toolSlot >= 0 && toolSlot < 9) {
            actualTool = player.inventory.mainInventory[toolSlot];
        }
        if (actualTool == null || actualTool.getItem() == null) {
            actualTool = player.getCurrentEquippedItem();
        }
        if (actualTool != null && actualTool.getItem() != null) {
            actualTool = actualTool.copy();
        }

        ActiveMine mine = new ActiveMine();
        mine.playerName = player.getDisplayName();
        mine.posX = x;
        mine.posY = y;
        mine.posZ = z;
        mine.face = face;
        mine.toolSlot = toolSlot;
        mine.toolItemId = toolItemId;
        mine.toolPrototype = actualTool;
        mine.allowPlacedBlockRecovery = allowPlacedBlockRecovery;
        mine.startTick = world.getTotalWorldTime();
        mine.progress = 0.0F;
        mine.lastStage = -1;
        mine.ultimine = ultimine;

        mine.speedMultiplier = computeSpeedMultiplier(player, actualTool, block, world, x, y, z);

        // P0-4: 连锁模式预收集目标方块（在破坏前收集，避免破坏后中心方块为空气导致 BFS 失败）
        if (ultimine) {
            int limit = RtsProgressionManager.getUltimineLimit(player);
            mine.pendingChainBlocks = RtsUltimineCollector.collect(world, x, y, z, limit, 64);
            RtsbuildingMod.LOGGER.debug(
                "RtsMineManager: {} pre-collected {} chain targets from ({},{},{})",
                player.getDisplayName(),
                mine.pendingChainBlocks.size(),
                x,
                y,
                z);
        }

        activeMines.put(uuid, mine);

        RtsbuildingMod.LOGGER.debug(
            "RtsMineManager: {} started mining ({}, {}, {}) hardness={} multiplier={} ultimine={} activeMines={}",
            player.getDisplayName(),
            x,
            y,
            z,
            hardness,
            mine.speedMultiplier,
            ultimine,
            activeMines.size());
    }

    /**
     * 开始连锁挖掘。
     * BFS 收集同类方块后存入 ActiveUltimine 状态机，逐 tick 处理。
     */
    public static void startUltimine(EntityPlayerMP player, int seedX, int seedY, int seedZ, byte face, int toolSlot,
        String toolItemId, ItemStack toolPrototype, int limit, byte mode) {
        if (PipelineRegistry.has(RtsWorkflowType.ULTIMINE)) {
            Map<String, Object> args = new HashMap<String, Object>();
            args.put(MiningExecutePipe.KEY_X.name(), Integer.valueOf(seedX));
            args.put(MiningExecutePipe.KEY_Y.name(), Integer.valueOf(seedY));
            args.put(MiningExecutePipe.KEY_Z.name(), Integer.valueOf(seedZ));
            args.put(MiningExecutePipe.KEY_FACE.name(), Byte.valueOf(face));
            args.put(MiningExecutePipe.KEY_TOOL_SLOT.name(), Byte.valueOf((byte) toolSlot));
            args.put(MiningExecutePipe.KEY_TOOL_ITEM_ID.name(), toolItemId);
            args.put(MiningExecutePipe.KEY_TOOL_PROTOTYPE.name(), toolPrototype);
            args.put(UltimineExecutePipe.KEY_LIMIT.name(), Integer.valueOf(limit));
            args.put(UltimineExecutePipe.KEY_MODE.name(), Byte.valueOf(mode));
            @SuppressWarnings("unchecked")
            WorkflowPipeline<PipelineContext> pipeline = (WorkflowPipeline<PipelineContext>) PipelineRegistry
                .get(RtsWorkflowType.ULTIMINE);
            PipelineResult result = pipeline.execute(new PipelineContext(player, args));
            if (!(result instanceof PipelineResult.Failure)) return;
            RtsbuildingMod.LOGGER
                .warn("RtsMineManager: pipeline startUltimine failed, falling back to direct execution");
        }
        startUltimineDirect(player, seedX, seedY, seedZ, face, toolSlot, toolItemId, toolPrototype, limit, mode);
    }

    public static void startUltimineDirect(EntityPlayerMP player, int seedX, int seedY, int seedZ, byte face,
        int toolSlot, String toolItemId, ItemStack toolPrototype, int limit, byte mode) {
        if (player == null || player.worldObj == null) return;
        UUID uuid = player.getUniqueID();

        // 中止当前单方块挖掘
        abortMining(player);
        // 中止当前连锁挖掘
        abortUltimine(player);

        World world = player.worldObj;

        // BFS 收集目标
        List<int[]> targets = RtsUltimineCollector.collect(world, seedX, seedY, seedZ, limit, 64);
        if (targets.isEmpty()) return;

        // 获取工具
        ItemStack tool = findTool(player, toolSlot, toolItemId, toolPrototype);

        // 创建连锁挖掘状态
        ActiveUltimine au = new ActiveUltimine();
        au.playerUuid = uuid;
        au.toolSlot = toolSlot;
        au.toolItemId = toolItemId;
        au.toolPrototype = tool != null ? tool.copy() : null;
        au.creative = player.capabilities.isCreativeMode;
        au.targets.addAll(targets);
        au.totalTargets = targets.size();
        au.processedTargets = 0;
        au.progressPos = new int[] { seedX, seedY, seedZ };

        activeUltimines.put(uuid, au);

        // 发送初始进度
        RtsNetworkManager.NETWORK.sendTo(new S2CRtsUltimineProgressMessage(0, au.totalTargets), player);

        if (Config.debugMode) {
            RtsbuildingMod.LOGGER.debug(
                "RtsMineManager: {} started ultimine from ({},{},{}) targets={} limit={}",
                player.getDisplayName(),
                seedX,
                seedY,
                seedZ,
                targets.size(),
                limit);
        }
    }

    /**
     * 中止连锁挖掘。
     */
    public static void abortUltimine(EntityPlayerMP player) {
        if (player == null) return;
        ActiveUltimine removed = activeUltimines.remove(player.getUniqueID());
        if (removed != null) {
            RtsNetworkManager.NETWORK.sendTo(new S2CRtsUltimineProgressMessage(-1, 0), player);
            if (Config.debugMode) {
                RtsbuildingMod.LOGGER.debug(
                    "RtsMineManager: {} aborted ultimine processed={}/{}",
                    player.getDisplayName(),
                    removed.processedTargets,
                    removed.totalTargets);
            }
        }
    }

    /**
     * 查找玩家背包中的工具。
     */
    public static ItemStack findTool(EntityPlayerMP player, int preferredSlot, String toolItemId, ItemStack prototype) {
        if (preferredSlot >= 0 && preferredSlot < 9) {
            ItemStack hotbar = player.inventory.getStackInSlot(preferredSlot);
            if (hotbar != null && matchesTool(hotbar, toolItemId, prototype)) return hotbar;
        }
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack s = player.inventory.getStackInSlot(i);
            if (s != null && matchesTool(s, toolItemId, prototype)) return s;
        }
        return null;
    }

    private static boolean matchesTool(ItemStack stack, String toolItemId, ItemStack prototype) {
        if (stack == null) return false;
        if (prototype != null && prototype.getItem() != null) {
            return stack.getItem() == prototype.getItem() && stack.getItemDamage() == prototype.getItemDamage();
        }
        if (toolItemId != null && !toolItemId.isEmpty()) {
            String stackId = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
            return toolItemId.equals(stackId);
        }
        return false;
    }

    /**
     * 中止当前挖掘。
     */
    public static void abortMining(EntityPlayerMP player) {
        if (player == null) return;
        UUID uuid = player.getUniqueID();
        ActiveMine removed = activeMines.remove(uuid);
        if (removed != null) {
            // 修复: 发送stage=-1清除客户端裂纹动画
            S2CRtsMineProgressMessage clearMsg = new S2CRtsMineProgressMessage(
                removed.posX,
                removed.posY,
                removed.posZ,
                (byte) -1);
            RtsNetworkManager.NETWORK.sendTo(clearMsg, player);
            if (Config.debugMode) {
                RtsbuildingMod.LOGGER.debug(
                    "RtsMineManager: {} aborted mining ({}, {}, {})",
                    player.getDisplayName(),
                    removed.posX,
                    removed.posY,
                    removed.posZ);
            }
        }
        // P0-4: 同时中止连锁挖掘（连锁由 C2SRtsUltimineMessage 独立管理）
        abortUltimine(player);
    }

    /**
     * 清除玩家的所有挖掘状态（玩家登出时调用）。
     */
    public static void removePlayer(EntityPlayerMP player) {
        if (player == null) return;
        activeMines.remove(player.getUniqueID());
        activeUltimines.remove(player.getUniqueID());
    }

    /**
     * 清除所有活跃挖掘（服务端关闭时调用）。
     */
    public static void clearAll() {
        activeMines.clear();
        activeUltimines.clear();
    }

    /**
     * 每 tick 推进所有活跃挖掘的进度。
     * 通过 FML ServerTickEvent 调用（CommonProxy 注册）。
     * Bug2修复：使用全局玩家列表搜索玩家，避免跨维度遗漏。
     */
    @SuppressWarnings("unchecked")
    public static void onServerTick(WorldServer world) {
        if (world == null) return;

        // === 单方块渐进式挖掘处理 ===
        if (!activeMines.isEmpty()) {
            // [调试日志] Issue 2: 确认onServerTick被调用
            RtsbuildingMod.LOGGER.debug("RtsMineManager: onServerTick activeMines={}", activeMines.size());

            long currentTick = world.getTotalWorldTime();
            Iterator<Map.Entry<UUID, ActiveMine>> it = activeMines.entrySet()
                .iterator();

            while (it.hasNext()) {
                Map.Entry<UUID, ActiveMine> entry = it.next();
                ActiveMine mine = entry.getValue();

                EntityPlayerMP player = findPlayerByUUID(entry.getKey());
                if (player == null) {
                    // [调试日志] Issue 2: 确认玩家查找失败
                    RtsbuildingMod.LOGGER
                        .debug("RtsMineManager: player not found for UUID={}, removing mine", entry.getKey());
                    it.remove();
                    continue;
                }

                World playerWorld = player.worldObj;

                if (!RtsBreakPolicy.canBreakBlock(player, playerWorld, mine.posX, mine.posY, mine.posZ)) {
                    // [调试日志] Issue 2: 确认挖掘被策略阻止
                    RtsbuildingMod.LOGGER.debug(
                        "RtsMineManager: {} mining blocked by policy at ({}, {}, {})",
                        player.getDisplayName(),
                        mine.posX,
                        mine.posY,
                        mine.posZ);
                    it.remove();
                    continue;
                }

                float increment = BASE_PROGRESS_PER_TICK * mine.speedMultiplier;
                mine.progress += increment;

                if (player.capabilities.isCreativeMode) {
                    mine.progress = BREAK_THRESHOLD + 0.1F;
                }

                if (mine.progress >= BREAK_THRESHOLD) {
                    breakBlock(player, playerWorld, mine);
                    it.remove();
                    continue;
                }

                int newStage = (int) (mine.progress * 10);
                if (newStage != mine.lastStage) {
                    mine.lastStage = newStage;
                    S2CRtsMineProgressMessage progressMsg = new S2CRtsMineProgressMessage(
                        mine.posX,
                        mine.posY,
                        mine.posZ,
                        (byte) newStage);
                    RtsNetworkManager.NETWORK.sendTo(progressMsg, player);
                    // [调试日志] 确认进度消息已发送
                    RtsbuildingMod.LOGGER.debug(
                        "RtsMineManager: {} progress stage={} at ({}, {}, {})",
                        player.getDisplayName(),
                        newStage,
                        mine.posX,
                        mine.posY,
                        mine.posZ);
                }
            }
        }

        // === 渐进式连锁挖掘处理 ===
        if (!activeUltimines.isEmpty()) {
            Iterator<Map.Entry<UUID, ActiveUltimine>> ultIt = activeUltimines.entrySet()
                .iterator();
            while (ultIt.hasNext()) {
                Map.Entry<UUID, ActiveUltimine> entry = ultIt.next();
                tickUltimine(entry.getKey(), entry.getValue(), ultIt, world);
            }
        }
    }

    /**
     * 根据 UUID 查找在线玩家。
     */
    private static EntityPlayerMP findPlayerByUUID(UUID uuid) {
        net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
        if (server != null && server.getConfigurationManager() != null) {
            for (Object obj : server.getConfigurationManager().playerEntityList) {
                if (obj instanceof EntityPlayerMP && uuid.equals(((EntityPlayerMP) obj).getUniqueID())) {
                    return (EntityPlayerMP) obj;
                }
            }
        }
        return null;
    }

    /**
     * 连锁挖掘 tick 处理 — 每 tick 破坏最多 ULTIMINE_BLOCKS_PER_TICK 个方块。
     */
    private static void tickUltimine(UUID uuid, ActiveUltimine au, Iterator<Map.Entry<UUID, ActiveUltimine>> it,
        WorldServer world) {
        EntityPlayerMP player = findPlayerByUUID(uuid);
        if (player == null) {
            it.remove();
            return;
        }

        if (au.targets.isEmpty()) {
            finishUltimine(player, au);
            it.remove();
            return;
        }

        int processedThisTick = 0;
        World playerWorld = player.worldObj;

        while (processedThisTick < ULTIMINE_BLOCKS_PER_TICK && !au.targets.isEmpty()) {
            int[] target = au.targets.removeFirst();
            processedThisTick++;
            au.processedTargets++;

            int x = target[0], y = target[1], z = target[2];

            if (!playerWorld.blockExists(x, y, z) || playerWorld.isAirBlock(x, y, z)) continue;
            Block block = playerWorld.getBlock(x, y, z);
            if (block == null) continue;
            float hardness = block.getBlockHardness(playerWorld, x, y, z);
            if (hardness < 0) continue;

            if (!au.creative) {
                if (au.toolPrototype == null) continue;
                if (!au.toolPrototype.getItem()
                    .canHarvestBlock(block, au.toolPrototype)) continue;
            }

            breakUltimineBlock(player, playerWorld, x, y, z, block, au);
        }

        // 发送进度更新
        RtsNetworkManager.NETWORK
            .sendTo(new S2CRtsUltimineProgressMessage(au.processedTargets, au.totalTargets), player);

        if (au.targets.isEmpty()) {
            finishUltimine(player, au);
            it.remove();
        }
    }

    /**
     * 连锁挖掘破坏单个方块。
     */
    private static void breakUltimineBlock(EntityPlayerMP player, World world, int x, int y, int z, Block block,
        ActiveUltimine au) {
        int metadata = world.getBlockMetadata(x, y, z);

        BlockEvent.BreakEvent breakEvent = new BlockEvent.BreakEvent(x, y, z, world, block, metadata, player);
        if (MinecraftForge.EVENT_BUS.post(breakEvent)) return;

        try {
            ItemStack prevHeld = player.getCurrentEquippedItem();
            if (au.toolPrototype != null && au.toolPrototype.getItem() != null) {
                player.inventory.mainInventory[player.inventory.currentItem] = au.toolPrototype.copy();
            }
            boolean removed = player.theItemInWorldManager.tryHarvestBlock(x, y, z);
            if (prevHeld != null) {
                player.inventory.mainInventory[player.inventory.currentItem] = prevHeld;
            }
            player.inventoryContainer.detectAndSendChanges();

            if (removed) {
                RtsStorageManager.onLinkedStorageBlockBroken(world, x, y, z);
                if (player.getUniqueID() != null) {
                    RtsStorageSession session = RtsStorageManager.getSession(player);
                    if (session != null && session.isAutoStoreMinedDrops()
                        && RtsProgressionManager.canUse(player, RtsFeature.AUTO_STORE_MINED_DROPS)) {
                        RtsStorageManager.absorbNearbyMinedDrops(player, x, y, z, session);
                    }
                }
                world.playAuxSFX(2001, x, y, z, Block.getIdFromBlock(block) + (metadata << 12));
            }
        } catch (Exception e) {
            boolean removed = world.setBlockToAir(x, y, z);
            if (removed && !au.creative) {
                block.dropBlockAsItem(world, x, y, z, metadata, 0);
            }
            RtsbuildingMod.LOGGER
                .warn("RtsMineManager: ultimine breakBlock failed at ({},{},{}): {}", x, y, z, e.getMessage());
        }
    }

    /**
     * 直接破坏单个方块（供管线系统分帧调用）。
     */
    public static boolean breakBlockDirect(EntityPlayerMP player, int x, int y, int z, ItemStack tool) {
        if (player == null || player.worldObj == null) return false;
        World world = player.worldObj;
        Block block = world.getBlock(x, y, z);
        if (block == null || block == Blocks.air || block.isAir(world, x, y, z)) return false;

        int metadata = world.getBlockMetadata(x, y, z);
        BlockEvent.BreakEvent breakEvent = new BlockEvent.BreakEvent(x, y, z, world, block, metadata, player);
        if (MinecraftForge.EVENT_BUS.post(breakEvent)) return false;

        try {
            ItemStack prevHeld = player.getCurrentEquippedItem();
            if (tool != null) {
                player.inventory.mainInventory[player.inventory.currentItem] = tool;
            }
            boolean removed = player.theItemInWorldManager.tryHarvestBlock(x, y, z);
            if (prevHeld != null) {
                player.inventory.mainInventory[player.inventory.currentItem] = prevHeld;
            }
            player.inventoryContainer.detectAndSendChanges();

            if (removed) {
                world.playAuxSFX(2001, x, y, z, Block.getIdFromBlock(block) + (metadata << 12));
            }
            return removed;
        } catch (Exception e) {
            boolean removed = world.setBlockToAir(x, y, z);
            if (removed && !player.capabilities.isCreativeMode) {
                block.dropBlockAsItem(world, x, y, z, metadata, 0);
            }
            RtsbuildingMod.LOGGER
                .warn("RtsMineManager.breakBlockDirect: failed at ({},{},{}): {}", x, y, z, e.getMessage());
            return true;
        }
    }

    /**
     * 连锁挖掘完成。
     */
    private static void finishUltimine(EntityPlayerMP player, ActiveUltimine au) {
        RtsNetworkManager.NETWORK.sendTo(new S2CRtsUltimineProgressMessage(-1, 0), player);

        if (Config.debugMode) {
            RtsbuildingMod.LOGGER.debug(
                "RtsMineManager: {} finished ultimine processed={}/{}",
                player.getDisplayName(),
                au.processedTargets,
                au.totalTargets);
        }
    }

    /**
     * 实际破坏方块。
     * 使用 player.theItemInWorldManager.tryHarvestBlock 以确保：
     * - 正确扣除工具耐久
     * - 触发 Forge BreakEvent
     * - 正确掉落物品（受时运等附魔影响）
     */
    private static void breakBlock(EntityPlayerMP player, World world, ActiveMine mine) {
        Block block = world.getBlock(mine.posX, mine.posY, mine.posZ);
        if (block == null || block == Blocks.air || block.isAir(world, mine.posX, mine.posY, mine.posZ)) {
            return;
        }

        // [调试日志] 确认breakBlock被调用及mine.ultimine值
        RtsbuildingMod.LOGGER.debug(
            "RtsMineManager.breakBlock: player={} pos=({},{},{}) ultimine={} progress={}",
            player.getDisplayName(),
            mine.posX,
            mine.posY,
            mine.posZ,
            mine.ultimine,
            mine.progress);

        int metadata = world.getBlockMetadata(mine.posX, mine.posY, mine.posZ);

        // 触发 Forge BreakEvent（允许其他 mod 取消破坏）
        BlockEvent.BreakEvent breakEvent = new BlockEvent.BreakEvent(
            mine.posX,
            mine.posY,
            mine.posZ,
            world,
            block,
            metadata,
            player);
        if (MinecraftForge.EVENT_BUS.post(breakEvent)) {
            return;
        }

        // 使用玩家 ItemInWorldManager 进行破坏（正确处理工具耐久和掉落）
        boolean removed;
        try {
            // 保存当前位置和工具
            double prevX = player.posX;
            double prevY = player.posY;
            double prevZ = player.posZ;
            ItemStack prevHeld = player.getCurrentEquippedItem();

            // 临时设置玩家位置到方块位置（部分 block 的破坏逻辑需要）
            player.setPositionAndUpdate(mine.posX + 0.5, mine.posY, mine.posZ + 0.5);

            // 临时装备挖掘工具
            if (mine.toolPrototype != null && mine.toolPrototype.getItem() != null) {
                player.inventory.mainInventory[player.inventory.currentItem] = mine.toolPrototype.copy();
            }

            // 使用 tryHarvestBlock 进行破坏（正确处理工具耐久/掉落）
            removed = player.theItemInWorldManager.tryHarvestBlock(mine.posX, mine.posY, mine.posZ);

            // 恢复位置和工具
            player.setPositionAndUpdate(prevX, prevY, prevZ);
            if (prevHeld != null) {
                player.inventory.mainInventory[player.inventory.currentItem] = prevHeld;
            }
            player.inventoryContainer.detectAndSendChanges();
        } catch (Exception e) {
            // 降级：直接 setBlockToAir + 手动掉落
            removed = world.setBlockToAir(mine.posX, mine.posY, mine.posZ);
            if (removed && !player.capabilities.isCreativeMode) {
                block.dropBlockAsItem(world, mine.posX, mine.posY, mine.posZ, metadata, 0);
            }
            RtsbuildingMod.LOGGER.warn(
                "RtsMineManager: tryHarvestBlock failed for {} at ({}, {}, {}), fallback to setBlockToAir: {}",
                player.getDisplayName(),
                mine.posX,
                mine.posY,
                mine.posZ,
                e.getMessage());
        }

        if (removed) {
            // Bug8修复：方块破坏后显式清理存储绑定（setBlockToAir 降级路径不会触发 Forge BreakEvent）
            RtsStorageManager.onLinkedStorageBlockBroken(world, mine.posX, mine.posY, mine.posZ);

            // Bug1.2修复：如果开启了自动存入，吸收周围掉落物到链接存储（需解锁 AUTO_STORE_MINED_DROPS）
            if (player != null && player.getUniqueID() != null) {
                RtsStorageSession session = RtsStorageManager.getSession(player);
                if (session != null && session.isAutoStoreMinedDrops()
                    && RtsProgressionManager.canUse(player, RtsFeature.AUTO_STORE_MINED_DROPS)) {
                    RtsStorageManager.absorbNearbyMinedDrops(player, mine.posX, mine.posY, mine.posZ, session);
                }
            }

            // 播放破坏音效
            world.playAuxSFX(2001, mine.posX, mine.posY, mine.posZ, Block.getIdFromBlock(block) + (metadata << 12));

            // 发送破坏完成消息（stage=10 表示完成）
            S2CRtsMineProgressMessage doneMsg = new S2CRtsMineProgressMessage(
                mine.posX,
                mine.posY,
                mine.posZ,
                (byte) 10);
            RtsNetworkManager.NETWORK.sendTo(doneMsg, player);

            if (Config.debugMode) {
                RtsbuildingMod.LOGGER.debug(
                    "RtsMineManager: {} broke ({}, {}, {}) progress={}",
                    player.getDisplayName(),
                    mine.posX,
                    mine.posY,
                    mine.posZ,
                    mine.progress);
            }

            // 连锁破坏：第一块方块破坏后，触发链式破坏相邻同类方块
            if (mine.ultimine) {
                triggerChainMining(player, world, mine);
            }
        }
    }

    /**
     * 连锁破坏：从被破坏方块出发BFS收集同类方块并直接破坏。
     * 连锁方块不需要破坏进度，基于连锁速度直接破坏。
     * 掉落物绑定自动入库设定。
     * P0-4: 优先使用预收集的 pendingChainBlocks（破坏前 BFS 收集），避免破坏后中心方块为空气。
     */
    private static void triggerChainMining(EntityPlayerMP player, World world, ActiveMine mine) {
        List<int[]> chainTargets;
        if (mine.pendingChainBlocks != null && !mine.pendingChainBlocks.isEmpty()) {
            // 使用预收集的目标（在 startMining 时收集，此时中心方块尚未被破坏）
            chainTargets = mine.pendingChainBlocks;
        } else {
            // 回退：实时 BFS 收集（仅在预收集不可用时使用）
            int limit = RtsProgressionManager.getUltimineLimit(player);
            chainTargets = RtsUltimineCollector.collect(world, mine.posX, mine.posY, mine.posZ, limit, 64);
        }

        boolean autoStore = false;
        RtsStorageSession session = null;
        if (player.getUniqueID() != null) {
            session = RtsStorageManager.getSession(player);
            if (session != null && session.isAutoStoreMinedDrops()
                && RtsProgressionManager.canUse(player, RtsFeature.AUTO_STORE_MINED_DROPS)) {
                autoStore = true;
            }
        }

        int broken = 0;
        for (int[] target : chainTargets) {
            int tx = target[0], ty = target[1], tz = target[2];
            // 跳过已破坏的种子方块
            if (tx == mine.posX && ty == mine.posY && tz == mine.posZ) continue;

            if (!world.blockExists(tx, ty, tz) || world.isAirBlock(tx, ty, tz)) continue;
            Block chainBlock = world.getBlock(tx, ty, tz);
            if (chainBlock == null) continue;
            float hardness = chainBlock.getBlockHardness(world, tx, ty, tz);
            if (hardness < 0) continue;

            int metadata = world.getBlockMetadata(tx, ty, tz);

            // 临时装备工具
            ItemStack prevHeld = player.getCurrentEquippedItem();
            if (mine.toolPrototype != null && mine.toolPrototype.getItem() != null) {
                player.inventory.mainInventory[player.inventory.currentItem] = mine.toolPrototype.copy();
            }

            try {
                boolean chainRemoved = player.theItemInWorldManager.tryHarvestBlock(tx, ty, tz);
                if (chainRemoved) {
                    broken++;
                    RtsStorageManager.onLinkedStorageBlockBroken(world, tx, ty, tz);
                    // 自动入库
                    if (autoStore && session != null) {
                        RtsStorageManager.absorbNearbyMinedDrops(player, tx, ty, tz, session);
                    }
                    world.playAuxSFX(2001, tx, ty, tz, Block.getIdFromBlock(chainBlock) + (metadata << 12));
                }
            } catch (Exception e) {
                boolean chainRemoved = world.setBlockToAir(tx, ty, tz);
                if (chainRemoved) {
                    broken++;
                    if (!player.capabilities.isCreativeMode) {
                        chainBlock.dropBlockAsItem(world, tx, ty, tz, metadata, 0);
                    }
                    world.playAuxSFX(2001, tx, ty, tz, Block.getIdFromBlock(chainBlock) + (metadata << 12));
                }
            }

            // 恢复工具
            if (prevHeld != null) {
                player.inventory.mainInventory[player.inventory.currentItem] = prevHeld;
            }
        }

        player.inventoryContainer.detectAndSendChanges();

        RtsbuildingMod.LOGGER.debug(
            "RtsMineManager: {} chain mined {} blocks from ({},{},{})",
            player.getDisplayName(),
            broken,
            mine.posX,
            mine.posY,
            mine.posZ);
    }

    /**
     * 计算工具对指定方块的挖掘速度。
     * 使用 ForgeHooks.blockStrength() 对齐原版 MC 1.7.10 的速度计算：
     * - 能采集时：breakSpeed / hardness / 30
     * - 不能采集时：breakSpeed / hardness / 100
     * - 自动包含效率附魔、药水效果等
     *
     * 远程挖掘特殊处理：强制 onGround=true（避免不在地面的 /5 惩罚）。
     */
    private static float computeSpeedMultiplier(EntityPlayerMP player, ItemStack tool, Block block, World world, int x,
        int y, int z) {
        float hardness = block.getBlockHardness(world, x, y, z);
        if (hardness < 0) return 0;

        // 临时装备工具到玩家手上（ForgeHooks.blockStrength 读取玩家手持物品）
        ItemStack prevHeld = player.getCurrentEquippedItem();
        if (tool != null && tool.getItem() != null) {
            player.inventory.mainInventory[player.inventory.currentItem] = tool.copy();
        }

        // 远程挖掘：强制 onGround=true，避免不在地面的 /5 惩罚
        boolean prevOnGround = player.onGround;
        player.onGround = true;

        float breakSpeed;
        try {
            breakSpeed = ForgeHooks.blockStrength(block, player, world, x, y, z);
        } finally {
            // 恢复
            player.onGround = prevOnGround;
            player.inventory.mainInventory[player.inventory.currentItem] = prevHeld;
        }

        // ForgeHooks.blockStrength 返回每 tick 进度 = toolSpeed / hardness / 30 (或 /100)
        // 当前公式: increment = BASE_PROGRESS_PER_TICK(1/30) * speedMultiplier
        // 所以 speedMultiplier = breakSpeed * 30
        return Math.max(breakSpeed * 30.0F, 0.0F);
    }

    /**
     * 活跃挖掘状态条目。
     */
    private static class ActiveMine {

        String playerName;
        int posX, posY, posZ;
        byte face;
        byte toolSlot;
        String toolItemId;
        ItemStack toolPrototype;
        boolean allowPlacedBlockRecovery;
        long startTick;
        float progress;
        float speedMultiplier;
        int lastStage;
        boolean ultimine;
        /** P0-4: 连锁预收集目标（在破坏前 BFS 收集，避免破坏后中心方块为空气） */
        List<int[]> pendingChainBlocks;
    }

    /**
     * 连锁挖掘状态条目。
     */
    private static class ActiveUltimine {

        UUID playerUuid;
        int toolSlot;
        String toolItemId;
        ItemStack toolPrototype;
        boolean creative;
        Deque<int[]> targets = new ArrayDeque<>();
        int totalTargets;
        int processedTargets;
        int[] progressPos;
    }

    /**
     * 注册 ServerTickEvent 监听器到 FML 事件总线。
     * 由 CommonProxy.init() 调用。
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
        if (server == null) return;

        // Bug修复：仅在主世界（overworld）处理挖掘进度，避免多维度重复累加
        WorldServer overworld = server.worldServers[0];
        if (overworld != null) {
            onServerTick(overworld);
        }
    }

    /**
     * 注册到 FML 事件总线。在 CommonProxy.init() 中调用。
     */
    public static void register() {
        RtsMineManager instance = new RtsMineManager();
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(instance);
        RtsbuildingMod.LOGGER.info("RtsMineManager: registered ServerTickEvent handler");
    }
}
