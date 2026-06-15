package com.rtsbuilding.rtsbuilding.client.panel;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.StorageViewModel;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsRequestStoragePageMessage;

/**
 * 排序按钮列 + 高度调节按钮 — 位于底部面板左侧。
 * 对齐原版 BottomPanel 的 sort buttons (S/A/D) 和 height buttons (+/-)。
 *
 * 布局（纵向排列，X=contentX）：
 * S 按钮 — 切换排序字段（name → count）
 * A/D 按钮 — 切换排序方向（asc/desc）
 * + 按钮 — 增大面板高度
 * - 按钮 — 减小面板高度
 * 排序标签 — 显示当前排序名称
 */
public class SortButtonsView {

    public static final int SORT_BUTTON_SIZE = 8;
    private static final int SORT_GAP = 2;
    private static final int HEIGHT_BUTTON_SIZE = 8;
    private static final int COL_WIDTH = 29;

    private final RtsClientState state;

    private int sortX, sortY;
    private int hoveredButton = -1; // 0=S, 1=A/D, 2=+, 3=-

    public SortButtonsView() {
        this.state = RtsClientState.get();
    }

    /** 返回排序列占用宽度 */
    public static int getColumnWidth() {
        return COL_WIDTH;
    }

    /**
     * 渲染排序按钮列。
     * 
     * @param baseX 排序列起始X（contentX）
     * @param baseY 排序列起始Y（contentY + 2）
     */
    public void render(GuiScreen screen, int baseX, int baseY, int mouseX, int mouseY) {
        this.sortX = baseX;
        this.sortY = baseY;

        FontRenderer fr = screen.mc.fontRenderer;

        // ── S 按钮（排序字段切换）──
        int sX = sortX;
        int sY = sortY;
        boolean sHover = isHover(sX, sY, mouseX, mouseY);
        Gui.drawRect(sX, sY, sX + SORT_BUTTON_SIZE, sY + SORT_BUTTON_SIZE, sHover ? 0xCC555555 : 0xBB333333);
        Gui.drawRect(sX, sY, sX + SORT_BUTTON_SIZE, sY + 1, 0xFF6E8799);
        fr.drawString("S", sX + SORT_BUTTON_SIZE / 2 - fr.getStringWidth("S") / 2, sY + 4, 0xFFFFFFFF);
        if (sHover) hoveredButton = 0;

        // ── A/D 按钮（排序方向）──
        int adX = sortX;
        int adY = sortY + SORT_BUTTON_SIZE + SORT_GAP;
        String adLabel = isAscending() ? "A" : "D";
        boolean adHover = isHover(adX, adY, mouseX, mouseY);
        Gui.drawRect(adX, adY, adX + SORT_BUTTON_SIZE, adY + SORT_BUTTON_SIZE, adHover ? 0xCC555555 : 0xBB333333);
        Gui.drawRect(adX, adY, adX + SORT_BUTTON_SIZE, adY + 1, 0xFF6E8799);
        fr.drawString(adLabel, adX + SORT_BUTTON_SIZE / 2 - fr.getStringWidth(adLabel) / 2, adY + 4, 0xFFFFFFFF);
        if (adHover) hoveredButton = 1;

        // ── +/- 按钮（高度调节）──
        int pmX = sortX + SORT_BUTTON_SIZE + 26;
        int plusY = sortY;
        int minusY = sortY + HEIGHT_BUTTON_SIZE + SORT_GAP;
        boolean plusHover = isHover(pmX, plusY, mouseX, mouseY);
        boolean minusHover = isHover(pmX, minusY, mouseX, mouseY);

        Gui.drawRect(
            pmX,
            plusY,
            pmX + HEIGHT_BUTTON_SIZE,
            plusY + HEIGHT_BUTTON_SIZE,
            plusHover ? 0xCC555555 : 0xBB333333);
        Gui.drawRect(pmX, plusY, pmX + HEIGHT_BUTTON_SIZE, plusY + 1, 0xFF6E8799);
        fr.drawString("+", pmX + HEIGHT_BUTTON_SIZE / 2 - fr.getStringWidth("+") / 2, plusY + 4, 0xFFFFFFFF);
        if (plusHover) hoveredButton = 2;

        Gui.drawRect(
            pmX,
            minusY,
            pmX + HEIGHT_BUTTON_SIZE,
            minusY + HEIGHT_BUTTON_SIZE,
            minusHover ? 0xCC555555 : 0xBB333333);
        Gui.drawRect(pmX, minusY, pmX + HEIGHT_BUTTON_SIZE, minusY + 1, 0xFF6E8799);
        fr.drawString("-", pmX + HEIGHT_BUTTON_SIZE / 2 - fr.getStringWidth("-") / 2, minusY + 4, 0xFFFFFFFF);
        if (minusHover) hoveredButton = 3;

        // ── 排序标签 ──
        String sortLabel = getSortLabel();
        fr.drawString(sortLabel, sortX, adY + HEIGHT_BUTTON_SIZE + 4, 0xFFAAAAAA);
    }

    /** 处理点击，返回 true 表示消费 */
    public boolean onMouseClick(int mouseX, int mouseY, int button) {
        if (button != 0) return false;

        // S 按钮
        if (hoveredButton == 0) {
            cycleSortField();
            return true;
        }
        // A/D 按钮
        if (hoveredButton == 1) {
            toggleSortDirection();
            return true;
        }
        // + 按钮
        if (hoveredButton == 2) {
            adjustHeight(1);
            return true;
        }
        // - 按钮
        if (hoveredButton == 3) {
            adjustHeight(-1);
            return true;
        }
        return false;
    }

    public void resetFrameState() {
        hoveredButton = -1;
    }

    // ======== 内部方法 ========

    private boolean isHover(int x, int y, int mx, int my) {
        return mx >= x && mx <= x + SORT_BUTTON_SIZE && my >= y && my <= y + SORT_BUTTON_SIZE;
    }

    private boolean isAscending() {
        return state.storage.sortMode.endsWith("_asc");
    }

    /** S 按钮：切换排序字段 name ↔ count */
    private void cycleSortField() {
        String mode = state.storage.sortMode;
        if (mode.startsWith("name")) {
            state.storage.sortMode = isAscending() ? "count_asc" : "count_desc";
        } else {
            state.storage.sortMode = isAscending() ? "name_asc" : "name_desc";
        }
        requestWithNewSort();
    }

    /** A/D 按钮：切换排序方向 */
    private void toggleSortDirection() {
        String mode = state.storage.sortMode;
        if (mode.endsWith("_asc")) {
            state.storage.sortMode = mode.replace("_asc", "_desc");
        } else {
            state.storage.sortMode = mode.replace("_desc", "_asc");
        }
        requestWithNewSort();
    }

    /** +/- 按钮：调整面板高度 */
    private void adjustHeight(int direction) {
        int newH = state.storage.panelHeight + direction * 11;
        newH = Math.max(StorageViewModel.MIN_PANEL_H, Math.min(StorageViewModel.MAX_PANEL_H, newH));
        state.storage.panelHeight = newH;
    }

    /** 排序变更后重新请求数据 */
    private void requestWithNewSort() {
        state.storage.currentPage = 0;
        state.storage.dirty = true;
        RtsNetworkManager.NETWORK
            .sendToServer(new C2SRtsRequestStoragePageMessage(state.storage.currentPage, 0, state.storage.sortMode));
    }

    private String getSortLabel() {
        String mode = state.storage.sortMode;
        String field = mode.startsWith("count") ? "Qty" : "Name";
        String dir = mode.endsWith("_asc") ? "\u2191" : "\u2193"; // ↑ ↓
        return field + dir;
    }
}
