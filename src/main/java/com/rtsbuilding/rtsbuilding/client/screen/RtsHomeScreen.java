package com.rtsbuilding.rtsbuilding.client.screen;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;

/**
 * RTS 主页屏幕 — 进度/技能树概览。
 * 阶段6实现：显示家园坐标、冷却时间、已解锁节点列表。
 */
public class RtsHomeScreen extends GuiScreen {

    private final RtsClientState state;

    public RtsHomeScreen() {
        this.state = RtsClientState.get();
    }

    @Override
    public void initGui() {
        state.resetForNewSession();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        // 标题区域
        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal("screen.rtsbuilding.home_screen.title"),
            width / 2,
            20,
            0xFFCCCC44);

        // 家园信息（来自 ProgressionViewModel）
        int infoY = 50;
        drawCenteredString(fontRendererObj, "\u00a7fHome Position", width / 2, infoY, 0xFFFFFFFF);

        String homeCoords = (state.progression.homeX != 0 || state.progression.homeY != 0
            || state.progression.homeZ != 0)
                ? String.format(
                    "X: %d  Y: %d  Z: %d",
                    state.progression.homeX,
                    state.progression.homeY,
                    state.progression.homeZ)
                : StatCollector.translateToLocal("screen.rtsbuilding.home_screen.home_not_set");
        drawCenteredString(fontRendererObj, homeCoords, width / 2, infoY + 16, 0xFFAAAAAA);

        // 进度概览
        infoY = 100;
        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal("screen.rtsbuilding.home_screen.progression"),
            width / 2,
            infoY,
            0xFFFFFFFF);

        int unlocked = state.progression.getUnlockedCount();
        int total = RtsFeature.values().length;
        String progressText = StatCollector
            .translateToLocalFormatted("screen.rtsbuilding.home_screen.nodes_unlocked", unlocked, total);
        drawCenteredString(fontRendererObj, progressText, width / 2, infoY + 16, 0xFFAAAAAA);

        // 进度条
        if (total > 0) {
            int barWidth = 200;
            int barX = width / 2 - barWidth / 2;
            int barY = infoY + 34;
            int filledWidth = (int) ((float) unlocked / total * barWidth);
            net.minecraft.client.gui.Gui.drawRect(barX, barY, barX + barWidth, barY + 8, 0xCC333333);
            net.minecraft.client.gui.Gui.drawRect(barX, barY, barX + filledWidth, barY + 8, 0xCC44AA44);
        }

        // 存储信息
        infoY = 170;
        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal("screen.rtsbuilding.storage.title"),
            width / 2,
            infoY,
            0xFFFFFFFF);
        String storageInfo = state.storage.linkedStorageCount > 0
            ? String.format(
                StatCollector.translateToLocalFormatted(
                    "screen.rtsbuilding.home_screen.storage_connected",
                    state.storage.linkedStorageCount,
                    state.storage.entries.size()))
            : StatCollector.translateToLocal("screen.rtsbuilding.home_screen.storage_not_linked");
        drawCenteredString(fontRendererObj, storageInfo, width / 2, infoY + 16, 0xFFAAAAAA);

        // 底部提示
        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal("screen.rtsbuilding.home_screen.press_esc"),
            width / 2,
            height - 20,
            0xFF666666);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
