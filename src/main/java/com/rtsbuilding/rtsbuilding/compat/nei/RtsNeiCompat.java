package com.rtsbuilding.rtsbuilding.compat.nei;

import com.rtsbuilding.rtsbuilding.client.screen.RtsCraftTerminalScreen;

import codechicken.nei.OffsetPositioner;
import codechicken.nei.api.API;
import cpw.mods.fml.common.Loader;

/**
 * NEI兼容层 — RTSbuilding与NotEnoughItems的软依赖桥接。
 *
 * 1.7.10 使用 codechicken.nei API (NEI 2.x for GTNH)。
 * NEI作为compileOnly依赖，使用直接API调用，运行时检测可用性。
 */
public final class RtsNeiCompat {

    public static final String MOD_ID = "NotEnoughItems";

    private RtsNeiCompat() {}

    /** NEI模组是否已加载 */
    public static boolean isAvailable() {
        return Loader.isModLoaded(MOD_ID);
    }

    /**
     * 注册 RTS Craft Terminal 的 NEI overlay handler。
     * 在客户端初始化时调用（NEI加载后）。
     */
    public static void registerNeiOverlayIfAvailable() {
        if (!isAvailable()) return;

        try {
            // 注册 GUI overlay — 让 NEI 物品面板出现在 RTS Craft Terminal 上
            API.registerGuiOverlay(RtsCraftTerminalScreen.class, "crafting", new OffsetPositioner(5, 11));

            // 注册 overlay handler — 支持 NEI → RTS 配方转移
            API.registerGuiOverlayHandler(RtsCraftTerminalScreen.class, new RtsCraftingOverlayHandler(), "crafting");

        } catch (Exception e) {
            // NEI 不可用或被禁用，静默降级
        }
    }

    /**
     * 检查给定屏幕是否是 RTS Craft Terminal。
     * 用于 NEI guihook 中判断是否需要特殊处理。
     */
    public static boolean isRtsCraftTerminalScreen(Object guiScreen) {
        return guiScreen instanceof RtsCraftTerminalScreen;
    }
}
