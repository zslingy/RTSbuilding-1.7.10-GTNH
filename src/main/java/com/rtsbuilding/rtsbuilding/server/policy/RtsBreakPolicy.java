package com.rtsbuilding.rtsbuilding.server.policy;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;

/**
 * RTS 方块破坏策略 — 验证方块破坏操作的合法性。
 *
 * 阶段B 实现：
 * - 范围检查（必须在 RTS 相机操作半径内）
 * - RTS 模式检查（必须在 RTS 俯视模式中）
 * - 不可破坏方块检查（基岩、空气等）
 * - GTNH 兼容：GT 工具/机器方块暂不限制
 */
public final class RtsBreakPolicy {

    private RtsBreakPolicy() {}

    /**
     * 检查玩家是否可以在给定位置破坏方块。
     *
     * 验证流程：
     * 1. 玩家必须在 RTS 相机模式中
     * 2. 目标位置必须在操作范围内
     * 3. 目标方块不能是空气
     * 4. 目标方块不能是不可破坏的（硬度 < 0，如基岩）
     */
    public static boolean canBreakBlock(EntityPlayerMP player, World world, int x, int y, int z) {
        if (player == null || world == null) return false;

        // 1. 必须在 RTS 模式中
        if (!RtsCameraManager.isActive(player)) return false;

        // 2. 必须在操作范围内
        if (!RtsCameraManager.isWithinActionRange(player, x, y, z)) return false;

        // 3. 目标方块存在
        Block block = world.getBlock(x, y, z);
        if (block == null || block.isAir(world, x, y, z)) return false;

        // 4. 不可破坏检查（硬度 < 0 表示不可破坏，如基岩）
        if (block.getBlockHardness(world, x, y, z) < 0) return false;

        // 5. 玩家不能是旁观者（1.7.10 无旁观模式，保留扩展点）

        return true;
    }

    /**
     * 检查是否允许 ultimine 破坏模式。
     * 阶段B：仅检查 RTS 模式，后续接入进度系统。
     */
    public static boolean canUltimine(EntityPlayerMP player) {
        if (player == null) return false;
        return RtsCameraManager.isActive(player);
    }

    /**
     * 检查是否允许 area destroy 模式。
     * 阶段B：仅检查 RTS 模式，后续接入进度系统。
     */
    public static boolean canAreaDestroy(EntityPlayerMP player) {
        if (player == null) return false;
        return RtsCameraManager.isActive(player);
    }
}
