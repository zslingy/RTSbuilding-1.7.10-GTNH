package com.rtsbuilding.rtsbuilding.client.panel.craft;

import java.awt.Rectangle;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

import com.rtsbuilding.rtsbuilding.client.CraftViewModel.CraftableEntry;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.RtsScreen;
import com.rtsbuilding.rtsbuilding.client.panel.IRtsPanel;
import com.rtsbuilding.rtsbuilding.client.panel.RtsBottomPanel;

/**
 * 可合成物品面板 — 显示 craftableEntries 列表。
 *
 * Bug5修复 (2026-06-11):
 * - isVisible() 改为仅在有合成数据时可见
 * - 坐标从 RtsBottomPanel 动态获取
 * - 集成到 RtsBottomPanel 内部渲染，移除独立 isVisible=true
 */
public class CraftPanelView implements IRtsPanel {

    private static final String PANEL_NAME = "craft_panel";
    private static final int LINE_H = 14;
    private static final int HEADER_H = 16;

    private final RtsClientState state;
    private int panelX, panelY, panelW, panelH;

    public CraftPanelView() {
        this.state = RtsClientState.get();
    }

    @Override
    public String panelName() {
        return PANEL_NAME;
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(panelX, panelY, panelW, panelH);
    }

    /** Bug5修复：仅在有合成数据时可见 */
    @Override
    public boolean isVisible() {
        return state.craft.craftableEntries != null && !state.craft.craftableEntries.isEmpty();
    }

    @Override
    public void render(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        FontRenderer fr = screen.mc.fontRenderer;

        // Bug5修复: 从 RtsBottomPanel 获取坐标
        RtsBottomPanel bp = findBottomPanel(screen);
        if (bp != null) {
            panelX = bp.getCraftX();
            panelY = bp.getCraftY();
            panelW = bp.getCraftW();
            panelH = bp.getCraftH();
        } else {
            // 后备估算
            panelX = screen.width - 8 - 126 - 4;
            panelY = screen.height - 110 - 4 + 18 + 4 + 14 + 2;
            panelW = 126;
            panelH = 80;
        }

        // 面板背景
        Gui.drawRect(panelX, panelY, panelX + panelW, panelY + panelH, 0xCC222233);

        // 标题
        String title = "Craftables";
        fr.drawString(title, panelX + 4, panelY + 3, 0xFFFFFF88);

        // 条目列表
        int y = panelY + HEADER_H + 2;
        for (int i = 0; i < state.craft.craftableEntries.size() && y < panelY + panelH - LINE_H; i++) {
            CraftableEntry entry = state.craft.craftableEntries.get(i);
            String display = (entry != null && entry.displayName != null)
                ? fr.trimStringToWidth(entry.displayName, panelW - 10)
                : "?";
            fr.drawString(display, panelX + 5, y + 2, 0xFFAACCFF);
            y += LINE_H;
        }
    }

    @Override
    public boolean onMouseClick(int mouseX, int mouseY, int button) {
        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH) {
            return false;
        }
        int headerH = 16;
        int lineY = panelY + headerH + 2;
        int craftScroll = state.craft.craftScroll;
        java.util.List<CraftableEntry> entries = state.craft.craftableEntries;
        if (entries == null || entries.isEmpty()) {
            state.craft.recipesDirty = true;
            com.rtsbuilding.rtsbuilding.network.RtsNetworkManager.NETWORK.sendToServer(
                new com.rtsbuilding.rtsbuilding.network.craft.C2SRtsRequestCraftablesMessage(
                    "",
                    false,
                    0,
                    50,
                    false,
                    null));
            return true;
        }
        for (int i = craftScroll; i < entries.size() && i < craftScroll + 8; i++) {
            int ey = lineY + (i - craftScroll) * 14;
            if (mouseY >= ey && mouseY <= ey + 14 && mouseX >= panelX + 4 && mouseX <= panelX + panelW - 4) {
                CraftableEntry entry = entries.get(i);
                if (entry != null && button == 0) {
                    state.craft.quantityDialogOpen = true;
                    state.craft.quantityDialogItemId = entry.itemId;
                    state.craft.quantityDialogCount = Math.min(entry.craftableCount, 64);
                    state.craft.quantityDialogMax = Math.min(entry.craftableCount, 64);
                    state.interaction.addRecentBlock(entry.itemId);
                }
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean onMouseScroll(int mouseX, int mouseY, int scroll) {
        return false;
    }

    @Override
    public boolean onKeyTyped(char c, int keyCode) {
        return false;
    }

    @Override
    public void resetFrameState() {}

    private static RtsBottomPanel findBottomPanel(GuiScreen screen) {
        if (screen instanceof RtsScreen) {
            IRtsPanel panel = ((RtsScreen) screen).getPanel(RtsBottomPanel.PANEL_NAME);
            if (panel instanceof RtsBottomPanel) {
                return (RtsBottomPanel) panel;
            }
        }
        return null;
    }
}
