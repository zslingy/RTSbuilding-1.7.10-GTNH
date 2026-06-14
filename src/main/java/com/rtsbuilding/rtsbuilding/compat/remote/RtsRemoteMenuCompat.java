package com.rtsbuilding.rtsbuilding.compat.remote;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

/**
 * 远程菜单兼容层 —— 跟踪玩家当前打开的远程容器，
 * 使原版 Container.canInteractWith() 检查绕过距离限制。
 */
public final class RtsRemoteMenuCompat {

    /** 玩家UUID → 活跃的远程容器实例 */
    private static final Map<UUID, Container> remoteSessions = new ConcurrentHashMap<>();

    private RtsRemoteMenuCompat() {}

    /**
     * 由 Mixin 调用：判断是否应绕过原版的 canInteractWith 检查。
     * 仅当玩家当前持有活跃的远程菜单会话且容器实例匹配时返回 true。
     */
    public static boolean shouldForceStillValid(Container container, EntityPlayer player) {
        if (container == null || player == null) return false;
        Container tracked = remoteSessions.get(player.getUniqueID());
        return tracked != null && tracked == container;
    }

    /**
     * 当玩家通过 RTS 打开远程容器时调用，开始跟踪会话。
     */
    public static void beginRemoteSession(EntityPlayer player, Container container) {
        if (player == null || container == null) return;
        remoteSessions.put(player.getUniqueID(), container);
    }

    /**
     * 当玩家关闭远程容器时调用，清除跟踪。
     */
    public static void endRemoteSession(EntityPlayer player) {
        if (player == null) return;
        remoteSessions.remove(player.getUniqueID());
    }

    /**
     * 检查玩家是否拥有活跃的远程菜单会话。
     */
    public static boolean hasActiveSession(EntityPlayer player) {
        if (player == null) return false;
        return remoteSessions.containsKey(player.getUniqueID());
    }
}
