package com.rtsbuilding.rtsbuilding.client.panel;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.StorageViewModel;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsRequestStoragePageMessage;

/**
 * 排序按钮列 — 位于底部面板左侧。
 *
 * 布局（纵向排列）：
 * S 按钮 — 切换排序字段（name → count → mod）
 * A/D 按钮 — 切换排序方向（asc/desc）
 * 排序标签 — 显示当前排序（列下方）
 * + 按钮 — 增大面板高度（右列上方）
 * - 按钮 — 减小面板高度（右列下方）
 */
public class SortButtonsView {

    public static final int SORT_BUTTON_SIZE = 16;
    private static final int SORT_GAP = 4;
    private static final int COL_WIDTH = 42;

    private final RtsClientState state;

    private int sortX, sortY;
    private int hoveredButton = -1; // 0=S, 1=A/D, 2=+, 3=-

    public SortButtonsView() {
        this.state = RtsClientState.get();
    }

    public static int getColumnWidth() {
        return COL_WIDTH;
    }

    public void render(GuiScreen screen, int baseX, int baseY, int mouseX, int mouseY) {
        this.sortX = baseX;
        this.sortY = baseY;

        FontRenderer fr = screen.mc.fontRenderer;

        // ── S 按钮（排序字段切换）──
        int sX = sortX;
        int sY = sortY;
        boolean sHover = isHover(sX, sY, mouseX, mouseY);
        Gui.drawRect(sX, sY, sX + SORT_BUTTON_SIZE, sY + SORT_BUTTON_SIZE, sHover ? 0xCC41576F : 0xAA29323D);
        Gui.drawRect(sX, sY, sX + SORT_BUTTON_SIZE, sY + 1, 0xFF6E8799);
        fr.drawString(
            "S",
            sX + SORT_BUTTON_SIZE / 2 - fr.getStringWidth("S") / 2,
            sY + (SORT_BUTTON_SIZE - 8) / 2,
            0xEAF4FF);
        if (sHover) hoveredButton = 0;

        // ── A/D 按钮（排序方向）──
        int adX = sortX;
        int adY = sortY + SORT_BUTTON_SIZE + SORT_GAP;
        String adLabel = isAscending() ? "A" : "D";
        boolean adHover = isHover(adX, adY, mouseX, mouseY);
        Gui.drawRect(adX, adY, adX + SORT_BUTTON_SIZE, adY + SORT_BUTTON_SIZE, adHover ? 0xCC41576F : 0xAA29323D);
        Gui.drawRect(adX, adY, adX + SORT_BUTTON_SIZE, adY + 1, 0xFF6E8799);
        fr.drawString(
            adLabel,
            adX + SORT_BUTTON_SIZE / 2 - fr.getStringWidth(adLabel) / 2,
            adY + (SORT_BUTTON_SIZE - 8) / 2,
            0xEAF4FF);
        if (adHover) hoveredButton = 1;

        // ── 排序标签（按钮列右侧）──
        String sortLabel = getSortLabel();
        fr.drawString(sortLabel, sortX + SORT_BUTTON_SIZE + 4, sortY + (SORT_BUTTON_SIZE - 8) / 2 + 2, 0xFFFFFF);

        // ── +/- 按钮（高度调节，右列）──
        int pmX = sortX + SORT_BUTTON_SIZE + 26;
        int plusY = sortY;
        int minusY = sortY + SORT_BUTTON_SIZE + SORT_GAP;
        boolean plusHover = isHover(pmX, plusY, mouseX, mouseY);
        boolean minusHover = isHover(pmX, minusY, mouseX, mouseY);

        Gui.drawRect(pmX, plusY, pmX + SORT_BUTTON_SIZE, plusY + SORT_BUTTON_SIZE, plusHover ? 0xCC41576F : 0xAA29323D);
        Gui.drawRect(pmX, plusY, pmX + SORT_BUTTON_SIZE, plusY + 1, 0xFF6E8799);
        fr.drawString(
            "+",
            pmX + SORT_BUTTON_SIZE / 2 - fr.getStringWidth("+") / 2,
            plusY + (SORT_BUTTON_SIZE - 8) / 2,
            0xEAF4FF);
        if (plusHover) hoveredButton = 2;

        Gui.drawRect(
            pmX,
            minusY,
            pmX + SORT_BUTTON_SIZE,
            minusY + SORT_BUTTON_SIZE,
            minusHover ? 0xCC41576F : 0xAA29323D);
        Gui.drawRect(pmX, minusY, pmX + SORT_BUTTON_SIZE, minusY + 1, 0xFF6E8799);
        fr.drawString(
            "-",
            pmX + SORT_BUTTON_SIZE / 2 - fr.getStringWidth("-") / 2,
            minusY + (SORT_BUTTON_SIZE - 8) / 2,
            0xEAF4FF);
        if (minusHover) hoveredButton = 3;
    }

    public boolean onMouseClick(int mouseX, int mouseY, int button) {
        if (button != 0) return false;

        if (hoveredButton == 0) {
            cycleSortField();
            return true;
        }
        if (hoveredButton == 1) {
            toggleSortDirection();
            return true;
        }
        if (hoveredButton == 2) {
            adjustHeight(1);
            return true;
        }
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

    /** S 按钮：切换排序字段 name → count → mod → name */
    private void cycleSortField() {
        String mode = state.storage.sortMode;
        if (mode.startsWith("name")) {
            state.storage.sortMode = isAscending() ? "count_asc" : "count_desc";
        } else if (mode.startsWith("count")) {
            state.storage.sortMode = isAscending() ? "mod_asc" : "mod_desc";
        } else {
            state.storage.sortMode = isAscending() ? "name_asc" : "name_desc";
        }
        requestWithNewSort();
    }

    private void toggleSortDirection() {
        String mode = state.storage.sortMode;
        if (mode.endsWith("_asc")) {
            state.storage.sortMode = mode.replace("_asc", "_desc");
        } else {
            state.storage.sortMode = mode.replace("_desc", "_asc");
        }
        requestWithNewSort();
    }

    private void adjustHeight(int direction) {
        int newH = state.storage.panelHeight + direction * 22;
        newH = Math.max(StorageViewModel.MIN_PANEL_H, Math.min(StorageViewModel.MAX_PANEL_H, newH));
        state.storage.panelHeight = newH;
    }

    private void requestWithNewSort() {
        state.storage.currentPage = 0;
        state.storage.dirty = true;
        RtsNetworkManager.NETWORK
            .sendToServer(new C2SRtsRequestStoragePageMessage(state.storage.currentPage, 0, state.storage.sortMode));
    }

    private String getSortLabel() {
        String mode = state.storage.sortMode;
        String field;
        if (mode.startsWith("count")) {
            field = "Qty";
        } else if (mode.startsWith("mod")) {
            field = "Mod";
        } else {
            field = "Name";
        }
        String dir = mode.endsWith("_asc") ? "\u2191" : "\u2193";
        return field + dir;
    }
}
