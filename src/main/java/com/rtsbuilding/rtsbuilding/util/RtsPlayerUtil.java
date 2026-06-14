package com.rtsbuilding.rtsbuilding.util;

import java.lang.reflect.Method;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

/**
 * 玩家工具类 — 通过反射+SRG名获取UUID，绕过MCP reobfuscation问题。
 *
 * 在1.7.10 Forge运行时环境，Minecraft类使用SRG名称（如func_110124_au），
 * 而非MCP名称（如getUniqueID）。如果mod jar未正确reobfuscate，直接调用
 * MCP方法会触发NoSuchMethodError。
 *
 * 本工具类在运行时通过反射查找SRG方法，确保在任何情况下都能获取UUID。
 */
public final class RtsPlayerUtil {

    /**
     * Entity.getUniqueID() 的SRG名称
     */
    private static final String SRG_GET_UNIQUE_ID = "func_110124_au";

    /**
     * EntityPlayer.getGameProfile() 的SRG名称
     */
    private static final String SRG_GET_GAME_PROFILE = "func_146103_bH";

    private static Method srgGetUniqueIdMethod;
    private static Method srgGetGameProfileMethod;
    private static boolean reflectionInitialized;

    private RtsPlayerUtil() {}

    /**
     * 获取玩家的UUID。按优先级尝试：
     * 1. SRG反射调用 func_110124_au (Entity.getUniqueID)
     * 2. SRG反射调用 func_146103_bH (EntityPlayer.getGameProfile) → getId()
     * 3. 尝试直接调用 MCP getUniqueID（兜底，适用于reobfuscation正常的环境）
     *
     * @param player 目标玩家（EntityPlayer 或 EntityPlayerMP）
     * @return 玩家的UUID，失败时返回nilUUID
     */
    public static UUID getUUID(EntityPlayer player) {
        if (player == null) {
            return new UUID(0L, 0L);
        }

        ensureReflectionInitialized();

        // 策略1：SRG反射 func_110124_au
        if (srgGetUniqueIdMethod != null) {
            try {
                return (UUID) srgGetUniqueIdMethod.invoke(player);
            } catch (Exception e) {
                RtsbuildingMod.LOGGER.debug("RtsPlayerUtil: SRG getUniqueID failed, trying fallback", e);
            }
        }

        // 策略2：SRG反射 func_146103_bH → GameProfile.getId()
        if (srgGetGameProfileMethod != null) {
            try {
                Object profile = srgGetGameProfileMethod.invoke(player);
                if (profile instanceof com.mojang.authlib.GameProfile) {
                    return ((com.mojang.authlib.GameProfile) profile).getId();
                }
            } catch (Exception e) {
                RtsbuildingMod.LOGGER.debug("RtsPlayerUtil: SRG getGameProfile failed", e);
            }
        }

        // 策略3：直接调用MCP方法（适用于reobfuscation正常的环境）
        try {
            return player.getUniqueID();
        } catch (NoSuchMethodError e) {
            RtsbuildingMod.LOGGER
                .warn("RtsPlayerUtil: MCP getUniqueID not available, " + "mod jar may not be reobfuscated");
        }

        RtsbuildingMod.LOGGER
            .error("RtsPlayerUtil: all UUID resolution strategies failed for {}", player.getCommandSenderName());
        return new UUID(0L, 0L);
    }

    private static void ensureReflectionInitialized() {
        if (reflectionInitialized) return;

        try {
            // 从EntityPlayer开始向上搜索Entity类层次
            srgGetUniqueIdMethod = findMethodInHierarchy(EntityPlayer.class, SRG_GET_UNIQUE_ID);
        } catch (Exception e) {
            RtsbuildingMod.LOGGER.warn("RtsPlayerUtil: SRG getUniqueID lookup failed", e);
        }

        try {
            srgGetGameProfileMethod = findMethodInHierarchy(EntityPlayer.class, SRG_GET_GAME_PROFILE);
        } catch (Exception e) {
            RtsbuildingMod.LOGGER.warn("RtsPlayerUtil: SRG getGameProfile lookup failed", e);
        }

        reflectionInitialized = true;
    }

    /**
     * 在类层次中查找指定名称的方法（包括父类）。
     */
    private static Method findMethodInHierarchy(Class<?> clazz, String methodName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                if (m.getName()
                    .equals(methodName)) {
                    m.setAccessible(true);
                    return m;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
